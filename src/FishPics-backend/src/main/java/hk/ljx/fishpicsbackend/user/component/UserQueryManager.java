package hk.ljx.fishpicsbackend.user.component;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import hk.ljx.fishpicsbackend.common.context.LoginContext;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.utils.LoginContextHelper;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import hk.ljx.fishpicsbackend.mapper.SpaceTeamMemberMapper;
import hk.ljx.fishpicsbackend.mapper.UserMapper;
import hk.ljx.fishpicsbackend.space.entity.SpaceTeamMember;
import hk.ljx.fishpicsbackend.user.dto.UserQueryWrapper;
import hk.ljx.fishpicsbackend.user.entity.User;
import hk.ljx.fishpicsbackend.user.vo.UserVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class UserQueryManager {

    @Resource
    private UserMapper userMapper;

    @Resource
    private SpaceTeamMemberMapper spaceTeamMemberMapper;

    public IPage<UserVO> getUserList(UserQueryWrapper wrapper, long current, long pageSize) {
        ExcUtils.throwIfTrue(current <= 0 || pageSize <= 0, ExceptionCode.PARAMETER_ERROR);
        IPage<User> userPage = userMapper.selectPage(new Page<>(current, pageSize), buildQueryWrapper(wrapper));
        return userPage.convert(this::toAdminVO);
    }

    public Boolean isMe(Long id) {
        User user = LoginContextHelper.requireUser();
        return user.getId().equals(id);
    }

    public UserVO getMyselfMessage() {
        User user = LoginContextHelper.requireUser();
        User fresh = userMapper.selectById(user.getId());
        ExcUtils.throwIfTrue(fresh == null, ExceptionCode.NOT_FOUND, "用户不存在");
        return UserVO.ofInfo(fresh.getId(), fresh.getUsername(), fresh.getNickname(), fresh.getAvatar(),
                fresh.getEmail(), fresh.getPhone(), fresh.getLevel(), fresh.getRole(), null,
                fresh.getCreateTime());
    }

    public UserVO getUserProfile(Long userId) {
        User currentUser = LoginContextHelper.requireUser();
        ExcUtils.throwIfTrue(userId == null, ExceptionCode.PARAMETER_ERROR);

        User targetUser = userMapper.selectById(userId);
        ExcUtils.throwIfTrue(targetUser == null || targetUser.getId() == null, ExceptionCode.NOT_FOUND, "用户不存在");
        ExcUtils.throwIfTrue(!canViewProfile(currentUser.getId(), targetUser.getId()),
                ExceptionCode.FORBIDDEN, "无权查看该用户资料");

        return UserVO.ofPublicProfile(
                targetUser.getId(),
                targetUser.getUsername(),
                targetUser.getNickname(),
                targetUser.getAvatar(),
                targetUser.getLevel(),
                targetUser.getCreateTime()
        );
    }

    public List<UserVO> searchUsers(String keyword) {
        if (StrUtil.isBlank(keyword)) {
            return new ArrayList<>();
        }
        String escaped = keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.and(wrapper -> wrapper.like(User::getUsername, escaped).or().like(User::getNickname, escaped));
        queryWrapper.eq(User::getStatus, 1);
        queryWrapper.last("LIMIT 20");
        return userMapper.selectList(queryWrapper).stream()
                .map(user -> UserVO.ofSearch(user.getId(), user.getNickname(), user.getAvatar()))
                .collect(Collectors.toList());
    }

    public UserVO adminGetUser(Long userId) {
        User user = userMapper.selectById(userId);
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(user) || user.getId() == null, ExceptionCode.NOT_FOUND, "用户不存在");
        return toAdminVO(user);
    }

    public UserVO adminGetUserDetail(Long userId) {
        User user = userMapper.selectById(userId);
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(user) || user.getId() == null, ExceptionCode.NOT_FOUND, "用户不存在");
        return UserVO.ofAdmin(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getAvatar(),
                user.getEmail(),
                user.getPhone(),
                user.getStatus(),
                user.getLevel(),
                user.getRole(),
                user.getCreateTime(),
                null,
                false
        );
    }

    public UserVO getCurrentUserVO() {
        LoginContext ctx = LoginContextHelper.requireLoginContext();
        List<String> allPerms = new ArrayList<>(
                ctx.getSystemPerms() != null ? ctx.getSystemPerms() : List.of());
        if (ctx.getVipPerms() != null) {
            allPerms.addAll(ctx.getVipPerms());
        }
        return UserVO.builder()
                .id(ctx.getUserId())
                .username(ctx.getUsername())
                .nickname(ctx.getNickname())
                .avatar(ctx.getAvatar())
                .level(ctx.getLevel())
                .roleId(ctx.getRole())
                .permissions(allPerms)
                .build();
    }

    public QueryWrapper<User> buildQueryWrapper(UserQueryWrapper userQueryWrapper) {
        Long id = userQueryWrapper.getId();
        String username = userQueryWrapper.getUsername();
        String email = userQueryWrapper.getEmail();
        String phone = userQueryWrapper.getPhone();
        String nickname = userQueryWrapper.getNickname();
        Integer status = userQueryWrapper.getStatus();
        LocalDateTime createTime = userQueryWrapper.getCreateTime();
        String sortField = userQueryWrapper.getSortField();
        String sortOrder = userQueryWrapper.getSortOrder();

        Set<String> allowedSortFields = Set.of(
                "id", "username", "email", "phone", "nickname", "status", "level", "create_time", "update_time"
        );
        boolean validSortField = sortField != null && allowedSortFields.contains(sortField);

        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        if (ObjectUtil.isNotNull(id)) {
            queryWrapper.eq("id", id);
        }
        if (ObjectUtil.isNotNull(username)) {
            queryWrapper.like("username", username);
        }
        if (ObjectUtil.isNotNull(email)) {
            queryWrapper.like("email", email);
        }
        if (ObjectUtil.isNotNull(phone)) {
            queryWrapper.like("phone", phone);
        }
        if (ObjectUtil.isNotNull(nickname)) {
            queryWrapper.like("nickname", nickname);
        }
        if (ObjectUtil.isNotNull(status)) {
            queryWrapper.eq("status", status);
        }
        if (ObjectUtil.isNotNull(createTime)) {
            queryWrapper.eq("create_time", createTime);
        }
        queryWrapper.orderBy(validSortField, "ascend".equals(sortOrder), sortField);
        return queryWrapper;
    }

    private boolean canViewProfile(Long currentUserId, Long targetUserId) {
        if (Objects.equals(currentUserId, targetUserId)) {
            return true;
        }
        LoginContext ctx = UserHolder.getLoginContext();
        if (ctx != null && ctx.hasSystemPerm("system:user:manage")) {
            return true;
        }
        return sharesTeam(currentUserId, targetUserId);
    }

    private boolean sharesTeam(Long currentUserId, Long targetUserId) {
        List<SpaceTeamMember> memberships = spaceTeamMemberMapper.selectList(
                new LambdaQueryWrapper<SpaceTeamMember>()
                        .in(SpaceTeamMember::getUserId, List.of(currentUserId, targetUserId))
                        .select(SpaceTeamMember::getSpaceId, SpaceTeamMember::getUserId));
        Set<Long> currentSpaceIds = memberships.stream()
                .filter(member -> Objects.equals(member.getUserId(), currentUserId))
                .map(SpaceTeamMember::getSpaceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return memberships.stream()
                .anyMatch(member -> Objects.equals(member.getUserId(), targetUserId)
                        && currentSpaceIds.contains(member.getSpaceId()));
    }

    private UserVO toAdminVO(User user) {
        return UserVO.ofAdmin(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getAvatar(),
                user.getEmail(),
                user.getPhone(),
                user.getStatus(),
                user.getLevel(),
                user.getRole(),
                user.getCreateTime(),
                null
        );
    }
}
