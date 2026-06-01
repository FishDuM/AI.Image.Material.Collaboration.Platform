package hk.ljx.fishpicsbackend.task.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import hk.ljx.fishpicsbackend.task.entity.Task;
import hk.ljx.fishpicsbackend.task.service.TaskService;
import hk.ljx.fishpicsbackend.mapper.TaskMapper;
import hk.ljx.fishpicsbackend.task.message.TaskMessage;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class TaskServiceImpl extends ServiceImpl<TaskMapper, Task>
        implements TaskService {

    @Resource
    private TaskMapper taskMapper;

    @Resource
    private RocketMQTemplate rocketMQTemplate;

    @Override
    public String submitTask(String bizType, String bizId, String param, Long userId) {
        Task task = new Task();
        task.setTaskId(IdUtil.fastSimpleUUID());
        task.setBizType(bizType);
        task.setBizId(bizId);
        task.setParam(param);
        task.setUserId(userId);
        task.setStatus("PENDING");
        taskMapper.insert(task);

        rocketMQTemplate.convertAndSend("task-topic", new TaskMessage(task.getTaskId()));
        log.info("task submitted: taskId={}, bizType={}, bizId={}", task.getTaskId(), bizType, bizId);
        return task.getTaskId();
    }

    @Override
    public Task getTaskByTaskId(String taskId) {
        return taskMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Task>()
                        .eq(Task::getTaskId, taskId));
    }
}
