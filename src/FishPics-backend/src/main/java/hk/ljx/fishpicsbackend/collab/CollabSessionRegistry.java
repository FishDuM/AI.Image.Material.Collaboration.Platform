package hk.ljx.fishpicsbackend.collab;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class CollabSessionRegistry {

    // spaceId → (userId → session)
    private final Map<Long, Map<Long, SessionInfo>> spaces = new ConcurrentHashMap<>();
    // spaceId → 当前编辑锁；同一团队空间同一时刻只允许一个编辑者
    private final Map<Long, LockInfo> spaceEditLocks = new ConcurrentHashMap<>();
    // spaceId → (pictureId → 最新 transform JSON)
    private final Map<Long, Map<Long, String>> pictureStates = new ConcurrentHashMap<>();

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class SessionInfo {
        private WebSocketSession session;
        private String nickname;
        private String avatar;
        private String sessionId;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class LockInfo {
        private Long userId;
        private String nickname;
        private Long spaceId;
        private Long pictureId;
    }

    public void addSession(Long spaceId, Long userId, WebSocketSession session, String nickname, String avatar) {
        spaces.computeIfAbsent(spaceId, k -> new ConcurrentHashMap<>())
                .put(userId, new SessionInfo(session, nickname, avatar, session.getId()));
        log.debug("协同会话注册: space={}, user={}", spaceId, userId);
    }

    // sessionId 校验：快速重连时旧 disconnect 不会误删新会话
    public void removeSession(Long spaceId, Long userId, String sessionId) {
        spaces.computeIfPresent(spaceId, (k, spaceSessions) -> {
            SessionInfo existing = spaceSessions.get(userId);
            if (sessionId != null && existing != null && !sessionId.equals(existing.getSessionId())) {
                log.debug("协同会话跳过移除(sessionId 不匹配): space={}, user={}", spaceId, userId);
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

    // 广播给空间内除了 excludeUserId 以外的所有人
    public void broadcast(Long spaceId, Long excludeUserId, String message) {
        Map<Long, SessionInfo> spaceSessions = spaces.get(spaceId);
        if (spaceSessions == null) return;
        TextMessage textMsg = new TextMessage(message);
        for (Map.Entry<Long, SessionInfo> entry : spaceSessions.entrySet()) {
            if (entry.getKey().equals(excludeUserId)) continue;
            sendSafe(entry.getValue().getSession(), textMsg, spaceId, entry.getKey());
        }
    }

    public void broadcastAll(Long spaceId, String message) {
        Map<Long, SessionInfo> spaceSessions = spaces.get(spaceId);
        if (spaceSessions == null) return;
        TextMessage textMsg = new TextMessage(message);
        for (Map.Entry<Long, SessionInfo> entry : spaceSessions.entrySet()) {
            sendSafe(entry.getValue().getSession(), textMsg, spaceId, entry.getKey());
        }
    }

    public void sendToUser(Long spaceId, Long userId, String message) {
        Map<Long, SessionInfo> spaceSessions = spaces.get(spaceId);
        if (spaceSessions == null) return;
        SessionInfo info = spaceSessions.get(userId);
        if (info == null) return;
        sendSafe(info.getSession(), new TextMessage(message), spaceId, userId);
    }

    private void sendSafe(WebSocketSession session, TextMessage msg, Long spaceId, Long userId) {
        try {
            if (session.isOpen()) {
                synchronized (session) {
                    session.sendMessage(msg);
                }
            }
        } catch (IOException e) {
            log.warn("广播消息失败: space={}, user={}", spaceId, userId, e);
        }
    }

    // ==================== 图片编辑锁 ====================

    // @return true=锁定成功，false=该空间已被其他人锁定
    public boolean tryLockPicture(Long pictureId, Long userId, String nickname, Long spaceId) {
        LockInfo existing = spaceEditLocks.putIfAbsent(spaceId, new LockInfo(userId, nickname, spaceId, pictureId));
        if (existing == null) return true;
        return existing.getUserId().equals(userId) && existing.getPictureId().equals(pictureId);
    }

    // 仅锁持有者可释放，用 remove(key,value) 原子删除避免竞态
    public boolean unlockPicture(Long spaceId, Long pictureId, Long userId) {
        LockInfo lock = spaceEditLocks.get(spaceId);
        if (lock != null
                && lock.getUserId().equals(userId)
                && lock.getPictureId().equals(pictureId)) {
            return spaceEditLocks.remove(spaceId, lock);
        }
        return false;
    }

    public LockInfo getSpaceLock(Long spaceId) {
        return spaceEditLocks.get(spaceId);
    }

    public boolean isLockHolder(Long spaceId, Long pictureId, Long userId) {
        LockInfo lock = spaceEditLocks.get(spaceId);
        return lock != null
                && lock.getUserId().equals(userId)
                && lock.getPictureId().equals(pictureId);
    }

    // 用户断连时清除其所有锁
    public Set<Long> clearLocksByUser(Long userId) {
        Set<Long> unlockedPictures = new HashSet<>();
        spaceEditLocks.entrySet().removeIf(entry -> {
            if (entry.getValue().getUserId().equals(userId)) {
                unlockedPictures.add(entry.getValue().getPictureId());
                return true;
            }
            return false;
        });
        return unlockedPictures;
    }

    // 只清指定空间的锁，不影响其他空间
    public Set<Long> clearLocksByUserInSpace(Long userId, Long spaceId) {
        Set<Long> unlockedPictures = new HashSet<>();
        LockInfo lock = spaceEditLocks.get(spaceId);
        if (lock != null && lock.getUserId().equals(userId)) {
            unlockedPictures.add(lock.getPictureId());
            spaceEditLocks.remove(spaceId, lock);
        }
        return unlockedPictures;
    }

    // ==================== transform 状态缓存 ====================

    public void updatePictureState(Long spaceId, Long pictureId, String stateJson) {
        pictureStates.computeIfAbsent(spaceId, k -> new ConcurrentHashMap<>())
                .put(pictureId, stateJson);
    }

    public String getPictureState(Long spaceId, Long pictureId) {
        Map<Long, String> space = pictureStates.get(spaceId);
        return space != null ? space.get(pictureId) : null;
    }

    public List<String> getSpacePictureStates(Long spaceId) {
        Map<Long, String> space = pictureStates.get(spaceId);
        if (space == null) return List.of();
        return new ArrayList<>(space.values());
    }

    public void clearPictureState(Long spaceId, Long pictureId) {
        Map<Long, String> space = pictureStates.get(spaceId);
        if (space != null) {
            space.remove(pictureId);
            if (space.isEmpty()) pictureStates.remove(spaceId);
        }
    }

    // resync 用，返回所有锁的快照
    public Map<Long, LockInfo> getAllPictureLocks() {
        Map<Long, LockInfo> locksByPicture = new HashMap<>();
        spaceEditLocks.values().forEach(lock -> locksByPicture.put(lock.getPictureId(), lock));
        return locksByPicture;
    }

    // 踢人：关连接 + 清锁 + 清申请
    public Set<Long> disconnectUserInSpaces(Long userId, Long spaceId, String code, String reason) {
        Set<Long> affected = new HashSet<>();
        Map<Long, SessionInfo> spaceSessions = spaces.get(spaceId);
        if (spaceSessions == null) return affected;
        SessionInfo info = spaceSessions.remove(userId);
        if (info != null) {
            affected.add(spaceId);
            try {
                WebSocketSession ws = info.getSession();
                if (ws.isOpen()) {
                    ws.close(CloseStatus.GOING_AWAY.withReason(reason));
                }
            } catch (IOException e) {
                log.warn("[SessionRegistry] 关闭用户连接失败: user={}, space={}", userId, spaceId, e);
            }
            LockInfo lock = spaceEditLocks.get(spaceId);
            if (lock != null && lock.getUserId().equals(userId)) {
                spaceEditLocks.remove(spaceId, lock);
            }
            if (spaceSessions.isEmpty()) {
                spaces.remove(spaceId);
            }
        }
        return affected;
    }

    public void clearAllPictureStates(Long spaceId) {
        pictureStates.remove(spaceId);
    }
}
