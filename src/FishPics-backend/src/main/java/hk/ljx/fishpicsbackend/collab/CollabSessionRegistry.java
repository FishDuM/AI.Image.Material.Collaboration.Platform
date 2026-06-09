package hk.ljx.fishpicsbackend.collab;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
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
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class LockInfo {
        private Long userId;
        private String nickname;
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
                .put(userId, new SessionInfo(session, nickname, avatar));
        log.debug("协同会话注册: space={}, user={}", spaceId, userId);
    }

    public void removeSession(Long spaceId, Long userId) {
        Map<Long, SessionInfo> spaceSessions = spaces.get(spaceId);
        if (spaceSessions != null) {
            spaceSessions.remove(userId);
            if (spaceSessions.isEmpty()) {
                spaces.remove(spaceId);
            }
        }
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
    public boolean tryLockPicture(Long pictureId, Long userId, String nickname) {
        LockInfo existing = pictureLocks.putIfAbsent(pictureId, new LockInfo(userId, nickname));
        if (existing == null) return true;
        // 自己重复加锁
        return existing.getUserId().equals(userId);
    }

    /**
     * 释放图片编辑锁（仅锁持有者可释放）
     */
    public boolean unlockPicture(Long pictureId, Long userId) {
        LockInfo lock = pictureLocks.get(pictureId);
        if (lock != null && lock.getUserId().equals(userId)) {
            pictureLocks.remove(pictureId);
            return true;
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
}
