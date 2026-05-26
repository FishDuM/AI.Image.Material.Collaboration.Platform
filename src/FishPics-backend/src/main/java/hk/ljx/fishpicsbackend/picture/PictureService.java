package hk.ljx.fishpicsbackend.picture;

import hk.ljx.fishpicsbackend.picture.dto.DeleteByIdList;
import hk.ljx.fishpicsbackend.picture.dto.PictureUpdateRequest;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import hk.ljx.fishpicsbackend.picture.vo.PictureAdminVO;
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
     * 首页获取图片列表（分页）
     * 
     * @param current  当前页
     * @param pageSize 每页数量
     * @return 图片分页列表
     */
    IPage<PictureListVO> getPictureList(int current, int pageSize, int flag);

    /**
     * 管理员获取所有图片列表（分页，按状态过滤）
     * 
     * @param current  当前页
     * @param pageSize 每页数量
     * @return 图片分页列表
     */
    IPage<PictureAdminVO> getAdminPictureList(int current, int pageSize, Integer status);

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

}
