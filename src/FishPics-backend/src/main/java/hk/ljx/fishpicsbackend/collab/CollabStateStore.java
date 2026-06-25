package hk.ljx.fishpicsbackend.collab;

import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
public class CollabStateStore {

    private static final Duration LOCK_TTL = Duration.ofMinutes(30);
    private static final Duration STATE_TTL = Duration.ofHours(2);
    private static final String LOCK_KEY_PREFIX = "collab:lock:";
    private static final String LOCK_SET_KEY_SUFFIX = ":locks";
    private static final String STATE_KEY_PREFIX = "collab:state:";
    private static final DefaultRedisScript<Long> DELETE_IF_VALUE_MATCHES =
            new DefaultRedisScript<>("""
                    if redis.call('get', KEYS[1]) == ARGV[1] then
                        return redis.call('del', KEYS[1])
                    end
                    return 0
                    """, Long.class);
    private static final DefaultRedisScript<Long> ATOMIC_RELEASE =
            new DefaultRedisScript<>("""
                    local lock_key = KEYS[1]
                    local set_key = KEYS[2]
                    local expected_userId = ARGV[1]
                    local expected_pictureId = ARGV[2]
                    local lock_json = redis.call('get', lock_key)
                    if not lock_json then return 0 end
                    local lock = cjson.decode(lock_json)
                    if lock['userId'] == tonumber(expected_userId) and tostring(lock['pictureId']) == expected_pictureId then
                        redis.call('srem', set_key, expected_pictureId)
                        return redis.call('del', lock_key)
                    end
                    return 0
                    """, Long.class);
    private static final DefaultRedisScript<String> ATOMIC_CLEAR_USER_LOCKS =
            new DefaultRedisScript<>("""
                    local set_key = KEYS[1]
                    local lock_prefix = KEYS[2]
                    local expected_userId = tonumber(ARGV[1])
                    local picture_ids = redis.call('smembers', set_key)
                    if not picture_ids or #picture_ids == 0 then return '' end
                    local cleared_ids = {}
                    for i, pid in ipairs(picture_ids) do
                        local lock_key = lock_prefix .. pid
                        local lock_json = redis.call('get', lock_key)
                        if lock_json then
                            local lock = cjson.decode(lock_json)
                            if lock['userId'] == expected_userId then
                                redis.call('srem', set_key, pid)
                                redis.call('del', lock_key)
                                cleared_ids[#cleared_ids + 1] = pid
                            end
                        else
                            redis.call('srem', set_key, pid)
                        end
                    end
                    if #cleared_ids == 0 then return '' end
                    return cjson.encode(cleared_ids)
                    """, String.class);

    private final StringRedisTemplate redis;

    public CollabStateStore(StringRedisTemplate redis) {
        this.redis = redis;
        DELETE_IF_VALUE_MATCHES.setResultType(Long.class);
        ATOMIC_RELEASE.setResultType(Long.class);
        ATOMIC_CLEAR_USER_LOCKS.setResultType(String.class);
    }

    /**
     * 尝试获取指定图片的编辑锁。
     * 锁是图片级别的：同一空间中不同图片可以由不同用户同时编辑。
     */
    public boolean tryAcquireLock(Long spaceId, CollabSessionRegistry.LockInfo lock) {
        String key = lockKey(spaceId, lock.getPictureId());
        String value = JSONUtil.toJsonStr(lock);
        Boolean acquired = redis.opsForValue().setIfAbsent(key, value, LOCK_TTL);
        if (Boolean.TRUE.equals(acquired)) {
            redis.opsForSet().add(lockSetKey(spaceId), String.valueOf(lock.getPictureId()));
            return true;
        }
        return matches(getLock(spaceId, lock.getPictureId()), lock.getPictureId(), lock.getUserId());
    }

    /**
     * 释放指定图片的编辑锁（原子操作：读取-检查-删除）。
     */
    public boolean releaseLock(Long spaceId, Long pictureId, Long userId) {
        String lockKeyStr = lockKey(spaceId, pictureId);
        String setKeyStr = lockSetKey(spaceId);
        Long result = redis.execute(ATOMIC_RELEASE,
                List.of(lockKeyStr, setKeyStr),
                String.valueOf(userId), String.valueOf(pictureId));
        return result != null && result > 0;
    }

    /**
     * 获取指定图片的锁信息。
     */
    public CollabSessionRegistry.LockInfo getLock(Long spaceId, Long pictureId) {
        String value = redis.opsForValue().get(lockKey(spaceId, pictureId));
        if (value == null) return null;
        return JSONUtil.toBean(value, CollabSessionRegistry.LockInfo.class);
    }

    /**
     * 获取空间中所有图片的锁信息。
     */
    public List<CollabSessionRegistry.LockInfo> getAllLocks(Long spaceId) {
        Set<String> pictureIds = redis.opsForSet().members(lockSetKey(spaceId));
        if (pictureIds == null || pictureIds.isEmpty()) return List.of();
        return pictureIds.stream()
                .map(pid -> getLock(spaceId, Long.valueOf(pid)))
                .filter(Objects::nonNull)
                .toList();
    }

    public boolean isLockHolder(Long spaceId, Long pictureId, Long userId) {
        return matches(getLock(spaceId, pictureId), pictureId, userId);
    }

    /**
     * 清除指定用户在空间中持有的所有锁（原子操作，单 Lua 脚本完成）。
     */
    public List<Long> clearLocksByUserInSpace(Long userId, Long spaceId) {
        String setKeyStr = lockSetKey(spaceId);
        String lockPrefix = LOCK_KEY_PREFIX + spaceId + ":";
        String result = redis.execute(ATOMIC_CLEAR_USER_LOCKS,
                List.of(setKeyStr, lockPrefix),
                String.valueOf(userId));
        if (result == null || result.isBlank()) return List.of();
        try {
            List<String> ids = JSONUtil.toList(result, String.class);
            return ids.stream().map(Long::valueOf).toList();
        } catch (Exception e) {
            log.warn("[Collab] 解析清除锁结果失败: {}", result, e);
            return List.of();
        }
    }

    public void updatePictureState(Long spaceId, Long pictureId, String stateJson) {
        redis.opsForHash().put(stateKey(spaceId), String.valueOf(pictureId), stateJson);
        redis.expire(stateKey(spaceId), STATE_TTL);
    }

    public List<String> getSpacePictureStates(Long spaceId) {
        Map<Object, Object> entries = redis.opsForHash().entries(stateKey(spaceId));
        return entries.values().stream()
                .map(v -> v != null ? v.toString() : "")
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public void clearPictureState(Long spaceId, Long pictureId) {
        redis.opsForHash().delete(stateKey(spaceId), String.valueOf(pictureId));
    }

    public void clearAllPictureStates(Long spaceId) {
        redis.delete(stateKey(spaceId));
    }

    private boolean matches(CollabSessionRegistry.LockInfo lock, Long pictureId, Long userId) {
        return lock != null
                && Objects.equals(lock.getPictureId(), pictureId)
                && Objects.equals(lock.getUserId(), userId);
    }

    private boolean deleteIfUnchanged(String key, String expectedValue) {
        Long deleted = redis.execute(DELETE_IF_VALUE_MATCHES, Collections.singletonList(key), expectedValue);
        return deleted != null && deleted > 0;
    }

    private String lockKey(Long spaceId, Long pictureId) {
        return LOCK_KEY_PREFIX + spaceId + ":" + pictureId;
    }

    private String lockSetKey(Long spaceId) {
        return LOCK_KEY_PREFIX + spaceId + LOCK_SET_KEY_SUFFIX;
    }

    private String stateKey(Long spaceId) {
        return STATE_KEY_PREFIX + spaceId;
    }
}
