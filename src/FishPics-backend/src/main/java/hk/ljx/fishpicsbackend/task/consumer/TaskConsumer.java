package hk.ljx.fishpicsbackend.task.consumer;

import com.alibaba.dashscope.exception.ApiException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.task.entity.Task;
import hk.ljx.fishpicsbackend.task.handler.TaskHandler;
import hk.ljx.fishpicsbackend.mapper.TaskMapper;
import hk.ljx.fishpicsbackend.task.message.TaskMessage;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class TaskConsumer {

    @Resource
    private TaskMapper taskMapper;

    @Resource
    private TransactionTemplate transactionTemplate;

    private final Map<String, TaskHandler> handlerMap;

    public TaskConsumer(List<TaskHandler> handlers) {
        this.handlerMap = handlers.stream()
                .collect(Collectors.toMap(TaskHandler::getBizType, Function.identity()));
    }

    /**
     * 消费任务消息（MQ 触发入口）
     */
    public void onMessage(TaskMessage message) {
        String taskId = message.getTaskId();

        // 原子抢占：使用条件 UPDATE 将 PENDING → PROCESSING，消除并发竞态
        int claimed = taskMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Task>()
                        .eq(Task::getTaskId, taskId)
                        .eq(Task::getStatus, "PENDING")
                        .set(Task::getStatus, "PROCESSING"));

        if (claimed == 0) {
            // 未抢占成功：任务可能已被其他 consumer 处理、已完成、或处于 PROCESSING
            Task task = taskMapper.selectOne(
                    new LambdaQueryWrapper<Task>()
                            .eq(Task::getTaskId, taskId));
            if (task == null) {
                log.warn("task not found: taskId={}", taskId);
                return;
            }
            if ("DONE".equals(task.getStatus())) {
                log.warn("task already done, skipping: taskId={}", taskId);
                return;
            }
            if ("PROCESSING".equals(task.getStatus())) {
                // 处理中的任务：如果超过5分钟认为 consumer 崩溃，允许重新处理
                long elapsed = task.getUpdateTime() != null
                        ? System.currentTimeMillis() - task.getUpdateTime().getTime()
                        : Long.MAX_VALUE;
                if (elapsed < 5 * 60 * 1000) {
                    log.warn("task is processing, skipping: taskId={}, elapsed={}ms", taskId, elapsed);
                    return;
                }
                log.warn("task stuck in PROCESSING 超过5分钟，重新抢占: taskId={}", taskId);
                // 重新抢占：WHERE 加时间条件，只有真正卡住的任务才会被重新处理
                claimed = taskMapper.update(null,
                        new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Task>()
                                .eq(Task::getTaskId, taskId)
                                .eq(Task::getStatus, "PROCESSING")
                                .apply("update_time < DATE_SUB(NOW(), INTERVAL 5 MINUTE)")
                                .set(Task::getStatus, "PROCESSING")
                                .set(Task::getUpdateTime, new java.util.Date()));
                if (claimed == 0) {
                    log.warn("task re-claim failed (another consumer took it), skipping: taskId={}", taskId);
                    return;
                }
            } else {
                log.warn("task in unexpected status '{}', skipping: taskId={}", task.getStatus(), taskId);
                return;
            }
        }

        Task task = taskMapper.selectOne(
                new LambdaQueryWrapper<Task>()
                        .eq(Task::getTaskId, taskId));
        if (task == null) {
            log.warn("task disappeared after claim: taskId={}", taskId);
            return;
        }

        processTask(task);
    }

    /**
     * 处理单个任务
     */
    private void processTask(Task task) {
        TaskHandler handler = handlerMap.get(task.getBizType());
        if (handler == null) {
            log.error("no handler for bizType={}, taskId={}", task.getBizType(), task.getTaskId());
            task.setStatus("FAILED");
            task.setErrorMsg("no handler for bizType: " + task.getBizType());
            taskMapper.updateById(task);
            return;
        }

        try {
            final Task currentTask = task;

            // execute 负责调用 AI 接口拿结果，不在事务内（避免长事务）
            handler.execute(currentTask);

            // persist 负责把结果写库，和标记 DONE 放在同一个事务里
            transactionTemplate.executeWithoutResult(status -> {
                handler.persist(currentTask);
                currentTask.setStatus("DONE");
                taskMapper.updateById(currentTask);
            });

            log.info("task done: taskId={}, bizType={}", currentTask.getTaskId(), currentTask.getBizType());
        } catch (Exception e) {
            log.error("task failed: taskId={}, bizType={}", task.getTaskId(), task.getBizType(), e);
            task.setStatus("FAILED");

            // DashScope 审核类错误使用友好提示，其他错误用原始消息
            String errorMsg = e instanceof ApiException ae
                    ? ExcUtils.translateDashScopeError(ae) : null;
            if (errorMsg == null) {
                errorMsg = e.getMessage();
            }
            task.setErrorMsg(errorMsg);
            taskMapper.updateById(task);
        }
    }
}
