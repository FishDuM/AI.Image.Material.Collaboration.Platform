package hk.ljx.fishpicsbackend.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.response.Response;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import hk.ljx.fishpicsbackend.dto.space.CreateSpace;
import hk.ljx.fishpicsbackend.dto.user.*;
import hk.ljx.fishpicsbackend.entity.User;
import hk.ljx.fishpicsbackend.mapper.PostMapper;
import hk.ljx.fishpicsbackend.mapper.UserFansMapper;
import hk.ljx.fishpicsbackend.mapper.UserMapper;
import hk.ljx.fishpicsbackend.mapper.UserPostCollectMapper;
import hk.ljx.fishpicsbackend.mapper.UserPostLikesMapper;
import hk.ljx.fishpicsbackend.service.SpaceService;
import hk.ljx.fishpicsbackend.vo.user.UserLoginVO;
import hk.ljx.fishpicsbackend.vo.user.UserMessageVO;
import hk.ljx.fishpicsbackend.vo.user.UserPublicProfileVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import static hk.ljx.fishpicsbackend.common.constants.UserConstants.SALT;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock private UserMapper userMapper;
    @Mock private PostMapper postMapper;
    @Mock private UserPostCollectMapper userPostCollectMapper;
    @Mock private UserPostLikesMapper userPostLikesMapper;
    @Mock private UserFansMapper userFansMapper;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private SpaceService spaceService;

    @InjectMocks
    private UserServiceImpl userService;

    private MockedStatic<UserHolder> userHolderMock;
    private User testUser;

    @BeforeEach
    void setUp() {
        userHolderMock = mockStatic(UserHolder.class);
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser123");
        testUser.setPassword("hashedpassword");
        testUser.setNickname("测试用户");
        testUser.setRole("user");
        testUser.setStatus(1);
        testUser.setLevel(0);
        testUser.setAvatar("https://example.com/avatar.png");
        testUser.setCreateTime(new Date());

        ReflectionTestUtils.setField(userService, "baseMapper", userMapper);
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @AfterEach
    void tearDown() {
        userHolderMock.close();
    }

    @Nested
    class UserRegisterTests {

        @Test
        void shouldRegisterSuccessfully() {
            UserRequestRequest req = UserRequestRequest.builder()
                    .username("newuser123").password("password123")
                    .checkPassword("password123").checkCode("ABCD").captchaKey("captcha-key-123").build();

            when(valueOperations.get(anyString())).thenReturn("ABCD");
            when(userMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
            when(userMapper.insert(any(User.class))).thenReturn(1);
            when(spaceService.createSpace(any(CreateSpace.class), any(User.class))).thenReturn(true);
            when(stringRedisTemplate.delete(anyString())).thenReturn(true);

            Response<Boolean> result = userService.userRegister(req);

            assertTrue(result.getData());
            verify(userMapper).insert(any(User.class));
            verify(spaceService).createSpace(any(CreateSpace.class), any(User.class));
        }

        @Test
        void shouldRejectWhenCaptchaIsNull() {
            UserRequestRequest req = UserRequestRequest.builder()
                    .username("newuser123").password("password123")
                    .checkPassword("password123").checkCode(null).captchaKey(null).build();

            assertThrows(BaseException.class, () -> userService.userRegister(req));
        }

        @Test
        void shouldRejectWhenUsernameTooShort() {
            UserRequestRequest req = UserRequestRequest.builder()
                    .username("abc").password("password123")
                    .checkPassword("password123").checkCode("A").captchaKey("k").build();

            assertThrows(BaseException.class, () -> userService.userRegister(req));
        }

        @Test
        void shouldRejectWhenUsernameTooLong() {
            UserRequestRequest req = UserRequestRequest.builder()
                    .username("123456789012").password("password123")
                    .checkPassword("password123").checkCode("A").captchaKey("k").build();

            assertThrows(BaseException.class, () -> userService.userRegister(req));
        }

        @Test
        void shouldRejectWhenPasswordTooShort() {
            UserRequestRequest req = UserRequestRequest.builder()
                    .username("newuser123").password("short")
                    .checkPassword("short").checkCode("A").captchaKey("k").build();

            assertThrows(BaseException.class, () -> userService.userRegister(req));
        }

        @Test
        void shouldRejectWhenPasswordsDoNotMatch() {
            UserRequestRequest req = UserRequestRequest.builder()
                    .username("newuser123").password("password123")
                    .checkPassword("different").checkCode("A").captchaKey("k").build();

            assertThrows(BaseException.class, () -> userService.userRegister(req));
        }

        @Test
        void shouldRejectWhenCaptchaWrong() {
            UserRequestRequest req = UserRequestRequest.builder()
                    .username("newuser123").password("password123")
                    .checkPassword("password123").checkCode("WRONG").captchaKey("k").build();

            when(valueOperations.get(anyString())).thenReturn("ABCD");

            assertThrows(BaseException.class, () -> userService.userRegister(req));
        }

        @Test
        void shouldRejectWhenUsernameExists() {
            UserRequestRequest req = UserRequestRequest.builder()
                    .username("existing").password("password123")
                    .checkPassword("password123").checkCode("ABCD").captchaKey("k").build();

            when(valueOperations.get(anyString())).thenReturn("ABCD");
            when(userMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);

            assertThrows(BaseException.class, () -> userService.userRegister(req));
        }

        @Test
        void shouldHashPasswordWithSalt() {
            UserRequestRequest req = UserRequestRequest.builder()
                    .username("newuser123").password("password123")
                    .checkPassword("password123").checkCode("ABCD").captchaKey("k").build();

            when(valueOperations.get(anyString())).thenReturn("ABCD");
            when(userMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
            when(userMapper.insert(any(User.class))).thenReturn(1);
            when(spaceService.createSpace(any(CreateSpace.class), any(User.class))).thenReturn(true);
            when(stringRedisTemplate.delete(anyString())).thenReturn(true);

            userService.userRegister(req);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userMapper).insert(captor.capture());
            String expectedHash = DigestUtil.md5Hex("password123" + SALT);
            assertEquals(expectedHash, captor.getValue().getPassword());
        }
    }

    @Nested
    class UserLoginTests {

        @Test
        void shouldLoginSuccessfully() {
            UserLoginRequest req = UserLoginRequest.builder()
                    .username("testuser").password("password123")
                    .checkCode("ABCD").captchaKey("login-key").build();

            when(valueOperations.get(anyString())).thenReturn("ABCD");
            String expectedPwd = DigestUtil.md5Hex("password123" + SALT);
            User dbUser = new User();
            dbUser.setId(1L); dbUser.setUsername("testuser");
            dbUser.setPassword(expectedPwd); dbUser.setStatus(1);
            dbUser.setNickname("nick"); dbUser.setAvatar("av");
            when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(dbUser);
            doNothing().when(valueOperations).set(anyString(), anyString(), eq(1L), eq(TimeUnit.DAYS));
            when(stringRedisTemplate.delete(anyString())).thenReturn(true);

            Response<UserLoginVO> result = userService.userLogin(req);

            assertEquals(1, result.getCode());
            assertNotNull(result.getData().getToken());
            assertEquals("testuser", result.getData().getUsername());
        }

        @Test
        void shouldRejectWhenParamsAllBlank() {
            UserLoginRequest req = new UserLoginRequest();
            assertThrows(BaseException.class, () -> userService.userLogin(req));
        }

        @Test
        void shouldRejectWhenCaptchaWrong() {
            UserLoginRequest req = UserLoginRequest.builder()
                    .username("test").password("pass").checkCode("WRONG").captchaKey("k").build();

            when(valueOperations.get(anyString())).thenReturn("ABCD");

            assertThrows(BaseException.class, () -> userService.userLogin(req));
        }

        @Test
        void shouldRejectWhenCredentialsWrong() {
            UserLoginRequest req = UserLoginRequest.builder()
                    .username("test").password("wrongpass").checkCode("ABCD").captchaKey("k").build();

            when(valueOperations.get(anyString())).thenReturn("ABCD");
            when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

            assertThrows(BaseException.class, () -> userService.userLogin(req));
        }

        @Test
        void shouldRejectWhenAccountDisabled() {
            UserLoginRequest req = UserLoginRequest.builder()
                    .username("test").password("pass").checkCode("ABCD").captchaKey("k").build();

            when(valueOperations.get(anyString())).thenReturn("ABCD");
            User dbUser = new User();
            dbUser.setPassword(DigestUtil.md5Hex("pass" + SALT));
            dbUser.setStatus(0);
            when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(dbUser);

            assertThrows(BaseException.class, () -> userService.userLogin(req));
        }
    }

    @Nested
    class SetStatusTests {

        @Test
        void shouldToggleStatusFromOneToZero() {
            User u = new User();
            u.setId(1L); u.setStatus(1);
            when(userMapper.selectById(1L)).thenReturn(u);
            when(userMapper.updateById(any(User.class))).thenReturn(1);
            doNothing().when(valueOperations).set(anyString(), anyString());

            Boolean result = userService.setStatus(1L);

            assertTrue(result);
            assertEquals(0, u.getStatus());
        }

        @Test
        void shouldToggleStatusFromZeroToOne() {
            User u = new User();
            u.setId(1L); u.setStatus(0);
            when(userMapper.selectById(1L)).thenReturn(u);
            when(userMapper.updateById(any(User.class))).thenReturn(1);
            doNothing().when(valueOperations).set(anyString(), anyString());

            userService.setStatus(1L);
            assertEquals(1, u.getStatus());
        }

        @Test
        void shouldThrowWhenUserNotFound() {
            when(userMapper.selectById(99L)).thenReturn(null);
            assertThrows(BaseException.class, () -> userService.setStatus(99L));
        }
    }

    @Nested
    class EditMyselfTests {

        @Test
        void shouldEditNicknameSuccessfully() {
            UserEditRequest req = UserEditRequest.builder().id(1L).nickname("新的昵称abc").build();

            userHolderMock.when(UserHolder::getUser).thenReturn(testUser);
            when(userMapper.selectById(1L)).thenReturn(testUser);
            when(userMapper.updateById(any(User.class))).thenReturn(1);
            doNothing().when(valueOperations).set(anyString(), anyString(), eq(1L), eq(TimeUnit.DAYS));

            Boolean result = userService.editMyself(req);

            assertTrue(result);
        }

        @Test
        void shouldRejectWhenEditingOthers() {
            UserEditRequest req = UserEditRequest.builder().id(2L).nickname("test").build();
            userHolderMock.when(UserHolder::getUser).thenReturn(testUser);

            assertThrows(BaseException.class, () -> userService.editMyself(req));
        }

        @Test
        void shouldRejectNicknameTooShort() {
            UserEditRequest req = UserEditRequest.builder().id(1L).nickname("ab").build();
            userHolderMock.when(UserHolder::getUser).thenReturn(testUser);

            assertThrows(BaseException.class, () -> userService.editMyself(req));
        }

        @Test
        void shouldRejectNicknameTooLong() {
            UserEditRequest req = UserEditRequest.builder().id(1L).nickname("1234567890123").build();
            userHolderMock.when(UserHolder::getUser).thenReturn(testUser);

            assertThrows(BaseException.class, () -> userService.editMyself(req));
        }

        @Test
        void shouldRequireOriginalPasswordWhenChangingPassword() {
            UserEditRequest req = UserEditRequest.builder()
                    .id(1L).password("newpass123").originalPassword(null).build();
            userHolderMock.when(UserHolder::getUser).thenReturn(testUser);
            when(userMapper.selectById(1L)).thenReturn(testUser);

            assertThrows(BaseException.class, () -> userService.editMyself(req));
        }

        @Test
        void shouldRejectWrongOriginalPassword() {
            UserEditRequest req = UserEditRequest.builder()
                    .id(1L).password("newpass123").originalPassword("wrongpass").build();
            userHolderMock.when(UserHolder::getUser).thenReturn(testUser);
            testUser.setPassword(DigestUtil.md5Hex("correctpass" + SALT));
            when(userMapper.selectById(1L)).thenReturn(testUser);

            assertThrows(BaseException.class, () -> userService.editMyself(req));
        }

        @Test
        void shouldChangePasswordWithCorrectOriginal() {
            UserEditRequest req = UserEditRequest.builder()
                    .id(1L).password("newpass123").originalPassword("correctpass").build();
            userHolderMock.when(UserHolder::getUser).thenReturn(testUser);
            testUser.setPassword(DigestUtil.md5Hex("correctpass" + SALT));
            when(userMapper.selectById(1L)).thenReturn(testUser);
            when(userMapper.updateById(any(User.class))).thenReturn(1);
            doNothing().when(valueOperations).set(anyString(), anyString(), eq(1L), eq(TimeUnit.DAYS));

            Boolean result = userService.editMyself(req);

            assertTrue(result);
        }
    }

    @Nested
    class UpdatePrivacyTests {

        @Test
        void shouldUpdatePrivacySuccessfully() {
            UserPrivacyRequest req = UserPrivacyRequest.builder()
                    .isPrivateFollows(1).isPrivateFans(1).build();
            userHolderMock.when(UserHolder::getUser).thenReturn(testUser);
            when(userMapper.updateById(any(User.class))).thenReturn(1);
            doNothing().when(valueOperations).set(anyString(), anyString(), eq(1L), eq(TimeUnit.DAYS));

            Boolean result = userService.updatePrivacy(req);

            assertTrue(result);
        }

        @Test
        void shouldThrowWhenNotLoggedIn() {
            userHolderMock.when(UserHolder::getUser).thenReturn(null);
            assertThrows(BaseException.class, () -> userService.updatePrivacy(new UserPrivacyRequest()));
        }
    }

    @Nested
    class IsMeTests {

        @Test
        void shouldReturnTrueForSameUser() {
            userHolderMock.when(UserHolder::getUser).thenReturn(testUser);
            assertTrue(userService.isMe(1L));
        }

        @Test
        void shouldReturnFalseForDifferentUser() {
            userHolderMock.when(UserHolder::getUser).thenReturn(testUser);
            assertFalse(userService.isMe(2L));
        }
    }

    @Nested
    class GetMyselfMessageTests {

        @Test
        void shouldReturnCurrentUserInfo() {
            userHolderMock.when(UserHolder::getUser).thenReturn(testUser);

            UserMessageVO result = userService.getMyselfMessage();

            assertEquals(testUser.getUsername(), result.getUsername());
            assertEquals(testUser.getNickname(), result.getNickname());
        }

        @Test
        void shouldThrowWhenNotLoggedIn() {
            userHolderMock.when(UserHolder::getUser).thenReturn(null);
            assertThrows(BaseException.class, () -> userService.getMyselfMessage());
        }
    }

    @Nested
    class GetUserProfileTests {

        @Test
        void shouldReturnPublicProfile() {
            User targetUser = new User();
            targetUser.setId(2L); targetUser.setUsername("target");
            targetUser.setNickname("目标"); targetUser.setAvatar("av");
            targetUser.setLevel(1); targetUser.setCreateTime(new Date());

            userHolderMock.when(UserHolder::getUser).thenReturn(testUser);
            when(userMapper.selectById(2L)).thenReturn(targetUser);
            when(userFansMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
            when(postMapper.selectCount(any(QueryWrapper.class))).thenReturn(5L);
            when(userPostCollectMapper.selectCount(any(QueryWrapper.class))).thenReturn(3L);
            when(userPostLikesMapper.selectCount(any(QueryWrapper.class))).thenReturn(10L);

            UserPublicProfileVO result = userService.getUserProfile(2L);

            assertEquals("target", result.getUsername());
            assertEquals("目标", result.getNickname());
            assertEquals(5L, result.getPostCount());
            assertEquals(3L, result.getCollectCount());
        }

        @Test
        void shouldThrowWhenTargetNotFound() {
            userHolderMock.when(UserHolder::getUser).thenReturn(testUser);
            when(userMapper.selectById(99L)).thenReturn(null);

            assertThrows(BaseException.class, () -> userService.getUserProfile(99L));
        }

        @Test
        void shouldHidePrivateDataWhenNotSelf() {
            User targetUser = new User();
            targetUser.setId(2L); targetUser.setUsername("target");
            targetUser.setNickname("目标"); targetUser.setIsPrivatePostCollect(1);
            targetUser.setIsPrivateLikes(1); targetUser.setIsPrivateFollows(1);
            targetUser.setIsPrivateFans(1);
            targetUser.setLevel(0); targetUser.setCreateTime(new Date());

            userHolderMock.when(UserHolder::getUser).thenReturn(testUser);
            when(userMapper.selectById(2L)).thenReturn(targetUser);
            when(userFansMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
            when(postMapper.selectCount(any(QueryWrapper.class))).thenReturn(5L);

            UserPublicProfileVO result = userService.getUserProfile(2L);

            assertNull(result.getCollectCount());
            assertNull(result.getLikeCount());
            assertNull(result.getFollowsCount());
            assertNull(result.getFansCount());
        }
    }
}
