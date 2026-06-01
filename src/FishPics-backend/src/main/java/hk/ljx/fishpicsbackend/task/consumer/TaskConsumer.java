package hk.ljx.fishpicsbackend.task.consumer;

import com.alibaba.dashscope.exception.ApiException;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.task.entity.Task;
import hk.ljx.fishpicsbackend.task.handler.TaskHandler;
import hk.ljx.fishpicsbackend.mapper.TaskMapper;
import hk.ljx.fishpicsbackend.task.message.TaskMessage;
import hk.ljx.fishpicsbackend.websocket.WebSocketHandler;
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

    @Resource
    private WebSocketHandler webSocketHandler;

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

            // WebSocket 推送完成通知
            notifyUser(task, "TASK_DONE", task.getResult(), null);
        } catch (Exception e) {
            log.error("task failed: taskId={}, bizType={}", taskId, task.getBizType(), e);
            task.setStatus("FAILED");

            // DashScope 审核类错误使用友好提示，其他错误用原始消息
            String errorMsg = e instanceof ApiException ae
                    ? ExcUtils.translateDashScopeError(ae) : null;
            if (errorMsg == null) {
                errorMsg = e.getMessage();
            }
            task.setErrorMsg(errorMsg);
            taskMapper.updateById(task);

            // WebSocket 推送失败通知
            notifyUser(task, "TASK_FAILED", null, errorMsg);
        }
    }

    private void notifyUser(Task task, String type, String result, String errorMsg) {
        if (task.getUserId() != null) {
            webSocketHandler.sendToUser(
                    task.getUserId(),
                    type,
                    task.getTaskId(),
                    task.getBizType(),
                    result,
                    errorMsg
            );
        }
    }
}
