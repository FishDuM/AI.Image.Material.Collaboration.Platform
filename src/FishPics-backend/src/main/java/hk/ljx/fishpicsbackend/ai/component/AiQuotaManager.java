package hk.ljx.fishpicsbackend.ai.component;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import hk.ljx.fishpicsbackend.ai.dto.AiConfigDTO;
import hk.ljx.fishpicsbackend.common.cache.RedisCacheManager;
import hk.ljx.fishpicsbackend.common.constants.SysConstants;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.mapper.PicSystemMapper;
import hk.ljx.fishpicsbackend.system.entity.PicSystem;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * AI 月度配额管理
 * 配额上限存数据库 (AiConfigDTO)，已用次数存 Redis 月度 key
 */
@Slf4j
@Component
public class AiQuotaManager {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedisCacheManager cacheManager;

    @Resource
    private PicSystemMapper picSystemMapper;

    private static final String QUOTA_KEY_PREFIX = "AI:QUOTA:";
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyyMM");

    /**
     * 检查并消费一次 AI 配额
     *
     * @param feature "tag" 或 "draw"
     * @param userId  用户 ID
     * @param level   用户等级 (0=普通, 1=VIP, 2=SVIP)
     * @return 本月剩余次数
     */
    public int checkAndConsume(String feature, Long userId, Integer level) {
        if (level == null || level <= 0) {
            throw new BaseException(ExceptionCode.FORBIDDEN, "升级 VIP 解锁 AI 功能");
        }

        int limit = getQuotaLimit(level, feature);
        if (limit <= 0) {
            throw new BaseException(ExceptionCode.FORBIDDEN, "当前等级无此功能配额");
        }

        String monthKey = YearMonth.now().format(MONTH_FMT);
        String redisKey = QUOTA_KEY_PREFIX + feature.toUpperCase() + ":" + userId + ":" + monthKey;

        Long used = stringRedisTemplate.opsForValue().increment(redisKey);
        // 首次写入设置 TTL 40 天（覆盖最长月份，下月初自动清理）
        if (used != null && used == 1) {
            stringRedisTemplate.expire(redisKey, 40, TimeUnit.DAYS);
        }

        if (used != null && used > limit) {
            // 超限回滚
            stringRedisTemplate.opsForValue().decrement(redisKey);
            throw new BaseException(ExceptionCode.TOO_MANY_REQUESTS,
                    "本月" + featureName(feature) + "额度已用完（" + limit + "次/月）");
        }

        int remaining = limit - (used != null ? used.intValue() : 0);
        log.info("AI配额消耗: userId={}, feature={}, used={}, limit={}, remaining={}",
                userId, feature, used, limit, remaining);
        return remaining;
    }

    /**
     * 查询本月剩余配额（不消费）
     */
    public int getRemaining(String feature, Long userId, Integer level) {
        if (level == null || level <= 0) return 0;
        int limit = getQuotaLimit(level, feature);
        if (limit <= 0) return 0;

        String monthKey = YearMonth.now().format(MONTH_FMT);
        String redisKey = QUOTA_KEY_PREFIX + feature.toUpperCase() + ":" + userId + ":" + monthKey;
        String val = stringRedisTemplate.opsForValue().get(redisKey);
        int used = (val != null) ? Integer.parseInt(val) : 0;
        return Math.max(0, limit - used);
    }

    private int getQuotaLimit(int level, String feature) {
        AiConfigDTO config = loadConfig();
        return switch (level) {
            case 1 -> "tag".equals(feature)
                    ? config.getVipTagQuota()
                    : config.getVipDrawQuota();
            case 2 -> "tag".equals(feature)
                    ? config.getSvipTagQuota()
                    : config.getSvipDrawQuota();
            default -> 0;
        };
    }

    private AiConfigDTO loadConfig() {
        AiConfigDTO config = cacheManager.getSysConfigCache().get(SysConstants.AI_CONFIG_KEY, AiConfigDTO.class);
        if (config != null) return config;

        List<PicSystem> records = picSystemMapper.selectList(
                new LambdaQueryWrapper<PicSystem>().eq(PicSystem::getSyskey, SysConstants.AI_CONFIG_KEY));
        if (records == null || records.isEmpty() || records.get(0).getSysvalue() == null) {
            return defaultQuotaConfig();
        }
        config = JSONUtil.toBean(records.get(0).getSysvalue(), AiConfigDTO.class);
        // 补齐配额默认值（兼容旧数据无配额字段的情况）
        config.fillDefaults();
        cacheManager.getSysConfigCache().put(SysConstants.AI_CONFIG_KEY, config);
        return config;
    }

    private AiConfigDTO defaultQuotaConfig() {
        AiConfigDTO config = new AiConfigDTO();
        config.fillDefaults();
        return config;
    }

    private String featureName(String feature) {
        return "tag".equals(feature) ? "AI标注" : "AI生图";
    }
}
