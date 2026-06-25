package hk.ljx.fishpicsbackend.collab;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class CollabSessionRegistry {

    private final Map<Long, Map<Long, SessionInfo>> sessionsBySpace = new ConcurrentHashMap<>();
    private final CollabStateStore stateStore;

    public CollabSessionRegistry(CollabStateStore stateStore) {
        this.stateStore = stateStore;
    }

    @Data
    @AllArgsConstructor
    public static class SessionInfo {
        private WebSocketSession session;
        private String nickname;
        private String avatar;
        private String sessionId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LockInfo {
        private Long userId;
        private String nickname;
        private Long spaceId;
        private Long pictureId;
        private String sessionId;
    }

    public void addSession(Long spaceId, Long userId, WebSocketSession session, String nickname, String avatar) {
        sessionsBySpace.computeIfAbsent(spaceId, key -> new ConcurrentHashMap<>())
                .compute(userId, (key, oldInfo) -> {
                    if (oldInfo != null && !session.getId().equals(oldInfo.getSessionId())) {
                        closeSession(oldInfo.getSession(), "replaced by a new connection");
                    }
                    return new SessionInfo(session, nickname, avatar, session.getId());
                });
        log.debug("[Collab] 会话已添加: space={}, user={}", spaceId, userId);
    }

    public void removeSession(Long spaceId, Long userId, String sessionId) {
        sessionsBySpace.computeIfPresent(spaceId, (key, spaceSessions) -> {
            SessionInfo existing = spaceSessions.get(userId);
            if (sessionId != null && existing != null && !sessionId.equals(existing.getSessionId())) {
                log.debug("[Collab] 跳过过期 session 移除: space={}, user={}", spaceId, userId);
                return spaceSessions;
            }
            spaceSessions.remove(userId);
            return spaceSessions.isEmpty() ? null : spaceSessions;
        });
        log.debug("[Collab] 会话已移除: space={}, user={}", spaceId, userId);
    }

    public Set<Long> getOnlineUserIds(Long spaceId) {
        Map<Long, SessionInfo> spaceSessions = sessionsBySpace.get(spaceId);
        return spaceSessions != null ? spaceSessions.keySet() : Set.of();
    }

    public Map<Long, SessionInfo> getSpaceSessions(Long spaceId) {
        return sessionsBySpace.getOrDefault(spaceId, Map.of());
    }

    public void broadcast(Long spaceId, Long excludeUserId, String message) {
        Map<Long, SessionInfo> spaceSessions = sessionsBySpace.get(spaceId);
        if (spaceSessions == null) return;
        TextMessage textMessage = new TextMessage(message);
        for (Map.Entry<Long, SessionInfo> entry : spaceSessions.entrySet()) {
            if (entry.getKey().equals(excludeUserId)) continue;
            sendSafe(entry.getValue().getSession(), textMessage, spaceId, entry.getKey());
        }
    }

    public void broadcastAll(Long spaceId, String message) {
        Map<Long, SessionInfo> spaceSessions = sessionsBySpace.get(spaceId);
        if (spaceSessions == null) return;
        TextMessage textMessage = new TextMessage(message);
        for (Map.Entry<Long, SessionInfo> entry : spaceSessions.entrySet()) {
            sendSafe(entry.getValue().getSession(), textMessage, spaceId, entry.getKey());
        }
    }

    public void sendToUser(Long spaceId, Long userId, String message) {
        Map<Long, SessionInfo> spaceSessions = sessionsBySpace.get(spaceId);
        if (spaceSessions == null) return;
        SessionInfo info = spaceSessions.get(userId);
        if (info == null) return;
        sendSafe(info.getSession(), new TextMessage(message), spaceId, userId);
    }

    private void sendSafe(WebSocketSession session, TextMessage message, Long spaceId, Long userId) {
        try {
            if (!session.isOpen()) {
                cleanupDeadSession(spaceId, userId, session);
                return;
            }
            synchronized (session) {
                session.sendMessage(message);
            }
        } catch (IOException e) {
            log.warn("[Collab] 发送消息失败，清理连接: space={}, user={}", spaceId, userId, e);
            removeSession(spaceId, userId, getSessionId(session));
            try { session.close(CloseStatus.GOING_AWAY.withReason("send failed")); } catch (Exception ignored) {}
        }
    }

    private String getSessionId(WebSocketSession session) {
        try {
            return session.getId();
        } catch (Exception e) {
            return null;
        }
    }

    private void cleanupDeadSession(Long spaceId, Long userId, WebSocketSession session) {
        Map<Long, SessionInfo> spaceSessions = sessionsBySpace.get(spaceId);
        if (spaceSessions == null) return;
        SessionInfo info = spaceSessions.get(userId);
        if (info != null && info.getSession().equals(session)) {
            spaceSessions.remove(userId);
            if (spaceSessions.isEmpty()) {
                sessionsBySpace.remove(spaceId);
            }
        }
    }

    public List<LockInfo> getSpaceLocks(Long spaceId) {
        return stateStore.getAllLocks(spaceId);
    }

    public boolean tryAcquireEditLock(Long spaceId, Long pictureId, Long userId, String nickname) {
        LockInfo newLock = new LockInfo(userId, nickname == null ? "" : nickname, spaceId, pictureId, "");
        return stateStore.tryAcquireLock(spaceId, newLock);
    }

    public boolean releaseEditLock(Long spaceId, Long pictureId, Long userId) {
        return stateStore.releaseLock(spaceId, pictureId, userId);
    }

    public LockInfo getSpaceLock(Long spaceId, Long pictureId) {
        return stateStore.getLock(spaceId, pictureId);
    }

    public boolean isEditLockHolder(Long spaceId, Long pictureId, Long userId) {
        return stateStore.isLockHolder(spaceId, pictureId, userId);
    }

    public List<Long> clearLocksByUserInSpace(Long userId, Long spaceId) {
        return stateStore.clearLocksByUserInSpace(userId, spaceId);
    }

    public void updatePictureState(Long spaceId, Long pictureId, String stateJson) {
        stateStore.updatePictureState(spaceId, pictureId, stateJson);
    }

    public List<String> getSpacePictureStates(Long spaceId) {
        return stateStore.getSpacePictureStates(spaceId);
    }

    public void clearPictureState(Long spaceId, Long pictureId) {
        stateStore.clearPictureState(spaceId, pictureId);
    }

    public boolean disconnectUserInSpace(Long userId, Long spaceId, String reason) {
        Map<Long, SessionInfo> spaceSessions = sessionsBySpace.get(spaceId);
        if (spaceSessions == null) return false;

        SessionInfo info = spaceSessions.get(userId);
        if (info == null) return false;

        removeSession(spaceId, userId, info.getSessionId());
        closeSession(info.getSession(), reason);

        List<Long> pictureIds = stateStore.clearLocksByUserInSpace(userId, spaceId);
        for (Long picId : pictureIds) {
            clearPictureState(spaceId, picId);
        }
        return true;
    }

    private void closeSession(WebSocketSession session, String reason) {
        try {
            if (session.isOpen()) {
                session.close(CloseStatus.GOING_AWAY.withReason(reason));
            }
        } catch (IOException e) {
            log.warn("[Collab] 关闭 session 失败: session={}", session.getId(), e);
        }
    }

    public void clearAllPictureStates(Long spaceId) {
        stateStore.clearAllPictureStates(spaceId);
    }
}
