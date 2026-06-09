package hk.ljx.fishpicsbackend.collab;

import hk.ljx.fishpicsbackend.collab.model.CollabEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Disruptor 事件消费处理器
 * 运行在 Disruptor 的消费者线程，与 WebSocket I/O 线程完全隔离
 *
 * 职责：接收事件 → 广播给同空间其他用户
 * 变换状态完全存在于内存（Session 级），无人在线时自然丢弃
 */
@Slf4j
@Component
public class CollabEventHandler implements com.lmax.disruptor.EventHandler<CollabEvent> {

    private CollabSessionRegistry sessionRegistry;

    /**
     * 由 DisruptorConfig 注入（解决循环依赖）
     */
    public void setSessionRegistry(CollabSessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public void onEvent(CollabEvent event, long sequence, boolean endOfBatch) {
        try {
            if (event.getSpaceId() == null) return;
            switch (event.getType()) {
                case CollabEvent.TYPE_TRANSFORM -> handleTransform(event);
                case CollabEvent.TYPE_JOIN -> handleJoin(event);
                case CollabEvent.TYPE_LEAVE -> handleLeave(event);
                case CollabEvent.TYPE_LOCK -> handleLock(event);
                case CollabEvent.TYPE_UNLOCK -> handleUnlock(event);
                case CollabEvent.TYPE_REQUEST_EDIT -> handleRequestEdit(event);
                case CollabEvent.TYPE_APPROVE -> handleApprove(event);
                case CollabEvent.TYPE_DENY -> handleDeny(event);
                default -> { }
            }
        } catch (Exception e) {
            log.error("协同事件处理异常: type={}, picture={}", event.getType(), event.getPictureId(), e);
        } finally {
            event.clear();
        }
    }

    private void handleTransform(CollabEvent event) {
        if (event.getPictureId() == null || event.getScale() == null || event.getRotation() == null) {
            return;
        }
        if (sessionRegistry != null) {
            var json = new cn.hutool.json.JSONObject();
            json.set("type", "transform");
            json.set("pictureId", event.getPictureId());
            json.set("scale", event.getScale());
            json.set("rotation", event.getRotation());
            json.set("userId", event.getUserId());
            json.set("nickname", event.getNickname() != null ? event.getNickname() : "");
            // 裁剪区域（可选）
            if (event.getCropX() != null) {
                json.set("crop", Map.of(
                        "x", event.getCropX(),
                        "y", event.getCropY(),
                        "w", event.getCropW(),
                        "h", event.getCropH()));
            }
            String msg = json.toString();
            // 缓存最新 transform 状态，供新用户加入时同步
            sessionRegistry.updatePictureState(event.getSpaceId(), event.getPictureId(), msg);
            int onlineCount = sessionRegistry.getOnlineUserIds(event.getSpaceId()).size();
            log.info("[CollabDisruptor] 广播 transform: space={}, picture={}, scale={}, rotation={}, crop={}, 在线人数={}",
                    event.getSpaceId(), event.getPictureId(), event.getScale(), event.getRotation(),
                    event.getCropX() != null ? "yes" : "no", onlineCount);
            sessionRegistry.broadcastAll(event.getSpaceId(), msg);
        } else {
            log.warn("[CollabDisruptor] sessionRegistry 为 null，无法广播");
        }
    }

    private void handleJoin(CollabEvent event) {
        if (sessionRegistry == null) return;
        String joinMsg = cn.hutool.json.JSONUtil.toJsonStr(Map.of(
                "type", "join",
                "userId", event.getUserId(),
                "nickname", event.getNickname() != null ? event.getNickname() : "",
                "avatar", event.getAvatar() != null ? event.getAvatar() : ""
        ));
        sessionRegistry.broadcast(event.getSpaceId(), event.getUserId(), joinMsg);

        // 向新加入用户发送当前在线用户列表
        var onlineUsers = sessionRegistry.getSpaceSessions(event.getSpaceId()).entrySet().stream()
                .map(e -> Map.of(
                        "userId", e.getKey(),
                        "nickname", e.getValue().getNickname() != null ? e.getValue().getNickname() : "",
                        "avatar", e.getValue().getAvatar() != null ? e.getValue().getAvatar() : ""
                ))
                .toList();
        String presenceMsg = cn.hutool.json.JSONUtil.toJsonStr(Map.of(
                "type", "presence",
                "users", onlineUsers
        ));
        sessionRegistry.sendToUser(event.getSpaceId(), event.getUserId(), presenceMsg);

        // 向新用户推送当前图片编辑状态（让迟到者看到最新 transform）
        for (String stateJson : sessionRegistry.getSpacePictureStates(event.getSpaceId())) {
            sessionRegistry.sendToUser(event.getSpaceId(), event.getUserId(), stateJson);
        }
    }

    private void handleLeave(CollabEvent event) {
        if (sessionRegistry == null) return;
        String leaveMsg = cn.hutool.json.JSONUtil.toJsonStr(Map.of(
                "type", "leave",
                "userId", event.getUserId()
        ));
        sessionRegistry.broadcast(event.getSpaceId(), event.getUserId(), leaveMsg);
    }

    private void handleLock(CollabEvent event) {
        if (sessionRegistry == null || event.getPictureId() == null) return;
        Long pictureId = event.getPictureId();
        Long userId = event.getUserId();
        String nickname = event.getNickname() != null ? event.getNickname() : "";

        boolean acquired = sessionRegistry.tryLockPicture(pictureId, userId, nickname);
        if (acquired) {
            // 锁定成功，广播给所有人
            String lockMsg = cn.hutool.json.JSONUtil.toJsonStr(Map.of(
                    "type", "lock",
                    "pictureId", pictureId,
                    "userId", userId,
                    "nickname", nickname
            ));
            sessionRegistry.broadcastAll(event.getSpaceId(), lockMsg);
            log.info("[CollabDisruptor] 锁定成功: picture={}, user={}", pictureId, userId);
        } else {
            // 锁定失败，通知请求者
            var lock = sessionRegistry.getPictureLock(pictureId);
            String deniedMsg = cn.hutool.json.JSONUtil.toJsonStr(Map.of(
                    "type", "lock-denied",
                    "pictureId", pictureId,
                    "userId", lock != null ? lock.getUserId() : 0,
                    "nickname", lock != null ? lock.getNickname() : ""
            ));
            sessionRegistry.sendToUser(event.getSpaceId(), userId, deniedMsg);
            log.info("[CollabDisruptor] 锁定拒绝: picture={}, user={}, 持有者={}", pictureId, userId,
                    lock != null ? lock.getNickname() : "unknown");
        }
    }

    private void handleUnlock(CollabEvent event) {
        if (sessionRegistry == null || event.getPictureId() == null) return;
        Long pictureId = event.getPictureId();
        Long userId = event.getUserId();

        boolean released = sessionRegistry.unlockPicture(pictureId, userId);
        if (!released) return;

        // 1. 优先转给排队的申请人
        var requests = sessionRegistry.getEditRequests(pictureId);
        if (!requests.isEmpty()) {
            var next = requests.iterator().next();
            sessionRegistry.removeEditRequest(pictureId, next.getUserId());
            sessionRegistry.tryLockPicture(pictureId, next.getUserId(), next.getNickname());
            broadcastTransfer(event.getSpaceId(), pictureId, userId, next.getUserId(), next.getNickname());
            log.info("[CollabDisruptor] 转让编辑权(排队): picture={}, from={}, to={}", pictureId, userId, next.getUserId());
            return;
        }

        // 2. 队列为空，转给其他在线用户
        var sessions = sessionRegistry.getSpaceSessions(event.getSpaceId());
        var nextUser = sessions.entrySet().stream()
                .filter(e -> !e.getKey().equals(userId))
                .findFirst()
                .orElse(null);
        if (nextUser != null) {
            Long toUserId = nextUser.getKey();
            String toNickname = nextUser.getValue().getNickname() != null ? nextUser.getValue().getNickname() : "";
            sessionRegistry.tryLockPicture(pictureId, toUserId, toNickname);
            broadcastTransfer(event.getSpaceId(), pictureId, userId, toUserId, toNickname);
            log.info("[CollabDisruptor] 转让编辑权(在线): picture={}, from={}, to={}", pictureId, userId, toUserId);
        } else {
            // 无其他人在线，释放锁
            String unlockMsg = cn.hutool.json.JSONUtil.toJsonStr(Map.of(
                    "type", "unlock", "pictureId", pictureId, "userId", userId));
            sessionRegistry.broadcastAll(event.getSpaceId(), unlockMsg);
            sessionRegistry.clearPictureState(event.getSpaceId(), pictureId);
            log.info("[CollabDisruptor] 解锁(无人在线): picture={}", pictureId);
        }
    }

    private void broadcastTransfer(Long spaceId, Long pictureId, Long fromUserId, Long toUserId, String toNickname) {
        String msg = cn.hutool.json.JSONUtil.toJsonStr(Map.of(
                "type", "lock-transfer",
                "pictureId", pictureId,
                "fromUserId", fromUserId,
                "toUserId", toUserId,
                "toNickname", toNickname != null ? toNickname : ""));
        sessionRegistry.broadcastAll(spaceId, msg);
    }

    private void handleRequestEdit(CollabEvent event) {
        if (sessionRegistry == null || event.getPictureId() == null) return;
        Long pictureId = event.getPictureId();
        Long requesterId = event.getUserId();
        String nickname = event.getNickname() != null ? event.getNickname() : "";
        String avatar = event.getAvatar() != null ? event.getAvatar() : "";

        // 记录申请
        sessionRegistry.addEditRequest(pictureId, requesterId, nickname, avatar);

        // 通知锁持有者（有人申请编辑权）
        var lock = sessionRegistry.getPictureLock(pictureId);
        if (lock != null) {
            String requestMsg = cn.hutool.json.JSONUtil.toJsonStr(Map.of(
                    "type", "request-edit",
                    "pictureId", pictureId,
                    "userId", requesterId,
                    "nickname", nickname,
                    "avatar", avatar
            ));
            sessionRegistry.sendToUser(event.getSpaceId(), lock.getUserId(), requestMsg);
            log.info("[CollabDisruptor] 编辑申请: picture={}, requester={}, editor={}",
                    pictureId, requesterId, lock.getUserId());
        }
    }

    private void handleApprove(CollabEvent event) {
        if (sessionRegistry == null || event.getPictureId() == null) return;
        Long pictureId = event.getPictureId();
        Long editorId = event.getUserId();
        Long targetUserId;
        try {
            targetUserId = Long.parseLong(event.getNickname());
        } catch (NumberFormatException e) { return; }

        // 验证审批者是当前锁持有者
        var lock = sessionRegistry.getPictureLock(pictureId);
        if (lock == null || !lock.getUserId().equals(editorId)) return;

        // 移除申请记录
        sessionRegistry.removeEditRequest(pictureId, targetUserId);

        // 解除当前锁，转移给申请人
        sessionRegistry.unlockPicture(pictureId, editorId);
        var requester = sessionRegistry.getSpaceSessions(event.getSpaceId()).get(targetUserId);
        String requesterName = requester != null ? requester.getNickname() : "";
        sessionRegistry.tryLockPicture(pictureId, targetUserId, requesterName);

        // 广播锁转移
        String transferMsg = cn.hutool.json.JSONUtil.toJsonStr(Map.of(
                "type", "lock-transfer",
                "pictureId", pictureId,
                "fromUserId", editorId,
                "toUserId", targetUserId,
                "toNickname", requesterName
        ));
        sessionRegistry.broadcastAll(event.getSpaceId(), transferMsg);
        log.info("[CollabDisruptor] 编辑权转移: picture={}, from={}, to={}", pictureId, editorId, targetUserId);
    }

    private void handleDeny(CollabEvent event) {
        if (sessionRegistry == null || event.getPictureId() == null) return;
        Long pictureId = event.getPictureId();
        Long editorId = event.getUserId();
        Long targetUserId;
        try {
            targetUserId = Long.parseLong(event.getNickname());
        } catch (NumberFormatException e) { return; }

        // 验证审批者是当前锁持有者
        var lock = sessionRegistry.getPictureLock(pictureId);
        if (lock == null || !lock.getUserId().equals(editorId)) return;

        // 移除申请记录
        sessionRegistry.removeEditRequest(pictureId, targetUserId);

        // 通知申请人被拒绝
        String denyMsg = cn.hutool.json.JSONUtil.toJsonStr(Map.of(
                "type", "edit-denied",
                "pictureId", pictureId
        ));
        sessionRegistry.sendToUser(event.getSpaceId(), targetUserId, denyMsg);
        log.info("[CollabDisruptor] 编辑申请拒绝: picture={}, editor={}, target={}", pictureId, editorId, targetUserId);
    }
}
