package hk.ljx.fishpicsbackend.common.infra;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class DistributedLockService {

    private final RedissonClient redissonClient;

    public DistributedLockService(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /** 尝试获取锁，超时等待 0 秒（立即返回） */
    public boolean tryLock(String key, long ttlSeconds) {
        RLock lock = redissonClient.getLock(key);
        try {
            return lock.tryLock(0, ttlSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** 释放锁 */
    public void unlock(String key) {
        RLock lock = redissonClient.getLock(key);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    /** tryLock + unlock 封装，返回 true 表示成功执行 */
    public boolean runWithLock(String key, long ttlSeconds, Runnable action) {
        if (!tryLock(key, ttlSeconds)) {
            return false;
        }
        try {
            action.run();
            return true;
        } finally {
            unlock(key);
        }
    }
}
