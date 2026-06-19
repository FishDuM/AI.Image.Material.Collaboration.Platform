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
    }

    public void addSession(Long spaceId, Long userId, WebSocketSession session, String nickname, String avatar) {
        sessionsBySpace.computeIfAbsent(spaceId, key -> new ConcurrentHashMap<>())
                .compute(userId, (key, oldInfo) -> {
                    if (oldInfo != null && !session.getId().equals(oldInfo.getSessionId())) {
                        closeSession(oldInfo.getSession(), "replaced by a new connection");
                    }
                    return new SessionInfo(session, nickname, avatar, session.getId());
                });
        log.debug("[Collab] session added: space={}, user={}", spaceId, userId);
    }

    public void removeSession(Long spaceId, Long userId, String sessionId) {
        sessionsBySpace.computeIfPresent(spaceId, (key, spaceSessions) -> {
            SessionInfo existing = spaceSessions.get(userId);
            if (sessionId != null && existing != null && !sessionId.equals(existing.getSessionId())) {
                log.debug("[Collab] skip stale session remove: space={}, user={}", spaceId, userId);
                return spaceSessions;
            }
            spaceSessions.remove(userId);
            return spaceSessions.isEmpty() ? null : spaceSessions;
        });
        log.debug("[Collab] session removed: space={}, user={}", spaceId, userId);
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
            if (!session.isOpen()) return;
            synchronized (session) {
                session.sendMessage(message);
            }
        } catch (IOException e) {
            log.warn("[Collab] send message failed: space={}, user={}", spaceId, userId, e);
        }
    }

    public boolean tryAcquireEditLock(Long spaceId, Long pictureId, Long userId, String nickname) {
        LockInfo newLock = new LockInfo(userId, nickname == null ? "" : nickname, spaceId, pictureId);
        return stateStore.tryAcquireLock(spaceId, newLock);
    }

    public boolean releaseEditLock(Long spaceId, Long pictureId, Long userId) {
        return stateStore.releaseLock(spaceId, pictureId, userId);
    }

    public LockInfo getSpaceLock(Long spaceId) {
        return stateStore.getLock(spaceId);
    }

    public boolean isEditLockHolder(Long spaceId, Long pictureId, Long userId) {
        return stateStore.isLockHolder(spaceId, pictureId, userId);
    }

    public Long clearLockByUserInSpace(Long userId, Long spaceId) {
        return stateStore.clearLockByUserInSpace(userId, spaceId);
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

        Long pictureId = stateStore.clearLockByUserInSpace(userId, spaceId);
        if (pictureId != null) {
            clearPictureState(spaceId, pictureId);
        }
        return true;
    }

    private void closeSession(WebSocketSession session, String reason) {
        try {
            if (session.isOpen()) {
                session.close(CloseStatus.GOING_AWAY.withReason(reason));
            }
        } catch (IOException e) {
            log.warn("[Collab] close session failed: session={}", session.getId(), e);
        }
    }

    public void clearAllPictureStates(Long spaceId) {
        stateStore.clearAllPictureStates(spaceId);
    }
}
