package hk.ljx.fishpicsbackend.collab;

import cn.hutool.json.JSONUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class CollabStateStore {

    private static final Duration LOCK_TTL = Duration.ofMinutes(30);
    private static final Duration STATE_TTL = Duration.ofHours(2);
    private static final String LOCK_KEY_PREFIX = "collab:lock:";
    private static final String STATE_KEY_PREFIX = "collab:state:";
    private static final DefaultRedisScript<Long> DELETE_IF_VALUE_MATCHES =
            new DefaultRedisScript<>("""
                    if redis.call('get', KEYS[1]) == ARGV[1] then
                        return redis.call('del', KEYS[1])
                    end
                    return 0
                    """, Long.class);

    private final StringRedisTemplate redis;

    public CollabStateStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean tryAcquireLock(Long spaceId, CollabSessionRegistry.LockInfo lock) {
        String key = lockKey(spaceId);
        String value = JSONUtil.toJsonStr(lock);
        Boolean acquired = redis.opsForValue().setIfAbsent(key, value, LOCK_TTL);
        if (Boolean.TRUE.equals(acquired)) return true;
        return matches(getLock(spaceId), lock.getPictureId(), lock.getUserId());
    }

    public boolean releaseLock(Long spaceId, Long pictureId, Long userId) {
        CollabSessionRegistry.LockInfo lock = getLock(spaceId);
        if (!matches(lock, pictureId, userId)) return false;
        return deleteIfUnchanged(lockKey(spaceId), JSONUtil.toJsonStr(lock));
    }

    public CollabSessionRegistry.LockInfo getLock(Long spaceId) {
        String value = redis.opsForValue().get(lockKey(spaceId));
        if (value == null) return null;
        return JSONUtil.toBean(value, CollabSessionRegistry.LockInfo.class);
    }

    public boolean isLockHolder(Long spaceId, Long pictureId, Long userId) {
        return matches(getLock(spaceId), pictureId, userId);
    }

    public Long clearLockByUserInSpace(Long userId, Long spaceId) {
        CollabSessionRegistry.LockInfo lock = getLock(spaceId);
        if (lock == null || !Objects.equals(lock.getUserId(), userId)) return null;
        return deleteIfUnchanged(lockKey(spaceId), JSONUtil.toJsonStr(lock)) ? lock.getPictureId() : null;
    }

    public void updatePictureState(Long spaceId, Long pictureId, String stateJson) {
        redis.opsForHash().put(stateKey(spaceId), String.valueOf(pictureId), stateJson);
        redis.expire(stateKey(spaceId), STATE_TTL);
    }

    public List<String> getSpacePictureStates(Long spaceId) {
        Map<Object, Object> entries = redis.opsForHash().entries(stateKey(spaceId));
        return entries.values().stream()
                .map(Object::toString)
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

    private String lockKey(Long spaceId) {
        return LOCK_KEY_PREFIX + spaceId;
    }

    private String stateKey(Long spaceId) {
        return STATE_KEY_PREFIX + spaceId;
    }
}
