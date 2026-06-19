package hk.ljx.fishpicsbackend.space.service;
import hk.ljx.fishpicsbackend.space.entity.Space;

import hk.ljx.fishpicsbackend.space.dto.*;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import hk.ljx.fishpicsbackend.user.entity.User;
import hk.ljx.fishpicsbackend.picture.vo.PictureVO;
import hk.ljx.fishpicsbackend.space.vo.SpaceMemberVO;
import hk.ljx.fishpicsbackend.space.vo.SpaceVO;

import java.util.List;

/**
 * 空间服务接口，提供私人空间(type=0)和团队空间(type=1)的CRUD操作
 */
public interface SpaceService extends IService<Space> {

    Boolean createSpace(CreateSpaceRequest createSpace, User user);

    List<SpaceVO> listSpace(Integer type);

    SpaceVO getSpace(Long id);

    Boolean updateSpace(UpdateSpaceRequest updateSpace);

    IPage<PictureVO> pictureList(SpacePictureListRequest spacePictureList);

    IPage<SpaceVO> adminList(SpaceQueryWrapper spaceQueryWrapper);

    Boolean adminUpdate(SpaceAdminUpdateRequest request);

    Boolean adminDelete(Long id);

    Boolean adminSetStatus(Long id, Integer status);

    List<SpaceMemberVO> teamMemberList(Long spaceId);

    Boolean teamInvite(TeamInviteRequest request);

    Boolean teamRemove(TeamRemoveRequest request);

    Boolean teamChangeRole(TeamChangeRoleRequest request);

    /**
     * 获取当前用户可保存图片的空间列表（私人空间 + 有上传权限的团队空间）
     */
    List<SpaceVO> saveableSpaces();

    // 查询空间并校验当前用户的访问权限
    Space resolveSpaceAccess(Long spaceId);
}
