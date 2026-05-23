package hk.ljx.fishpicsbackend.common.stream;

import hk.ljx.fishpicsbackend.common.stream.consumer.AiTaggingConsumer;
import hk.ljx.fishpicsbackend.common.stream.consumer.AiTaskConsumer;
import hk.ljx.fishpicsbackend.common.stream.consumer.CosCleanupConsumer;
import hk.ljx.fishpicsbackend.common.stream.consumer.SocialNotificationConsumer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.stream.StreamCreateGroupArgs;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

import static hk.ljx.fishpicsbackend.common.stream.StreamConstants.*;

@Configuration
@Slf4j
public class StreamConfig {

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private AiTaggingConsumer aiTaggingConsumer;

    @Resource
    private AiTaskConsumer aiTaskConsumer;

    @Resource
    private SocialNotificationConsumer socialConsumer;

    @Resource
    private CosCleanupConsumer cosCleanupConsumer;

    private final List<Thread> consumerThreads = new ArrayList<>();

    @PostConstruct
    public void init() {
        ensureGroup(STREAM_AI_TAGGING, GROUP_AI_TAGGING);
        ensureGroup(STREAM_AI_TASK, GROUP_AI_TASK);
        ensureGroup(STREAM_SOCIAL, GROUP_SOCIAL);
        ensureGroup(STREAM_COS_CLEANUP, GROUP_COS_CLEANUP);

        consumerThreads.add(new Thread(aiTaggingConsumer, "stream-ai-tagging"));
        consumerThreads.add(new Thread(aiTaskConsumer, "stream-ai-task"));
        consumerThreads.add(new Thread(socialConsumer, "stream-social"));
        consumerThreads.add(new Thread(cosCleanupConsumer, "stream-cos-cleanup"));

        consumerThreads.forEach(t -> {
            t.setDaemon(true);
            t.start();
        });
        log.info("Redis Stream consumers started: {} threads", consumerThreads.size());
    }

    private void ensureGroup(String streamKey, String groupName) {
        RStream<String, String> stream = redissonClient.getStream(streamKey);
        try {
            stream.createGroup(StreamCreateGroupArgs.name(groupName).makeStream());
            log.info("Created consumer group '{}' for stream '{}'", groupName, streamKey);
        } catch (Exception e) {
            log.info("Consumer group '{}' already exists for stream '{}'", groupName, streamKey);
        }
    }

    @PreDestroy
    public void shutdown() {
        aiTaggingConsumer.shutdown();
        aiTaskConsumer.shutdown();
        socialConsumer.shutdown();
        cosCleanupConsumer.shutdown();
        consumerThreads.forEach(t -> {
            try {
                t.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        log.info("Redis Stream consumers shut down");
    }
}
