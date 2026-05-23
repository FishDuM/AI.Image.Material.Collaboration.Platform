package hk.ljx.fishpicsbackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;

@TableName(value = "ai_task")
@Data
public class AiTask implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Integer type;

    private String subType;

    private String inputData;

    private String outputData;

    private Integer status;

    private String errorMsg;

    private Long pictureId;

    private Date createTime;

    private Date updateTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
