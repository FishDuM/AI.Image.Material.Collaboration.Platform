package hk.ljx.fishpicsbackend.task.component;

import com.alibaba.dashscope.exception.ApiException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import hk.ljx.fishpicsbackend.ai.sse.AiSseEmitterRegistry;
import hk.ljx.fishpicsbackend.mapper.TaskMapper;
import hk.ljx.fishpicsbackend.task.entity.Task;
import hk.ljx.fishpicsbackend.task.handler.TaskHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class TaskProcessor {

    private static final int MAX_RETRY_COUNT = 3;
    private static final long STUCK_PROCESSING_MS = 5 * 60 * 1000L;

    @Resource
    private TaskMapper taskMapper;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private AiSseEmitterRegistry sseEmitterRegistry;

    @Resource
    @Qualifier("applicationTaskExecutor")
    private Executor taskExecutor;

    private final Map<String, TaskHandler> handlerMap;

    public TaskProcessor(List<TaskHandler> handlers) {
        this.handlerMap = handlers.stream()
                .collect(Collectors.toMap(TaskHandler::getBizType, Function.identity()));
    }

    public void dispatch(String taskId) {
        try {
            CompletableFuture.runAsync(() -> process(taskId), taskExecutor)
                    .exceptionally(e -> {
                        log.error("task processor crashed: taskId={}", taskId, e);
                        if (e instanceof RejectedExecutionException
                                || e.getCause() instanceof RejectedExecutionException) {
                            markFailedByTaskId(taskId, "任务调度线程池繁忙，请稍后重试");
                        }
                        return null;
                    });
        } catch (RejectedExecutionException e) {
            markFailedByTaskId(taskId, "任务调度线程池繁忙，请稍后重试");
        }
    }

    public void process(String taskId) {
        if (!claimTask(taskId)) {
            return;
        }

        Task task = taskMapper.selectOne(new LambdaQueryWrapper<Task>().eq(Task::getTaskId, taskId));
        if (task == null) {
            log.warn("task disappeared after claim: taskId={}", taskId);
            return;
        }

        processClaimedTask(task);
    }

    private boolean claimTask(String taskId) {
        int claimed = taskMapper.update(null,
                new LambdaUpdateWrapper<Task>()
                        .eq(Task::getTaskId, taskId)
                        .eq(Task::getStatus, "PENDING")
                        .set(Task::getStatus, "PROCESSING"));

        if (claimed > 0) {
            return true;
        }

        Task task = taskMapper.selectOne(new LambdaQueryWrapper<Task>().eq(Task::getTaskId, taskId));
        if (task == null) {
            log.warn("task not found: taskId={}", taskId);
            return false;
        }
        if ("DONE".equals(task.getStatus())) {
            log.debug("task already done, skipping: taskId={}", taskId);
            return false;
        }
        if (!"PROCESSING".equals(task.getStatus())) {
            log.warn("task in unexpected status '{}', skipping: taskId={}", task.getStatus(), taskId);
            return false;
        }

        long elapsed = task.getUpdateTime() != null
                ? ChronoUnit.MILLIS.between(task.getUpdateTime(), LocalDateTime.now())
                : Long.MAX_VALUE;
        if (elapsed < STUCK_PROCESSING_MS) {
            log.debug("task is processing, skipping: taskId={}, elapsed={}ms", taskId, elapsed);
            return false;
        }

        int reclaimed = taskMapper.update(null,
                new LambdaUpdateWrapper<Task>()
                        .eq(Task::getTaskId, taskId)
                        .eq(Task::getStatus, "PROCESSING")
                        .apply("update_time < DATE_SUB(NOW(), INTERVAL 5 MINUTE)")
                        .set(Task::getUpdateTime, LocalDateTime.now()));
        if (reclaimed == 0) {
            log.warn("task re-claim failed, skipping: taskId={}", taskId);
            return false;
        }
        log.warn("reclaimed stuck task: taskId={}", taskId);
        return true;
    }

    private void processClaimedTask(Task task) {
        TaskHandler handler = handlerMap.get(task.getBizType());
        if (handler == null) {
            markFailed(task, "no handler for bizType: " + task.getBizType(), true);
            return;
        }

        try {
            handler.execute(task);
            transactionTemplate.executeWithoutResult(status -> {
                handler.persist(task);
                int rows = taskMapper.update(null,
                        new LambdaUpdateWrapper<Task>()
                                .eq(Task::getTaskId, task.getTaskId())
                                .eq(Task::getStatus, "PROCESSING")
                                .set(Task::getStatus, "DONE")
                                .set(Task::getResult, task.getResult())
                                .set(Task::getErrorMsg, null));
                if (rows == 0) {
                    throw new IllegalStateException("task status changed before commit, taskId=" + task.getTaskId());
                }
            });

            log.info("task done: taskId={}, bizType={}", task.getTaskId(), task.getBizType());
            sseEmitterRegistry.completeWithResult(task.getTaskId(), task.getResult());
        } catch (Exception e) {
            boolean permanently = handleFailure(task, e);
            if (permanently) {
                handler.onFailed(task);
            }
        }
    }

    /**
     * @return true 表示任务已永久失败，false 表示已安排重试
     */
    private boolean handleFailure(Task task, Exception e) {
        String errorMsg = friendlyError(e);
        int newRetryCount = (task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1;

        if (newRetryCount <= MAX_RETRY_COUNT && moveBackToPending(task, errorMsg, newRetryCount)) {
            long delaySec = switch (newRetryCount) {
                case 1 -> 5L;
                case 2 -> 10L;
                default -> 30L;
            };
            try {
                CompletableFuture.delayedExecutor(delaySec, TimeUnit.SECONDS, taskExecutor)
                        .execute(() -> process(task.getTaskId()));
            } catch (RejectedExecutionException rejected) {
                markFailed(task, "任务重试调度失败，请稍后重新提交", true);
                return true;
            }
            log.warn("task retry {}/{} after {}s: taskId={}",
                    newRetryCount, MAX_RETRY_COUNT, delaySec, task.getTaskId(), e);
            return false;
        }

        markFailed(task, errorMsg, true);
        return true;
    }

    private boolean moveBackToPending(Task task, String errorMsg, int retryCount) {
        int rows = taskMapper.update(null,
                new LambdaUpdateWrapper<Task>()
                        .eq(Task::getTaskId, task.getTaskId())
                        .eq(Task::getStatus, "PROCESSING")
                        .set(Task::getStatus, "PENDING")
                        .set(Task::getRetryCount, retryCount)
                        .set(Task::getErrorMsg, errorMsg));
        if (rows == 0) {
            log.warn("task retry CAS failed: taskId={}", task.getTaskId());
            return false;
        }
        return true;
    }

    private void markFailed(Task task, String errorMsg, boolean notifySse) {
        int rows = taskMapper.update(null,
                new LambdaUpdateWrapper<Task>()
                        .eq(Task::getTaskId, task.getTaskId())
                        .in(Task::getStatus, "PENDING", "PROCESSING")
                        .set(Task::getStatus, "FAILED")
                        .set(Task::getErrorMsg, errorMsg)
                        .set(Task::getRetryCount, (task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1));
        if (rows == 0) {
            log.warn("task FAILED mark skipped: taskId={}", task.getTaskId());
            return;
        }
        log.error("task FAILED: taskId={}, errorMsg={}", task.getTaskId(), errorMsg);
        if (notifySse) {
            sseEmitterRegistry.completeWithError(task.getTaskId(), errorMsg);
        }
    }

    private void markFailedByTaskId(String taskId, String errorMsg) {
        Task task = taskMapper.selectOne(new LambdaQueryWrapper<Task>().eq(Task::getTaskId, taskId));
        if (task != null) {
            markFailed(task, errorMsg, true);
        }
    }

    private String friendlyError(Exception e) {
        if (e instanceof ApiException ae && ae.getMessage() != null) {
            String msg = ae.getMessage();
            if (msg.contains("DataInspectionFailed")) {
                return "生成的图片内容不合规";
            }
            if (msg.contains("IPInfringementSuspect")) {
                return "输入提示词涉嫌侵权";
            }
        }
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }
}
