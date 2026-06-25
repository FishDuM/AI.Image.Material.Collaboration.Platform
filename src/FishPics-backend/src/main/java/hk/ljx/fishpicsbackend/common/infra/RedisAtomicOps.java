package hk.ljx.fishpicsbackend.common.infra;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Slf4j
@Component
public class RedisAtomicOps {

    private static final String LUA_INCR_WITH_EXPIRE =
            "local v = redis.call('INCR', KEYS[1]) " +
            "if v == 1 then " +
            "  redis.call('EXPIRE', KEYS[1], ARGV[1]) " +
            "end " +
            "return v";

    private static final String LUA_GET_AND_DELETE =
            "local v = redis.call('GET', KEYS[1]) " +
            "if v then " +
            "  redis.call('DEL', KEYS[1]) " +
            "end " +
            "return v";

    private static final String LUA_SET_IF_ABSENT_OR_GET =
            "local v = redis.call('GET', KEYS[1]) " +
            "if v then " +
            "  return v " +
            "end " +
            "redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[2]) " +
            "return ARGV[1]";

    private static final RedisScript<Long> SCRIPT_INCR_WITH_EXPIRE =
            new DefaultRedisScript<>(LUA_INCR_WITH_EXPIRE, Long.class);
    private static final RedisScript<String> SCRIPT_GET_AND_DELETE =
            new DefaultRedisScript<>(LUA_GET_AND_DELETE, String.class);
    private static final RedisScript<String> SCRIPT_SET_IF_ABSENT_OR_GET =
            new DefaultRedisScript<>(LUA_SET_IF_ABSENT_OR_GET, String.class);

    private final StringRedisTemplate stringRedisTemplate;

    public RedisAtomicOps(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long incrWithExpire(String key, long ttlSeconds) {
        Long v = stringRedisTemplate.execute(
                SCRIPT_INCR_WITH_EXPIRE,
                Collections.singletonList(key),
                String.valueOf(ttlSeconds));
        return v == null ? 0L : v;
    }

    public String setIfAbsentOrGet(String key, String value, long ttlSeconds) {
        String v = stringRedisTemplate.execute(
                SCRIPT_SET_IF_ABSENT_OR_GET,
                Collections.singletonList(key),
                value,
                String.valueOf(ttlSeconds));
        return v;
    }

    public String getAndDelete(String key) {
        return stringRedisTemplate.execute(
                SCRIPT_GET_AND_DELETE,
                Collections.singletonList(key));
    }

    private static final String LUA_INCR_WITH_CHECK =
            "local v = redis.call('INCR', KEYS[1]) " +
            "if v == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end " +
            "local max = tonumber(ARGV[2]) " +
            "if max > 0 and v > max then " +
            "  redis.call('DECR', KEYS[1]) " +
            "  return -1 " +
            "end " +
            "return v";

    private static final RedisScript<Long> SCRIPT_INCR_WITH_CHECK =
            new DefaultRedisScript<>(LUA_INCR_WITH_CHECK, Long.class);

    public long incrWithCheckAndRollback(String key, long ttlSec, long maxCount) {
        Long v = stringRedisTemplate.execute(
                SCRIPT_INCR_WITH_CHECK,
                Collections.singletonList(key),
                String.valueOf(ttlSec),
                String.valueOf(maxCount));
        return v == null ? -1L : v;
    }
}
