package hk.ljx.fishpicsbackend.task.consumer;

import hk.ljx.fishpicsbackend.task.entity.Task;
import hk.ljx.fishpicsbackend.task.handler.TaskHandler;
import hk.ljx.fishpicsbackend.mapper.TaskMapper;
import hk.ljx.fishpicsbackend.task.message.TaskMessage;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@RocketMQMessageListener(
        topic = "task-topic",
        consumerGroup = "fish-pics-consumer-group",
        selectorExpression = "*"
)
public class TaskConsumer implements RocketMQListener<TaskMessage> {

    @Resource
    private TaskMapper taskMapper;

    @Resource
    private TransactionTemplate transactionTemplate;

    private final Map<String, TaskHandler> handlerMap;

    public TaskConsumer(List<TaskHandler> handlers) {
        this.handlerMap = handlers.stream()
                .collect(Collectors.toMap(TaskHandler::getBizType, Function.identity()));
    }

    @Override
    public void onMessage(TaskMessage message) {
        String taskId = message.getTaskId();
        Task task = taskMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Task>()
                        .eq(Task::getTaskId, taskId));
        if (task == null) {
            log.warn("task not found: taskId={}", taskId);
            return;
        }

        // 幂等检查：已处理或处理中的任务直接跳过
        if ("DONE".equals(task.getStatus()) || "PROCESSING".equals(task.getStatus())) {
            log.warn("task already processed, skipping: taskId={}, status={}", taskId, task.getStatus());
            return;
        }

        task.setStatus("PROCESSING");
        taskMapper.updateById(task);

        TaskHandler handler = handlerMap.get(task.getBizType());
        if (handler == null) {
            log.error("no handler for bizType={}, taskId={}", task.getBizType(), taskId);
            task.setStatus("FAILED");
            task.setErrorMsg("no handler for bizType: " + task.getBizType());
            taskMapper.updateById(task);
            return;
        }

        try {
            handler.execute(task);

            transactionTemplate.executeWithoutResult(status -> {
                handler.persist(task);
                task.setStatus("DONE");
                taskMapper.updateById(task);
            });

            log.info("task done: taskId={}, bizType={}", taskId, task.getBizType());
        } catch (Exception e) {
            log.error("task failed: taskId={}, bizType={}", taskId, task.getBizType(), e);
            task.setStatus("FAILED");
            task.setErrorMsg(e.getMessage());
            taskMapper.updateById(task);
            throw new RuntimeException("task processing failed, will retry: " + taskId, e);
        }
    }
}
