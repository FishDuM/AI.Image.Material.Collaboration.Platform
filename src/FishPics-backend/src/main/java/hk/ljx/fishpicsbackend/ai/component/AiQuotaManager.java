package hk.ljx.fishpicsbackend.ai.component;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import hk.ljx.fishpicsbackend.ai.dto.AiConfigDTO;
import hk.ljx.fishpicsbackend.common.cache.RedisCacheManager;
import hk.ljx.fishpicsbackend.common.constants.SysConstants;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.infra.RedisAtomicOps;
import hk.ljx.fishpicsbackend.mapper.PicSystemMapper;
import hk.ljx.fishpicsbackend.system.entity.PicSystem;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Component
public class AiQuotaManager {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedisCacheManager cacheManager;

    @Resource
    private RedisAtomicOps redisAtomicOps;

    @Resource
    private PicSystemMapper picSystemMapper;

    private static final String QUOTA_KEY_PREFIX = "AI:QUOTA:";
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyyMM");

    public void refund(String feature, Long userId) {
        String monthKey = YearMonth.now().format(MONTH_FMT);
        String redisKey = QUOTA_KEY_PREFIX + feature.toUpperCase() + ":" + userId + ":" + monthKey;
        Long val = stringRedisTemplate.opsForValue().decrement(redisKey);
        if (val != null && val < 0) {
            stringRedisTemplate.delete(redisKey);
        }
        log.debug("AI配额退还: userId={}, feature={}", userId, feature);
    }

    // feature: "tag" 或 "draw"，level: 0=普通 1=VIP 2=SVIP
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

        long used = redisAtomicOps.incrWithCheckAndRollback(redisKey, 40 * 24 * 3600, limit);
        if (used < 0) {
            throw new BaseException(ExceptionCode.TOO_MANY_REQUESTS,
                    "本月" + featureName(feature) + "额度已用完（" + limit + "次/月）");
        }

        int remaining = limit - (int) used;
        log.debug("AI配额消耗: userId={}, feature={}, used={}, limit={}, remaining={}",
                userId, feature, used, limit, remaining);
        return remaining;
    }

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

    public AiConfigDTO loadRawConfig() {
        AiConfigDTO config = cacheManager.getSysConfigCache().get(SysConstants.AI_CONFIG_KEY, AiConfigDTO.class);
        if (config != null) return config;

        List<PicSystem> records = picSystemMapper.selectList(
                new LambdaQueryWrapper<PicSystem>().eq(PicSystem::getSyskey, SysConstants.AI_CONFIG_KEY));
        if (records == null || records.isEmpty() || records.get(0).getSysvalue() == null) {
            return null;
        }
        config = JSONUtil.toBean(records.get(0).getSysvalue(), AiConfigDTO.class);
        cacheManager.getSysConfigCache().put(SysConstants.AI_CONFIG_KEY, config);
        return config;
    }

    private AiConfigDTO loadConfig() {
        AiConfigDTO config = loadRawConfig();
        if (config == null) {
            return defaultQuotaConfig();
        }
        // 兼容旧数据：补齐配额默认值
        config.fillDefaults();
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
