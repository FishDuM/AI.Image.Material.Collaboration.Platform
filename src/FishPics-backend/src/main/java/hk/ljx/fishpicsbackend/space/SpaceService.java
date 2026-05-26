package hk.ljx.fishpicsbackend.space;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import hk.ljx.fishpicsbackend.space.dto.CreateSpace;
import hk.ljx.fishpicsbackend.space.dto.SpaceAdminUpdateRequest;
import hk.ljx.fishpicsbackend.space.dto.SpacePictureList;
import hk.ljx.fishpicsbackend.space.dto.SpaceQueryWrapper;
import hk.ljx.fishpicsbackend.space.dto.UpdateSpace;
import hk.ljx.fishpicsbackend.space.Space;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import hk.ljx.fishpicsbackend.user.User;
import hk.ljx.fishpicsbackend.picture.vo.PicturePageVO;
import hk.ljx.fishpicsbackend.space.vo.SpaceVO;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 空间服务接口，提供私人空间(type=0)和团队空间(type=1)的CRUD操作
 */
public interface SpaceService extends IService<Space> {

    /**
     * 创建空间
     * @param createSpace 创建空间请求参数
     * @param user 当前登录用户
     * @return 创建成功返回true
     */
    Boolean createSpace(CreateSpace createSpace, User user);

    /**
     * 获取当前用户的空间列表
     * @param type 空间类型：0-私人空间，1-团队空间
     * @return 空间VO列表（含图片数量、创建人、成员信息）
     */
    List<SpaceVO> listSpace(Integer type);

    /**
     * 获取单个空间详情
     * @param id 空间ID
     * @return 空间VO
     */
    SpaceVO getSpace(Long id);

    /**
     * 更新空间信息
     * @param updateSpace 更新请求参数
     * @return 更新成功返回true
     */
    Boolean updateSpace(UpdateSpace updateSpace);

    /**
     * 获取空间图片列表（分页）
     * @param spacePictureList 查询参数
     * @return 图片分页结果
     */
    PicturePageVO pictureList(SpacePictureList spacePictureList);

    /**
     * 构建空间查询条件
     * @param spaceQueryWrapper 查询条件包装器
     * @return QueryWrapper对象
     */
    QueryWrapper<Space> getSpaceQueryWrapper(SpaceQueryWrapper spaceQueryWrapper);

    /**
     * 管理员分页查看所有空间
     */
    IPage<SpaceVO> adminList(SpaceQueryWrapper spaceQueryWrapper, long current, long pageSize);

    /**
     * 管理员编辑任意空间
     */
    Boolean adminUpdate(SpaceAdminUpdateRequest request);

    /**
     * 管理员删除空间
     */
    Boolean adminDelete(Long id);

    /**
     * 管理员设置空间状态
     */
    Boolean adminSetStatus(Long id, Integer status);
}
