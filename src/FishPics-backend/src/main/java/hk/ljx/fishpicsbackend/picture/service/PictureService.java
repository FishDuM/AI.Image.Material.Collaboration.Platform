package hk.ljx.fishpicsbackend.picture.service;
import hk.ljx.fishpicsbackend.picture.entity.Picture;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import hk.ljx.fishpicsbackend.common.dto.PageRequest;
import hk.ljx.fishpicsbackend.picture.dto.AdminPictureListDTO;
import hk.ljx.fishpicsbackend.picture.dto.DeleteByIdList;
import hk.ljx.fishpicsbackend.picture.dto.PictureQueryRequest;
import hk.ljx.fishpicsbackend.picture.dto.PictureUpdateRequest;
import hk.ljx.fishpicsbackend.picture.vo.PictureAdminVO;
import hk.ljx.fishpicsbackend.picture.vo.PictureEditVO;
import hk.ljx.fishpicsbackend.picture.vo.PictureListVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * @author 30574
 * @description 针对表【picture(图片表)】的数据库操作Service
 * @createDate 2026-04-13 21:24:49
 */
public interface PictureService extends IService<Picture> {

    /**
     * 用户上传头像或管理员修改用户头像
     *
     * @param file    图片
     * @param id      用户id
     * @return 头像地址
     */
    String uploadAvatar(MultipartFile file, Long id);

    /**
     * 用户上传图片
     *
     * @param file          图片
     * @param targetSpaceId 目标空间ID，为null时默认上传至私人空间
     * @return 图片
     */
    Picture uploadPicture(MultipartFile file, Long targetSpaceId);

    /**
     * 通过 URL 保存图片到空间
     *
     * @param url           图片URL
     * @param targetSpaceId 目标空间ID，为null时默认上传至私人空间
     * @return 图片
     */
    Picture savePictureByUrl(String url, Long targetSpaceId);

    /**
     * 首页获取图片列表（分页）
     *
     * @return 图片分页列表
     */
    IPage<PictureListVO> getPictureList(PictureQueryRequest pictureQueryRequest);

    /**
     * 管理员获取所有图片列表（分页，按状态过滤）
     *
     * @param dto 查询参数（包含分页和状态筛选）
     * @return 图片管理分页列表
     */
    IPage<PictureAdminVO> getAdminPictureList(AdminPictureListDTO dto);

    /**
     * 管理员审核图片
     * 
     * @param pictureId 图片id
     * @param status    目标状态 1-通过 0-拒绝
     */
    void reviewPicture(Long pictureId, Integer status, Integer selected);

    /**
     * 删除图片
     * 
     * @param deleteByIdList 图片id list
     * @return 返回的 message
     */
    String deletePicture(DeleteByIdList deleteByIdList);

    /**
     * 批量编辑图片信息
     * 
     * @param request 编辑请求
     */
    void updatePicture(PictureUpdateRequest request);

    /**
     * 编辑时图片信息回填
     * @param id 图片id
     * @return 图片信息
     */
    PictureEditVO getPictureEditMessage(Long id);

    /**
     * 获取推荐图片列表（基于用户兴趣画像标签匹配）
     */
    IPage<PictureListVO> getRecommendPictures(PageRequest pageRequest, Long userId);
}
