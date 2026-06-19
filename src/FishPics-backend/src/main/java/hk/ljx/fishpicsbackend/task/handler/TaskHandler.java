package hk.ljx.fishpicsbackend.task.handler;

import hk.ljx.fishpicsbackend.task.entity.Task;

public interface TaskHandler {

    String getBizType();

    void execute(Task task) throws Exception;

    default void persist(Task task) {}
}
