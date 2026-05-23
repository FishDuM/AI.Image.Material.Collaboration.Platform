package hk.ljx.fishpicsbackend.common.stream.consumer;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import hk.ljx.fishpicsbackend.ai.dto.EditingRequest;
import hk.ljx.fishpicsbackend.ai.dto.EditingResult;
import hk.ljx.fishpicsbackend.ai.dto.GenerationRequest;
import hk.ljx.fishpicsbackend.ai.dto.GenerationResult;
import hk.ljx.fishpicsbackend.ai.dto.RecommendationRequest;
import hk.ljx.fishpicsbackend.ai.dto.RecommendationResult;
import hk.ljx.fishpicsbackend.ai.interfaces.ImageEditingService;
import hk.ljx.fishpicsbackend.ai.interfaces.ImageGenerationService;
import hk.ljx.fishpicsbackend.ai.interfaces.ImageRecommendationService;
import hk.ljx.fishpicsbackend.ai.mapper.AiTaskMapper;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.stream.AbstractStreamConsumer;
import hk.ljx.fishpicsbackend.common.stream.StreamEvent;
import hk.ljx.fishpicsbackend.common.utils.CosService;
import hk.ljx.fishpicsbackend.dto.picture.PictureMessage;
import hk.ljx.fishpicsbackend.entity.AiTask;
import hk.ljx.fishpicsbackend.entity.Picture;
import hk.ljx.fishpicsbackend.entity.Space;
import hk.ljx.fishpicsbackend.entity.User;
import hk.ljx.fishpicsbackend.mapper.PictureMapper;
import hk.ljx.fishpicsbackend.mapper.SpaceMapper;
import hk.ljx.fishpicsbackend.mapper.UserMapper;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static hk.ljx.fishpicsbackend.common.constants.UserConstants.ADMIN;
import static hk.ljx.fishpicsbackend.common.stream.StreamConstants.*;

@Component
@Slf4j
public class AiTaskConsumer extends AbstractStreamConsumer {

    @Resource
    private AiTaskMapper aiTaskMapper;

    @Resource
    private ImageGenerationService imageGenerationService;

    @Resource
    private ImageEditingService imageEditingService;

    @Resource
    private ImageRecommendationService imageRecommendationService;

    @Resource
    private CosService cosService;

    @Resource
    private SpaceMapper spaceMapper;

    @Resource
    private PictureMapper pictureMapper;

    @Resource
    private UserMapper userMapper;

    @Override
    protected String getStreamKey() {
        return STREAM_AI_TASK;
    }

    @Override
    protected String getGroupName() {
        return GROUP_AI_TASK;
    }

    @Override
    protected String getConsumerName() {
        return CONSUMER_AI_TASK;
    }

    @Override
    protected void processEvent(StreamEvent event) throws Exception {
        Long taskId = ((Number) event.getPayload().get("taskId")).longValue();
        AiTask task = aiTaskMapper.selectById(taskId);
        if (task == null) {
            log.warn("AiTaskConsumer: task {} not found, skipping", taskId);
            return;
        }

        switch (event.getEventType()) {
            case EVENT_AI_GENERATION -> processGeneration(task);
            case EVENT_AI_EDITING -> processEditing(task);
            case EVENT_AI_RECOMMENDATION -> processRecommendation(task);
            default -> log.warn("AiTaskConsumer: unknown event type {}", event.getEventType());
        }
    }

    private void processGeneration(AiTask task) {
        GenerationRequest request = JSONUtil.toBean(task.getInputData(), GenerationRequest.class);
        GenerationResult result = imageGenerationService.generateImage(request);

        List<String> permanentUrls = saveImagesToSpace(result.getResultUrls(), task.getUserId(), task.getId());
        result.setResultUrls(permanentUrls);

        task.setStatus(1);
        task.setOutputData(JSONUtil.toJsonStr(result));
        task.setUpdateTime(new Date());
        aiTaskMapper.updateById(task);
        log.info("AI generation completed for task {}: {} images saved", task.getId(), permanentUrls.size());
    }

    private void processEditing(AiTask task) {
        EditingRequest request = JSONUtil.toBean(task.getInputData(), EditingRequest.class);
        EditingResult result = imageEditingService.editImage(request);

        List<String> permanentUrls = saveImagesToSpace(result.getResultUrls(), task.getUserId(), task.getId());
        result.setResultUrls(permanentUrls);

        task.setStatus(1);
        task.setOutputData(JSONUtil.toJsonStr(result));
        task.setUpdateTime(new Date());
        aiTaskMapper.updateById(task);
        log.info("AI editing completed for task {}: {} images saved", task.getId(), permanentUrls.size());
    }

    private void processRecommendation(AiTask task) {
        RecommendationRequest request = JSONUtil.toBean(task.getInputData(), RecommendationRequest.class);
        RecommendationResult result = imageRecommendationService.recommend(request);
        task.setStatus(1);
        task.setOutputData(JSONUtil.toJsonStr(result));
        task.setUpdateTime(new Date());
        aiTaskMapper.updateById(task);
        log.info("AI recommendation completed for task {}: {} recommendations", task.getId(),
            result.getPictureIds() != null ? result.getPictureIds().size() : 0);
    }

    /**
     * Download images from temporary AI service URLs, upload to COS,
     * and save as Picture records in the user's personal space.
     */
    private List<String> saveImagesToSpace(List<String> tempUrls, Long userId, Long taskId) {
        if (tempUrls == null || tempUrls.isEmpty()) {
            return tempUrls;
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            log.warn("saveImagesToSpace: user {} not found for task {}", userId, taskId);
            return tempUrls;
        }

        Space space = spaceMapper.selectOne(
            new QueryWrapper<Space>().eq("user_id", userId).eq("type", 0));
        if (space == null) {
            log.warn("saveImagesToSpace: no personal space for user {}, task {}", userId, taskId);
            return tempUrls;
        }

        List<String> permanentUrls = new ArrayList<>();
        for (String tempUrl : tempUrls) {
            try {
                String permanentUrl = saveSingleImage(tempUrl, user, space);
                permanentUrls.add(permanentUrl);
            } catch (Exception e) {
                log.error("Failed to save image {} for user {}: {}", tempUrl, userId, e.getMessage());
                permanentUrls.add(tempUrl);
            }
        }
        return permanentUrls;
    }

    private String saveSingleImage(String tempUrl, User user, Space space) {
        String suffix = ".png";
        String lowerUrl = tempUrl.toLowerCase();
        if (lowerUrl.contains(".jpg") || lowerUrl.contains(".jpeg")) {
            suffix = ".jpg";
        } else if (lowerUrl.contains(".webp")) {
            suffix = ".webp";
        }

        byte[] imageBytes = HttpUtil.downloadBytes(tempUrl);

        String cosKey = cosService.uploadBytes(imageBytes, suffix);

        PictureMessage pictureMessage = cosService.getPictureMessage(cosKey);

        Picture picture = new Picture();
        BeanUtil.copyProperties(pictureMessage, picture);
        picture.setUserId(user.getId());
        if (picture.getSize() == null && pictureMessage.getSize() != null) {
            picture.setSize(Long.parseLong(pictureMessage.getSize()));
        }

        long usedSize = space.getSize() != null ? space.getSize() : 0L;
        long storageSize = space.getStorageSize() != null ? space.getStorageSize() : 0L;
        long updateSize = usedSize + picture.getSize();
        if (updateSize > storageSize) {
            cosService.deletePicture(cosKey);
            throw new BaseException(ExceptionCode.UNAUTHORIZED, "空间磁盘不足");
        }

        spaceMapper.update(null,
            new UpdateWrapper<Space>().set("size", updateSize).eq("id", space.getId()));
        space.setSize(updateSize);

        picture.setSpaceId(space.getId());
        picture.setStatus(ADMIN.equals(user.getRole()) ? 1 : 2);
        picture.setCreateTime(new Date());
        picture.setUpdateTime(new Date());
        pictureMapper.insert(picture);

        log.info("Saved AI image to personal space: pictureId={}, userId={}", picture.getId(), user.getId());
        return picture.getUrl();
    }
}
