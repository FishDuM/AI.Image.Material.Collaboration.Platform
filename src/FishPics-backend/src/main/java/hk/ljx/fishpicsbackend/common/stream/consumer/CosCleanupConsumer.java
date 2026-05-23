package hk.ljx.fishpicsbackend.common.stream.consumer;

import hk.ljx.fishpicsbackend.common.stream.AbstractStreamConsumer;
import hk.ljx.fishpicsbackend.common.stream.StreamEvent;
import hk.ljx.fishpicsbackend.common.utils.CosService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

import static hk.ljx.fishpicsbackend.common.stream.StreamConstants.*;

@Component
@Slf4j
public class CosCleanupConsumer extends AbstractStreamConsumer {

    @Resource
    private CosService cosService;

    @Override
    protected String getStreamKey() {
        return STREAM_COS_CLEANUP;
    }

    @Override
    protected String getGroupName() {
        return GROUP_COS_CLEANUP;
    }

    @Override
    protected String getConsumerName() {
        return CONSUMER_COS;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void processEvent(StreamEvent event) {
        List<String> keys = (List<String>) event.getPayload().get("keys");
        if (keys == null || keys.isEmpty()) return;

        for (String key : keys) {
            try {
                cosService.deletePictureByUrl(key);
                log.info("COS object deleted: {}", key);
            } catch (Exception e) {
                log.error("COS deletion failed for key {}: {}", key, e.getMessage());
                throw e;
            }
        }
    }
}
