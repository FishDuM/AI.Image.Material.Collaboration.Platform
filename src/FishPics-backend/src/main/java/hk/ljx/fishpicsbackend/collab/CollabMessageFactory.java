package hk.ljx.fishpicsbackend.collab;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

import java.util.Map;

/**
 * Builds outbound WebSocket messages for the collaboration protocol.
 */
public final class CollabMessageFactory {

    private CollabMessageFactory() {
    }

    public record Crop(Integer x, Integer y, Integer w, Integer h) {
    }

    public record TransformMessage(Long pictureId,
                                   Double scale,
                                   Integer rotation,
                                   Long userId,
                                   String nickname,
                                   Crop crop) {
    }

    public record UserMessage(Long userId, String nickname, String avatar) {
    }

    public record PictureUserMessage(Long pictureId, Long userId, String nickname) {
    }

    public static String transform(TransformMessage message) {
        var json = new JSONObject();
        json.set("type", "transform");
        json.set("pictureId", message.pictureId());
        json.set("scale", message.scale());
        json.set("rotation", message.rotation());
        json.set("userId", message.userId());
        json.set("nickname", blankIfNull(message.nickname()));
        if (message.crop() != null) {
            Crop crop = message.crop();
            json.set("crop", Map.of("x", crop.x(), "y", crop.y(), "w", crop.w(), "h", crop.h()));
        }
        return json.toString();
    }

    public static String join(UserMessage message) {
        return JSONUtil.toJsonStr(Map.of(
                "type", "join",
                "userId", message.userId(),
                "nickname", blankIfNull(message.nickname()),
                "avatar", blankIfNull(message.avatar())
        ));
    }

    public static String leave(Long userId) {
        return JSONUtil.toJsonStr(Map.of(
                "type", "leave",
                "userId", userId
        ));
    }

    public static String presence(Map<Long, CollabSessionRegistry.SessionInfo> sessions) {
        var users = sessions.entrySet().stream()
                .map(entry -> Map.of(
                        "userId", entry.getKey(),
                        "nickname", blankIfNull(entry.getValue().getNickname()),
                        "avatar", blankIfNull(entry.getValue().getAvatar())
                ))
                .toList();
        return JSONUtil.toJsonStr(Map.of(
                "type", "presence",
                "users", users
        ));
    }

    public static String lock(PictureUserMessage message) {
        return JSONUtil.toJsonStr(Map.of(
                "type", "lock",
                "pictureId", message.pictureId(),
                "userId", message.userId(),
                "nickname", blankIfNull(message.nickname())
        ));
    }

    public static String lockDenied(Long pictureId, CollabSessionRegistry.LockInfo lock) {
        return JSONUtil.toJsonStr(Map.of(
                "type", "lock-denied",
                "pictureId", pictureId,
                "userId", lock != null ? lock.getUserId() : 0,
                "nickname", lock != null ? blankIfNull(lock.getNickname()) : ""
        ));
    }

    public static String unlock(Long pictureId, Long userId) {
        return JSONUtil.toJsonStr(Map.of(
                "type", "unlock",
                "pictureId", pictureId,
                "userId", userId
        ));
    }

    public static String fileReplaced(PictureUserMessage message) {
        String nickname = blankIfNull(message.nickname());
        return JSONUtil.toJsonStr(Map.of(
                "type", "file-replaced",
                "pictureId", message.pictureId(),
                "userId", message.userId(),
                "nickname", nickname,
                "fromNickname", nickname
        ));
    }

    private static String blankIfNull(String value) {
        return value == null ? "" : value;
    }
}
