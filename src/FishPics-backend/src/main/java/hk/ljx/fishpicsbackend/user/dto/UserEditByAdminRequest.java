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
    private Long id;

    private String username;

    private String password;

    private String email;

    private String phone;

    private String nickname;

    private List<Long> roleIds;

    /** 用户等级：0-普通，1-VIP，2-SVIP */
    private Integer level;

    /** 用户角色：0-普通，1-管理员 */
    private Integer role;
}
