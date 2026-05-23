package hk.ljx.fishpicsbackend.common.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamMessageId;
import org.redisson.api.stream.StreamAddArgs;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static hk.ljx.fishpicsbackend.common.stream.StreamConstants.*;

@Component
@Slf4j
public class StreamProducer {

    @Resource
    private RedissonClient redissonClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void send(String streamKey, StreamEvent event) {
        try {
            RStream<String, String> stream = redissonClient.getStream(streamKey);
            String json = objectMapper.writeValueAsString(event);
            StreamMessageId id = stream.add(StreamAddArgs.entry(event.getEventId(), json));
            log.debug("Stream event sent: stream={}, id={}, type={}", streamKey, id, event.getEventType());
        } catch (Exception e) {
            log.error("Failed to send stream event: stream={}, type={}, error={}",
                    streamKey, event.getEventType(), e.getMessage());
            throw new RuntimeException("Stream send failed", e);
        }
    }

    public void sendAiTaggingEvent(Long pictureId, String pictureUrl, Long userId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("pictureId", pictureId);
        payload.put("pictureUrl", pictureUrl);
        payload.put("userId", userId);
        send(STREAM_AI_TAGGING, StreamEvent.of(EVENT_AI_TAGGING, payload));
    }

    public void sendCosCleanupEvent(List<String> keys) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("keys", keys);
        send(STREAM_COS_CLEANUP, StreamEvent.of(EVENT_COS_CLEANUP, payload));
    }

    public void sendSocialEvent(String eventType, Long actorUserId, Long targetUserId,
                                Long postId, String action) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("actorUserId", actorUserId);
        payload.put("targetUserId", targetUserId);
        payload.put("postId", postId);
        payload.put("action", action);
        send(STREAM_SOCIAL, StreamEvent.of(eventType, payload));
    }

    public void sendCommentEvent(Long actorUserId, Long postId, Long commentId,
                                 Long parentId, Long toUserId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("actorUserId", actorUserId);
        payload.put("postId", postId);
        payload.put("commentId", commentId);
        payload.put("parentId", parentId);
        payload.put("toUserId", toUserId);
        payload.put("action", "CREATE");
        send(STREAM_SOCIAL, StreamEvent.of(EVENT_SOCIAL_COMMENT, payload));
    }

    public void sendAiTaskEvent(String eventType, Long taskId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("taskId", taskId);
        send(STREAM_AI_TASK, StreamEvent.of(eventType, payload));
    }
}
