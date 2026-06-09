package hk.ljx.fishpicsbackend.task.service;

import hk.ljx.fishpicsbackend.task.entity.Task;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class TaskDispatchCompensator {

    private static final int BATCH_SIZE = 100;

    @Resource
    private TaskService taskService;

    @Scheduled(fixedDelayString = "${task.dispatch.retry-interval-ms:30000}")
    public void retryPendingTasks() {
        List<Task> pendingTasks = taskService.lambdaQuery()
                .eq(Task::getStatus, "PENDING")
                .orderByAsc(Task::getId)
                .last("LIMIT " + BATCH_SIZE)
                .list();
        if (pendingTasks.isEmpty()) {
            return;
        }

        for (Task task : pendingTasks) {
            try {
                taskService.dispatchTask(task.getTaskId());
            } catch (Exception e) {
                log.warn("task retry dispatch failed, stop current batch: taskId={}", task.getTaskId(), e);
                break;
            }
        }
    }
}
