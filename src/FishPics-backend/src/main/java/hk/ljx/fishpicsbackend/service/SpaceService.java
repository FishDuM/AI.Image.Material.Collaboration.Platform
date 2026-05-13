package hk.ljx.fishpicsbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import hk.ljx.fishpicsbackend.dto.space.CreateSpace;
import hk.ljx.fishpicsbackend.dto.space.SpacePictureList;
import hk.ljx.fishpicsbackend.dto.space.SpaceQueryWrapper;
import hk.ljx.fishpicsbackend.dto.space.UpdateSpace;
import hk.ljx.fishpicsbackend.entity.Space;
import com.baomidou.mybatisplus.extension.service.IService;
import hk.ljx.fishpicsbackend.entity.User;
import hk.ljx.fishpicsbackend.vo.picture.PicturePageVO;
import hk.ljx.fishpicsbackend.vo.space.SpaceVO;

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
     * @param request HTTP请求
     * @return 空间VO列表（含图片数量、创建人、成员信息）
     */
    List<SpaceVO> listSpace(Integer type, HttpServletRequest request);

    /**
     * 获取单个空间详情
     * @param id 空间ID
     * @param request HTTP请求
     * @return 空间VO
     */
    SpaceVO getSpace(Long id, HttpServletRequest request);

    /**
     * 更新空间信息
     * @param updateSpace 更新请求参数
     * @param request HTTP请求
     * @return 更新成功返回true
     */
    Boolean updateSpace(UpdateSpace updateSpace, HttpServletRequest request);

    /**
     * 获取空间图片列表（分页）
     * @param spacePictureList 查询参数
     * @param request HTTP请求
     * @return 图片分页结果
     */
    PicturePageVO pictureList(SpacePictureList spacePictureList, HttpServletRequest request);

    /**
     * 构建空间查询条件
     * @param spaceQueryWrapper 查询条件包装器
     * @return QueryWrapper对象
     */
    QueryWrapper<Space> getSpaceQueryWrapper(SpaceQueryWrapper spaceQueryWrapper);
}
