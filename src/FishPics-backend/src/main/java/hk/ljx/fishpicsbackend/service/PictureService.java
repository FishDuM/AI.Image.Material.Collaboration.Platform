package hk.ljx.fishpicsbackend.service;

import hk.ljx.fishpicsbackend.dto.picture.DeleteByIdList;
import hk.ljx.fishpicsbackend.dto.picture.PictureCropRequest;
import hk.ljx.fishpicsbackend.dto.picture.PictureScaleRequest;
import hk.ljx.fishpicsbackend.dto.picture.PictureUpdateRequest;
import hk.ljx.fishpicsbackend.dto.picture.PictureWatermarkRequest;
import hk.ljx.fishpicsbackend.entity.Picture;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import hk.ljx.fishpicsbackend.vo.picture.PictureAdminVO;
import hk.ljx.fishpicsbackend.vo.picture.PictureListVO;
import hk.ljx.fishpicsbackend.vo.picture.PicturePostVO;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

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
     * @param request token
     * @return 头像地址
     */
    String uploadAvatar(MultipartFile file, Long id, HttpServletRequest request);

    /**
     * 用户上传图片
     *
     * @param file          图片
     * @param targetSpaceId 目标空间ID，为null时默认上传至私人空间
     * @param request       token
     * @return 图片
     */
    Picture uploadPicture(MultipartFile file, Long targetSpaceId, HttpServletRequest request);

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
     * @param request        请求
     * @return 返回的 message
     */
    String deletePicture(DeleteByIdList deleteByIdList, HttpServletRequest request);

    /**
     * 批量编辑图片信息
     * 
     * @param request 编辑请求
     */
    void updatePicture(PictureUpdateRequest request);

    /**
     * 裁剪图片（服务端下载、裁剪、旋转、重新上传）
     * 先按原始图像坐标裁剪，再旋转（如有），最后上传至COS并更新数据库
     *
     * @param request        裁剪请求，含图片id、裁剪区域坐标(x/y/width/height)、旋转角度、输出格式
     * @param servletRequest HTTP请求，用于权限校验
     * @return 裁剪后新图片的COS访问URL
     */
    String cropPicture(PictureCropRequest request, HttpServletRequest servletRequest);

    /**
     * 缩放图片
     * 支持按比例(scale)或按目标宽度(targetWidth)等比缩放，处理完成后上传至COS并更新数据库
     *
     * @param request        缩放请求，含图片id、缩放比例或目标宽度、输出格式
     * @param servletRequest HTTP请求，用于权限校验
     * @return 缩放后新图片的COS访问URL
     */
    String scalePicture(PictureScaleRequest request, HttpServletRequest servletRequest);

    /**
     * 添加文字水印
     * 在图片中央叠加半透明白色文字，处理完成后上传至COS并更新数据库
     *
     * @param request        水印请求，含图片id、水印文字、输出格式
     * @param servletRequest HTTP请求，用于权限校验
     * @return 添加水印后新图片的COS访问URL
     */
    String watermarkPicture(PictureWatermarkRequest request, HttpServletRequest servletRequest);
}
