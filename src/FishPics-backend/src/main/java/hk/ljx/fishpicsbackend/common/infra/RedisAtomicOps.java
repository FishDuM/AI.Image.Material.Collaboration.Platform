package hk.ljx.fishpicsbackend.common.infra;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * Redis 原子操作工具类
 *
 * 用 Lua 脚本把多步操作封成单步原子操作
 * - INCR + EXPIRE 在同一 Lua 中
 * - GET + DELETE 在同一 Lua 中
 */
@Slf4j
@Component
public class RedisAtomicOps {

    /** INCR + (NX 时设 EXPIRE) — 原子限流 */
    private static final String LUA_INCR_WITH_EXPIRE =
            "local v = redis.call('INCR', KEYS[1]) " +
            "if v == 1 then " +
            "  redis.call('EXPIRE', KEYS[1], ARGV[1]) " +
            "end " +
            "return v";

    /** GET + DELETE — 原子消费(防重放/竞态) */
    private static final String LUA_GET_AND_DELETE =
            "local v = redis.call('GET', KEYS[1]) " +
            "if v then " +
            "  redis.call('DEL', KEYS[1]) " +
            "end " +
            "return v";

    /** INCR + EXPIRE(无条件,用于登录失败计数) */
    private static final String LUA_INCR_EXPIRE =
            "local v = redis.call('INCR', KEYS[1]) " +
            "redis.call('EXPIRE', KEYS[1], ARGV[1], 'NX') " +
            "return v";

    /** SET IF ABSENT OR GET — 如果 key 不存在则 set,否则返回已存在的 value */
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
    private static final RedisScript<Long> SCRIPT_INCR_EXPIRE =
            new DefaultRedisScript<>(LUA_INCR_EXPIRE, Long.class);
    private static final RedisScript<String> SCRIPT_SET_IF_ABSENT_OR_GET =
            new DefaultRedisScript<>(LUA_SET_IF_ABSENT_OR_GET, String.class);

    private final StringRedisTemplate stringRedisTemplate;

    public RedisAtomicOps(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 原子 INCR + 首次设 TTL
     * @return INCR 后的值
     */
    public long incrWithExpire(String key, long ttlSeconds) {
        Long v = stringRedisTemplate.execute(
                SCRIPT_INCR_WITH_EXPIRE,
                Collections.singletonList(key),
                String.valueOf(ttlSeconds));
        return v == null ? 0L : v;
    }

    /**
     * 原子 INCR + 用 NX 模式补 EXPIRE(不覆盖已有 TTL)
     * 用于登录失败计数 — 第一次 INCR 时 EXPIRE,后续 INCR 不动 TTL
     */
    public long incrExpireNx(String key, long ttlSeconds) {
        Long v = stringRedisTemplate.execute(
                SCRIPT_INCR_EXPIRE,
                Collections.singletonList(key),
                String.valueOf(ttlSeconds));
        return v == null ? 0L : v;
    }

    /**
     * 原子 SET-IF-ABSENT-OR-GET
     * 防止两个并发请求都 initiateMultipartUpload
     * - key 不存在:写入 value,设 TTL,返回 value
     * - key 已存在:不写,返回已存在的 value
     */
    public String setIfAbsentOrGet(String key, String value, long ttlSeconds) {
        String v = stringRedisTemplate.execute(
                SCRIPT_SET_IF_ABSENT_OR_GET,
                Collections.singletonList(key),
                value,
                String.valueOf(ttlSeconds));
        return v;
    }

    /**
     * 原子 GET + DEL — 一次性消费 key(防重放)
     * @return key 存在时返回值,key 不存在返回 null
     */
    public String getAndDelete(String key) {
        return stringRedisTemplate.execute(
                SCRIPT_GET_AND_DELETE,
                Collections.singletonList(key));
    }

    /**
     * 原子 INCR + CHECK + 超额回滚
     * - INCR key，第一次时设 EXPIRE
     * - 如果新值 > maxCount：回滚(DECR)+ 返回 -1
     * - 否则返回新值
     */
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
