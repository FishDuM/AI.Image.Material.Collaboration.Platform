package hk.ljx.fishpicsbackend.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import lombok.Data;

/**
 * 用户粉丝表
 * @TableName user_fans
 */
@TableName(value ="user_fans")
@Data
public class UserFans implements Serializable {
    /**
     * 
     */
    private Long id;

    /**
     * 
     */
    private Long userId;

    /**
     * 
     */
    private Long fanId;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}