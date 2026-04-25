package hk.ljx.fishpicsbackend.vo.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckCodeVO implements Serializable {
    private String captchaKey;
    private String base64Image;
}
