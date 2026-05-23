package hk.ljx.fishpicsbackend.ai.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import hk.ljx.fishpicsbackend.ai.dto.AiConfigUpdateRequest;
import hk.ljx.fishpicsbackend.ai.dto.EditingRequest;
import hk.ljx.fishpicsbackend.ai.dto.EditingResult;
import hk.ljx.fishpicsbackend.ai.dto.GenerationRequest;
import hk.ljx.fishpicsbackend.ai.dto.GenerationResult;
import hk.ljx.fishpicsbackend.ai.dto.RecommendationRequest;
import hk.ljx.fishpicsbackend.ai.dto.RecommendationResult;
import hk.ljx.fishpicsbackend.ai.dto.TaggingResult;
import hk.ljx.fishpicsbackend.ai.interfaces.ImageEditingService;
import hk.ljx.fishpicsbackend.ai.interfaces.ImageGenerationService;
import hk.ljx.fishpicsbackend.ai.interfaces.ImageRecommendationService;
import hk.ljx.fishpicsbackend.ai.interfaces.ImageTaggingService;
import hk.ljx.fishpicsbackend.ai.mapper.AiTaskMapper;
import hk.ljx.fishpicsbackend.ai.service.AiTaskAsyncProcessor;
import hk.ljx.fishpicsbackend.ai.service.AiTaskService;
import hk.ljx.fishpicsbackend.common.stream.StreamProducer;
import hk.ljx.fishpicsbackend.entity.AiTask;
import hk.ljx.fishpicsbackend.entity.PicSystem;
import hk.ljx.fishpicsbackend.entity.Picture;
import hk.ljx.fishpicsbackend.mapper.PictureMapper;
import hk.ljx.fishpicsbackend.service.PicSystemService;
import hk.ljx.fishpicsbackend.vo.ai.AiConfigVO;
import hk.ljx.fishpicsbackend.vo.ai.AiStatsVO;
import hk.ljx.fishpicsbackend.vo.ai.AiTaskVO;
import jakarta.annotation.Resource;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static hk.ljx.fishpicsbackend.common.stream.StreamConstants.*;

@Slf4j
@Service
public class AiTaskServiceImpl extends ServiceImpl<AiTaskMapper, AiTask> implements AiTaskService {

    @Resource
    private AiTaskMapper aiTaskMapper;

    @Resource
    private PictureMapper pictureMapper;

    @Resource
    private ImageTaggingService imageTaggingService;

    @Resource
    private ImageEditingService imageEditingService;

    @Resource
    private ImageGenerationService imageGenerationService;

    @Resource
    private ImageRecommendationService imageRecommendationService;

    @Resource
    private PicSystemService picSystemService;

    @Resource
    private StreamProducer streamProducer;

    @Resource
    private AiTaskAsyncProcessor asyncProcessor;

    @Override
    public Long submitGeneration(GenerationRequest request, Long userId) {
        AiTask task = createTask(userId, 2, JSONUtil.toJsonStr(request), null, null);
        try {
            streamProducer.sendAiTaskEvent(EVENT_AI_GENERATION, task.getId());
        } catch (Exception e) {
            log.error("Failed to send AI generation event, falling back to @Async", e);
            asyncProcessor.processGeneration(task.getId(), request);
        }
        return task.getId();
    }

    @Override
    public Long submitEditing(EditingRequest request, Long userId) {
        AiTask task = createTask(userId, 1, JSONUtil.toJsonStr(request), request.getEditType(), null);
        try {
            streamProducer.sendAiTaskEvent(EVENT_AI_EDITING, task.getId());
        } catch (Exception e) {
            log.error("Failed to send AI editing event, falling back to @Async", e);
            asyncProcessor.processEditing(task.getId(), request);
        }
        return task.getId();
    }

    @Override
    public Long submitManualTagging(Long pictureId, Long userId) {
        Picture picture = pictureMapper.selectById(pictureId);
        if (picture == null) return 0L;
        AiTask task = createTask(userId, 0, JSONUtil.toJsonStr(picture.getUrl()), null, pictureId);
        try {
            streamProducer.sendAiTaggingEvent(pictureId, picture.getUrl(), userId);
        } catch (Exception e) {
            log.error("Failed to send AI tagging event, falling back to @Async", e);
            asyncProcessor.processTagging(task.getId(), picture);
        }
        return task.getId();
    }

    @Override
    public Long submitRecommendation(RecommendationRequest request, Long userId) {
        AiTask task = createTask(userId, 3, JSONUtil.toJsonStr(request), null, null);
        try {
            streamProducer.sendAiTaskEvent(EVENT_AI_RECOMMENDATION, task.getId());
        } catch (Exception e) {
            log.error("Failed to send AI recommendation event, falling back to @Async", e);
            asyncProcessor.processRecommendation(task.getId(), request);
        }
        return task.getId();
    }

    private AiTask createTask(Long userId, int type, String inputData, String subType, Long pictureId) {
        AiTask task = new AiTask();
        task.setUserId(userId);
        task.setType(type);
        task.setSubType(subType);
        task.setInputData(inputData);
        task.setPictureId(pictureId);
        task.setStatus(0);
        task.setCreateTime(new Date());
        task.setUpdateTime(new Date());
        aiTaskMapper.insert(task);
        return task;
    }

    @Override
    @Async
    @Transactional(rollbackFor = Exception.class)
    public void triggerTaggingAsync(Picture picture) {
        AiTask task = new AiTask();
        task.setUserId(picture.getUserId());
        task.setType(0); // 自动标注
        task.setPictureId(picture.getId());
        task.setStatus(0); // 处理中
        task.setCreateTime(new Date());
        task.setUpdateTime(new Date());
        aiTaskMapper.insert(task);

        try {
            TaggingResult result = imageTaggingService.analyzeImage(picture.getUrl());
            String tags = String.join(",", result.getTags());

            Picture update = new Picture();
            update.setId(picture.getId());
            update.setTags(tags);
            update.setIntroduction(result.getDescription());
            pictureMapper.updateById(update);

            task.setStatus(1); // 成功
            task.setOutputData(JSONUtil.toJsonStr(result));
            log.info("AI auto-tagging completed for picture {}: tags={}", picture.getId(), tags);
        } catch (Exception e) {
            task.setStatus(2); // 失败
            task.setErrorMsg(e.getMessage());
            log.error("AI auto-tagging failed for picture {}", picture.getId(), e);
        }

        task.setUpdateTime(new Date());
        aiTaskMapper.updateById(task);
    }

    @Override
    public AiTaskVO getTaskVOById(Long id) {
        AiTask task = aiTaskMapper.selectById(id);
        if (task == null) return null;
        return toVO(task);
    }

    @Override
    public IPage<AiTaskVO> getMyTasks(long current, long pageSize, Long userId) {
        QueryWrapper<AiTask> wrapper = new QueryWrapper<AiTask>()
            .eq("user_id", userId)
            .orderByDesc("create_time");
        Page<AiTask> page = new Page<>(current, pageSize);
        IPage<AiTask> taskPage = aiTaskMapper.selectPage(page, wrapper);
        return taskPage.convert(this::toVO);
    }

    @Override
    public IPage<AiTaskVO> getAllTasks(long current, long pageSize, Integer type, Integer status, Long userId) {
        QueryWrapper<AiTask> wrapper = new QueryWrapper<AiTask>()
            .orderByDesc("create_time");
        if (type != null) wrapper.eq("type", type);
        if (status != null) wrapper.eq("status", status);
        if (userId != null) wrapper.eq("user_id", userId);
        Page<AiTask> page = new Page<>(current, pageSize);
        IPage<AiTask> taskPage = aiTaskMapper.selectPage(page, wrapper);
        return taskPage.convert(this::toVO);
    }

    @Override
    public AiStatsVO getStats() {
        List<AiTask> all = aiTaskMapper.selectList(null);
        long total = all.size();
        long success = all.stream().filter(t -> t.getStatus() == 1).count();
        long failed = all.stream().filter(t -> t.getStatus() == 2).count();
        long processing = all.stream().filter(t -> t.getStatus() == 0).count();

        Map<Integer, Long> typeCounts = all.stream()
            .collect(Collectors.groupingBy(AiTask::getType, Collectors.counting()));

        return AiStatsVO.builder()
            .totalTasks(total)
            .successTasks(success)
            .failedTasks(failed)
            .processingTasks(processing)
            .typeCounts(typeCounts)
            .build();
    }

    @Override
    public AiConfigVO getConfig() {
        List<PicSystem> configs = picSystemService.list(
            new QueryWrapper<PicSystem>().like("syskey", "ai."));
        Map<String, String> map = new HashMap<>();
        configs.forEach(c -> map.put(c.getSyskey(), c.getSysvalue()));

        return AiConfigVO.builder()
            .taggingEnabled(!"false".equals(map.getOrDefault("ai.tagging.enabled", "true")))
            .editingEnabled(!"false".equals(map.getOrDefault("ai.editing.enabled", "true")))
            .generationEnabled(!"false".equals(map.getOrDefault("ai.generation.enabled", "true")))
            .recommendationEnabled(!"false".equals(map.getOrDefault("ai.recommendation.enabled", "true")))
            .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateConfig(AiConfigUpdateRequest request) {
        if (request.getTaggingEnabled() != null) {
            saveOrUpdateConfig("ai.tagging.enabled", request.getTaggingEnabled().toString());
        }
        if (request.getEditingEnabled() != null) {
            saveOrUpdateConfig("ai.editing.enabled", request.getEditingEnabled().toString());
        }
        if (request.getGenerationEnabled() != null) {
            saveOrUpdateConfig("ai.generation.enabled", request.getGenerationEnabled().toString());
        }
        if (request.getRecommendationEnabled() != null) {
            saveOrUpdateConfig("ai.recommendation.enabled", request.getRecommendationEnabled().toString());
        }
    }

    private void saveOrUpdateConfig(String key, String value) {
        PicSystem existing = picSystemService.getOne(
            new QueryWrapper<PicSystem>().eq("syskey", key));
        if (existing != null) {
            existing.setSysvalue(value);
            picSystemService.updateById(existing);
        } else {
            PicSystem config = new PicSystem();
            config.setSyskey(key);
            config.setSysvalue(value);
            picSystemService.save(config);
        }
    }

    private AiTaskVO toVO(AiTask task) {
        AiTaskVO vo = new AiTaskVO();
        BeanUtil.copyProperties(task, vo);
        if (StrUtil.isNotBlank(task.getOutputData())) {
            try {
                switch (task.getType()) {
                    case 0:
                        vo.setOutput(JSONUtil.toBean(task.getOutputData(), TaggingResult.class));
                        break;
                    case 1:
                        vo.setOutput(JSONUtil.toBean(task.getOutputData(), EditingResult.class));
                        break;
                    case 2:
                        vo.setOutput(JSONUtil.toBean(task.getOutputData(), GenerationResult.class));
                        break;
                    case 3:
                        vo.setOutput(JSONUtil.toBean(task.getOutputData(), RecommendationResult.class));
                        break;
                }
            } catch (Exception e) {
                vo.setOutput(task.getOutputData());
            }
        }
        return vo;
    }
}
