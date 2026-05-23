package hk.ljx.fishpicsbackend.common.stream.consumer;

import cn.hutool.json.JSONUtil;
import hk.ljx.fishpicsbackend.ai.dto.TaggingResult;
import hk.ljx.fishpicsbackend.ai.interfaces.ImageTaggingService;
import hk.ljx.fishpicsbackend.ai.mapper.AiTaskMapper;
import hk.ljx.fishpicsbackend.common.stream.AbstractStreamConsumer;
import hk.ljx.fishpicsbackend.common.stream.StreamEvent;
import hk.ljx.fishpicsbackend.entity.AiTask;
import hk.ljx.fishpicsbackend.entity.Picture;
import hk.ljx.fishpicsbackend.mapper.PictureMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Date;

import static hk.ljx.fishpicsbackend.common.stream.StreamConstants.*;

@Component
@Slf4j
public class AiTaggingConsumer extends AbstractStreamConsumer {

    @Resource
    private AiTaskMapper aiTaskMapper;

    @Resource
    private PictureMapper pictureMapper;

    @Resource
    private ImageTaggingService imageTaggingService;

    @Override
    protected String getStreamKey() {
        return STREAM_AI_TAGGING;
    }

    @Override
    protected String getGroupName() {
        return GROUP_AI_TAGGING;
    }

    @Override
    protected String getConsumerName() {
        return CONSUMER_AI_TAGGING;
    }

    @Override
    protected void processEvent(StreamEvent event) throws Exception {
        Long pictureId = ((Number) event.getPayload().get("pictureId")).longValue();
        String pictureUrl = (String) event.getPayload().get("pictureUrl");
        Long userId = ((Number) event.getPayload().get("userId")).longValue();

        // If taskId is provided, reuse existing task (manual trigger); otherwise create new
        Object taskIdObj = event.getPayload().get("taskId");
        AiTask task;
        if (taskIdObj != null) {
            task = aiTaskMapper.selectById(((Number) taskIdObj).longValue());
            if (task == null) {
                log.warn("AiTaggingConsumer: task {} not found, creating new", taskIdObj);
                task = createTask(userId, pictureId);
            }
        } else {
            task = createTask(userId, pictureId);
        }

        TaggingResult result = imageTaggingService.analyzeImage(pictureUrl);
        String tags = String.join(",", result.getTags());

        Picture update = new Picture();
        update.setId(pictureId);
        update.setTags(tags);
        update.setIntroduction(result.getDescription());
        pictureMapper.updateById(update);

        task.setStatus(1);
        task.setOutputData(JSONUtil.toJsonStr(result));
        task.setUpdateTime(new Date());
        aiTaskMapper.updateById(task);

        log.info("AI tagging completed for picture {}: tags={}", pictureId, tags);
    }

    private AiTask createTask(Long userId, Long pictureId) {
        AiTask task = new AiTask();
        task.setUserId(userId);
        task.setType(0);
        task.setPictureId(pictureId);
        task.setStatus(0);
        task.setCreateTime(new Date());
        task.setUpdateTime(new Date());
        aiTaskMapper.insert(task);
        return task;
    }
}
