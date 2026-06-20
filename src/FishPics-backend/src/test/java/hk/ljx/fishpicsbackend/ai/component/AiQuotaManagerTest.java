package hk.ljx.fishpicsbackend.ai.component;

import hk.ljx.fishpicsbackend.ai.dto.AiConfigDTO;
import hk.ljx.fishpicsbackend.common.cache.RedisCacheManager;
import hk.ljx.fishpicsbackend.common.cache.RedisTtlCache;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.infra.RedisAtomicOps;
import hk.ljx.fishpicsbackend.mapper.PicSystemMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AiQuotaManager 单元测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiQuotaManagerTest {

    @InjectMocks
    private AiQuotaManager aiQuotaManager;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private RedisCacheManager cacheManager;

    @Mock
    private PicSystemMapper picSystemMapper;

    @Mock
    private RedisAtomicOps redisAtomicOps;

    @Mock
    private RedisTtlCache sysConfigCache;

    @Mock
    private ValueOperations<String, String> valueOps;

    @BeforeEach
    void setUp() {
        // 默认缓存未命中，DB 也无配置 → 使用默认配额
        when(cacheManager.getSysConfigCache()).thenReturn(sysConfigCache);
        when(sysConfigCache.get(anyString(), eq(AiConfigDTO.class))).thenReturn(null);
        when(picSystemMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
    }

    private void mockConfig(AiConfigDTO config) {
        when(sysConfigCache.get(anyString(), eq(AiConfigDTO.class))).thenReturn(config);
    }

    // ==================== 普通用户拒绝 ====================

    @Test
    @DisplayName("普通用户 (level=0) 应被拒绝")
    void checkAndConsume_normalUser_throwsForbidden() {
        BaseException ex = assertThrows(BaseException.class,
                () -> aiQuotaManager.checkAndConsume("tag", 1L, 0));
        assertTrue(ex.getMessage().contains("VIP"));
    }

    @Test
    @DisplayName("level=null 应被拒绝")
    void checkAndConsume_nullLevel_throwsForbidden() {
        assertThrows(BaseException.class,
                () -> aiQuotaManager.checkAndConsume("draw", 1L, null));
    }

    // ==================== VIP 标注配额 ====================

    @Test
    @DisplayName("VIP 标注 - 首次使用应成功")
    void checkAndConsume_vipTag_firstUse_success() {
        when(redisAtomicOps.incrWithCheckAndRollback(anyString(), anyLong(), eq(1000L))).thenReturn(1L);

        int remaining = aiQuotaManager.checkAndConsume("tag", 100L, 1);

        assertEquals(999, remaining); // 默认 1000 - 1
    }

    @Test
    @DisplayName("VIP 标注 - 第二次使用应返回剩余配额")
    void checkAndConsume_vipTag_secondUse_noTtlSet() {
        when(redisAtomicOps.incrWithCheckAndRollback(anyString(), anyLong(), eq(1000L))).thenReturn(2L);

        int remaining = aiQuotaManager.checkAndConsume("tag", 100L, 1);

        assertEquals(998, remaining);
    }

    // ==================== VIP 生图配额 ====================

    @Test
    @DisplayName("VIP 生图 - 正常消耗")
    void checkAndConsume_vipDraw_success() {
        when(redisAtomicOps.incrWithCheckAndRollback(anyString(), anyLong(), eq(50L))).thenReturn(1L);

        int remaining = aiQuotaManager.checkAndConsume("draw", 100L, 1);

        assertEquals(49, remaining); // 默认 50 - 1
    }

    // ==================== SVIP 配额 ====================

    @Test
    @DisplayName("SVIP 标注 - 使用默认配额 5000")
    void checkAndConsume_svipTag_defaultQuota() {
        when(redisAtomicOps.incrWithCheckAndRollback(anyString(), anyLong(), eq(5000L))).thenReturn(1L);

        int remaining = aiQuotaManager.checkAndConsume("tag", 100L, 2);

        assertEquals(4999, remaining);
    }

    @Test
    @DisplayName("SVIP 生图 - 使用默认配额 200")
    void checkAndConsume_svipDraw_defaultQuota() {
        when(redisAtomicOps.incrWithCheckAndRollback(anyString(), anyLong(), eq(200L))).thenReturn(1L);

        int remaining = aiQuotaManager.checkAndConsume("draw", 100L, 2);

        assertEquals(199, remaining);
    }

    // ==================== 自定义配额 ====================

    @Test
    @DisplayName("自定义配额 - 数据库配置优先于默认值")
    void checkAndConsume_customQuota_fromConfig() {
        AiConfigDTO config = new AiConfigDTO();
        config.setVipDrawQuota(30);
        config.setVipTagQuota(500);
        mockConfig(config);

        when(redisAtomicOps.incrWithCheckAndRollback(anyString(), anyLong(), eq(30L))).thenReturn(1L);

        int remaining = aiQuotaManager.checkAndConsume("draw", 100L, 1);

        assertEquals(29, remaining); // 自定义 30 - 1
    }

    // ==================== 超限回滚 ====================

    @Test
    @DisplayName("超限 - Lua 脚本返回 -1 应抛异常")
    void checkAndConsume_exceeded_rollbackAndThrow() {
        when(redisAtomicOps.incrWithCheckAndRollback(anyString(), anyLong(), eq(50L))).thenReturn(-1L);

        BaseException ex = assertThrows(BaseException.class,
                () -> aiQuotaManager.checkAndConsume("draw", 100L, 1));

        assertTrue(ex.getMessage().contains("额度已用完"));
    }

    // ==================== getRemaining ====================

    @Test
    @DisplayName("getRemaining - 普通用户返回 0")
    void getRemaining_normalUser_returnsZero() {
        assertEquals(0, aiQuotaManager.getRemaining("tag", 1L, 0));
    }

    @Test
    @DisplayName("getRemaining - 无使用记录时返回完整配额")
    void getRemaining_noUsage_returnsFullQuota() {
        when(valueOps.get(anyString())).thenReturn(null);

        int remaining = aiQuotaManager.getRemaining("draw", 100L, 1);

        assertEquals(50, remaining);
    }

    @Test
    @DisplayName("getRemaining - 已使用部分后返回剩余")
    void getRemaining_partialUsage_returnsRemaining() {
        when(valueOps.get(anyString())).thenReturn("10");

        int remaining = aiQuotaManager.getRemaining("tag", 100L, 1);

        assertEquals(990, remaining);
    }
}
