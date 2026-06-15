package hk.ljx.fishpicsbackend.collab;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.util.DaemonThreadFactory;
import hk.ljx.fishpicsbackend.collab.model.CollabEvent;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Disruptor 无锁队列配置
 *
 * 架构：WebSocket I/O 线程 → publish() → Ring Buffer → 消费者线程（事件处理 + 广播）
 * 消费者线程与 I/O 线程完全隔离
 *
 * 生产者类型 MULTI：支持多个 WebSocket 线程并发发布
 * 等待策略 BlockingWaitStrategy：CPU 友好
 */
@Configuration
public class DisruptorConfig {

    private static final int RING_BUFFER_SIZE = 1024;

    private Disruptor<CollabEvent> disruptor;

    @Bean
    public RingBuffer<CollabEvent> disruptorRingBuffer(CollabEventHandler eventHandler,
                                                        CollabSessionRegistry sessionRegistry) {
        disruptor = new Disruptor<>(
                CollabEvent.FACTORY,
                RING_BUFFER_SIZE,
                DaemonThreadFactory.INSTANCE,
                ProducerType.MULTI,
                new BlockingWaitStrategy()
        );

        // 注入会话注册表（解决 Disruptor 启动时序问题）
        eventHandler.setSessionRegistry(sessionRegistry);

        // 单线程消费者，保证同一空间的事件按序处理
        disruptor.handleEventsWith(eventHandler);
        disruptor.start();

        return disruptor.getRingBuffer();
    }

    @PreDestroy
    public void shutdown() {
        if (disruptor != null) {
            disruptor.shutdown();
        }
    }
}
