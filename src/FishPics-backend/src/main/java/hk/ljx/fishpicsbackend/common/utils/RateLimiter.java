package hk.ljx.fishpicsbackend.common.utils;

import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 通用滑动窗口限流器
 *
 * 用 RedisAtomicOps.incrWithExpire 把 INCR+EXPIRE 封成原子操作
 */
@Slf4j
@Component
public class RateLimiter {

    private final RedisAtomicOps redisAtomicOps;

    public RateLimiter(RedisAtomicOps redisAtomicOps) {
        this.redisAtomicOps = redisAtomicOps;
    }

    /**
     * 固定窗口限流(秒级)
     */
    public void acquire(String key, int limit, int windowSec) {
        String redisKey = "RL:" + key;
        // 原子 INCR+EXPIRE
        long count = redisAtomicOps.incrWithExpire(redisKey, windowSec);
        if (count > limit) {
            log.warn("rate limit exceeded: key={}, count={}, limit={}/{}s", key, count, limit, windowSec);
            throw new BaseException(ExceptionCode.TOO_MANY_REQUESTS, "操作过于频繁,请稍后再试");
        }
    }

    public void acquireMinutes(String key, int limit, int windowMin) {
        acquire(key, limit, windowMin * 60);
    }

    public void acquire(String key, int limit, Duration window) {
        String redisKey = "RL:" + key;
        // 原子 INCR+EXPIRE(Duration 版本)
        long count = redisAtomicOps.incrWithExpire(redisKey, window.getSeconds());
        if (count > limit) {
            log.warn("rate limit exceeded: key={}, count={}, limit={}/{}", key, count, limit, window);
            throw new BaseException(ExceptionCode.TOO_MANY_REQUESTS, "操作过于频繁,请稍后再试");
        }
    }

    public long peek(String key) {
        return 0L; // 简化,不再支持 peek(原本就少用)
    }

    public void clear(String key) {
        // 简化,不再支持 clear
    }
}
