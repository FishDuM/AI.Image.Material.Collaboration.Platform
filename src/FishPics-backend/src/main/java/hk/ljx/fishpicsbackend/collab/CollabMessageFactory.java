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

    public static String transform(Long pictureId,
                                   Double scale,
                                   Integer rotation,
                                   Long userId,
                                   String nickname,
                                   Integer cropX,
                                   Integer cropY,
                                   Integer cropW,
                                   Integer cropH) {
        var json = new JSONObject();
        json.set("type", "transform");
        json.set("pictureId", pictureId);
        json.set("scale", scale);
        json.set("rotation", rotation);
        json.set("userId", userId);
        json.set("nickname", blankIfNull(nickname));
        if (cropX != null) {
            json.set("crop", Map.of("x", cropX, "y", cropY, "w", cropW, "h", cropH));
        }
        return json.toString();
    }

    public static String join(Long userId, String nickname, String avatar) {
        return JSONUtil.toJsonStr(Map.of(
                "type", "join",
                "userId", userId,
                "nickname", blankIfNull(nickname),
                "avatar", blankIfNull(avatar)
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

    public static String lock(Long pictureId, Long userId, String nickname) {
        return JSONUtil.toJsonStr(Map.of(
                "type", "lock",
                "pictureId", pictureId,
                "userId", userId,
                "nickname", blankIfNull(nickname)
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

    public static String fileReplaced(Long pictureId, Long userId, String nickname) {
        return JSONUtil.toJsonStr(Map.of(
                "type", "file-replaced",
                "pictureId", pictureId,
                "userId", userId,
                "nickname", blankIfNull(nickname)
        ));
    }

    private static String blankIfNull(String value) {
        return value == null ? "" : value;
    }
}
