package hk.ljx.fishpicsbackend.user.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserSearchVO {
    private Long id;
    private String nickname;
    private String avatar;
}
