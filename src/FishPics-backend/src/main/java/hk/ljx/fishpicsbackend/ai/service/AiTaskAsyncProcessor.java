package hk.ljx.fishpicsbackend.ai.service;

import cn.hutool.json.JSONUtil;
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
import hk.ljx.fishpicsbackend.entity.AiTask;
import hk.ljx.fishpicsbackend.entity.Picture;
import hk.ljx.fishpicsbackend.mapper.PictureMapper;
import jakarta.annotation.Resource;
import java.util.Date;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AiTaskAsyncProcessor {

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

    @Async
    public void processTagging(Long taskId, Picture picture) {
        AiTask task = aiTaskMapper.selectById(taskId);
        if (task == null) return;
        try {
            TaggingResult result = imageTaggingService.analyzeImage(picture.getUrl());
            String tags = String.join(",", result.getTags());

            Picture update = new Picture();
            update.setId(picture.getId());
            update.setTags(tags);
            update.setIntroduction(result.getDescription());
            pictureMapper.updateById(update);

            task.setStatus(1);
            task.setOutputData(JSONUtil.toJsonStr(result));
            log.info("AI tagging completed for picture {}: tags={}", picture.getId(), tags);
        } catch (Exception e) {
            task.setStatus(2);
            task.setErrorMsg(e.getMessage());
            log.error("AI tagging failed for picture {}", picture.getId(), e);
        }
        task.setUpdateTime(new Date());
        aiTaskMapper.updateById(task);
    }

    @Async
    public void processGeneration(Long taskId, GenerationRequest request) {
        AiTask task = aiTaskMapper.selectById(taskId);
        if (task == null) return;
        try {
            GenerationResult result = imageGenerationService.generateImage(request);
            task.setStatus(1);
            task.setOutputData(JSONUtil.toJsonStr(result));
            log.info("AI generation completed for task {}: {} images", taskId,
                result.getResultUrls() != null ? result.getResultUrls().size() : 0);
        } catch (Exception e) {
            task.setStatus(2);
            task.setErrorMsg(e.getMessage());
            log.error("AI generation failed for task {}", taskId, e);
        }
        task.setUpdateTime(new Date());
        aiTaskMapper.updateById(task);
    }

    @Async
    public void processEditing(Long taskId, EditingRequest request) {
        AiTask task = aiTaskMapper.selectById(taskId);
        if (task == null) return;
        try {
            EditingResult result = imageEditingService.editImage(request);
            task.setStatus(1);
            task.setOutputData(JSONUtil.toJsonStr(result));
            log.info("AI editing completed for task {}: {} results", taskId,
                result.getResultUrls() != null ? result.getResultUrls().size() : 0);
        } catch (Exception e) {
            task.setStatus(2);
            task.setErrorMsg(e.getMessage());
            log.error("AI editing failed for task {}", taskId, e);
        }
        task.setUpdateTime(new Date());
        aiTaskMapper.updateById(task);
    }

    @Async
    public void processRecommendation(Long taskId, RecommendationRequest request) {
        AiTask task = aiTaskMapper.selectById(taskId);
        if (task == null) return;
        try {
            RecommendationResult result = imageRecommendationService.recommend(request);
            task.setStatus(1);
            task.setOutputData(JSONUtil.toJsonStr(result));
            log.info("AI recommendation completed for task {}: {} recommendations", taskId,
                result.getPictureIds() != null ? result.getPictureIds().size() : 0);
        } catch (Exception e) {
            task.setStatus(2);
            task.setErrorMsg(e.getMessage());
            log.error("AI recommendation failed for task {}", taskId, e);
        }
        task.setUpdateTime(new Date());
        aiTaskMapper.updateById(task);
    }
}
