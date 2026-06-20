package hk.ljx.fishpicsbackend.task.component;

import hk.ljx.fishpicsbackend.task.entity.Task;
import hk.ljx.fishpicsbackend.task.service.TaskService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class TaskDispatchCompensator {

    private static final int BATCH_SIZE = 100;
    private static final long PENDING_DELAY_MS = 60_000L;
    private static final long PROCESSING_STUCK_MS = 5 * 60_000L;

    @Resource
    private TaskService taskService;

    @Resource
    private TaskProcessor taskProcessor;

    @Scheduled(fixedDelayString = "${task.dispatch.retry-interval-ms:30000}")
    public void retryTasks() {
        retryPendingTasks(selectPendingTasks());
        retryStuckProcessingTasks(selectStuckProcessingTasks());
    }

    private List<Task> selectPendingTasks() {
        return taskService.lambdaQuery()
                .eq(Task::getStatus, "PENDING")
                .lt(Task::getUpdateTime, LocalDateTime.now().minus(Duration.ofMillis(PENDING_DELAY_MS)))
                .orderByAsc(Task::getId)
                .last("LIMIT " + BATCH_SIZE)
                .list();
    }

    private List<Task> selectStuckProcessingTasks() {
        return taskService.lambdaQuery()
                .eq(Task::getStatus, "PROCESSING")
                .lt(Task::getUpdateTime, LocalDateTime.now().minus(Duration.ofMillis(PROCESSING_STUCK_MS)))
                .orderByAsc(Task::getId)
                .last("LIMIT " + BATCH_SIZE)
                .list();
    }

    private void retryPendingTasks(List<Task> pendingTasks) {
        if (pendingTasks.isEmpty()) {
            return;
        }
        int success = 0;
        int failure = 0;
        for (Task task : pendingTasks) {
            try {
                taskService.dispatchTask(task.getTaskId());
                success++;
            } catch (Exception e) {
                failure++;
                log.warn("pending task re-dispatch failed: taskId={}, bizType={}",
                        task.getTaskId(), task.getBizType(), e);
            }
        }
        log.info("pending task compensator done: success={}, failure={}, total={}",
                success, failure, pendingTasks.size());
    }

    private void retryStuckProcessingTasks(List<Task> stuckTasks) {
        if (stuckTasks.isEmpty()) {
            return;
        }
        int success = 0;
        int failure = 0;
        for (Task task : stuckTasks) {
            try {
                taskProcessor.dispatch(task.getTaskId());
                success++;
            } catch (Exception e) {
                failure++;
                log.warn("stuck processing task re-dispatch failed: taskId={}, bizType={}",
                        task.getTaskId(), task.getBizType(), e);
            }
        }
        log.warn("stuck processing task compensator done: success={}, failure={}, total={}",
                success, failure, stuckTasks.size());
    }
}
