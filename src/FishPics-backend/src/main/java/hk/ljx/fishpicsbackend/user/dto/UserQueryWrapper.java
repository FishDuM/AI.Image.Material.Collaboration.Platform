package hk.ljx.fishpicsbackend.user.dto;

import hk.ljx.fishpicsbackend.common.dto.PageRequest;
import lombok.*;

import java.io.Serializable;
import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true)
public class UserQueryWrapper extends PageRequest implements Serializable {

    /**
     * 用户ID
     */
    private Long id;

    /**
     * 账号（登录用）
     */
    private String username;

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
     * 创建时间
     */
    private Date createTime;

}
