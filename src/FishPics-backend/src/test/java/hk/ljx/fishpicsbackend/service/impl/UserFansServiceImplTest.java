package hk.ljx.fishpicsbackend.service.impl;
import hk.ljx.fishpicsbackend.user.UserFansServiceImpl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import hk.ljx.fishpicsbackend.user.User;
import hk.ljx.fishpicsbackend.user.UserFans;
import hk.ljx.fishpicsbackend.mapper.UserFansMapper;
import hk.ljx.fishpicsbackend.mapper.UserMapper;
import hk.ljx.fishpicsbackend.user.vo.FollowUserVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserFansServiceImplTest {

    @Mock private UserFansMapper userFansMapper;
    @Mock private UserMapper userMapper;

    @InjectMocks
    private UserFansServiceImpl userFansService;

    private MockedStatic<UserHolder> userHolderMock;
    private User currentUser;
    private User targetUser;

    @BeforeEach
    void setUp() {
        userHolderMock = mockStatic(UserHolder.class);
        currentUser = new User();
        currentUser.setId(1L);
        currentUser.setUsername("userA");

        targetUser = new User();
        targetUser.setId(2L);
        targetUser.setUsername("userB");

        ReflectionTestUtils.setField(userFansService, "baseMapper", userFansMapper);
    }

    @AfterEach
    void tearDown() {
        userHolderMock.close();
    }

    @Nested
    class FollowTests {

        @Test
        void shouldFollowSuccessfully() {
            userHolderMock.when(UserHolder::getUser).thenReturn(currentUser);
            when(userMapper.selectById(2L)).thenReturn(targetUser);
            when(userFansMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);
            doReturn(1).when(userFansMapper).insert(any(UserFans.class));

            boolean result = userFansService.follow(2L);

            assertTrue(result);
            verify(userFansMapper).insert(any(UserFans.class));
        }

        @Test
        void shouldUnfollowWhenAlreadyFollowing() {
            UserFans existing = new UserFans();
            existing.setId(100L);
            existing.setUserId(2L);
            existing.setFanId(1L);

            userHolderMock.when(UserHolder::getUser).thenReturn(currentUser);
            when(userMapper.selectById(2L)).thenReturn(targetUser);
            when(userFansMapper.selectOne(any(QueryWrapper.class))).thenReturn(existing);
            when(userFansMapper.deleteById(100L)).thenReturn(1);

            boolean result = userFansService.follow(2L);

            assertFalse(result);
            verify(userFansMapper).deleteById(100L);
        }

        @Test
        void shouldRejectSelfFollow() {
            userHolderMock.when(UserHolder::getUser).thenReturn(currentUser);

            assertThrows(BaseException.class, () -> userFansService.follow(1L));
        }

        @Test
        void shouldRejectWhenTargetNotFound() {
            userHolderMock.when(UserHolder::getUser).thenReturn(currentUser);
            when(userMapper.selectById(99L)).thenReturn(null);

            assertThrows(BaseException.class, () -> userFansService.follow(99L));
        }
    }

    @Nested
    class GetFansTests {

        @Test
        @SuppressWarnings("unchecked")
        void shouldReturnFansWhenPublic() {
            targetUser.setIsPrivateFans(0);
            userHolderMock.when(UserHolder::getUser).thenReturn(currentUser);
            when(userMapper.selectById(2L)).thenReturn(targetUser);
            when(userFansMapper.selectFansPage(any(Page.class), eq(2L)))
                    .thenReturn(new Page<FollowUserVO>(1, 20).setRecords(new ArrayList<>()));

            IPage<FollowUserVO> result = userFansService.getFans(2L, 1, 20);

            assertNotNull(result);
        }

        @Test
        void shouldRejectWhenFansArePrivateAndNotSelf() {
            targetUser.setIsPrivateFans(1);
            userHolderMock.when(UserHolder::getUser).thenReturn(currentUser);
            when(userMapper.selectById(2L)).thenReturn(targetUser);

            assertThrows(BaseException.class, () -> userFansService.getFans(2L, 1, 20));
        }

        @Test
        @SuppressWarnings("unchecked")
        void shouldAllowWhenFansArePrivateButIsSelf() {
            targetUser.setIsPrivateFans(1);
            targetUser.setId(1L);
            userHolderMock.when(UserHolder::getUser).thenReturn(currentUser);
            when(userMapper.selectById(1L)).thenReturn(targetUser);
            when(userFansMapper.selectFansPage(any(Page.class), eq(1L)))
                    .thenReturn(new Page<FollowUserVO>(1, 20).setRecords(new ArrayList<>()));

            IPage<FollowUserVO> result = userFansService.getFans(1L, 1, 20);

            assertNotNull(result);
        }
    }

    @Nested
    class GetFollowsTests {

        @Test
        @SuppressWarnings("unchecked")
        void shouldReturnFollowsWhenPublic() {
            targetUser.setIsPrivateFollows(0);
            userHolderMock.when(UserHolder::getUser).thenReturn(currentUser);
            when(userMapper.selectById(2L)).thenReturn(targetUser);
            when(userFansMapper.selectFollowsPage(any(Page.class), eq(2L)))
                    .thenReturn(new Page<FollowUserVO>(1, 20).setRecords(new ArrayList<>()));

            IPage<FollowUserVO> result = userFansService.getFollows(2L, 1, 20);

            assertNotNull(result);
        }

        @Test
        void shouldRejectWhenFollowsArePrivateAndNotSelf() {
            targetUser.setIsPrivateFollows(1);
            userHolderMock.when(UserHolder::getUser).thenReturn(currentUser);
            when(userMapper.selectById(2L)).thenReturn(targetUser);

            assertThrows(BaseException.class, () -> userFansService.getFollows(2L, 1, 20));
        }
    }
}
