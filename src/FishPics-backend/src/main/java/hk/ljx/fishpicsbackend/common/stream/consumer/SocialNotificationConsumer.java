package hk.ljx.fishpicsbackend.common.stream.consumer;

import hk.ljx.fishpicsbackend.common.stream.AbstractStreamConsumer;
import hk.ljx.fishpicsbackend.common.stream.StreamEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static hk.ljx.fishpicsbackend.common.stream.StreamConstants.*;

@Component
@Slf4j
public class SocialNotificationConsumer extends AbstractStreamConsumer {

    @Override
    protected String getStreamKey() {
        return STREAM_SOCIAL;
    }

    @Override
    protected String getGroupName() {
        return GROUP_SOCIAL;
    }

    @Override
    protected String getConsumerName() {
        return CONSUMER_SOCIAL;
    }

    @Override
    protected void processEvent(StreamEvent event) {
        Long actorUserId = event.getPayload().get("actorUserId") != null
                ? ((Number) event.getPayload().get("actorUserId")).longValue() : null;
        Long targetUserId = event.getPayload().get("targetUserId") != null
                ? ((Number) event.getPayload().get("targetUserId")).longValue() : null;
        Long postId = event.getPayload().get("postId") != null
                ? ((Number) event.getPayload().get("postId")).longValue() : null;
        String action = (String) event.getPayload().get("action");

        log.info("Social event: type={}, actor={}, target={}, post={}, action={}",
                event.getEventType(), actorUserId, targetUserId, postId, action);
    }
}
