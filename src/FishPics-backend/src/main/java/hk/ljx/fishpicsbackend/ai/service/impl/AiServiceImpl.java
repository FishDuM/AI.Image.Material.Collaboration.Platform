package hk.ljx.fishpicsbackend.ai.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeConnectionProperties;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import hk.ljx.fishpicsbackend.ai.component.AiQuotaManager;
import hk.ljx.fishpicsbackend.ai.dto.AiDrawPictureDTO;
import hk.ljx.fishpicsbackend.ai.dto.AiConfigDTO;
import hk.ljx.fishpicsbackend.ai.dto.AiTaskQueryDTO;
import hk.ljx.fishpicsbackend.ai.service.AiService;
import hk.ljx.fishpicsbackend.ai.vo.AiStatsVO;
import hk.ljx.fishpicsbackend.ai.vo.AiTaskVO;
import hk.ljx.fishpicsbackend.common.cache.RedisCacheManager;
import hk.ljx.fishpicsbackend.common.constants.SysConstants;
import hk.ljx.fishpicsbackend.common.enums.PicturePromptEnum;
import hk.ljx.fishpicsbackend.common.enums.PictureSizeEnum;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.infra.DistributedLockService;
import hk.ljx.fishpicsbackend.mapper.PicSystemMapper;
import hk.ljx.fishpicsbackend.system.entity.PicSystem;
import hk.ljx.fishpicsbackend.task.entity.Task;
import hk.ljx.fishpicsbackend.task.service.TaskService;
import hk.ljx.fishpicsbackend.common.utils.LoginContextHelper;
import hk.ljx.fishpicsbackend.picture.entity.Picture;
import hk.ljx.fishpicsbackend.picture.service.PictureService;
import hk.ljx.fishpicsbackend.user.entity.User;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class AiServiceImpl implements AiService {

    private static final String CONFIG_LOCK_KEY = "ai:config:update";

    private static final String FIELD_TAGGING = "taggingEnabled";
    private static final String FIELD_EDITING = "editingEnabled";
    private static final String FIELD_GENERATION = "generationEnabled";
    private static final String FIELD_RECOMMENDATION = "recommendationEnabled";

    private static AiConfigDTO defaultConfig() {
        return AiConfigDTO.withDefaults();
    }

    @Resource
    private TaskService taskService;

    @Resource
    private DashScopeConnectionProperties dashScopeConnectionProperties;

    @Resource
    private PictureService pictureService;

    @Resource
    private AiQuotaManager aiQuotaManager;

    @Resource
    private DistributedLockService distributedLockService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private PicSystemMapper picSystemMapper;

    @Resource
    private RedisCacheManager cacheManager;

    @Override
    public String submitTagTask(Long pictureId) {
        User user = LoginContextHelper.requireUser();

        // 鉴权（图片归属 + 私密图）先于配额检查，避免攻击者用任意 pictureId 消耗自己配额
        Picture picture = pictureService.getById(pictureId);
        ExcUtils.throwIfTrue(picture == null, "图片不存在");
        ExcUtils.throwIfTrue(picture.getUserId() == null, "图片数据异常");
        ExcUtils.throwIfTrue(!user.getId().equals(picture.getUserId()), ExceptionCode.FORBIDDEN, "只能对自己的图片触发 AI 标签识别");

        aiQuotaManager.checkAndConsume("tag", user.getId(), user.getLevel());

        // 防双击/重试导致重复提交任务
        String dedupKey = "AI:SUBMIT:TAG:" + user.getId() + ":" + pictureId;
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
        User user = LoginContextHelper.requireUser();
        ExcUtils.throwIfTrue(!user.getId().equals(task.getUserId()),
                ExceptionCode.FORBIDDEN, "无权查看此任务");
        return task;
    }

    @Override
    public String submitDrawTask(AiDrawPictureDTO drawPictureDTO, Long userId) {
        ExcUtils.throwIfTrue(drawPictureDTO == null || drawPictureDTO.getDescription() == null,
                "画面描述不能为空");
        String description = drawPictureDTO.getDescription().trim();
        drawPictureDTO.setDescription(description);
        normalizeDrawOptions(drawPictureDTO);
        ExcUtils.throwIfTrue(description.length() > 500, ExceptionCode.PARAMETER_ERROR, "画面描述不能超过 500 字符");

        User user = LoginContextHelper.requireUser();
        ExcUtils.throwIfTrue(userId == null || !user.getId().equals(userId),
                ExceptionCode.FORBIDDEN, "用户身份不匹配");
        int level = user.getLevel() != null ? user.getLevel() : 0;

        aiQuotaManager.checkAndConsume("draw", userId, level);

        String dedupInput = description
                + "|" + (drawPictureDTO.getStyle() != null ? drawPictureDTO.getStyle() : "")
                + "|" + (drawPictureDTO.getSize() != null ? drawPictureDTO.getSize() : "")
                + "|" + (drawPictureDTO.getExclusion() != null ? drawPictureDTO.getExclusion() : "");
        String descHash = DigestUtil.sha256Hex(dedupInput);
        String dedupKey = "AI:SUBMIT:DRAW:" + userId + ":" + descHash;
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

    private void normalizeDrawOptions(AiDrawPictureDTO drawPictureDTO) {
        String style = drawPictureDTO.getStyle();
        if (style == null || style.isBlank()) {
            drawPictureDTO.setStyle("photography");
        } else {
            style = style.trim();
            ExcUtils.throwIfTrue(!PicturePromptEnum.isValidCode(style),
                    ExceptionCode.PARAMETER_ERROR, "不支持的绘图风格");
            drawPictureDTO.setStyle(style);
        }

        String size = drawPictureDTO.getSize();
        if (size == null || size.isBlank()) {
            drawPictureDTO.setSize("1:1");
        } else {
            size = size.trim();
            ExcUtils.throwIfTrue(!PictureSizeEnum.isValidCode(size),
                    ExceptionCode.PARAMETER_ERROR, "不支持的图片尺寸");
            drawPictureDTO.setSize(size);
        }

        String exclusion = drawPictureDTO.getExclusion();
        if (exclusion != null) {
            drawPictureDTO.setExclusion(exclusion.trim());
        }
    }

    @Override
    public Task getDrawResult(String taskId) {
        Task task = taskService.getTaskByTaskId(taskId);
        ExcUtils.throwIfTrue(task == null, ExceptionCode.NOT_FOUND, "任务不存在");
        // 加 user 校验，防暴力枚举 UUID 偷他人结果
        User user = LoginContextHelper.requireUser();
        ExcUtils.throwIfTrue(!user.getId().equals(task.getUserId()),
                ExceptionCode.FORBIDDEN, "无权查看此任务");
        return task;
    }

    @Override
    public String getDownloadImageUrl(String taskId) {
        Task task = taskService.getTaskByTaskId(taskId);
        ExcUtils.throwIfTrue(task == null, ExceptionCode.NOT_FOUND, "任务不存在");

        User user = LoginContextHelper.requireUser();
        ExcUtils.throwIfTrue(!user.getId().equals(task.getUserId()), ExceptionCode.FORBIDDEN, "无权下载此任务结果");
        ExcUtils.throwIfTrue(!"ai_draw".equals(task.getBizType()), ExceptionCode.PARAMETER_ERROR, "任务类型不支持下载");
        ExcUtils.throwIfTrue(!"DONE".equals(task.getStatus()), ExceptionCode.PARAMETER_ERROR, "任务尚未完成");
        ExcUtils.throwIfTrue(task.getResult() == null || task.getResult().isBlank(), ExceptionCode.NOT_FOUND, "图片结果不存在");
        return task.getResult();
    }

    // 无配置/null = 全开；editingEnabled 默认 false（还没实现对应端点）
    // 缓存复用 sysConfigCache
    @Override
    public boolean isFeatureEnabled(String fieldName) {
        try {
            AiConfigDTO config = loadAiConfig();
            if (config == null) {
                // 无配置:tagging/generation/recommendation 默认开,editing 默认关
                return !FIELD_EDITING.equals(fieldName);
            }
            switch (fieldName) {
                case FIELD_TAGGING: return config.getTaggingEnabled() == null || config.getTaggingEnabled();
                case FIELD_EDITING: return config.getEditingEnabled() != null && config.getEditingEnabled();
                case FIELD_GENERATION: return config.getGenerationEnabled() == null || config.getGenerationEnabled();
                case FIELD_RECOMMENDATION: return config.getRecommendationEnabled() == null || config.getRecommendationEnabled();
                default: return true;
            }
        } catch (Exception e) {
            log.warn("[AI] isFeatureEnabled check failed for {}, defaulting to false: {}", fieldName, e.getMessage());
            return false;
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
            vo.setPictureId(parsePictureId(task.getBizId()));
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
        // 单条 SQL 聚合查询，避免 5 次独立 count
        List<Map<String, Object>> rows = taskService.getBaseMapper().selectMaps(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Task>()
                        .select("status",
                                "COUNT(*) AS cnt",
                                "SUM(CASE WHEN biz_type='ai_tag' THEN 1 ELSE 0 END) AS tag_cnt",
                                "SUM(CASE WHEN biz_type='ai_draw' THEN 1 ELSE 0 END) AS draw_cnt")
                        .groupBy("status"));

        long total = 0, success = 0, failed = 0, processing = 0, tagCount = 0, drawCount = 0;
        for (Map<String, Object> row : rows) {
            long cnt = ((Number) row.get("cnt")).longValue();
            total += cnt;
            String status = (String) row.get("status");
            if ("DONE".equals(status)) success = cnt;
            else if ("FAILED".equals(status)) failed = cnt;
            else processing += cnt;
            tagCount += ((Number) row.get("tag_cnt")).longValue();
            drawCount += ((Number) row.get("draw_cnt")).longValue();
        }

        AiStatsVO stats = new AiStatsVO();
        stats.setTotalTasks(total);
        stats.setSuccessTasks(success);
        stats.setFailedTasks(failed);
        stats.setProcessingTasks(processing);
        Map<String, Long> typeCounts = new HashMap<>();
        typeCounts.put("0", tagCount);
        typeCounts.put("2", drawCount);
        stats.setTypeCounts(typeCounts);
        return stats;
    }

    @Override
    public AiConfigDTO getAdminConfig() {
        AiConfigDTO config = loadAiConfig();
        return config != null ? config : defaultConfig();
    }

    private AiConfigDTO loadAiConfig() {
        return aiQuotaManager.loadRawConfig();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateAdminConfig(AiConfigDTO configDTO) {
        boolean locked = distributedLockService.tryLock(CONFIG_LOCK_KEY, 10);
        ExcUtils.throwIfTrue(!locked, ExceptionCode.CONFLICT, "配置正在更新，请稍后重试");
        try {
            List<PicSystem> records = picSystemMapper.selectList(
                    new LambdaQueryWrapper<PicSystem>().eq(PicSystem::getSyskey, SysConstants.AI_CONFIG_KEY));

            AiConfigDTO config;
            PicSystem target;
            if (records == null || records.isEmpty() || records.get(0).getSysvalue() == null) {
                config = defaultConfig();
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
            distributedLockService.unlock(CONFIG_LOCK_KEY);
        }
    }

    private AiConfigDTO applyDtoToConfig(AiConfigDTO config, AiConfigDTO configDTO) {
        if (configDTO.getTaggingEnabled() != null) config.setTaggingEnabled(configDTO.getTaggingEnabled());
        if (configDTO.getEditingEnabled() != null) config.setEditingEnabled(configDTO.getEditingEnabled());
        if (configDTO.getGenerationEnabled() != null) config.setGenerationEnabled(configDTO.getGenerationEnabled());
        if (configDTO.getRecommendationEnabled() != null) config.setRecommendationEnabled(configDTO.getRecommendationEnabled());
        if (configDTO.getVipTagQuota() != null) config.setVipTagQuota(configDTO.getVipTagQuota());
        if (configDTO.getVipDrawQuota() != null) config.setVipDrawQuota(configDTO.getVipDrawQuota());
        if (configDTO.getSvipTagQuota() != null) config.setSvipTagQuota(configDTO.getSvipTagQuota());
        if (configDTO.getSvipDrawQuota() != null) config.setSvipDrawQuota(configDTO.getSvipDrawQuota());
        return config;
    }

    private Long parsePictureId(String bizId) {
        if (bizId == null || bizId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(bizId);
        } catch (NumberFormatException e) {
            log.warn("invalid AI task bizId: {}", bizId);
            return null;
        }
    }
}
