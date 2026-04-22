package hk.ljx.fishpicsbackend.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserRequestRequest implements Serializable {

    private String username;

    private String password;

    private String checkPassword;

    /**
     * 验证码
     */
    private String checkCode;
}
