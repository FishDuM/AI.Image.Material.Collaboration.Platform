package hk.ljx.fishpicsbackend.collab;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Coordinates the single edit lock for a team space.
 */
@Slf4j
@Component
public class CollabLockCoordinator {

    @Resource
    private CollabSessionRegistry sessionRegistry;

    public void handleLock(Long spaceId, Long pictureId, Long userId, String nickname) {
        if (spaceId == null || pictureId == null || userId == null) return;

        String safeNickname = blankIfNull(nickname);
        boolean acquired = sessionRegistry.tryLockPicture(pictureId, userId, safeNickname, spaceId);
        if (acquired) {
            sessionRegistry.broadcastAll(spaceId, CollabMessageFactory.lock(pictureId, userId, safeNickname));
            log.info("[Collab] lock acquired: picture={}, user={}", pictureId, userId);
            return;
        }

        var lock = sessionRegistry.getSpaceLock(spaceId);
        sessionRegistry.sendToUser(spaceId, userId, CollabMessageFactory.lockDenied(pictureId, lock));
        log.info("[Collab] lock denied: picture={}, user={}, holder={}",
                pictureId, userId, lock != null ? lock.getNickname() : "unknown");
    }

    public void handleUnlock(Long spaceId, Long pictureId, Long userId) {
        if (spaceId == null || pictureId == null || userId == null) return;

        boolean released = sessionRegistry.unlockPicture(spaceId, pictureId, userId);
        if (!released) return;

        broadcastUnlockAndClearState(spaceId, pictureId, userId);
        log.info("[Collab] unlocked with no online successor: picture={}", pictureId);
    }

    public void handleDisconnect(Long spaceId, Long userId, String sessionId) {
        if (spaceId == null || userId == null) return;

        sessionRegistry.removeSession(spaceId, userId, sessionId);

        Set<Long> unlockedPictures = sessionRegistry.clearLocksByUserInSpace(userId, spaceId);
        for (Long pictureId : unlockedPictures) {
            broadcastUnlockAndClearState(spaceId, pictureId, userId);
            log.info("[Collab] disconnected user lock released: picture={}", pictureId);
        }

        sessionRegistry.broadcast(spaceId, userId, CollabMessageFactory.leave(userId));
        log.info("[Collab] user disconnect handled: user={}, space={}", userId, spaceId);
    }

    private void broadcastUnlockAndClearState(Long spaceId, Long pictureId, Long userId) {
        sessionRegistry.broadcastAll(spaceId, CollabMessageFactory.unlock(pictureId, userId));
        sessionRegistry.clearPictureState(spaceId, pictureId);
    }

    private String blankIfNull(String value) {
        return value == null ? "" : value;
    }
}
