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
public class UserLoginRequest implements Serializable {

    private String username;

    private String password;

    private String checkCode;

    private String captchaKey;
}
