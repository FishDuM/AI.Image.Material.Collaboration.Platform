package hk.ljx.fishpicsbackend.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.dto.post.UploadPostRequest;
import hk.ljx.fishpicsbackend.entity.Picture;
import hk.ljx.fishpicsbackend.entity.Post;
import hk.ljx.fishpicsbackend.entity.User;
import hk.ljx.fishpicsbackend.mapper.PictureMapper;
import hk.ljx.fishpicsbackend.service.PictureService;
import hk.ljx.fishpicsbackend.service.PostService;
import hk.ljx.fishpicsbackend.mapper.PostMapper;
import hk.ljx.fishpicsbackend.service.UserService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
* @author 30574
* @description 针对表【post(帖子表)】的数据库操作Service实现
* @createDate 2026-04-13 21:24:41
*/
@Service
public class PostServiceImpl extends ServiceImpl<PostMapper, Post>
    implements PostService{

    @Resource
    private PictureMapper pictureMapper;

    @Resource
    private UserService userService;

    @Resource
    private PostMapper postMapper;

    @Resource
    private PictureService pictureService;

    @Override
    public void uploadPost(UploadPostRequest uploadPostRequest, HttpServletRequest request) {
        // 校验参数
        List<Long> imageId = uploadPostRequest.getImageId();
        String title = uploadPostRequest.getTitle();
        String content = uploadPostRequest.getContent();
        Long cover = uploadPostRequest.getCover();
        Integer isPrivate = uploadPostRequest.getIsPrivate();

        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(imageId), "图片不能为空");
        ExcUtils.throwIfTrue(imageId.size() > 15, "最多只能上传 15 张图片");
        ExcUtils.throwIfTrue(ObjectUtil.isAllEmpty(title, content, cover, isPrivate), "参数不能为空");
        // 获取用户信息
        User loginUser = userService.getLoginUser(request);
        Long userId = loginUser.getId();

        // 校验图片是否属于该用户
        QueryWrapper<Picture> pictureQueryWrapper = new QueryWrapper<>();
        for (Long id : imageId) {
            pictureQueryWrapper.or(
                    wrapper -> wrapper.eq("id", id).eq("user_id", userId)
            );
        }

        List<Picture> pictures = pictureMapper.selectList(pictureQueryWrapper);
        ExcUtils.throwIfTrue(imageId.size() != pictures.size(), "有图片不存在");

        // 保存帖子
        Post post = Post.builder().userId(userId).title(title).content(content).cover(cover).isPrivate(isPrivate).build();
        int insert = postMapper.insert(post);
        ExcUtils.throwIfTrue(insert != 1, ExceptionCode.INTERNAL_SERVER_ERROR, "保存失败，数据库错误");
        // 设置图片和帖子的关联
        pictureService.setPicturePostId(imageId, post.getId());
    }
}




