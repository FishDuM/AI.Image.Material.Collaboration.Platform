package hk.ljx.fishpicsbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import hk.ljx.fishpicsbackend.common.response.Response;
import hk.ljx.fishpicsbackend.dto.space.CreateSpace;
import hk.ljx.fishpicsbackend.dto.space.SpacePictureList;
import hk.ljx.fishpicsbackend.dto.space.SpaceQueryWrapper;
import hk.ljx.fishpicsbackend.dto.space.UpdateSpace;
import hk.ljx.fishpicsbackend.entity.Space;
import com.baomidou.mybatisplus.extension.service.IService;
import hk.ljx.fishpicsbackend.vo.picture.PictureListVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
* @author abc
* @description 针对表【space(空间表)】的数据库操作Service
* @createDate 2026-05-03 15:29:23
*/
public interface SpaceService extends IService<Space> {

    /**
     * 用户创建空间
     *
     * @param createSpace 创建空间参数
     * @return 结果
     */
    Boolean createSpace(CreateSpace createSpace, HttpServletRequest request);

    /**
     * 获取空间列表
     * @param type 空间类型 0:私人空间 1:团队空间
     * @param request 请求
     * @return 空间列表
     */
    List<Space> listSpace(Integer type, HttpServletRequest request);

    /**
     * 更新空间信息
     *
     * @param updateSpace 更新空间参数
     * @param request     请求
     * @return 结果
     */
    Boolean updateSpace(UpdateSpace updateSpace, HttpServletRequest request);

    /**
     * 获取空间图片列表
     *
     * @param spacePictureList 空间ID
     * @param request          请求
     * @return 图片列表
     */
    List<PictureListVO> pictureList(SpacePictureList spacePictureList, HttpServletRequest request);

    /**
     * 获取空间查询条件
     *
     * @param spaceQueryWrapper 空间条件
     * @return 查询条件
     */
    QueryWrapper<Space> getSpaceQueryWrapper(SpaceQueryWrapper spaceQueryWrapper);
}
