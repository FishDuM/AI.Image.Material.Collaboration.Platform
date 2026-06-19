package hk.ljx.fishpicsbackend.user.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEditRequest {
    @NotNull(message = "用户ID不能为空")
    private Long id;

    private String username;

    private String password;

    /** 如果修改密码要先输入原始密码 */
    private String originalPassword;

    private String email;

    private String phone;

    private String nickname;
}
