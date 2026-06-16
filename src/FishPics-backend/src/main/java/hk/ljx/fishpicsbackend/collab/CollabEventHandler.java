package hk.ljx.fishpicsbackend.collab;

import hk.ljx.fishpicsbackend.collab.model.CollabEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Disruptor 事件消费处理器
 * 运行在 Disruptor 的消费者线程，与 WebSocket I/O 线程完全隔离
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
                case CollabEvent.TYPE_DISCONNECT -> handleDisconnect(event);
                case CollabEvent.TYPE_FILE_REPLACED -> handleFileReplaced(event);
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

        boolean acquired = sessionRegistry.tryLockPicture(pictureId, userId, nickname, event.getSpaceId());
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
            boolean locked = sessionRegistry.tryLockPicture(pictureId, next.getUserId(), next.getNickname(), event.getSpaceId());
            if (locked) {
                broadcastTransfer(event.getSpaceId(), pictureId, userId, next.getUserId(), next.getNickname());
                log.info("[CollabDisruptor] 转让编辑权(排队): picture={}, from={}, to={}", pictureId, userId, next.getUserId());
            } else {
                // 锁转移失败，广播解锁
                String unlockMsg = cn.hutool.json.JSONUtil.toJsonStr(Map.of(
                        "type", "unlock", "pictureId", pictureId, "userId", userId));
                sessionRegistry.broadcastAll(event.getSpaceId(), unlockMsg);
                log.warn("[CollabDisruptor] 转让编辑权失败(排队): picture={}, target={}", pictureId, next.getUserId());
            }
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
            boolean locked = sessionRegistry.tryLockPicture(pictureId, toUserId, toNickname, event.getSpaceId());
            if (locked) {
                broadcastTransfer(event.getSpaceId(), pictureId, userId, toUserId, toNickname);
                log.info("[CollabDisruptor] 转让编辑权(在线): picture={}, from={}, to={}", pictureId, userId, toUserId);
            } else {
                // 锁转移失败，广播解锁
                String unlockMsg = cn.hutool.json.JSONUtil.toJsonStr(Map.of(
                        "type", "unlock", "pictureId", pictureId, "userId", userId));
                sessionRegistry.broadcastAll(event.getSpaceId(), unlockMsg);
                log.warn("[CollabDisruptor] 转让编辑权失败(在线): picture={}, target={}", pictureId, toUserId);
            }
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
        // 从专用 targetUserId 字段读取
        Long targetUserId = event.getTargetUserId();
        if (targetUserId == null) return;

        // 验证审批者是当前锁持有者
        var lock = sessionRegistry.getPictureLock(pictureId);
        if (lock == null || !lock.getUserId().equals(editorId)) return;

        // 移除申请记录
        sessionRegistry.removeEditRequest(pictureId, targetUserId);

        // 先检查目标用户是否在线，再解锁和转移锁
        // 防止锁转给已离线的用户导致锁永久卡住
        var requester = sessionRegistry.getSpaceSessions(event.getSpaceId()).get(targetUserId);
        if (requester == null) {
            // 目标用户已离线，通知审批者，不转移锁
            String offlineMsg = cn.hutool.json.JSONUtil.toJsonStr(Map.of(
                    "type", "edit-denied",
                    "pictureId", pictureId,
                    "reason", "target_offline"
            ));
            sessionRegistry.sendToUser(event.getSpaceId(), editorId, offlineMsg);
            log.info("[CollabDisruptor] 审批取消(目标离线): picture={}, editor={}, target={}", pictureId, editorId, targetUserId);
            return;
        }

        // 解除当前锁，转移给申请人
        sessionRegistry.unlockPicture(pictureId, editorId);
        String requesterName = requester.getNickname();
        sessionRegistry.tryLockPicture(pictureId, targetUserId, requesterName, event.getSpaceId());

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
        // 从专用 targetUserId 字段读取
        Long targetUserId = event.getTargetUserId();
        if (targetUserId == null) return;

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

    /**
     * 用户断连后由 Disruptor 消费者线程处理锁转移/清理
     */
    private void handleDisconnect(CollabEvent event) {
        if (sessionRegistry == null) return;
        Long userId = event.getUserId();
        Long spaceId = event.getSpaceId();
        if (userId == null || spaceId == null) return;

        // 1. 移除会话（传 sessionId 校验，防快速重连误删新会话）
        sessionRegistry.removeSession(spaceId, userId, event.getSessionId());

        // 2. 清理该用户在当前空间持有的锁，并逐个处理转移或释放
        Set<Long> unlockedPictures = sessionRegistry.clearLocksByUserInSpace(userId, spaceId);
        for (Long pid : unlockedPictures) {
            // 优先转给排队的申请人
            var requests = sessionRegistry.getEditRequests(pid);
            if (!requests.isEmpty()) {
                var next = requests.iterator().next();
                sessionRegistry.removeEditRequest(pid, next.getUserId());
                sessionRegistry.tryLockPicture(pid, next.getUserId(), next.getNickname(), spaceId);
                broadcastTransfer(spaceId, pid, userId, next.getUserId(), next.getNickname());
                log.info("[CollabDisruptor] 断连转让(排队): picture={}, from={}, to={}", pid, userId, next.getUserId());
                continue;
            }
            // 队列为空,广播解锁
            String unlockMsg = cn.hutool.json.JSONUtil.toJsonStr(Map.of(
                    "type", "unlock", "pictureId", pid, "userId", userId));
            sessionRegistry.broadcastAll(spaceId, unlockMsg);
            sessionRegistry.clearPictureState(spaceId, pid);
            log.info("[CollabDisruptor] 断连解锁(无排队): picture={}", pid);
        }

        // 3. 清理该用户在当前空间的编辑申请
        sessionRegistry.clearEditRequestsByUserInSpace(userId, spaceId);

        // 4. 广播 leave
        String leaveMsg = cn.hutool.json.JSONUtil.toJsonStr(Map.of(
                "type", "leave", "userId", userId));
        sessionRegistry.broadcast(spaceId, userId, leaveMsg);
        log.info("[CollabDisruptor] 用户断连处理完成: user={}, space={}", userId, spaceId);
    }

    /**
     * 文件被替换后广播给同空间所有用户（重置 cropper）
     * 由 HTTP /picture/replace 事务提交后通过 Disruptor 触发
     */
    private void handleFileReplaced(CollabEvent event) {
        if (sessionRegistry == null || event.getPictureId() == null) return;
        String msg = cn.hutool.json.JSONUtil.toJsonStr(Map.of(
                "type", "file-replaced",
                "pictureId", event.getPictureId(),
                "userId", event.getUserId(),
                "nickname", event.getNickname() != null ? event.getNickname() : ""));
        sessionRegistry.broadcastAll(event.getSpaceId(), msg);
        // 文件已替换,清除旧的 transform 状态
        sessionRegistry.clearPictureState(event.getSpaceId(), event.getPictureId());
        log.info("[CollabDisruptor] 文件替换广播: picture={}, space={}", event.getPictureId(), event.getSpaceId());
    }
}
