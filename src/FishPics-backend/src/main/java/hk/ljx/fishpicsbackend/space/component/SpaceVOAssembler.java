package hk.ljx.fishpicsbackend.space.component;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import hk.ljx.fishpicsbackend.mapper.PictureMapper;
import hk.ljx.fishpicsbackend.picture.entity.Picture;
import hk.ljx.fishpicsbackend.space.entity.Space;
import hk.ljx.fishpicsbackend.space.entity.SpaceTeamMember;
import hk.ljx.fishpicsbackend.space.enums.TeamMemberRole;
import hk.ljx.fishpicsbackend.space.vo.SpaceMemberVO;
import hk.ljx.fishpicsbackend.space.vo.SpaceVO;
import hk.ljx.fishpicsbackend.user.entity.User;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class SpaceVOAssembler {

    public static final int MAX_TEAM_MEMBER_DISPLAY = 10;

    @Resource
    private PictureMapper pictureMapper;

    public Map<Long, Long> pictureCountMap(List<Long> spaceIds) {
        if (spaceIds == null || spaceIds.isEmpty()) {
            return Collections.emptyMap();
        }
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("space_id", "COUNT(*) as cnt")
                .in("space_id", spaceIds)
                .groupBy("space_id");
        List<Map<String, Object>> rows = pictureMapper.selectMaps(queryWrapper);
        return rows.stream()
                .collect(Collectors.toMap(
                        row -> ((Number) row.get("space_id")).longValue(),
                        row -> ((Number) row.get("cnt")).longValue()));
    }

    public SpaceVO build(Space space, Map<Long, User> userMap,
                         Map<Long, Long> pictureCountMap,
                         List<SpaceTeamMember> teamMembers,
                         int maxMembers) {
        SpaceVO vo = new SpaceVO();
        BeanUtil.copyProperties(space, vo);
        vo.setPictureCount(pictureCountMap.getOrDefault(space.getId(), 0L));
        User creator = userMap.get(space.getUserId());
        if (creator != null) {
            vo.setUserName(creator.getNickname());
            vo.setUserAvatar(creator.getAvatar());
        }
        if (teamMembers != null && !teamMembers.isEmpty()) {
            vo.setTeamMembers(buildTeamMembers(teamMembers, userMap, maxMembers));
        }
        return vo;
    }

    public List<SpaceMemberVO> buildTeamMembers(List<SpaceTeamMember> teamMembers,
                                                Map<Long, User> userMap,
                                                int maxMembers) {
        Map<Long, Long> userIdRoleIdMap = teamMembers.stream()
                .collect(Collectors.toMap(SpaceTeamMember::getUserId,
                        member -> member.getRoleId() != null ? member.getRoleId().longValue() : 0L,
                        (a, b) -> a));
        Stream<SpaceTeamMember> stream = maxMembers > 0 ? teamMembers.stream().limit(maxMembers) : teamMembers.stream();
        return stream
                .map(SpaceTeamMember::getUserId)
                .map(userMap::get)
                .filter(Objects::nonNull)
                .map(user -> {
                    Long roleId = userIdRoleIdMap.get(user.getId());
                    return new SpaceMemberVO(user.getId(), user.getNickname(), user.getAvatar(),
                            roleId, roleId != null ? TeamMemberRole.nameOf(roleId.intValue()) : "");
                })
                .collect(Collectors.toList());
    }
}
