package hk.ljx.fishpicsbackend.ai.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeConnectionProperties;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import hk.ljx.fishpicsbackend.ai.dto.AiDrawPictureDTO;
import hk.ljx.fishpicsbackend.ai.dto.AiConfigDTO;
import hk.ljx.fishpicsbackend.ai.dto.AiTaskQueryDTO;
import hk.ljx.fishpicsbackend.ai.vo.AiStatsVO;
import hk.ljx.fishpicsbackend.ai.vo.AiTaskVO;
import hk.ljx.fishpicsbackend.common.cache.MultiLevelCacheManager;
import hk.ljx.fishpicsbackend.common.constants.SysConstants;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.utils.RateLimiter;
import hk.ljx.fishpicsbackend.mapper.PicSystemMapper;
import hk.ljx.fishpicsbackend.system.entity.PicSystem;
import hk.ljx.fishpicsbackend.task.entity.Task;
import hk.ljx.fishpicsbackend.task.service.TaskService;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import hk.ljx.fishpicsbackend.picture.entity.Picture;
import hk.ljx.fishpicsbackend.picture.service.PictureService;
import hk.ljx.fishpicsbackend.user.entity.User;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
public class AiServiceImpl implements AiService {

    /** 按用户等级差异化 AI 配额 */
    private static final int AI_DRAW_LIMIT_PER_MIN = 3;
    private static final int AI_DRAW_LIMIT_PER_HOUR = 20;
    private static final int AI_DRAW_LIMIT_PER_DAY = 100;
    /** VIP/SVIP 提高配额 */
    private static final int AI_DRAW_LIMIT_PER_MIN_VIP = 6;
    private static final int AI_DRAW_LIMIT_PER_HOUR_VIP = 50;
    private static final int AI_DRAW_LIMIT_PER_DAY_VIP = 300;

    /** AI 标签任务限流 */
    private static final int AI_TAG_LIMIT_PER_MIN = 5;
    private static final int AI_TAG_LIMIT_PER_HOUR = 30;

    private final ReentrantLock configLock = new ReentrantLock();

    @Resource
    private TaskService taskService;

    @Resource
    private DashScopeConnectionProperties dashScopeConnectionProperties;

    @Resource
    private PictureService pictureService;

    @Resource
    private RateLimiter rateLimiter;

    @Resource
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    @Resource
    private PicSystemMapper picSystemMapper;

    @Resource
    private MultiLevelCacheManager cacheManager;

    /**
     * 提交图片标签任务
     */
    @Override
    public String submitTagTask(Long pictureId) {
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(user == null, ExceptionCode.NOT_LOGIN);

        // 鉴权（图片归属 + 私密图）先于限流，避免攻击者用任意 pictureId 消耗自己配额
        Picture picture = pictureService.getById(pictureId);
        ExcUtils.throwIfTrue(picture == null, "图片不存在");
        ExcUtils.throwIfTrue(picture.getUserId() == null, "图片数据异常");
        // 已登录但不是图片 owner → FORBIDDEN
        ExcUtils.throwIfTrue(!user.getId().equals(picture.getUserId()), ExceptionCode.FORBIDDEN, "只能对自己的图片触发 AI 标签识别");
        ExcUtils.throwIfTrue(picture.getIsPrivate() != null && picture.getIsPrivate() == 1,
                ExceptionCode.FORBIDDEN, "私密图片不能使用 AI 功能");

        // 鉴权后再限流
        rateLimiter.acquireMinutes("AI:TAG:" + user.getId(), AI_TAG_LIMIT_PER_MIN, 1);
        rateLimiter.acquireMinutes("AI:TAG:HOUR:" + user.getId(), AI_TAG_LIMIT_PER_HOUR, 60);

        // 防双击/重试导致重复提交任务
        // 用 SETNX 抢占 key,30 秒内同 user+picture 不允许重复提交
        String dedupKey = "AI:SUBMIT:TAG:" + user.getId() + ":" + pictureId;
        // dedup TTL 30s（仅防双击/防抖）
        Boolean firstTime = stringRedisTemplate.opsForValue().setIfAbsent(dedupKey, "1", 30, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(firstTime)) {
            throw new BaseException(ExceptionCode.TOO_MANY_REQUESTS, "该图片的 AI 任务正在处理中,请稍后再试");
        }

        return taskService.submitTask("ai_tag", String.valueOf(pictureId), null, user.getId());
    }

    @Override
    public Task getTagResult(String taskId) {
        Task task = taskService.getTaskByTaskId(taskId);
        ExcUtils.throwIfTrue(task == null, ExceptionCode.NOT_FOUND, "任务不存在");
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(user == null, ExceptionCode.NOT_LOGIN);
        ExcUtils.throwIfTrue(!user.getId().equals(task.getUserId()),
                ExceptionCode.FORBIDDEN, "无权查看此任务");
        return task;
    }

    /**
     * AI 生图限流
     * 普通用户 3/min, 20/hour, 100/day
     * VIP/SVIP 用户 6/min, 50/hour, 300/day
     */
    @Override
    public String submitDrawTask(AiDrawPictureDTO drawPictureDTO, Long userId) {
        ExcUtils.throwIfTrue(drawPictureDTO == null || drawPictureDTO.getDescription() == null,
                "画面描述不能为空");
        // description 长度限制(防 DoS/注入)
        String description = drawPictureDTO.getDescription();
        ExcUtils.throwIfTrue(description.length() > 500, ExceptionCode.PARAMETER_ERROR, "画面描述不能超过 500 字符");

        User user = UserHolder.getUser();
        // 校验传入 userId 与登录用户一致
        if (user != null && user.getId() != null) {
            userId = user.getId();
        }
        int level = user != null && user.getLevel() != null ? user.getLevel() : 0;
        boolean vip = level >= 1;

        // 三级限流 — 分钟/小时/天
        rateLimiter.acquire("AI:DRAW:MIN:" + userId,
                vip ? AI_DRAW_LIMIT_PER_MIN_VIP : AI_DRAW_LIMIT_PER_MIN, 60);
        rateLimiter.acquire("AI:DRAW:HOUR:" + userId,
                vip ? AI_DRAW_LIMIT_PER_HOUR_VIP : AI_DRAW_LIMIT_PER_HOUR, 3600);
        rateLimiter.acquire("AI:DRAW:DAY:" + userId,
                vip ? AI_DRAW_LIMIT_PER_DAY_VIP : AI_DRAW_LIMIT_PER_DAY, Duration.ofDays(1));

        // 去重 key 包含 style/size/exclusion，防止同描述不同风格被错误去重
        String dedupInput = description
                + "|" + (drawPictureDTO.getStyle() != null ? drawPictureDTO.getStyle() : "")
                + "|" + (drawPictureDTO.getSize() != null ? drawPictureDTO.getSize() : "")
                + "|" + (drawPictureDTO.getExclusion() != null ? drawPictureDTO.getExclusion() : "");
        String descHash = DigestUtil.sha256Hex(dedupInput);
        String dedupKey = "AI:SUBMIT:DRAW:" + userId + ":" + descHash;
        // dedup TTL 设为 200s，略大于任务最大超时 180s，确保任务进行中用户不能重复提交
        Boolean firstTime = stringRedisTemplate.opsForValue().setIfAbsent(dedupKey, "1", 200, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(firstTime)) {
            throw new BaseException(ExceptionCode.TOO_MANY_REQUESTS, "相同描述的绘图任务正在处理中,请稍后再试");
        }

        String apikey = dashScopeConnectionProperties.getApiKey();
        ExcUtils.throwIfTrue(apikey == null || apikey.isBlank(),
                ExceptionCode.SERVICE_UNAVAILABLE, "AI服务未配置，无法提交任务");
        String paramJson = JSONUtil.toJsonStr(drawPictureDTO);
        return taskService.submitTask("ai_draw", null, paramJson, userId);
    }

    @Override
    public Task getDrawResult(String taskId) {
        Task task = taskService.getTaskByTaskId(taskId);
        ExcUtils.throwIfTrue(task == null, ExceptionCode.NOT_FOUND, "任务不存在");
        // 加 user 校验，防暴力枚举 UUID 偷他人结果
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(user == null, ExceptionCode.NOT_LOGIN);
        ExcUtils.throwIfTrue(!user.getId().equals(task.getUserId()),
                ExceptionCode.FORBIDDEN, "无权查看此任务");
        return task;
    }

    @Override
    public String getDownloadImageUrl(String taskId) {
        Task task = taskService.getTaskByTaskId(taskId);
        ExcUtils.throwIfTrue(task == null, ExceptionCode.NOT_FOUND, "任务不存在");

        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(user == null, ExceptionCode.NOT_LOGIN);
        // 已登录但不是任务 owner → FORBIDDEN
        ExcUtils.throwIfTrue(!user.getId().equals(task.getUserId()), ExceptionCode.FORBIDDEN, "无权下载此任务结果");
        ExcUtils.throwIfTrue(!"ai_draw".equals(task.getBizType()), ExceptionCode.PARAMETER_ERROR, "任务类型不支持下载");
        ExcUtils.throwIfTrue(!"DONE".equals(task.getStatus()), ExceptionCode.PARAMETER_ERROR, "任务尚未完成");
        ExcUtils.throwIfTrue(task.getResult() == null || task.getResult().isBlank(), ExceptionCode.NOT_FOUND, "图片结果不存在");
        return task.getResult();
    }

    /**
     * 从 AiController 移到 AiService，让其他端点能查 AiConfig 开关
     * 无配置记录/null 字段 = 全开
     * editingEnabled 特殊：默认 false（无对应端点实现）
     * 多级缓存：复用 sysConfigCache（L1 Caffeine + L2 Redis）
     */
    @Override
    public boolean isFeatureEnabled(String fieldName) {
        try {
            // 先查多级缓存
            Object cached = cacheManager.getSysConfigCache().get(SysConstants.AI_CONFIG_KEY);
            AiConfigDTO config;
            if (cached instanceof AiConfigDTO dto) {
                config = dto;
            } else {
                // 缓存miss，查数据库
                List<PicSystem> records = picSystemMapper.selectList(
                        new LambdaQueryWrapper<PicSystem>().eq(PicSystem::getSyskey, SysConstants.AI_CONFIG_KEY));
                if (records == null || records.isEmpty() || records.get(0).getSysvalue() == null) {
                    // 无配置:tagging/generation/recommendation 默认开,editing 默认关
                    return !"editingEnabled".equals(fieldName);
                }
                config = JSONUtil.toBean(records.get(0).getSysvalue(), AiConfigDTO.class);
                cacheManager.getSysConfigCache().put(SysConstants.AI_CONFIG_KEY, config);
            }
            switch (fieldName) {
                case "taggingEnabled": return config.getTaggingEnabled() == null || config.getTaggingEnabled();
                case "editingEnabled": return config.getEditingEnabled() != null && config.getEditingEnabled();
                case "generationEnabled": return config.getGenerationEnabled() == null || config.getGenerationEnabled();
                case "recommendationEnabled": return config.getRecommendationEnabled() == null || config.getRecommendationEnabled();
                default: return true;
            }
        } catch (Exception e) {
            log.warn("read AI config failed, default to enabled: {}", e.getMessage());
            return true;
        }
    }

    @Override
    public Task getTaskByTaskId(String taskId) {
        return taskService.getTaskByTaskId(taskId);
    }

    @Override
    public IPage<AiTaskVO> getAdminTasks(AiTaskQueryDTO queryDTO) {
        int current = Math.max(queryDTO.getCurrent(), 1);
        int pageSize = Math.min(Math.max(queryDTO.getPageSize(), 1), 100);
        Page<Task> page = new Page<>(current, pageSize);
        LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<>();

        if (queryDTO.getType() != null) {
            String bizType = switch (queryDTO.getType()) {
                case 0 -> "ai_tag";
                case 2 -> "ai_draw";
                default -> null;
            };
            if (bizType != null) {
                wrapper.eq(Task::getBizType, bizType);
            }
        }

        if (queryDTO.getStatus() != null) {
            switch (queryDTO.getStatus()) {
                case 0 -> wrapper.in(Task::getStatus, "PENDING", "PROCESSING");
                case 1 -> wrapper.eq(Task::getStatus, "DONE");
                case 2 -> wrapper.eq(Task::getStatus, "FAILED");
                default -> {
                }
            }
        }

        wrapper.orderByDesc(Task::getCreateTime);
        IPage<Task> taskPage = taskService.getBaseMapper().selectPage(page, wrapper);

        List<AiTaskVO> voList = new ArrayList<>();
        for (Task task : taskPage.getRecords()) {
            AiTaskVO vo = new AiTaskVO();
            vo.setId(task.getId());
            vo.setUserId(task.getUserId());
            vo.setSubType(task.getBizType());
            vo.setPictureId(task.getBizId());
            vo.setCreateTime(task.getCreateTime());
            vo.setErrorMsg(task.getErrorMsg());
            vo.setType(switch (task.getBizType()) {
                case "ai_tag" -> 0;
                case "ai_draw" -> 2;
                default -> -1;
            });
            vo.setStatus(switch (task.getStatus()) {
                case "DONE" -> 1;
                case "FAILED" -> 2;
                default -> 0;
            });
            voList.add(vo);
        }

        Page<AiTaskVO> result = new Page<>(taskPage.getCurrent(), taskPage.getSize(), taskPage.getTotal());
        result.setRecords(voList);
        return result;
    }

    @Override
    public AiStatsVO getAdminStats() {
        AiStatsVO stats = new AiStatsVO();
        stats.setTotalTasks(taskService.count());
        stats.setSuccessTasks(taskService.count(new LambdaQueryWrapper<Task>().eq(Task::getStatus, "DONE")));
        stats.setFailedTasks(taskService.count(new LambdaQueryWrapper<Task>().eq(Task::getStatus, "FAILED")));
        stats.setProcessingTasks(taskService.count(new LambdaQueryWrapper<Task>().in(Task::getStatus, "PENDING", "PROCESSING")));

        Map<String, Long> typeCounts = new HashMap<>();
        typeCounts.put("0", taskService.count(new LambdaQueryWrapper<Task>().eq(Task::getBizType, "ai_tag")));
        typeCounts.put("2", taskService.count(new LambdaQueryWrapper<Task>().eq(Task::getBizType, "ai_draw")));
        stats.setTypeCounts(typeCounts);
        return stats;
    }

    @Override
    public AiConfigDTO getAdminConfig() {
        List<PicSystem> records = picSystemMapper.selectList(
                new LambdaQueryWrapper<PicSystem>().eq(PicSystem::getSyskey, SysConstants.AI_CONFIG_KEY));

        if (records == null || records.isEmpty() || records.get(0).getSysvalue() == null) {
            AiConfigDTO config = new AiConfigDTO();
            config.setTaggingEnabled(true);
            config.setEditingEnabled(false);
            config.setGenerationEnabled(true);
            config.setRecommendationEnabled(true);
            return config;
        }
        return JSONUtil.toBean(records.get(0).getSysvalue(), AiConfigDTO.class);
    }

    @Override
    public Boolean updateAdminConfig(AiConfigDTO configDTO) {
        configLock.lock();
        try {
            List<PicSystem> records = picSystemMapper.selectList(
                    new LambdaQueryWrapper<PicSystem>().eq(PicSystem::getSyskey, SysConstants.AI_CONFIG_KEY));

            AiConfigDTO config;
            PicSystem target;
            if (records == null || records.isEmpty() || records.get(0).getSysvalue() == null) {
                config = new AiConfigDTO();
                config.setTaggingEnabled(true);
                config.setEditingEnabled(false);
                config.setGenerationEnabled(true);
                config.setRecommendationEnabled(true);
                target = new PicSystem();
                target.setSyskey(SysConstants.AI_CONFIG_KEY);
                target.setSysvalue(JSONUtil.toJsonStr(applyDtoToConfig(config, configDTO)));
                picSystemMapper.insert(target);
            } else {
                PicSystem canonical = records.get(0);
                config = JSONUtil.toBean(canonical.getSysvalue(), AiConfigDTO.class);
                applyDtoToConfig(config, configDTO);
                canonical.setSysvalue(JSONUtil.toJsonStr(config));
                picSystemMapper.updateById(canonical);
                if (records.size() > 1) {
                    for (int i = 1; i < records.size(); i++) {
                        picSystemMapper.deleteById(records.get(i).getId());
                    }
                }
            }
            cacheManager.getSysConfigCache().evict(SysConstants.AI_CONFIG_KEY);
            return true;
        } finally {
            configLock.unlock();
        }
    }

    private AiConfigDTO applyDtoToConfig(AiConfigDTO config, AiConfigDTO configDTO) {
        if (configDTO.getTaggingEnabled() != null) config.setTaggingEnabled(configDTO.getTaggingEnabled());
        if (configDTO.getEditingEnabled() != null) config.setEditingEnabled(configDTO.getEditingEnabled());
        if (configDTO.getGenerationEnabled() != null) config.setGenerationEnabled(configDTO.getGenerationEnabled());
        if (configDTO.getRecommendationEnabled() != null) config.setRecommendationEnabled(configDTO.getRecommendationEnabled());
        return config;
    }
}
