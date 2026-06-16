package hk.ljx.fishpicsbackend.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import lombok.Data;

/**
 * 系统表
 * @TableName pic_system
 */
@TableName(value ="pic_system")
@Data
public class PicSystem implements Serializable {
    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 
     */
    private String syskey;

    /**
     * 
     */
    private String sysvalue;

    private static final long serialVersionUID = 1L;
}