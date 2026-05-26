package hk.ljx.fishpicsbackend.space.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SpaceMemberVO {
    private Long id;
    private String nickname;
    private String avatar;
}
