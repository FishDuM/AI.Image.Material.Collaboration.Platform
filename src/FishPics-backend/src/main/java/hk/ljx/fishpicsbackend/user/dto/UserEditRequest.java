package hk.ljx.fishpicsbackend.user.dto;

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
     * 账号（登录用）
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 如果修改密码要先输入原始密码
     */
    private String originalPassword;

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
}
