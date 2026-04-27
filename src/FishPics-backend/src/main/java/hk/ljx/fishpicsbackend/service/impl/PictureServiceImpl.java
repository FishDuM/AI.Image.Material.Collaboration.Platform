package hk.ljx.fishpicsbackend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.entity.Picture;
import hk.ljx.fishpicsbackend.entity.User;
import hk.ljx.fishpicsbackend.mapper.UserMapper;
import hk.ljx.fishpicsbackend.service.CosService;
import hk.ljx.fishpicsbackend.service.PictureService;
import hk.ljx.fishpicsbackend.mapper.PictureMapper;
import hk.ljx.fishpicsbackend.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

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
}




