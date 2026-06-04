package hk.ljx.fishpicsbackend.common.config;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.SimpleMessageConverter;
import lombok.extern.slf4j.Slf4j;

import hk.ljx.fishpicsbackend.task.consumer.TaskConsumer;

@Configuration
@Slf4j
public class RocketMQConfig {

    @Value("${rocketmq.name-server}")
    private String nameServer;

    @Value("${rocketmq.consumer.group}")
    private String consumerGroup;

    @Bean
    public SimpleMessageConverter rocketMQMessageConverter() {
        return new SimpleMessageConverter();
    }

    @Bean(initMethod = "start", destroyMethod = "shutdown")
    public DefaultMQPushConsumer taskConsumerContainer(TaskConsumer listener) throws Exception {
        log.info("TaskConsumer init, nameServer={}", nameServer);

        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(consumerGroup);
        consumer.setNamesrvAddr(nameServer);
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        consumer.setConsumeThreadMin(2);
        consumer.setConsumeThreadMax(4);
        // 每次只投递1条消息，避免批量消费中单条失败导致整批重试
        consumer.setConsumeMessageBatchMaxSize(1);
        consumer.subscribe("task-topic", "*");
        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            for (var msg : msgs) {
                try {
                    String body = new String(msg.getBody());
                    listener.onMessage(new hk.ljx.fishpicsbackend.task.message.TaskMessage(body));
                } catch (Exception e) {
                    log.error("consume message error, msgId={}", msg.getMsgId(), e);
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        return consumer;
    }
}
