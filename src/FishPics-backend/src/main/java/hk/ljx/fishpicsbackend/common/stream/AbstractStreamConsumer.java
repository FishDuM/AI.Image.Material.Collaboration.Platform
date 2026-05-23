package hk.ljx.fishpicsbackend.common.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamMessageId;
import org.redisson.api.stream.StreamReadGroupArgs;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static hk.ljx.fishpicsbackend.common.stream.StreamConstants.POLL_COUNT;
import static hk.ljx.fishpicsbackend.common.stream.StreamConstants.POLL_TIMEOUT_MS;
import static hk.ljx.fishpicsbackend.common.stream.StreamConstants.MAX_RETRIES;

@Slf4j
public abstract class AbstractStreamConsumer implements Runnable {

    @Resource
    protected RedissonClient redissonClient;

    protected final ObjectMapper objectMapper = new ObjectMapper();
    protected volatile boolean running = true;
    private final Map<String, Integer> retryCounts = new ConcurrentHashMap<>();

    protected abstract String getStreamKey();
    protected abstract String getGroupName();
    protected abstract String getConsumerName();
    protected abstract void processEvent(StreamEvent event) throws Exception;

    @Override
    public void run() {
        RStream<String, String> stream = redissonClient.getStream(getStreamKey());
        log.info("Consumer '{}' started for stream '{}'", getConsumerName(), getStreamKey());

        while (running) {
            try {
                Map<StreamMessageId, Map<String, String>> messages = stream.readGroup(
                        getGroupName(), getConsumerName(),
                        StreamReadGroupArgs.greaterThan(StreamMessageId.NEVER_DELIVERED)
                                .count(POLL_COUNT)
                                .timeout(Duration.ofMillis(POLL_TIMEOUT_MS)));

                if (messages != null && !messages.isEmpty()) {
                    for (Map.Entry<StreamMessageId, Map<String, String>> entry : messages.entrySet()) {
                        StreamMessageId msgId = entry.getKey();
                        String json = entry.getValue().values().iterator().next();
                        StreamEvent event = objectMapper.readValue(json, StreamEvent.class);
                        try {
                            processEvent(event);
                            stream.ack(getGroupName(), msgId);
                            retryCounts.remove(event.getEventId());
                            log.debug("Acked message {} from stream '{}'", msgId, getStreamKey());
                        } catch (Exception e) {
                            int attempts = retryCounts.merge(event.getEventId(), 1, Integer::sum);
                            if (attempts >= MAX_RETRIES) {
                                log.error("Event {} reached max retries ({}), giving up: {}",
                                        event.getEventId(), MAX_RETRIES, e.getMessage());
                                stream.ack(getGroupName(), msgId);
                                retryCounts.remove(event.getEventId());
                            } else {
                                log.error("Failed to process event {} (attempt {}), will retry: {}",
                                        event.getEventId(), attempts, e.getMessage());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Consumer '{}' poll error, will retry: {}", getConsumerName(), e.getMessage());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        log.info("Consumer '{}' stopped", getConsumerName());
    }

    public void shutdown() {
        running = false;
    }
}
