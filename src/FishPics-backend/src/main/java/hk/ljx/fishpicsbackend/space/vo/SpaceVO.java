package hk.ljx.fishpicsbackend.space.vo;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class SpaceVO {
    private Long id;
    private String name;
    private String introduction;
    private Integer type;
    private String teamUsersId;
    private Long userId;
    private Long storageSize;
    private Integer level;
    private Long size;
    private Integer status;
    private String userName;
    private String userAvatar;
    private Long pictureCount;
    private List<SpaceMemberVO> teamMembers;
}
