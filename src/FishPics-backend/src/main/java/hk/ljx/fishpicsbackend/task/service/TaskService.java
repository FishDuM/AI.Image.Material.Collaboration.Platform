package hk.ljx.fishpicsbackend.task.service;

import com.baomidou.mybatisplus.extension.service.IService;
import hk.ljx.fishpicsbackend.task.entity.Task;

public interface TaskService extends IService<Task> {

    String submitTask(String bizType, String bizId, String param, Long userId);

    Task getTaskByTaskId(String taskId);

    void dispatchTask(String taskId);
}
