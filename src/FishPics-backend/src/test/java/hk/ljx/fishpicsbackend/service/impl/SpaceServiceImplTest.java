package hk.ljx.fishpicsbackend.service.impl;
import hk.ljx.fishpicsbackend.space.SpaceServiceImpl;

import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import hk.ljx.fishpicsbackend.space.dto.CreateSpace;
import hk.ljx.fishpicsbackend.space.dto.UpdateSpace;
import hk.ljx.fishpicsbackend.space.Space;
import hk.ljx.fishpicsbackend.user.User;
import hk.ljx.fishpicsbackend.mapper.SpaceMapper;
import hk.ljx.fishpicsbackend.mapper.UserMapper;
import hk.ljx.fishpicsbackend.picture.PictureService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static hk.ljx.fishpicsbackend.common.constants.SpaceConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SpaceServiceImplTest {

    @Mock private SpaceMapper spaceMapper;
    @Mock private PictureService pictureService;
    @Mock private UserMapper userMapper;

    @InjectMocks
    private SpaceServiceImpl spaceService;

    private MockedStatic<UserHolder> userHolderMock;
    private User testUser;

    @BeforeEach
    void setUp() {
        userHolderMock = mockStatic(UserHolder.class);
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setRole("user");
        testUser.setLevel(0);

        ReflectionTestUtils.setField(spaceService, "baseMapper", spaceMapper);
    }

    @AfterEach
    void tearDown() {
        userHolderMock.close();
    }

    @Nested
    class CreateSpaceTests {

        @Test
        void shouldCreatePersonalSpaceForLevel0() {
            CreateSpace req = new CreateSpace("我的空间", "介绍", 0);
            when(spaceMapper.selectList(any())).thenReturn(Collections.emptyList());
            doReturn(1).when(spaceMapper).insert(ArgumentMatchers.<Space>any());

            Boolean result = spaceService.createSpace(req, testUser);

            assertTrue(result);
            ArgumentCaptor<Space> captor = ArgumentCaptor.forClass(Space.class);
            verify(spaceMapper).insert(captor.capture());
            assertEquals(DEFAULT_STORAGE_SIZE, captor.getValue().getStorageSize());
            assertEquals(0, captor.getValue().getLevel());
        }

        @Test
        void shouldCreatePersonalSpaceForLevel1() {
            testUser.setLevel(1);
            CreateSpace req = new CreateSpace("VIP空间", "介绍", 0);
            when(spaceMapper.selectList(any())).thenReturn(Collections.emptyList());
            doReturn(1).when(spaceMapper).insert(ArgumentMatchers.<Space>any());

            spaceService.createSpace(req, testUser);

            ArgumentCaptor<Space> captor = ArgumentCaptor.forClass(Space.class);
            verify(spaceMapper).insert(captor.capture());
            assertEquals(VIP_STORAGE_SIZE, captor.getValue().getStorageSize());
        }

        @Test
        void shouldCreatePersonalSpaceForLevel2() {
            testUser.setLevel(2);
            CreateSpace req = new CreateSpace("SVIP空间", "介绍", 0);
            when(spaceMapper.selectList(any())).thenReturn(Collections.emptyList());
            doReturn(1).when(spaceMapper).insert(ArgumentMatchers.<Space>any());

            spaceService.createSpace(req, testUser);

            ArgumentCaptor<Space> captor = ArgumentCaptor.forClass(Space.class);
            verify(spaceMapper).insert(captor.capture());
            assertEquals(SVIP_STORAGE_SIZE, captor.getValue().getStorageSize());
        }

        @Test
        void shouldRejectDuplicatePersonalSpace() {
            CreateSpace req = new CreateSpace("空间", "介绍", 0);
            when(spaceMapper.selectList(any())).thenReturn(Collections.singletonList(new Space()));

            assertThrows(BaseException.class, () -> spaceService.createSpace(req, testUser));
        }

        @Test
        void shouldRejectWhenTeamSpaceLimitReachedForLevel0() {
            testUser.setLevel(0);
            CreateSpace req = new CreateSpace("团队空间", "介绍", 1);
            when(spaceMapper.selectList(any())).thenReturn(Collections.singletonList(new Space()));

            assertThrows(BaseException.class, () -> spaceService.createSpace(req, testUser));
        }

        @Test
        void shouldAllowTeamSpaceWhenWithinLimit() {
            testUser.setLevel(1);
            CreateSpace req = new CreateSpace("团队空间", "介绍", 1);
            when(spaceMapper.selectList(any())).thenReturn(Collections.singletonList(new Space()));
            doReturn(1).when(spaceMapper).insert(ArgumentMatchers.<Space>any());

            Boolean result = spaceService.createSpace(req, testUser);

            assertTrue(result);
        }

        @Test
        void shouldSetTeamUsersOnTeamSpace() {
            testUser.setLevel(0);
            CreateSpace req = new CreateSpace("团队空间", "介绍", 1);
            when(spaceMapper.selectList(any())).thenReturn(Collections.emptyList());
            doReturn(1).when(spaceMapper).insert(ArgumentMatchers.<Space>any());

            spaceService.createSpace(req, testUser);

            ArgumentCaptor<Space> captor = ArgumentCaptor.forClass(Space.class);
            verify(spaceMapper).insert(captor.capture());
            assertNotNull(captor.getValue().getTeamUsersId());
        }

        @Test
        void shouldRejectWhenNameIsNull() {
            CreateSpace req = new CreateSpace(null, "介绍", 0);
            assertThrows(BaseException.class, () -> spaceService.createSpace(req, testUser));
        }

        @Test
        void shouldUseTeamStorageQuota() {
            testUser.setLevel(1);
            CreateSpace req = new CreateSpace("团队", "", 1);
            when(spaceMapper.selectList(any())).thenReturn(Collections.emptyList());
            doReturn(1).when(spaceMapper).insert(ArgumentMatchers.<Space>any());

            spaceService.createSpace(req, testUser);

            ArgumentCaptor<Space> captor = ArgumentCaptor.forClass(Space.class);
            verify(spaceMapper).insert(captor.capture());
            assertEquals(TEAM_VIP_STORAGE_SIZE, captor.getValue().getStorageSize());
        }
    }

    @Nested
    class UpdateSpaceTests {

        @Test
        void shouldUpdateWhenCreator() {
            Space space = new Space();
            space.setId(1L);
            space.setUserId(1L);
            space.setName("旧名称");

            UpdateSpace req = new UpdateSpace();
            req.setId(1L);
            req.setName("新名称");
            req.setIntroduction("新介绍");

            userHolderMock.when(UserHolder::getUser).thenReturn(testUser);
            when(spaceMapper.selectById(1L)).thenReturn(space);
            when(spaceMapper.update(any(), any())).thenReturn(1);

            Boolean result = spaceService.updateSpace(req);

            assertTrue(result);
        }

        @Test
        void shouldRejectWhenNotCreatorNorAdmin() {
            Space space = new Space();
            space.setId(1L);
            space.setUserId(999L);

            UpdateSpace req = new UpdateSpace();
            req.setId(1L);
            req.setName("新名称");

            userHolderMock.when(UserHolder::getUser).thenReturn(testUser);
            when(spaceMapper.selectById(1L)).thenReturn(space);

            assertThrows(BaseException.class, () -> spaceService.updateSpace(req));
        }

        @Test
        void shouldRejectWhenNameIsEmpty() {
            UpdateSpace req = new UpdateSpace();
            req.setId(1L);
            req.setName(null);

            assertThrows(BaseException.class, () -> spaceService.updateSpace(req));
        }
    }

    @Nested
    class AdminSetStatusTests {

        @Test
        void shouldSetStatus() {
            Space space = new Space();
            space.setId(1L);
            space.setStatus(1);

            when(spaceMapper.selectById(1L)).thenReturn(space);
            when(spaceMapper.updateById(ArgumentMatchers.<Space>any())).thenReturn(1);

            Boolean result = spaceService.adminSetStatus(1L, 0);

            assertTrue(result);
            assertEquals(0, space.getStatus());
        }

        @Test
        void shouldRejectWhenSpaceIdIsNull() {
            assertThrows(BaseException.class, () -> spaceService.adminSetStatus(null, 1));
        }
    }

    @Nested
    class AdminDeleteTests {

        @Test
        void shouldDeleteSpace() {
            Space space = new Space();
            space.setId(1L);

            when(spaceMapper.selectById(1L)).thenReturn(space);
            when(spaceMapper.deleteById(1L)).thenReturn(1);

            Boolean result = spaceService.adminDelete(1L);

            assertTrue(result);
        }

        @Test
        void shouldRejectWhenSpaceNotFound() {
            when(spaceMapper.selectById(99L)).thenReturn(null);
            assertThrows(BaseException.class, () -> spaceService.adminDelete(99L));
        }
    }
}
