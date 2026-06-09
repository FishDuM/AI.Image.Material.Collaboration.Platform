package hk.ljx.fishpicsbackend.collab;

import com.lmax.disruptor.RingBuffer;
import hk.ljx.fishpicsbackend.collab.model.CollabEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 协同事件发布器
 * 将事件发布到 Disruptor Ring Buffer，由消费者线程异步处理
 * publish() 是非阻塞的，仅将事件写入 Ring Buffer 槽位后立即返回
 */
@Slf4j
@Component
public class CollabEventPublisher {

    private final RingBuffer<CollabEvent> ringBuffer;

    public CollabEventPublisher(RingBuffer<CollabEvent> ringBuffer) {
        this.ringBuffer = ringBuffer;
    }

    /**
     * 发布协同事件到 Ring Buffer
     *
     * @param translator 事件数据填充函数
     */
    public void publish(EventTranslator translator) {
        long sequence = ringBuffer.next();
        try {
            CollabEvent event = ringBuffer.get(sequence);
            translator.translate(event);
        } finally {
            ringBuffer.publish(sequence);
        }
    }

    @FunctionalInterface
    public interface EventTranslator {
        void translate(CollabEvent event);
    }
}
