package hk.ljx.fishpicsbackend.task.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskMessage implements Serializable {
    private String taskId;
}
