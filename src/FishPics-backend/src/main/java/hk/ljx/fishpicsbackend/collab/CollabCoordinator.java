package hk.ljx.fishpicsbackend.collab;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Direct collaboration workflow for non-lock events.
 */
@Slf4j
@Component
public class CollabCoordinator {

    @Resource
    private CollabSessionRegistry sessionRegistry;

    public void handleTransform(Long spaceId,
                                Long pictureId,
                                Long userId,
                                String nickname,
                                Double scale,
                                Integer rotation,
                                Integer cropX,
                                Integer cropY,
                                Integer cropW,
                                Integer cropH) {
        if (spaceId == null || pictureId == null || scale == null || rotation == null) {
            return;
        }

        String message = CollabMessageFactory.transform(
                pictureId, scale, rotation, userId, nickname, cropX, cropY, cropW, cropH);
        sessionRegistry.updatePictureState(spaceId, pictureId, message);
        sessionRegistry.broadcastAll(spaceId, message);

        int onlineCount = sessionRegistry.getOnlineUserIds(spaceId).size();
        log.info("[Collab] transform broadcast: space={}, picture={}, scale={}, rotation={}, crop={}, online={}",
                spaceId, pictureId, scale, rotation, cropX != null ? "yes" : "no", onlineCount);
    }

    public void handleJoin(Long spaceId, Long userId, String nickname, String avatar) {
        if (spaceId == null || userId == null) return;

        sessionRegistry.broadcast(spaceId, userId, CollabMessageFactory.join(userId, nickname, avatar));
        sessionRegistry.sendToUser(spaceId, userId,
                CollabMessageFactory.presence(sessionRegistry.getSpaceSessions(spaceId)));

        for (String stateJson : sessionRegistry.getSpacePictureStates(spaceId)) {
            sessionRegistry.sendToUser(spaceId, userId, stateJson);
        }
    }

    public void handleFileReplaced(Long spaceId, Long pictureId, Long userId, String nickname) {
        if (spaceId == null || pictureId == null) return;

        sessionRegistry.broadcastAll(spaceId, CollabMessageFactory.fileReplaced(pictureId, userId, nickname));
        sessionRegistry.clearPictureState(spaceId, pictureId);
        log.info("[Collab] file replaced broadcast: picture={}, space={}", pictureId, spaceId);
    }
}
