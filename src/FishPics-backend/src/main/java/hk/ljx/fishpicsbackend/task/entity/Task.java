package hk.ljx.fishpicsbackend.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 异步任务表
 * @TableName task
 */
@TableName(value ="task")
@Data
public class Task implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务唯一标识(UUID) */
    private String taskId;

    private Long userId;

    /** 业务类型: ai_tag / ai_draw / notify / export ... */
    private String bizType;

    private String bizId;

    /** 状态: PENDING / PROCESSING / DONE / FAILED */
    private String status;

    private Integer retryCount;

    /** 任务参数JSON */
    private String param;

    /** 任务结果JSON */
    private String result;

    private String errorMsg;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private static final long serialVersionUID = 1L;
}