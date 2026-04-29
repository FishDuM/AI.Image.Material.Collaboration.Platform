package hk.ljx.fishpicsbackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.dto.picture.PictureMessage;
import hk.ljx.fishpicsbackend.entity.Picture;
import hk.ljx.fishpicsbackend.entity.User;
import hk.ljx.fishpicsbackend.mapper.UserMapper;
import hk.ljx.fishpicsbackend.service.CosService;
import hk.ljx.fishpicsbackend.service.PictureService;
import hk.ljx.fishpicsbackend.mapper.PictureMapper;
import hk.ljx.fishpicsbackend.service.UserService;
import hk.ljx.fishpicsbackend.vo.picture.PictureListVO;
import hk.ljx.fishpicsbackend.vo.picture.PicturePostVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import java.util.List;

import static hk.ljx.fishpicsbackend.common.constants.UserConstants.ADMIN;

/**
* @author 30574
* @description 针对表【picture(图片表)】的数据库操作Service实现
* @createDate 2026-04-13 21:24:49
*/
@Service
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
    implements PictureService {

    @Resource
    private UserService userService;

    @Resource
    private UserMapper userMapper;

    @Resource
    private CosService cosService;

    @Resource
    private PictureMapper pictureMapper;

    @Override
    public String uploadAvatar(MultipartFile file, Long id, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        ExcUtils.throwIfTrue(loginUser == null || loginUser.getId() == null, "请先登录");
        User user = userMapper.selectById(loginUser.getId());
        ExcUtils.throwIfTrue(user == null, "用户不存在");
        // 只有自己或管理员可以修改头像
        ExcUtils.throwIfTrue(!loginUser.getId().equals(id) && !loginUser.getRole().equals(ADMIN), "没有权限");
        user = userMapper.selectById(id);
        // 删除旧头像
        cosService.deletePicture(user.getAvatar());
        String url = cosService.uploadAndGetImageUrl(file);
        user.setAvatar(url);
        ExcUtils.throwIfTrue(userMapper.updateById(user) != 1, "上传失败，数据库错误");
        return url;
    }

    @Override
    public PicturePostVO uploadPicture4Post(MultipartFile file, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Long userId = loginUser.getId();
        ExcUtils.throwIfTrue(ObjUtil.isEmpty(loginUser) || userId == null, "请先登录");
        // 上传图片
        String key = cosService.uploadPicture(file);
        // 获取图片信息
        PictureMessage pictureMessage = cosService.getPictureMessage(key);
        Picture picture = new Picture();
        BeanUtil.copyProperties(pictureMessage, picture);
        picture.setUserId(userId);

        ExcUtils.throwIfTrue(pictureMapper.insert(picture) != 1, "上传失败，数据库错误");
        return PicturePostVO.builder().url(picture.getUrl()).pictureId(picture.getId()).build();
    }

    @Override
    public void setPicturePostId(List<Long> imageId, Long postId) {
        // 构建条件：id in (?)
        QueryWrapper<Picture> wrapper = new QueryWrapper<>();
        wrapper.in("id", imageId);

        // 构建要更新的字段
        Picture picture = new Picture();
        picture.setPostId(postId);

        // 一次性批量更新
        int update = pictureMapper.update(picture, wrapper);
        ExcUtils.throwIfTrue(update != imageId.size(), "更新失败，数据库错误");
    }

    @Override
    public List<PictureListVO> getPictureList() {
        return List.of();
    }
}




