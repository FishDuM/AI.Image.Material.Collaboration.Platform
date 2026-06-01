package hk.ljx.fishpicsbackend.user.vo;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AdminGetUserVO implements Serializable {
    /**
     * 用户ID
     */
    private Long id;

    /**
     * 用户名（登录用）
     */
    private String username;

    /**
     * 头像URL
     */
    private String avatar;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 昵称（展示用）
     */
    private String nickname;

    /**
     * 状态 1-正常 0-禁用 2-待审核
     */
    private Integer status;

    /**
     * 用户的权限
     */
    private String role;

    /**
     * 0-普通，1-VIP，2-SVIP
     */
    private Integer level;

    @Serial
    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

}
