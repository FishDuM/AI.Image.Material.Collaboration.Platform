package hk.ljx.fishpicsbackend.collab;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 协同编辑会话注册表
 * 维护 spaceId → {userId → Session} 的映射，支持按空间广播
 */
@Slf4j
@Component
public class CollabSessionRegistry {

    /** spaceId → (userId → session info) */
    private final Map<Long, Map<Long, SessionInfo>> spaces = new ConcurrentHashMap<>();

    /** pictureId → LockInfo（图片编辑锁） */
    private final Map<Long, LockInfo> pictureLocks = new ConcurrentHashMap<>();

    /** pictureId → (requesterId → RequesterInfo)（待审批的编辑申请） */
    private final Map<Long, Map<Long, RequesterInfo>> editRequests = new ConcurrentHashMap<>();

    /** spaceId → (pictureId → 最新 transform 状态 JSON) */
    private final Map<Long, Map<Long, String>> pictureStates = new ConcurrentHashMap<>();

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class SessionInfo {
        private WebSocketSession session;
        private String nickname;
        private String avatar;
        /** WebSocket session ID，removeSession 时校验防误删新会话 */
        private String sessionId;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class LockInfo {
        private Long userId;
        private String nickname;
        /** 锁记录空间 ID，供 resync 时按 spaceId 过滤 */
        private Long spaceId;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class RequesterInfo {
        private Long userId;
        private String nickname;
        private String avatar;
    }

    public void addSession(Long spaceId, Long userId, WebSocketSession session, String nickname, String avatar) {
        spaces.computeIfAbsent(spaceId, k -> new ConcurrentHashMap<>())
                .put(userId, new SessionInfo(session, nickname, avatar, session.getId()));
        log.debug("协同会话注册: space={}, user={}", spaceId, userId);
    }

    /**
     * 移除会话
     * sessionId 校验，防止快速重连时旧 disconnect 事件误删新会话
     */
    public void removeSession(Long spaceId, Long userId, String sessionId) {
        spaces.computeIfPresent(spaceId, (k, spaceSessions) -> {
            SessionInfo existing = spaceSessions.get(userId);
            // sessionId 不匹配说明是旧连接的 disconnect 事件，跳过
            if (sessionId != null && existing != null && !sessionId.equals(existing.getSessionId())) {
                log.debug("协同会话跳过移除(sessionId 不匹配): space={}, user={}, expected={}, actual={}",
                        spaceId, userId, sessionId, existing.getSessionId());
                return spaceSessions;
            }
            spaceSessions.remove(userId);
            return spaceSessions.isEmpty() ? null : spaceSessions;
        });
        log.debug("协同会话移除: space={}, user={}", spaceId, userId);
    }

    public Set<Long> getOnlineUserIds(Long spaceId) {
        Map<Long, SessionInfo> spaceSessions = spaces.get(spaceId);
        return spaceSessions != null ? spaceSessions.keySet() : Set.of();
    }

    public Map<Long, SessionInfo> getSpaceSessions(Long spaceId) {
        return spaces.getOrDefault(spaceId, Map.of());
    }

    /**
     * 向指定空间的所有在线用户广播消息（排除指定用户）
     */
    public void broadcast(Long spaceId, Long excludeUserId, String message) {
        Map<Long, SessionInfo> spaceSessions = spaces.get(spaceId);
        if (spaceSessions == null) return;
        TextMessage textMsg = new TextMessage(message);
        for (Map.Entry<Long, SessionInfo> entry : spaceSessions.entrySet()) {
            if (entry.getKey().equals(excludeUserId)) continue;
            try {
                WebSocketSession session = entry.getValue().getSession();
                if (session.isOpen()) {
                    synchronized (session) {
                        session.sendMessage(textMsg);
                    }
                }
            } catch (IOException e) {
                log.warn("广播消息失败: space={}, user={}", spaceId, entry.getKey(), e);
            }
        }
    }

    /**
     * 向指定空间的所有在线用户广播消息（不排除任何人）
     */
    public void broadcastAll(Long spaceId, String message) {
        Map<Long, SessionInfo> spaceSessions = spaces.get(spaceId);
        if (spaceSessions == null) return;
        TextMessage textMsg = new TextMessage(message);
        for (Map.Entry<Long, SessionInfo> entry : spaceSessions.entrySet()) {
            try {
                WebSocketSession session = entry.getValue().getSession();
                if (session.isOpen()) {
                    synchronized (session) {
                        session.sendMessage(textMsg);
                    }
                }
            } catch (IOException e) {
                log.warn("广播消息失败: space={}, user={}", spaceId, entry.getKey(), e);
            }
        }
    }

    /**
     * 向指定空间内指定用户发送消息
     */
    public void sendToUser(Long spaceId, Long userId, String message) {
        Map<Long, SessionInfo> spaceSessions = spaces.get(spaceId);
        if (spaceSessions == null) return;
        SessionInfo info = spaceSessions.get(userId);
        if (info == null) return;
        try {
            WebSocketSession session = info.getSession();
            if (session.isOpen()) {
                synchronized (session) {
                    session.sendMessage(new TextMessage(message));
                }
            }
        } catch (IOException e) {
            log.warn("发送消息失败: space={}, user={}", spaceId, userId, e);
        }
    }

    // ==================== 图片编辑锁 ====================

    /**
     * 尝试锁定图片编辑权
     * @return true=锁定成功，false=已被其他人锁定
     */
    public boolean tryLockPicture(Long pictureId, Long userId, String nickname, Long spaceId) {
        LockInfo existing = pictureLocks.putIfAbsent(pictureId, new LockInfo(userId, nickname, spaceId));
        if (existing == null) return true;
        // 自己重复加锁
        return existing.getUserId().equals(userId);
    }

    /**
     * 释放图片编辑锁（仅锁持有者可释放）
     * 使用 ConcurrentHashMap.remove(key, value) 原子条件删除，避免 get+remove 之间的竞态
     */
    public boolean unlockPicture(Long pictureId, Long userId) {
        LockInfo lock = pictureLocks.get(pictureId);
        if (lock != null && lock.getUserId().equals(userId)) {
            return pictureLocks.remove(pictureId, lock);
        }
        return false;
    }

    /**
     * 获取图片当前锁信息
     */
    public LockInfo getPictureLock(Long pictureId) {
        return pictureLocks.get(pictureId);
    }

    /**
     * 清除用户持有的所有锁（用户断开连接时调用）
     * @return 被解锁的图片 ID 集合
     */
    public java.util.Set<Long> clearLocksByUser(Long userId) {
        java.util.Set<Long> unlockedPictures = new java.util.HashSet<>();
        pictureLocks.entrySet().removeIf(entry -> {
            if (entry.getValue().getUserId().equals(userId)) {
                unlockedPictures.add(entry.getKey());
                return true;
            }
            return false;
        });
        return unlockedPictures;
    }

    /**
     * 清除用户在指定空间持有的锁（用户从单个空间断连时调用,不影响其他空间）
     * @return 被解锁的图片 ID 集合
     */
    public java.util.Set<Long> clearLocksByUserInSpace(Long userId, Long spaceId) {
        java.util.Set<Long> unlockedPictures = new java.util.HashSet<>();
        pictureLocks.entrySet().removeIf(entry -> {
            if (entry.getValue().getUserId().equals(userId)
                    && spaceId.equals(entry.getValue().getSpaceId())) {
                unlockedPictures.add(entry.getKey());
                return true;
            }
            return false;
        });
        return unlockedPictures;
    }

    // ==================== 编辑申请管理 ====================

    /**
     * 添加编辑申请
     */
    public void addEditRequest(Long pictureId, Long userId, String nickname, String avatar) {
        editRequests.computeIfAbsent(pictureId, k -> new ConcurrentHashMap<>())
                .put(userId, new RequesterInfo(userId, nickname, avatar));
    }

    /**
     * 移除编辑申请（审批后调用）
     */
    public void removeEditRequest(Long pictureId, Long userId) {
        Map<Long, RequesterInfo> requests = editRequests.get(pictureId);
        if (requests != null) {
            requests.remove(userId);
            if (requests.isEmpty()) editRequests.remove(pictureId);
        }
    }

    /**
     * 获取某图片的所有待审批申请
     */
    public java.util.Collection<RequesterInfo> getEditRequests(Long pictureId) {
        Map<Long, RequesterInfo> requests = editRequests.get(pictureId);
        return requests != null ? requests.values() : java.util.List.of();
    }

    /**
     * 清除用户发起的所有编辑申请（断线时调用）
     */
    public void clearEditRequestsByUser(Long userId) {
        editRequests.values().forEach(reqs -> reqs.remove(userId));
        editRequests.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    /**
     * 清除用户在指定空间发起的编辑申请（从单个空间断连时调用）
     */
    public void clearEditRequestsByUserInSpace(Long userId, Long spaceId) {
        // 收集该空间中所有有锁的 pictureId
        Set<Long> picturesInSpace = new java.util.HashSet<>();
        pictureLocks.forEach((pictureId, lock) -> {
            if (spaceId.equals(lock.getSpaceId())) {
                picturesInSpace.add(pictureId);
            }
        });
        // 同时收集该空间中所有有 transform 状态的 pictureId
        Map<Long, String> spaceStates = pictureStates.get(spaceId);
        if (spaceStates != null) {
            picturesInSpace.addAll(spaceStates.keySet());
        }
        // 只清除该空间内图片的编辑申请
        editRequests.forEach((pictureId, reqs) -> {
            if (picturesInSpace.contains(pictureId)) {
                reqs.remove(userId);
            }
        });
        editRequests.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    // ==================== 图片变换状态缓存 ====================

    /**
     * 缓存图片最新 transform 状态（JSON 字符串）
     */
    public void updatePictureState(Long spaceId, Long pictureId, String stateJson) {
        pictureStates.computeIfAbsent(spaceId, k -> new ConcurrentHashMap<>())
                .put(pictureId, stateJson);
    }

    /**
     * 获取指定图片的缓存状态
     */
    public String getPictureState(Long spaceId, Long pictureId) {
        Map<Long, String> space = pictureStates.get(spaceId);
        return space != null ? space.get(pictureId) : null;
    }

    /**
     * 获取指定空间内所有图片的缓存状态
     */
    public java.util.List<String> getSpacePictureStates(Long spaceId) {
        Map<Long, String> space = pictureStates.get(spaceId);
        if (space == null) return java.util.List.of();
        return new java.util.ArrayList<>(space.values());
    }

    /**
     * 清除图片缓存状态（解锁时调用）
     */
    public void clearPictureState(Long spaceId, Long pictureId) {
        Map<Long, String> space = pictureStates.get(spaceId);
        if (space != null) {
            space.remove(pictureId);
            if (space.isEmpty()) pictureStates.remove(spaceId);
        }
    }

    // ==================== V14 补充方法（SpaceService / resync 依赖） ====================

    /**
     * 获取所有图片锁的快照（供 resync 时按 spaceId 过滤推送）
     */
    public Map<Long, LockInfo> getAllPictureLocks() {
        return new java.util.HashMap<>(pictureLocks);
    }

    /**
     * 断开指定空间内的指定用户连接，清除其所有锁和编辑申请
     * @return 实际受影响的空间 ID 集合（断开成功时包含 spaceId）
     */
    public java.util.Set<Long> disconnectUserInSpaces(Long userId, Long spaceId, String code, String reason) {
        java.util.Set<Long> affected = new java.util.HashSet<>();
        Map<Long, SessionInfo> spaceSessions = spaces.get(spaceId);
        if (spaceSessions == null) return affected;
        SessionInfo info = spaceSessions.remove(userId);
        if (info != null) {
            affected.add(spaceId);
            // 关闭 WebSocket 连接
            try {
                WebSocketSession ws = info.getSession();
                if (ws.isOpen()) {
                    ws.close(org.springframework.web.socket.CloseStatus.GOING_AWAY.withReason(reason));
                }
            } catch (IOException e) {
                log.warn("[SessionRegistry] 关闭用户连接失败: user={}, space={}", userId, spaceId, e);
            }
            // 清理该用户在该空间持有的图片锁
            pictureLocks.entrySet().removeIf(entry ->
                    entry.getValue().getUserId().equals(userId)
                            && spaceId.equals(entry.getValue().getSpaceId()));
            // 清理该用户的编辑申请
            clearEditRequestsByUser(userId);
            if (spaceSessions.isEmpty()) {
                spaces.remove(spaceId);
            }
        }
        return affected;
    }

    /**
     * 清除指定空间的所有图片 transform 状态缓存
     */
    public void clearAllPictureStates(Long spaceId) {
        pictureStates.remove(spaceId);
    }
}
