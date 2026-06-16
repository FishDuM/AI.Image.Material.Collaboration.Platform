package hk.ljx.fishpicsbackend.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserRegisterRequest implements Serializable {

    private String username;

    private String password;

    private String checkPassword;

    /**
     * 验证码
     */
    private String checkCode;

    private String captchaKey;
}
