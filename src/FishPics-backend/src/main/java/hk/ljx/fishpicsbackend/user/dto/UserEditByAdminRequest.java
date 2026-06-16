package hk.ljx.fishpicsbackend.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEditByAdminRequest {
    /**
     * 用户ID
     */
    private Long id;

    /**
     * 账号（登录用）
     */
    private String username;

    /**
     * 密码
     */
    private String password;

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
     * 用户角色ID列表
     */
    private List<Long> roleIds;

    /**
     * 用户等级：0-普通，1-VIP，2-SVIP
     */
    private Integer level;

    /**
     * 用户角色：0-普通，1-管理员
     */
    private Integer role;
}
