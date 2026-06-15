package hk.ljx.fishpicsbackend.common.config;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.SimpleMessageConverter;
import lombok.extern.slf4j.Slf4j;

import hk.ljx.fishpicsbackend.task.consumer.TaskConsumer;
import hk.ljx.fishpicsbackend.task.handler.TaskHandler;
import hk.ljx.fishpicsbackend.common.entity.SysAuditLog;
import hk.ljx.fishpicsbackend.mapper.SysAuditLogMapper;
import cn.hutool.json.JSONUtil;

import java.util.List;

@Configuration
@Slf4j
public class RocketMQConfig {

    /** TaskProducer / TaskConsumer 约定的 topic 前缀,详见 TaskServiceImpl#dispatchTask */
    private static final String TASK_TOPIC_PREFIX = "task-topic-";

    @Value("${rocketmq.name-server}")
    private String nameServer;

    @Value("${rocketmq.consumer.group}")
    private String consumerGroup;

    @Bean
    public SimpleMessageConverter rocketMQMessageConverter() {
        return new SimpleMessageConverter();
    }

    // ==================== 任务 Producer(失败时内部重新投递) ================

    @Bean(name = "taskProducer", initMethod = "start", destroyMethod = "shutdown")
    public DefaultMQProducer taskProducer() {
        DefaultMQProducer producer = new DefaultMQProducer("task-producer-group");
        producer.setNamesrvAddr(nameServer);
        producer.setRetryTimesWhenSendFailed(2);
        return producer;
    }

    /**
     * 任务 Consumer
     *
     * <p>订阅策略:从 Spring 容器中收集所有 {@link TaskHandler} bean,按其 {@link TaskHandler#getBizType()}
     * 自动订阅对应的 task-topic-{bizType}。</p>
     *
     * <p>为什么不用通配符:RocketMQ 4.x client 不支持 topic 通配符订阅,使用 task-topic-* 会
     * 静默失败(订阅成功但拉不到任何消息),导致任务永远卡 PENDING。
     * 自动从 handler 派生订阅后,新增 bizType 只需新增一个 @Component implements TaskHandler,
     * 不再需要改本配置类,避免漏改导致 PENDING 死锁。</p>
     */
    @Bean(initMethod = "start", destroyMethod = "shutdown")
    public DefaultMQPushConsumer taskConsumerContainer(TaskConsumer listener,
                                                       List<TaskHandler> taskHandlers) throws Exception {
        log.info("TaskConsumer init, nameServer={}, group={}", nameServer, consumerGroup);

        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(consumerGroup);
        consumer.setNamesrvAddr(nameServer);
        // 必须从最早 offset 开始消费，否则重启后已有消息会被跳过，任务永远卡 PENDING
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
        consumer.setConsumeThreadMin(2);
        consumer.setConsumeThreadMax(4);
        // 每次只投递1条消息，避免批量消费中单条失败导致整批重试
        consumer.setConsumeMessageBatchMaxSize(1);
        // 毒消息保护:超过 3 次系统级重试后,让消息进死信队列,不再阻塞 2-4 消费线程
        // (与 audit-log-consumer 保持一致,TaskConsumer 之前漏配,系统异常时默认 16 次重试会耗尽延迟等级)
        consumer.setMaxReconsumeTimes(3);

        // 从 handler 派生订阅 topic(替代之前 task-topic-* 通配符)
        List<String> subscribedTopics = taskHandlers.stream()
                .map(TaskHandler::getBizType)
                .filter(bizType -> bizType != null && !bizType.isBlank())
                .map(bizType -> TASK_TOPIC_PREFIX + bizType)
                .toList();
        if (subscribedTopics.isEmpty()) {
            // 启动期就能发现的硬错:容器里没有 TaskHandler,直接抛错阻止启动
            throw new IllegalStateException(
                    "No TaskHandler beans found, cannot subscribe to any task topic. "
                            + "Add at least one @Component implements TaskHandler.");
        }
        for (String topic : subscribedTopics) {
            consumer.subscribe(topic, "*");
        }
        log.info("TaskConsumer subscribed to {} topic(s): {}", subscribedTopics.size(), subscribedTopics);

        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            for (var msg : msgs) {
                try {
                    String body = new String(msg.getBody(), java.nio.charset.StandardCharsets.UTF_8);
                    listener.onMessage(new hk.ljx.fishpicsbackend.task.message.TaskMessage(body));
                } catch (Exception e) {
                    // 区分系统级异常和业务异常
                    // 业务异常(BaseException)已在 TaskConsumer 内标记 FAILED，无需重试
                    // 系统级异常应重试
                    if (e instanceof hk.ljx.fishpicsbackend.common.exception.BaseException) {
                        log.error("consume business error, msgId={}, will not retry", msg.getMsgId(), e);
                        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                    }
                    log.error("consume system error, msgId={}, will retry later", msg.getMsgId(), e);
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        return consumer;
    }

    // ==================== 审计日志 Producer ====================

    @Bean(name = "auditLogProducer", initMethod = "start", destroyMethod = "shutdown")
    public DefaultMQProducer auditLogProducer() {
        DefaultMQProducer producer = new DefaultMQProducer("audit-log-producer-group");
        producer.setNamesrvAddr(nameServer);
        producer.setRetryTimesWhenSendFailed(2);
        return producer;
    }

    // ==================== 审计日志 Consumer ====================

    @Bean(initMethod = "start", destroyMethod = "shutdown")
    public DefaultMQPushConsumer auditLogConsumerContainer(SysAuditLogMapper sysAuditLogMapper) throws Exception {
        log.info("AuditLogConsumer init, nameServer={}", nameServer);

        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("audit-log-consumer-group");
        consumer.setNamesrvAddr(nameServer);
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        consumer.setConsumeThreadMin(1);
        consumer.setConsumeThreadMax(2);
        consumer.setConsumeMessageBatchMaxSize(1);
        // 毒消息保护 — 超过 3 次重试后,直接进死信
        consumer.setMaxReconsumeTimes(3);
        consumer.subscribe("audit-log-topic", "*");
        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            for (var msg : msgs) {
                try {
                    String body = new String(msg.getBody(), java.nio.charset.StandardCharsets.UTF_8);
                    SysAuditLog auditLog = JSONUtil.toBean(body, SysAuditLog.class);
                    sysAuditLogMapper.insert(auditLog);
                    log.debug("审计日志异步写入成功: operation={}", auditLog.getOperation());
                } catch (Exception e) {
                    int reconsumeTimes = msg.getReconsumeTimes();
                    log.error("审计日志消费失败, msgId={}, reconsume={}/3", msg.getMsgId(), reconsumeTimes, e);
                    if (reconsumeTimes >= 3) {
                        // 已达上限,直接成功消费(让消息进死信队列,不再阻塞消费线程)
                        log.error("审计日志毒消息放弃, msgId={}, body={}", msg.getMsgId(),
                                new String(msg.getBody(), java.nio.charset.StandardCharsets.UTF_8));
                        continue;
                    }
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        return consumer;
    }
}
