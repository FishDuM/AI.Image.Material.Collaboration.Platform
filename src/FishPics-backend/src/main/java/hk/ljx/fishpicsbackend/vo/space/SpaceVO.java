package hk.ljx.fishpicsbackend.vo.space;

import hk.ljx.fishpicsbackend.entity.Space;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SpaceVO extends Space {
    private String userName;
    private String userAvatar;
    private Long pictureCount;
    private List<SpaceMemberVO> teamMembers;

    public SpaceVO(Space space) {
        this.setId(space.getId());
        this.setName(space.getName());
        this.setIntroduction(space.getIntroduction());
        this.setType(space.getType());
        this.setTeamUsersId(space.getTeamUsersId());
        this.setUserId(space.getUserId());
        this.setStorageSize(space.getStorageSize());
        this.setLevel(space.getLevel());
        this.setSize(space.getSize());
    }
}
