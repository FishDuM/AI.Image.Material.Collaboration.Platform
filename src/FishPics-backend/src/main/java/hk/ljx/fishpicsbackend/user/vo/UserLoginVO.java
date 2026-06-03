package hk.ljx.fishpicsbackend.user.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 用户登录 VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserLoginVO implements Serializable {

    // 用户ID
    private Long id;

    // 账号（登录用）
    private String username;

    // 头像URL
    private String avatar;

    // 邮箱
    private String email;

    // 手机号
    private String phone;

    // 用户等级 0-普通 1-VIP 2-SVIP
    private Integer level;

    // 昵称（展示用）
    private String nickname;

    // token
    private String token;

    // 拥有的权限码列表（前端用于控制菜单/按钮显示）
    private List<String> permissions;
}
