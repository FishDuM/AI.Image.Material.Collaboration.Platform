package hk.ljx.fishpicsbackend.collab;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import hk.ljx.fishpicsbackend.collab.CollabMessageFactory.Crop;
import hk.ljx.fishpicsbackend.collab.CollabMessageFactory.PictureUserMessage;
import hk.ljx.fishpicsbackend.collab.CollabMessageFactory.TransformMessage;
import hk.ljx.fishpicsbackend.collab.CollabMessageFactory.UserMessage;
import hk.ljx.fishpicsbackend.common.constants.RedisConstants;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.infra.JwtUtils;
import hk.ljx.fishpicsbackend.space.component.SpacePermissionChecker;
import hk.ljx.fishpicsbackend.mapper.PictureMapper;
import hk.ljx.fishpicsbackend.mapper.SpaceMapper;
import hk.ljx.fishpicsbackend.picture.entity.Picture;
import hk.ljx.fishpicsbackend.space.entity.Space;
import hk.ljx.fishpicsbackend.user.entity.User;
import hk.ljx.fishpicsbackend.user.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class CollabWebSocketHandler extends TextWebSocketHandler {

    private static final String ATTR_USER_ID = "userId";
    private static final String ATTR_SPACE_ID = "spaceId";
    private static final String ATTR_NICKNAME = "nickname";
    private static final String ATTR_AVATAR = "avatar";
    private static final int MAX_WS_MESSAGE_SIZE = 64 * 1024;

    @Resource
    private JwtUtils jwtUtils;

    @Resource
    private UserService userService;

    @Resource
    private SpaceMapper spaceMapper;

    @Resource
    private SpacePermissionChecker spacePermissionChecker;

    @Resource
    private PictureMapper pictureMapper;

    @Resource
    private CollabSessionRegistry sessionRegistry;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        SessionContext context = resolveSessionContext(session);
        if (context == null) return;

        session.getAttributes().put(ATTR_USER_ID, context.userId());
        session.getAttributes().put(ATTR_SPACE_ID, context.spaceId());
        session.getAttributes().put(ATTR_NICKNAME, context.nickname());
        session.getAttributes().put(ATTR_AVATAR, context.avatar());

        sessionRegistry.addSession(context.spaceId(), context.userId(), session, context.nickname(), context.avatar());
        handleJoin(context.spaceId(), context.userId(), context.nickname(), context.avatar());

        log.info("[CollabWS] 已连接: space={}, user={}", context.spaceId(), context.userId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Long userId = getAttr(session, ATTR_USER_ID);
        Long spaceId = getAttr(session, ATTR_SPACE_ID);
        if (userId == null || spaceId == null) return;

        if (message.getPayloadLength() > MAX_WS_MESSAGE_SIZE) {
            log.warn("[CollabWS] 消息过大: user={}, size={}", userId, message.getPayloadLength());
            return;
        }

        try {
            JSONObject data = JSONUtil.parseObj(message.getPayload());
            String type = data.getStr("type");
            if (type == null) return;

            switch (type) {
                case "transform" -> handleTransform(session, data, spaceId, userId);
                case "lock" -> handleLock(session, data, spaceId, userId);
                case "unlock" -> handleUnlock(data, spaceId, userId);
                case "file-replaced" -> log.warn("[CollabWS] 拒绝客户端文件替换: user={}, pictureId={}",
                        userId, data.get("pictureId"));
                case "resync" -> handleResync(spaceId, userId);
                default -> log.debug("[CollabWS] 未知消息类型: {}", type);
            }
        } catch (Exception e) {
            log.warn("[CollabWS] 处理消息失败: user={}, type={}", userId,
                    extractType(message.getPayload()), e);
            sendErrorToClient(session, "操作失败: " + e.getMessage());
        }
    }

    private String extractType(String payload) {
        try {
            return JSONUtil.parseObj(payload).getStr("type", "?");
        } catch (Exception ignored) {
            return "?";
        }
    }

    private void sendErrorToClient(WebSocketSession session, String msg) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(
                        JSONUtil.toJsonStr(Map.of("type", "error", "message", msg))));
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = getAttr(session, ATTR_USER_ID);
        Long spaceId = getAttr(session, ATTR_SPACE_ID);
        if (userId != null && spaceId != null) {
            handleDisconnect(spaceId, userId, session.getId());
        } else {
            log.info("[CollabWS] 断开连接(无属性): status={}", status);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        Long userId = getAttr(session, ATTR_USER_ID);
        Long spaceId = getAttr(session, ATTR_SPACE_ID);
        log.warn("[CollabWS] 传输错误: userId={}", userId, exception);
        if (userId != null && spaceId != null) {
            handleDisconnect(spaceId, userId, session.getId());
        }
        try {
            if (session.isOpen()) {
                session.close(CloseStatus.SERVER_ERROR.withReason("transport error"));
            }
        } catch (Exception ignored) {
        }
    }

    private void handleTransform(WebSocketSession session, JSONObject data, Long spaceId, Long userId) {
        Long pictureId = getLong(data, "pictureId");
        if (!isEditLockHolder(pictureId, spaceId, userId)) {
            log.warn("[CollabWS] 无编辑锁拒绝变换: space={}, picture={}, user={}",
                    spaceId, pictureId, userId);
            return;
        }

        Double scale = getDouble(data, "scale");
        Integer rotation = getInteger(data, "rotation");
        if (scale == null || rotation == null) return;

        Crop crop = parseCrop(data.get("crop"));
        if (data.get("crop") != null && crop == null) return;

        var transform = new TransformMessage(
                pictureId, scale, rotation, userId, getAttrStr(session, ATTR_NICKNAME), crop);
        String outgoing = CollabMessageFactory.transform(transform);
        sessionRegistry.updatePictureState(spaceId, pictureId, outgoing);
        sessionRegistry.broadcastAll(spaceId, outgoing);

        log.debug("[Collab] 变换广播: space={}, picture={}, online={}",
                spaceId, pictureId, sessionRegistry.getOnlineUserIds(spaceId).size());
    }

    private void handleLock(WebSocketSession session, JSONObject data, Long spaceId, Long userId) {
        Long pictureId = getLong(data, "pictureId");
        if (!isPictureInSpace(pictureId, spaceId)) return;

        String nickname = getAttrStr(session, ATTR_NICKNAME);
        boolean acquired = sessionRegistry.tryAcquireEditLock(spaceId, pictureId, userId, nickname);
        if (acquired) {
            sessionRegistry.broadcastAll(spaceId,
                    CollabMessageFactory.lock(new PictureUserMessage(pictureId, userId, nickname)));
            log.debug("[Collab] 锁已获取: space={}, picture={}, user={}", spaceId, pictureId, userId);
            return;
        }

        var lock = sessionRegistry.getSpaceLock(spaceId, pictureId);
        sessionRegistry.sendToUser(spaceId, userId, CollabMessageFactory.lockDenied(pictureId, lock));
        log.debug("[Collab] 锁被拒绝: space={}, picture={}, user={}", spaceId, pictureId, userId);
    }

    private void handleUnlock(JSONObject data, Long spaceId, Long userId) {
        Long pictureId = getLong(data, "pictureId");

        boolean released = sessionRegistry.releaseEditLock(spaceId, pictureId, userId);
        if (released) {
            broadcastUnlockAndClearState(spaceId, pictureId, userId);
            log.debug("[Collab] 锁已释放: space={}, picture={}, user={}", spaceId, pictureId, userId);
        }
    }

    private void handleJoin(Long spaceId, Long userId, String nickname, String avatar) {
        sessionRegistry.broadcast(spaceId, userId,
                CollabMessageFactory.join(new UserMessage(userId, nickname, avatar)));
        sessionRegistry.sendToUser(spaceId, userId,
                CollabMessageFactory.presence(sessionRegistry.getSpaceSessions(spaceId)));
        sendCurrentState(spaceId, userId);
    }

    private void handleDisconnect(Long spaceId, Long userId, String sessionId) {
        sessionRegistry.removeSession(spaceId, userId, sessionId);

        List<Long> pictureIds = sessionRegistry.clearLocksByUserInSpace(userId, spaceId);
        for (Long pictureId : pictureIds) {
            broadcastUnlockAndClearState(spaceId, pictureId, userId);
            log.debug("[Collab] 断开连接用户锁已释放: space={}, picture={}", spaceId, pictureId);
        }

        sessionRegistry.broadcast(spaceId, userId, CollabMessageFactory.leave(userId));
        log.debug("[Collab] 用户断开已处理: space={}, user={}", spaceId, userId);
    }

    private void handleResync(Long spaceId, Long userId) {
        sendCurrentState(spaceId, userId);
        log.info("[CollabWS] 重新同步: space={}, user={}", spaceId, userId);
    }

    private void sendCurrentState(Long spaceId, Long userId) {
        for (var entry : sessionRegistry.getSpaceSessions(spaceId).entrySet()) {
            var info = entry.getValue();
            sessionRegistry.sendToUser(spaceId, userId,
                    CollabMessageFactory.join(new UserMessage(
                            entry.getKey(), info.getNickname(), info.getAvatar())));
        }
        for (var lock : sessionRegistry.getSpaceLocks(spaceId)) {
            sessionRegistry.sendToUser(spaceId, userId,
                    CollabMessageFactory.lock(new PictureUserMessage(
                            lock.getPictureId(), lock.getUserId(), lock.getNickname())));
        }
        for (String stateJson : sessionRegistry.getSpacePictureStates(spaceId)) {
            sessionRegistry.sendToUser(spaceId, userId, stateJson);
        }
    }

    private void broadcastUnlockAndClearState(Long spaceId, Long pictureId, Long userId) {
        sessionRegistry.broadcastAll(spaceId, CollabMessageFactory.unlock(pictureId, userId));
        sessionRegistry.clearPictureState(spaceId, pictureId);
    }

    private SessionContext resolveSessionContext(WebSocketSession session) throws Exception {
        String token = getParam(session, "token");
        String spaceIdStr = getParam(session, "spaceId");
        if (token == null || spaceIdStr == null) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("missing params"));
            return null;
        }

        Long userId = jwtUtils.getUserId(token);
        if (userId == null || jwtUtils.isBlacklisted(token)) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("auth failed"));
            return null;
        }

        Long spaceId = parseLong(spaceIdStr);
        if (spaceId == null) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("invalid spaceId"));
            return null;
        }

        Space space = spaceMapper.selectById(spaceId);
        if (space == null || !ExcUtils.eq(space.getStatus(), 1)
                || !spacePermissionChecker.canAccess(space, userId)) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("no space access"));
            return null;
        }

        User user = userService.getById(userId);
        if (user == null) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("user not found"));
            return null;
        }
        if (!user.isActive()) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("user disabled"));
            return null;
        }
        Boolean isBanned = stringRedisTemplate.opsForSet().isMember(
                RedisConstants.BANNED_USERS_KEY, userId.toString());
        if (Boolean.TRUE.equals(isBanned)) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("user banned"));
            return null;
        }

        String nickname = user.getNickname() != null ? user.getNickname() : "";
        String avatar = user.getAvatar() != null ? user.getAvatar() : "";
        return new SessionContext(spaceId, userId, nickname, avatar);
    }

    private record SessionContext(Long spaceId, Long userId, String nickname, String avatar) {
    }

    private String getParam(WebSocketSession session, String name) {
        var uri = session.getUri();
        if (uri == null) return null;
        return UriComponentsBuilder.fromUri(uri)
                .build()
                .getQueryParams()
                .getFirst(name);
    }

    @SuppressWarnings("unchecked")
    private <T> T getAttr(WebSocketSession session, String key) {
        Object val = session.getAttributes().get(key);
        return val != null ? (T) val : null;
    }

    private String getAttrStr(WebSocketSession session, String key) {
        Object val = session.getAttributes().get(key);
        return val instanceof String ? (String) val : "";
    }

    private boolean isPictureInSpace(Long pictureId, Long spaceId) {
        if (pictureId == null || spaceId == null) return false;
        Picture picture = pictureMapper.selectById(pictureId);
        return picture != null && spaceId.equals(picture.getSpaceId());
    }

    private boolean isEditLockHolder(Long pictureId, Long spaceId, Long userId) {
        return sessionRegistry.isEditLockHolder(spaceId, pictureId, userId);
    }

    private Long getLong(JSONObject data, String key) {
        return parseLong(data.get(key));
    }

    private Double getDouble(JSONObject data, String key) {
        Object value = data.get(key);
        if (value == null) return null;
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer getInteger(JSONObject data, String key) {
        return parseInteger(data.get(key));
    }

    private Crop parseCrop(Object value) {
        if (value == null) return null;
        if (!(value instanceof Map<?, ?> crop)) return null;

        Integer x = parseInteger(crop.get("x"));
        Integer y = parseInteger(crop.get("y"));
        Integer w = parseInteger(crop.get("w"));
        Integer h = parseInteger(crop.get("h"));
        if (x == null || y == null || w == null || h == null) return null;
        return new Crop(x, y, w, h);
    }

    private Long parseLong(Object value) {
        if (value == null) return null;
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseInteger(Object value) {
        if (value == null) return null;
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
