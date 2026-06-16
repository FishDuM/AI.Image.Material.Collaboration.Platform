package hk.ljx.fishpicsbackend.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserRegisterRequest {

    @NotBlank(message = "账号不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    @NotBlank(message = "确认密码不能为空")
    private String checkPassword;

    /**
     * 验证码
     */
    @NotBlank(message = "验证码不能为空")
    private String checkCode;

    @NotBlank(message = "验证码key不能为空")
    private String captchaKey;
}
