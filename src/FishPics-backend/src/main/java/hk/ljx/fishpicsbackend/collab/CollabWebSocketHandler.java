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
 * 事件流：收到消息 → 解析 → 发布到 Disruptor Ring Buffer（非阻塞）
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

    @Resource
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    private static final String ATTR_USER_ID = "userId";
    private static final String ATTR_SPACE_ID = "spaceId";
    private static final String ATTR_NICKNAME = "nickname";
    private static final String ATTR_AVATAR = "avatar";

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
        // 与 HTTP 拦截器对齐，校验 user.status（避免封禁用户绕过拦截器连 WS）
        if (user.getStatus() == null || !Integer.valueOf(1).equals(user.getStatus())) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("账号已被禁用"));
            return;
        }
        // 校验 BANNED_USERS 集合（与 TokenRefreshInterceptor 一致）
        Boolean isBanned = stringRedisTemplate.opsForSet().isMember(
                hk.ljx.fishpicsbackend.common.constants.RedisConstants.BANNED_USERS_KEY, userId.toString());
        if (Boolean.TRUE.equals(isBanned)) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("账号已被封禁"));
            return;
        }

        session.getAttributes().put(ATTR_USER_ID, userId);
        session.getAttributes().put(ATTR_SPACE_ID, spaceId);
        session.getAttributes().put(ATTR_NICKNAME, user.getNickname() != null ? user.getNickname() : "");
        session.getAttributes().put(ATTR_AVATAR, user.getAvatar() != null ? user.getAvatar() : "");
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
     * 收到客户端消息：解析 JSON → 发布到 Disruptor（非阻塞）
     */
    // WS 消息 size 上限 64KB
    private static final int MAX_WS_MESSAGE_SIZE = 64 * 1024;

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Long userId = getAttr(session, ATTR_USER_ID);
        Long spaceId = getAttr(session, ATTR_SPACE_ID);
        if (userId == null || spaceId == null) return;

        if (message.getPayloadLength() > MAX_WS_MESSAGE_SIZE) {
            log.warn("[CollabWS] 消息过大(>{}B),丢弃: user={}, size={}",
                    MAX_WS_MESSAGE_SIZE, userId, message.getPayloadLength());
            return;
        }

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
                    String nickname = getAttrStr(session, ATTR_NICKNAME);
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
                    String nickname = getAttrStr(session, ATTR_NICKNAME);
                    String avatar = getAttrStr(session, ATTR_AVATAR);
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
                        // 从专用字段读取 targetUserId
                        event.setTargetUserId(targetUserId);
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
                        event.setTargetUserId(targetUserId);
                    });
                }
                case "file-replaced" -> {
                    log.warn("[CollabWS] 拒绝客户端伪造的 file-replaced: user={}, pictureId={}",
                            userId, data.get("pictureId"));
                }
                case "resync" -> {
                    // 重连后遍历 sessionRegistry 给当前 user 重发所有 pictureLocks
                    sessionRegistry.getAllPictureLocks().forEach((pid, lock) -> {
                        if (!spaceId.equals(lock.getSpaceId())) return;
                        try {
                            String lockMsg = JSONUtil.toJsonStr(java.util.Map.of(
                                    "type", "lock",
                                    "pictureId", pid,
                                    "userId", lock.getUserId(),
                                    "nickname", lock.getNickname() != null ? lock.getNickname() : ""
                            ));
                            sessionRegistry.sendToUser(spaceId, userId, lockMsg);
                        } catch (Exception ex) {
                            log.warn("[CollabWS] resync 锁推送失败: {}", ex.getMessage());
                        }
                    });
                    // 重发该空间的 transform 状态
                    for (String stateJson : sessionRegistry.getSpacePictureStates(spaceId)) {
                        sessionRegistry.sendToUser(spaceId, userId, stateJson);
                    }
                    log.info("[CollabWS] 重连 resync: space={}, user={}, 重发锁数={}", spaceId, userId,
                            sessionRegistry.getAllPictureLocks().size());
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
            // 断连后把锁转移/清理/广播全部通过 Disruptor 异步处理
            eventPublisher.publish(event -> {
                event.setType(CollabEvent.TYPE_DISCONNECT);
                event.setSpaceId(spaceId);
                event.setUserId(userId);
                event.setSessionId(session.getId());
            });
        } else {
            log.info("[CollabWS] disconnect without attrs, status={}", status);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("WebSocket 传输错误: user={}", getAttr(session, ATTR_USER_ID), exception);
    }

    private String getParam(WebSocketSession session, String name) {
        // 用 UriComponentsBuilder 处理 URL 编码（JWT 内含 Base64 可能被 URL 编码）
        var uri = session.getUri();
        if (uri == null) return null;
        var queryParams = org.springframework.web.util.UriComponentsBuilder.fromUri(uri)
                .build()
                .getQueryParams();
        return queryParams.getFirst(name);
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
}
