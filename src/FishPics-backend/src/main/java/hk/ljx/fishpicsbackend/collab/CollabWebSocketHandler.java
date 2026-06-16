package hk.ljx.fishpicsbackend.collab;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import hk.ljx.fishpicsbackend.common.constants.RedisConstants;
import hk.ljx.fishpicsbackend.common.infra.JwtUtils;
import hk.ljx.fishpicsbackend.mapper.PictureMapper;
import hk.ljx.fishpicsbackend.mapper.SpaceTeamMemberMapper;
import hk.ljx.fishpicsbackend.picture.entity.Picture;
import hk.ljx.fishpicsbackend.space.entity.SpaceTeamMember;
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

import java.util.Map;

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
    private SpaceTeamMemberMapper teamMemberMapper;

    @Resource
    private PictureMapper pictureMapper;

    @Resource
    private CollabSessionRegistry sessionRegistry;

    @Resource
    private CollabCoordinator collabCoordinator;

    @Resource
    private CollabLockCoordinator lockCoordinator;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = getParam(session, "token");
        String spaceIdStr = getParam(session, "spaceId");

        if (token == null || spaceIdStr == null) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("missing params"));
            return;
        }

        Long userId = jwtUtils.getUserId(token);
        if (userId == null || jwtUtils.isBlacklisted(token)) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("auth failed"));
            return;
        }

        Long spaceId = parseLong(spaceIdStr);
        if (spaceId == null) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("invalid spaceId"));
            return;
        }

        if (!isTeamMember(spaceId, userId)) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("not team member"));
            return;
        }

        User user = userService.getById(userId);
        if (user == null) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("user not found"));
            return;
        }
        if (user.getStatus() == null || !Integer.valueOf(1).equals(user.getStatus())) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("user disabled"));
            return;
        }
        Boolean isBanned = stringRedisTemplate.opsForSet().isMember(
                RedisConstants.BANNED_USERS_KEY, userId.toString());
        if (Boolean.TRUE.equals(isBanned)) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("user banned"));
            return;
        }

        String nickname = user.getNickname() != null ? user.getNickname() : "";
        String avatar = user.getAvatar() != null ? user.getAvatar() : "";
        session.getAttributes().put(ATTR_USER_ID, userId);
        session.getAttributes().put(ATTR_SPACE_ID, spaceId);
        session.getAttributes().put(ATTR_NICKNAME, nickname);
        session.getAttributes().put(ATTR_AVATAR, avatar);

        sessionRegistry.addSession(spaceId, userId, session, nickname, avatar);
        collabCoordinator.handleJoin(spaceId, userId, nickname, avatar);

        log.info("[CollabWS] connected: space={}, user={}", spaceId, userId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Long userId = getAttr(session, ATTR_USER_ID);
        Long spaceId = getAttr(session, ATTR_SPACE_ID);
        if (userId == null || spaceId == null) return;

        if (message.getPayloadLength() > MAX_WS_MESSAGE_SIZE) {
            log.warn("[CollabWS] message too large: user={}, size={}", userId, message.getPayloadLength());
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
                case "file-replaced" -> log.warn("[CollabWS] rejected client file-replaced: user={}, pictureId={}",
                        userId, data.get("pictureId"));
                case "resync" -> handleResync(spaceId, userId);
                default -> log.debug("[CollabWS] unknown message type: {}", type);
            }
        } catch (Exception e) {
            log.warn("[CollabWS] failed to handle message: user={}", userId, e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = getAttr(session, ATTR_USER_ID);
        Long spaceId = getAttr(session, ATTR_SPACE_ID);
        if (userId != null && spaceId != null) {
            lockCoordinator.handleDisconnect(spaceId, userId, session.getId());
        } else {
            log.info("[CollabWS] disconnected without attrs, status={}", status);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("[CollabWS] transport error: user={}", getAttr(session, ATTR_USER_ID), exception);
    }

    private void handleTransform(WebSocketSession session, JSONObject data, Long spaceId, Long userId) {
        Long pictureId = getLong(data, "pictureId");
        if (!isPictureInSpace(pictureId, spaceId)) return;
        if (!isLockHolder(pictureId, spaceId, userId)) {
            log.warn("[CollabWS] rejected transform without edit lock: space={}, picture={}, user={}",
                    spaceId, pictureId, userId);
            return;
        }

        Integer cropX = null;
        Integer cropY = null;
        Integer cropW = null;
        Integer cropH = null;
        Object cropObj = data.get("crop");
        if (cropObj instanceof Map<?, ?> crop) {
            cropX = parseInteger(crop.get("x"));
            cropY = parseInteger(crop.get("y"));
            cropW = parseInteger(crop.get("w"));
            cropH = parseInteger(crop.get("h"));
        }

        collabCoordinator.handleTransform(
                spaceId,
                pictureId,
                userId,
                getAttrStr(session, ATTR_NICKNAME),
                getDouble(data, "scale"),
                getInteger(data, "rotation"),
                cropX,
                cropY,
                cropW,
                cropH);
    }

    private void handleLock(WebSocketSession session, JSONObject data, Long spaceId, Long userId) {
        Long pictureId = getLong(data, "pictureId");
        if (!isPictureInSpace(pictureId, spaceId)) return;

        lockCoordinator.handleLock(spaceId, pictureId, userId, getAttrStr(session, ATTR_NICKNAME));
    }

    private void handleUnlock(JSONObject data, Long spaceId, Long userId) {
        Long pictureId = getLong(data, "pictureId");
        if (!isPictureInSpace(pictureId, spaceId)) return;

        lockCoordinator.handleUnlock(spaceId, pictureId, userId);
    }

    private void handleResync(Long spaceId, Long userId) {
        sessionRegistry.getAllPictureLocks().forEach((pictureId, lock) -> {
            if (!spaceId.equals(lock.getSpaceId())) return;
            sessionRegistry.sendToUser(spaceId, userId,
                    CollabMessageFactory.lock(pictureId, lock.getUserId(), lock.getNickname()));
        });
        for (String stateJson : sessionRegistry.getSpacePictureStates(spaceId)) {
            sessionRegistry.sendToUser(spaceId, userId, stateJson);
        }
        log.info("[CollabWS] resync: space={}, user={}, lockCount={}",
                spaceId, userId, sessionRegistry.getAllPictureLocks().size());
    }

    private boolean isTeamMember(Long spaceId, Long userId) {
        var memberQuery = new LambdaQueryWrapper<SpaceTeamMember>()
                .eq(SpaceTeamMember::getSpaceId, spaceId)
                .eq(SpaceTeamMember::getUserId, userId)
                .select(SpaceTeamMember::getRoleId);
        return teamMemberMapper.selectOne(memberQuery) != null;
    }

    private String getParam(WebSocketSession session, String name) {
        var uri = session.getUri();
        if (uri == null) return null;
        return org.springframework.web.util.UriComponentsBuilder.fromUri(uri)
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

    private boolean isLockHolder(Long pictureId, Long spaceId, Long userId) {
        return sessionRegistry.isLockHolder(spaceId, pictureId, userId);
    }

    private Long getLong(JSONObject data, String key) {
        return parseLong(data.get(key));
    }

    private Double getDouble(JSONObject data, String key) {
        Object value = data.get(key);
        return value == null ? null : Double.parseDouble(value.toString());
    }

    private Integer getInteger(JSONObject data, String key) {
        return parseInteger(data.get(key));
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
        return Integer.parseInt(value.toString());
    }
}
