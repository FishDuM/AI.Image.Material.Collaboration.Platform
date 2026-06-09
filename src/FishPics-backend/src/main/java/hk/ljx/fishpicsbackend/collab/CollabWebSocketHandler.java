package hk.ljx.fishpicsbackend.collab;

import cn.hutool.json.JSONUtil;
import hk.ljx.fishpicsbackend.collab.model.CollabEvent;
import hk.ljx.fishpicsbackend.common.utils.JwtUtils;
import hk.ljx.fishpicsbackend.mapper.SpaceTeamMemberMapper;
import hk.ljx.fishpicsbackend.user.entity.User;
import hk.ljx.fishpicsbackend.user.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

/**
 * 协同编辑 WebSocket 处理器
 *
 * 连接生命周期：afterConnectionEstablished → handleTextMessage → afterConnectionClosed
 * 认证：握手阶段从 URL 参数提取 JWT，验证有效性 + 黑名单 + 团队成员资格
 * 事件流：收到消息 → 解析 → 丰富用户信息 → 发布到 Disruptor Ring Buffer（非阻塞）
 */
@Slf4j
@Component
public class CollabWebSocketHandler extends TextWebSocketHandler {

    @Resource
    private JwtUtils jwtUtils;

    @Resource
    private UserService userService;

    @Resource
    private SpaceTeamMemberMapper teamMemberMapper;

    @Resource
    private CollabSessionRegistry sessionRegistry;

    @Resource
    private CollabEventPublisher eventPublisher;

    private static final String ATTR_USER_ID = "userId";
    private static final String ATTR_SPACE_ID = "spaceId";

    /**
     * WebSocket 连接建立：JWT 认证 + 团队成员校验 + 注册会话
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = getParam(session, "token");
        String spaceIdStr = getParam(session, "spaceId");

        // 1. 校验参数
        if (token == null || spaceIdStr == null) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("缺少参数"));
            return;
        }

        // 2. JWT 认证
        Long userId = jwtUtils.getUserId(token);
        if (userId == null || jwtUtils.isBlacklisted(token)) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("认证失败"));
            return;
        }

        // 3. 解析空间 ID
        Long spaceId;
        try {
            spaceId = Long.parseLong(spaceIdStr);
        } catch (NumberFormatException e) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("无效的空间ID"));
            return;
        }

        // 4. 验证团队成员资格（owner=1 或 member=2）
        var memberQuery = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<hk.ljx.fishpicsbackend.space.entity.SpaceTeamMember>()
                .eq(hk.ljx.fishpicsbackend.space.entity.SpaceTeamMember::getSpaceId, spaceId)
                .eq(hk.ljx.fishpicsbackend.space.entity.SpaceTeamMember::getUserId, userId)
                .select(hk.ljx.fishpicsbackend.space.entity.SpaceTeamMember::getRoleId);
        var member = teamMemberMapper.selectOne(memberQuery);
        if (member == null) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("非团队成员"));
            return;
        }

        // 5. 获取用户信息并注册会话
        User user = userService.getById(userId);
        if (user == null) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("用户不存在"));
            return;
        }

        session.getAttributes().put(ATTR_USER_ID, userId);
        session.getAttributes().put(ATTR_SPACE_ID, spaceId);
        sessionRegistry.addSession(spaceId, userId, session, user.getNickname(), user.getAvatar());

        // 6. 发布加入事件到 Disruptor（通知其他用户）
        eventPublisher.publish(event -> {
            event.setType(CollabEvent.TYPE_JOIN);
            event.setSpaceId(spaceId);
            event.setUserId(userId);
            event.setNickname(user.getNickname());
            event.setAvatar(user.getAvatar());
        });

        log.info("协同编辑连接建立: space={}, user={}", spaceId, userId);
    }

    /**
     * 收到客户端消息：解析 JSON → 丰富用户信息 → 发布到 Disruptor（非阻塞）
     * I/O 线程仅做 JSON 解析和事件发布，实际处理在 Disruptor 消费者线程
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Long userId = getAttr(session, ATTR_USER_ID);
        Long spaceId = getAttr(session, ATTR_SPACE_ID);
        if (userId == null || spaceId == null) return;

        try {
            Map<String, Object> data = JSONUtil.parseObj(message.getPayload());
            String type = (String) data.get("type");
            if (type == null) return;

            switch (type) {
                case "transform" -> {
                    Long pictureId = data.get("pictureId") != null
                            ? Long.parseLong(data.get("pictureId").toString()) : null;
                    Double scale = data.get("scale") != null
                            ? Double.parseDouble(data.get("scale").toString()) : null;
                    Integer rotation = data.get("rotation") != null
                            ? Integer.parseInt(data.get("rotation").toString()) : null;
                    // 裁剪区域（可选）
                    Object cropObj = data.get("crop");
                    Integer cropX = null, cropY = null, cropW = null, cropH = null;
                    if (cropObj instanceof Map) {
                        Map<String, Object> crop = (Map<String, Object>) cropObj;
                        cropX = crop.get("x") != null ? Integer.parseInt(crop.get("x").toString()) : null;
                        cropY = crop.get("y") != null ? Integer.parseInt(crop.get("y").toString()) : null;
                        cropW = crop.get("w") != null ? Integer.parseInt(crop.get("w").toString()) : null;
                        cropH = crop.get("h") != null ? Integer.parseInt(crop.get("h").toString()) : null;
                    }

                    log.info("[CollabWS] 收到 transform: user={}, picture={}, scale={}, rotation={}, crop={}",
                            userId, pictureId, scale, rotation, cropObj);

                    final Integer finalCropX = cropX, finalCropY = cropY, finalCropW = cropW, finalCropH = cropH;
                    eventPublisher.publish(event -> {
                        event.setType(CollabEvent.TYPE_TRANSFORM);
                        event.setPictureId(pictureId);
                        event.setSpaceId(spaceId);
                        event.setUserId(userId);
                        event.setScale(scale);
                        event.setRotation(rotation);
                        event.setCropX(finalCropX);
                        event.setCropY(finalCropY);
                        event.setCropW(finalCropW);
                        event.setCropH(finalCropH);
                    });
                }
                case "lock" -> {
                    Long pictureId = data.get("pictureId") != null
                            ? Long.parseLong(data.get("pictureId").toString()) : null;
                    if (pictureId == null) return;
                    User user = userService.getById(userId);
                    String nickname = user != null ? user.getNickname() : "";
                    log.info("[CollabWS] 收到 lock: user={}, picture={}", userId, pictureId);
                    eventPublisher.publish(event -> {
                        event.setType(CollabEvent.TYPE_LOCK);
                        event.setPictureId(pictureId);
                        event.setSpaceId(spaceId);
                        event.setUserId(userId);
                        event.setNickname(nickname);
                    });
                }
                case "unlock" -> {
                    Long pictureId = data.get("pictureId") != null
                            ? Long.parseLong(data.get("pictureId").toString()) : null;
                    if (pictureId == null) return;
                    log.info("[CollabWS] 收到 unlock: user={}, picture={}", userId, pictureId);
                    eventPublisher.publish(event -> {
                        event.setType(CollabEvent.TYPE_UNLOCK);
                        event.setPictureId(pictureId);
                        event.setSpaceId(spaceId);
                        event.setUserId(userId);
                    });
                }
                case "request-edit" -> {
                    Long pictureId = data.get("pictureId") != null
                            ? Long.parseLong(data.get("pictureId").toString()) : null;
                    if (pictureId == null) return;
                    User user = userService.getById(userId);
                    String nickname = user != null ? user.getNickname() : "";
                    String avatar = user != null ? user.getAvatar() : "";
                    log.info("[CollabWS] 收到 request-edit: user={}, picture={}", userId, pictureId);
                    eventPublisher.publish(event -> {
                        event.setType(CollabEvent.TYPE_REQUEST_EDIT);
                        event.setPictureId(pictureId);
                        event.setSpaceId(spaceId);
                        event.setUserId(userId);
                        event.setNickname(nickname);
                        event.setAvatar(avatar);
                    });
                }
                case "approve" -> {
                    Long pictureId = data.get("pictureId") != null
                            ? Long.parseLong(data.get("pictureId").toString()) : null;
                    Long targetUserId = data.get("targetUserId") != null
                            ? Long.parseLong(data.get("targetUserId").toString()) : null;
                    if (pictureId == null || targetUserId == null) return;
                    log.info("[CollabWS] 收到 approve: editor={}, target={}, picture={}", userId, targetUserId, pictureId);
                    eventPublisher.publish(event -> {
                        event.setType(CollabEvent.TYPE_APPROVE);
                        event.setPictureId(pictureId);
                        event.setSpaceId(spaceId);
                        event.setUserId(userId);
                        // targetUserId 通过 nickname 字段传递（复用字段）
                        event.setNickname(targetUserId.toString());
                    });
                }
                case "deny" -> {
                    Long pictureId = data.get("pictureId") != null
                            ? Long.parseLong(data.get("pictureId").toString()) : null;
                    Long targetUserId = data.get("targetUserId") != null
                            ? Long.parseLong(data.get("targetUserId").toString()) : null;
                    if (pictureId == null || targetUserId == null) return;
                    log.info("[CollabWS] 收到 deny: editor={}, target={}, picture={}", userId, targetUserId, pictureId);
                    eventPublisher.publish(event -> {
                        event.setType(CollabEvent.TYPE_DENY);
                        event.setPictureId(pictureId);
                        event.setSpaceId(spaceId);
                        event.setUserId(userId);
                        event.setNickname(targetUserId.toString());
                    });
                }
                case "file-replaced" -> {
                    // 透传给同空间其他用户（不走 Disruptor，直接广播）
                    sessionRegistry.broadcast(spaceId, userId, message.getPayload());
                    log.info("[CollabWS] 文件已替换，通知其他用户: user={}, pictureId={}", userId, data.get("pictureId"));
                }
                default -> log.debug("未知消息类型: {}", type);
            }
        } catch (Exception e) {
            log.warn("处理协同消息异常: user={}", userId, e);
        }
    }

    /**
     * 连接关闭：注销会话 → 发布离开事件
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = getAttr(session, ATTR_USER_ID);
        Long spaceId = getAttr(session, ATTR_SPACE_ID);
        if (userId != null && spaceId != null) {
            sessionRegistry.removeSession(spaceId, userId);
            // 清除该用户持有的图片编辑锁，自动转让或广播 unlock
            java.util.Set<Long> unlockedPictures = sessionRegistry.clearLocksByUser(userId);
            for (Long pid : unlockedPictures) {
                // 优先转给排队申请人
                var requests = sessionRegistry.getEditRequests(pid);
                if (!requests.isEmpty()) {
                    var next = requests.iterator().next();
                    sessionRegistry.removeEditRequest(pid, next.getUserId());
                    sessionRegistry.tryLockPicture(pid, next.getUserId(), next.getNickname());
                    String msg = cn.hutool.json.JSONUtil.toJsonStr(java.util.Map.of(
                            "type", "lock-transfer", "pictureId", pid,
                            "fromUserId", userId, "toUserId", next.getUserId(),
                            "toNickname", next.getNickname() != null ? next.getNickname() : ""));
                    sessionRegistry.broadcastAll(spaceId, msg);
                    log.info("[CollabWS] 断线转让(排队): picture={}, to={}", pid, next.getUserId());
                    continue;
                }
                // 队列空，转给其他在线用户
                var sessions = sessionRegistry.getSpaceSessions(spaceId);
                var nextUser = sessions.entrySet().stream()
                        .filter(e -> !e.getKey().equals(userId)).findFirst().orElse(null);
                if (nextUser != null) {
                    Long toId = nextUser.getKey();
                    String toName = nextUser.getValue().getNickname() != null ? nextUser.getValue().getNickname() : "";
                    sessionRegistry.tryLockPicture(pid, toId, toName);
                    String msg = cn.hutool.json.JSONUtil.toJsonStr(java.util.Map.of(
                            "type", "lock-transfer", "pictureId", pid,
                            "fromUserId", userId, "toUserId", toId, "toNickname", toName));
                    sessionRegistry.broadcastAll(spaceId, msg);
                    log.info("[CollabWS] 断线转让(在线): picture={}, to={}", pid, toId);
                } else {
                    String msg = cn.hutool.json.JSONUtil.toJsonStr(java.util.Map.of(
                            "type", "unlock", "pictureId", pid, "userId", userId));
                    sessionRegistry.broadcastAll(spaceId, msg);
                    sessionRegistry.clearPictureState(spaceId, pid);
                }
            }
            // 清除该用户发起的编辑申请
            sessionRegistry.clearEditRequestsByUser(userId);
            eventPublisher.publish(event -> {
                event.setType(CollabEvent.TYPE_LEAVE);
                event.setSpaceId(spaceId);
                event.setUserId(userId);
            });
            log.info("协同编辑连接关闭: space={}, user={}, status={}", spaceId, userId, status);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("WebSocket 传输错误: user={}", getAttr(session, ATTR_USER_ID), exception);
    }

    private String getParam(WebSocketSession session, String name) {
        var uri = session.getUri();
        if (uri == null || uri.getQuery() == null) return null;
        for (String param : uri.getQuery().split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2 && kv[0].equals(name)) return kv[1];
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private <T> T getAttr(WebSocketSession session, String key) {
        Object val = session.getAttributes().get(key);
        return val != null ? (T) val : null;
    }
}
