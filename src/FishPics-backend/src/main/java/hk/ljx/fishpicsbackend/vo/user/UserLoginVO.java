package hk.ljx.fishpicsbackend.vo.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 用户登录 VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserLoginVO implements Serializable {

    /**
     * 用户ID
     */
    private Long id;

    /**
     * 账号（登录用）
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
     * 用户的权限
     */
    private String role;

    /**
     * 昵称（展示用）
     */
    private String nickname;

    /**
     * token
     */
    private String token;
}
