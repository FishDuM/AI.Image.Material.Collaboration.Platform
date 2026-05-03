package hk.ljx.fishpicsbackend.service;

import hk.ljx.fishpicsbackend.entity.Picture;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import hk.ljx.fishpicsbackend.vo.picture.PictureAdminVO;
import hk.ljx.fishpicsbackend.vo.picture.PictureListVO;
import hk.ljx.fishpicsbackend.vo.picture.PicturePostVO;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
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
     * 用户上传帖子图片
     *
     * @param file    图片
     * @param request token
     * @return 图片地址
     */
    PicturePostVO uploadPicture4Post(MultipartFile file, HttpServletRequest request);

    /**
     * 设置图片和帖子的关联
     * @param imageId 图片id
     * @param id 帖子id
     */
    void setPicturePostId(List<Long> imageId, Long id);

    /**
     * 首页获取图片列表（分页）
     * @param current 当前页
     * @param pageSize 每页数量
     * @return 图片分页列表
     */
    IPage<PictureListVO> getPictureList(int current, int pageSize, int flag);

    /**
     * 管理员获取所有图片列表（分页，不按状态过滤）
     * @param current 当前页
     * @param pageSize 每页数量
     * @return 图片分页列表
     */
    IPage<PictureAdminVO> getAdminPictureList(int current, int pageSize);

    /**
     * 管理员审核图片
     * @param pictureId 图片id
     * @param status 目标状态 1-通过 0-拒绝
     */
    void reviewPicture(Long pictureId, Integer status);
}
