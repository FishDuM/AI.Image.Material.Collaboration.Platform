package hk.ljx.fishpicsbackend.task.component;

import hk.ljx.fishpicsbackend.task.entity.Task;
import hk.ljx.fishpicsbackend.task.service.TaskService;
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
        // 只补偿 1 分钟前提交还在 PENDING 的任务
        List<Task> pendingTasks = taskService.lambdaQuery()
                .eq(Task::getStatus, "PENDING")
                .lt(Task::getCreateTime, new java.util.Date(System.currentTimeMillis() - 60_000L))
                .orderByAsc(Task::getId)
                .last("LIMIT " + BATCH_SIZE)
                .list();
        if (pendingTasks.isEmpty()) {
            return;
        }

        // 失败不中断:之前一条 dispatch 失败就 break,导致同一批剩余 99 条当周期全部跳过
        // 单条失败不影响同批其他任务，下个周期仍会重试留在 PENDING 的任务。
        // 改为失败记录后继续,下个周期仍会重试这一批(数据库里 status 仍 PENDING),不会丢任务
        int success = 0;
        int failure = 0;
        for (Task task : pendingTasks) {
            try {
                taskService.dispatchTask(task.getTaskId());
                success++;
            } catch (Exception e) {
                failure++;
                log.warn("task retry dispatch failed, will retry next cycle: taskId={}, bizType={}",
                        task.getTaskId(), task.getBizType(), e);
            }
        }
        if (success > 0 || failure > 0) {
            log.info("compensator batch done: success={}, failure={}, total={}",
                    success, failure, pendingTasks.size());
        }
    }
}
