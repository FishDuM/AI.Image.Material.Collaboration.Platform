package hk.ljx.fishpicsbackend.task.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.mapper.TaskMapper;
import hk.ljx.fishpicsbackend.task.entity.Task;
import hk.ljx.fishpicsbackend.task.service.TaskService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@Slf4j
public class TaskServiceImpl extends ServiceImpl<TaskMapper, Task>
        implements TaskService {

    @Resource
    private TaskMapper taskMapper;

    @Resource
    private RocketMQTemplate rocketMQTemplate;

    @Resource
    @Qualifier("taskProducer")
    private DefaultMQProducer taskProducer;

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
                    try {
                        dispatchTask(task.getTaskId());
                    } catch (Exception e) {
                        log.error("task initial dispatch failed, will retry later: taskId={}, bizType={}",
                                task.getTaskId(), bizType, e);
                    }
                }
            });
        } else {
            dispatchTask(task.getTaskId());
        }

        log.info("task submitted: taskId={}, bizType={}, bizId={}", task.getTaskId(), bizType, bizId);
        return task.getTaskId();
    }

    @Override
    public Task getTaskByTaskId(String taskId) {
        return taskMapper.selectOne(new LambdaQueryWrapper<Task>()
                .eq(Task::getTaskId, taskId));
    }

    @Override
    public void dispatchTask(String taskId) {
        Task task = taskMapper.selectOne(new LambdaQueryWrapper<Task>()
                .eq(Task::getTaskId, taskId));
        if (task == null) {
            throw new BaseException(ExceptionCode.NOT_FOUND, "task not found");
        }
        if (!"PENDING".equals(task.getStatus())) {
            return;
        }
        try {
            // 按 bizType 分桶到不同 topic
            String topic = "task-topic-" + (task.getBizType() != null ? task.getBizType() : "default");
            // 发送纯 taskId 字节,consumer 端用纯字符串解析 taskId
            Message msg = new Message(topic, taskId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            taskProducer.send(msg);
        } catch (Exception e) {
            throw new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "task dispatch failed");
        }
    }
}
