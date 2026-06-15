package hk.ljx.fishpicsbackend.task.consumer;

import com.alibaba.dashscope.exception.ApiException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.task.entity.Task;
import hk.ljx.fishpicsbackend.task.handler.TaskHandler;
import hk.ljx.fishpicsbackend.mapper.TaskMapper;
import hk.ljx.fishpicsbackend.task.message.TaskMessage;
import hk.ljx.fishpicsbackend.ai.sse.AiSseEmitterRegistry;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class TaskConsumer {

    /** 最大重试次数,超过则标 FAILED */
    private static final int MAX_RETRY_COUNT = 3;

    @Resource
    private TaskMapper taskMapper;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    @Qualifier("taskProducer")
    private DefaultMQProducer taskProducer;

    @Resource
    private AiSseEmitterRegistry sseEmitterRegistry;

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
            // 修复:用 WHERE status='PROCESSING' 做 CAS,避免与并发抢占者互相覆盖结果
            transactionTemplate.executeWithoutResult(status -> {
                handler.persist(currentTask);
                currentTask.setStatus("DONE");
                int rows = taskMapper.update(null,
                        new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Task>()
                                .eq(Task::getTaskId, currentTask.getTaskId())
                                .eq(Task::getStatus, "PROCESSING")
                                .set(Task::getStatus, "DONE")
                                .set(Task::getResult, currentTask.getResult()));
                if (rows == 0) {
                    // 已被其他 consumer 抢先完成(理论上不会发生,因为 RocketMQ 串行消费)
                    // 显式抛异常回滚事务,避免 persist 重复执行
                    throw new com.baomidou.mybatisplus.core.exceptions.MybatisPlusException(
                            "task status changed before commit, taskId=" + currentTask.getTaskId());
                }
            });

            log.info("task done: taskId={}, bizType={}", currentTask.getTaskId(), currentTask.getBizType());
            // SSE 推送：任务完成
            sseEmitterRegistry.completeWithResult(currentTask.getTaskId(), currentTask.getResult());
        } catch (Exception e) {
            log.error("task failed: taskId={}, bizType={}", task.getTaskId(), task.getBizType(), e);

            // DashScope 审核类错误使用友好提示，其他错误用原始消息
            String errorMsg = e instanceof ApiException ae
                    ? ExcUtils.translateDashScopeError(ae) : null;
            if (errorMsg == null) {
                errorMsg = e.getMessage();
            }
            int newRetryCount = (task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1;

            if (newRetryCount <= MAX_RETRY_COUNT) {
                // 未达上限 → CAS 状态回退到 PENDING,retry_count++,errorMsg 暂存,然后重发
                int rows = taskMapper.update(null,
                        new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Task>()
                                .eq(Task::getTaskId, task.getTaskId())
                                .eq(Task::getStatus, "PROCESSING")
                                .set(Task::getStatus, "PENDING")
                                .set(Task::getRetryCount, newRetryCount)
                                .set(Task::getErrorMsg, errorMsg));
                if (rows > 0) {
                    // 退避:第 N 次重试延迟 N*5 秒(避免雪崩)
                    long delaySec = newRetryCount * 5L;
                    try {
                        // 重发也按 bizType 分桶
                        String retryTopic = "task-topic-" + (task.getBizType() != null ? task.getBizType() : "default");
                        // 与初始发送保持一致(纯 taskId 字节)
                        Message msg = new Message(retryTopic,
                                task.getTaskId().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        // 用 RocketMQ 原生 delay level（服务端延迟投递）
                        // level: 1=1s, 2=5s, 3=10s, 4=30s, 5=1m, 6=2m, 7=3m
                        // 重试 1/2/3 → level 2(5s)/3(10s)/4(30s)
                        int delayLevel = switch (newRetryCount) {
                            case 1 -> 2;  // 5s
                            case 2 -> 3;  // 10s
                            case 3 -> 4;  // 30s
                            default -> 2;
                        };
                        msg.setDelayTimeLevel(delayLevel);
                        taskProducer.send(msg);
                        log.warn("task retry {}/{} (delayLevel={}): taskId={}", newRetryCount, MAX_RETRY_COUNT, delayLevel, task.getTaskId());
                        return;
                    } catch (Exception sendEx) {
                        // MQ 重发失败时,任务已 CAS 成 PENDING(上面 retry 分支)
                        // 但 FAILED 分支用 status=PROCESSING CAS,会找不到 → 任务卡 PENDING 无人重试
                        // 这里:把任务从 PENDING 改回 FAILED
                        String combinedErr = errorMsg + " | MQ重发失败: " + sendEx.getMessage();
                        int pendingToFailedRows = taskMapper.update(null,
                                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Task>()
                                        .eq(Task::getTaskId, task.getTaskId())
                                        .eq(Task::getStatus, "PENDING")
                                        .set(Task::getStatus, "FAILED")
                                        .set(Task::getErrorMsg, combinedErr)
                                        .set(Task::getRetryCount, newRetryCount));
                        if (pendingToFailedRows == 0) {
                            log.error("task PENDING→FAILED CAS 也失败(task 状态被并发改了): taskId={}", task.getTaskId());
                        } else {
                            log.error("task FAILED(MQ 重发失败): taskId={}, errorMsg={}", task.getTaskId(), combinedErr);
                            sseEmitterRegistry.completeWithError(task.getTaskId(), combinedErr);
                        }
                        return; // 任务已 FAILED,不再走下面的 FAILED 分支
                    }
                } else {
                    log.warn("task retry CAS failed (status already changed), taskId={}", task.getTaskId());
                    return;
                }
            }

            // 超过重试上限 → 标 FAILED
            int rows = taskMapper.update(null,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Task>()
                            .eq(Task::getTaskId, task.getTaskId())
                            .eq(Task::getStatus, "PROCESSING")
                            .set(Task::getStatus, "FAILED")
                            .set(Task::getErrorMsg, errorMsg)
                            .set(Task::getRetryCount, newRetryCount));
            if (rows == 0) {
                log.warn("task FAILED mark skipped (status already changed), taskId={}", task.getTaskId());
            } else {
                log.error("task FAILED after {} retries: taskId={}", newRetryCount, task.getTaskId());
                // SSE 推送：任务失败
                sseEmitterRegistry.completeWithError(task.getTaskId(), errorMsg);
            }
        }
    }
}
