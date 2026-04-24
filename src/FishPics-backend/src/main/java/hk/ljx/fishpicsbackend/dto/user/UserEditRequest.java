package hk.ljx.fishpicsbackend.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEditRequest {
    /**
     * 用户ID
     */
    private Long id;

    /**
     * 用户名（登录用）
     */
    private String username;

    /**
     * 密码
     */
    private String password;

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
}
