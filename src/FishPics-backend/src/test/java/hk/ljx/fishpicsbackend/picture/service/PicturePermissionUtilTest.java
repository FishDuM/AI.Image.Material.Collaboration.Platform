package hk.ljx.fishpicsbackend.picture.service;

import hk.ljx.fishpicsbackend.common.context.LoginContext;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import hk.ljx.fishpicsbackend.picture.entity.Picture;
import hk.ljx.fishpicsbackend.space.entity.SpaceTeamMember;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * PicturePermissionUtil 单元测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PicturePermissionUtilTest {

    @Mock
    private BaseMapper<SpaceTeamMember> teamMemberMapper;

    @AfterEach
    void cleanup() {
        UserHolder.removeLoginContext();
    }

    private void setLoginUser(Long userId) {
        UserHolder.setLoginContext(LoginContext.builder()
                .userId(userId)
                .username("testuser")
                .status(1)
                .level(0)
                .role(0)
                .isAdmin(false)
                .build());
    }

    private void setLoginAdmin(Long userId) {
        UserHolder.setLoginContext(LoginContext.builder()
                .userId(userId)
                .username("admin")
                .status(1)
                .level(0)
                .role(1)
                .isAdmin(true)
                .build());
    }

    private Picture makePicture(Long id, Long userId, Long spaceId) {
        Picture p = new Picture();
        p.setId(id);
        p.setUserId(userId);
        p.setSpaceId(spaceId);
        return p;
    }

    // ==================== checkWrite ====================

    @Test
    @DisplayName("checkWrite - 未登录应抛异常")
    void checkWrite_notLoggedIn_throws() {
        Picture pic = makePicture(1L, 1L, null);
        assertThrows(BaseException.class,
                () -> PicturePermissionUtil.checkWrite(pic, "编辑", teamMemberMapper));
    }

    @Test
    @DisplayName("checkWrite - 管理员应直接通过")
    void checkWrite_admin_passes() {
        setLoginAdmin(999L);
        Picture pic = makePicture(1L, 1L, null);
        assertDoesNotThrow(
                () -> PicturePermissionUtil.checkWrite(pic, "编辑", teamMemberMapper));
        verify(teamMemberMapper, never()).selectOne(any());
    }

    @Test
    @DisplayName("checkWrite - 图片所有者应通过")
    void checkWrite_owner_passes() {
        setLoginUser(1L);
        Picture pic = makePicture(1L, 1L, null);
        assertDoesNotThrow(
                () -> PicturePermissionUtil.checkWrite(pic, "编辑", teamMemberMapper));
    }

    @Test
    @DisplayName("checkWrite - 非所有者且无团队关系应抛异常")
    void checkWrite_notOwner_noTeam_throws() {
        setLoginUser(2L);
        Picture pic = makePicture(1L, 1L, 100L);
        when(teamMemberMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThrows(BaseException.class,
                () -> PicturePermissionUtil.checkWrite(pic, "编辑", teamMemberMapper));
    }

    @Test
    @DisplayName("checkWrite - 团队成员 (roleId=2) 应有写权限")
    void checkWrite_teamMember_passes() {
        setLoginUser(2L);
        Picture pic = makePicture(1L, 1L, 100L);

        SpaceTeamMember member = new SpaceTeamMember();
        member.setRoleId(2);
        member.setUserId(2L);
        member.setSpaceId(100L);
        when(teamMemberMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(member);

        assertDoesNotThrow(
                () -> PicturePermissionUtil.checkWrite(pic, "编辑", teamMemberMapper));
    }

    @Test
    @DisplayName("checkWrite - 团队成员 roleId=4 (只读) 无写权限")
    void checkWrite_readOnlyMember_throws() {
        setLoginUser(2L);
        Picture pic = makePicture(1L, 1L, 100L);

        SpaceTeamMember member = new SpaceTeamMember();
        member.setRoleId(4);
        member.setUserId(2L);
        member.setSpaceId(100L);
        when(teamMemberMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(member);

        assertThrows(BaseException.class,
                () -> PicturePermissionUtil.checkWrite(pic, "编辑", teamMemberMapper));
    }

    @Test
    @DisplayName("checkWrite ownerOnly - 团队成员 roleId=2 应被拒绝")
    void checkWrite_ownerOnly_teamMember_throws() {
        setLoginUser(2L);
        Picture pic = makePicture(1L, 1L, 100L);

        SpaceTeamMember member = new SpaceTeamMember();
        member.setRoleId(2);
        member.setUserId(2L);
        member.setSpaceId(100L);
        when(teamMemberMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(member);

        assertThrows(BaseException.class,
                () -> PicturePermissionUtil.checkWrite(pic, "删除", true, teamMemberMapper));
    }

    @Test
    @DisplayName("checkWrite ownerOnly - 空间所有者 (roleId=1) 应通过")
    void checkWrite_ownerOnly_spaceOwner_passes() {
        setLoginUser(2L);
        Picture pic = makePicture(1L, 1L, 100L);

        SpaceTeamMember member = new SpaceTeamMember();
        member.setRoleId(1);
        member.setUserId(2L);
        member.setSpaceId(100L);
        when(teamMemberMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(member);

        assertDoesNotThrow(
                () -> PicturePermissionUtil.checkWrite(pic, "删除", true, teamMemberMapper));
    }

    // ==================== filterDeletableIds ====================

    @Test
    @DisplayName("filterDeletableIds - 管理员应返回所有 ID")
    void filterDeletableIds_admin_returnsAll() {
        setLoginAdmin(999L);
        var pics = java.util.List.of(makePicture(1L, 1L, null), makePicture(2L, 2L, null));
        var requested = java.util.List.of(1L, 2L);

        var result = PicturePermissionUtil.filterDeletableIds(pics, requested, teamMemberMapper);
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("filterDeletableIds - 普通用户只能删除自己的图片")
    void filterDeletableIds_normalUser_ownOnly() {
        setLoginUser(1L);
        var pics = java.util.List.of(makePicture(1L, 1L, null), makePicture(2L, 2L, null));
        var requested = java.util.List.of(1L, 2L);

        when(teamMemberMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        var result = PicturePermissionUtil.filterDeletableIds(pics, requested, teamMemberMapper);
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0));
    }
}
