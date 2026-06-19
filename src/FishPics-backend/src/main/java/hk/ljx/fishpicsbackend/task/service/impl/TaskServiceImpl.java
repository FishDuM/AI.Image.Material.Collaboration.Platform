package hk.ljx.fishpicsbackend.task.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.mapper.TaskMapper;
import hk.ljx.fishpicsbackend.task.entity.Task;
import hk.ljx.fishpicsbackend.task.component.TaskProcessor;
import hk.ljx.fishpicsbackend.task.service.TaskService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
public class TaskServiceImpl extends ServiceImpl<TaskMapper, Task> implements TaskService {

    @Resource
    private TaskMapper taskMapper;

    @Resource
    private TaskProcessor taskProcessor;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String submitTask(String bizType, String bizId, String param, Long userId) {
        Task task = new Task();
        task.setTaskId(IdUtil.fastSimpleUUID());
        task.setBizType(bizType);
        task.setBizId(bizId);
        task.setParam(param);
        task.setUserId(userId);
        task.setStatus("PENDING");
        taskMapper.insert(task);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatchTask(task.getTaskId());
                }
            });
        } else {
            dispatchTask(task.getTaskId());
        }

        log.debug("task submitted: taskId={}, bizType={}, bizId={}", task.getTaskId(), bizType, bizId);
        return task.getTaskId();
    }

    @Override
    public Task getTaskByTaskId(String taskId) {
        return taskMapper.selectOne(new LambdaQueryWrapper<Task>().eq(Task::getTaskId, taskId));
    }

    @Override
    public void dispatchTask(String taskId) {
        Task task = getTaskByTaskId(taskId);
        if (task == null) {
            throw new BaseException(ExceptionCode.NOT_FOUND, "task not found");
        }
        if (!"PENDING".equals(task.getStatus())) {
            return;
        }
        taskProcessor.dispatch(taskId);
    }
}
