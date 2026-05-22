package hk.ljx.fishpicsbackend.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
     * 用户的权限
     */
    private String role;

    /**
     * 用户等级：0-普通，1-VIP，2-SVIP
     */
    private Integer level;
}
