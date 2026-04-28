package hk.ljx.fishpicsbackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.dto.post.EditPostRequest;
import hk.ljx.fishpicsbackend.dto.post.PostQueryRequest;
import hk.ljx.fishpicsbackend.dto.post.PostQueryWrapper;
import hk.ljx.fishpicsbackend.dto.post.UploadPostRequest;
import hk.ljx.fishpicsbackend.entity.Picture;
import hk.ljx.fishpicsbackend.entity.Post;
import hk.ljx.fishpicsbackend.entity.User;
import hk.ljx.fishpicsbackend.mapper.PictureMapper;
import hk.ljx.fishpicsbackend.mapper.UserMapper;
import hk.ljx.fishpicsbackend.service.PictureService;
import hk.ljx.fishpicsbackend.service.PostService;
import hk.ljx.fishpicsbackend.mapper.PostMapper;
import hk.ljx.fishpicsbackend.service.UserService;
import hk.ljx.fishpicsbackend.vo.post.PostDetailVO;
import hk.ljx.fishpicsbackend.vo.post.PostListVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

import static hk.ljx.fishpicsbackend.common.constants.UserConstants.ADMIN;

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
    @Autowired
    private UserMapper userMapper;

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

    @Override
    public PostDetailVO getPost(Long id) {
        Post post = postMapper.selectById(id);
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(post) || post.getId() == null, ExceptionCode.NOT_FOUND, "帖子不存在");
        PostDetailVO postDetailVO = new PostDetailVO();
        BeanUtil.copyProperties(post, postDetailVO);
        // 获取图片列表
        List<Picture> pictures = pictureMapper.selectList(new QueryWrapper<Picture>().eq("post_id", id));
        ArrayList<Long> pictureIds = new ArrayList<>();
        for (Picture picture : pictures) {
            pictureIds.add(picture.getId());
        }
        postDetailVO.setPictureId(pictureIds);
        return postDetailVO;
    }

    @Override
    public void editPost(EditPostRequest editPostRequest, HttpServletRequest request) {
        Long id = editPostRequest.getId();
        List<Long> imageId = editPostRequest.getImageId();
        Long cover = editPostRequest.getCover();
        ExcUtils.throwIfTrue(id == null, ExceptionCode.PARAMETER_ERROR, "帖子ID不能为空");
        if (CollUtil.isNotEmpty(imageId)) {
            ExcUtils.throwIfTrue(imageId.size() > 15, ExceptionCode.PARAMETER_ERROR, "图片数量不能超过15张");
        }

        // 判断是否是自己的帖子 || 是否为管理员
        User loginUser = userService.getLoginUser(request);
        // 校验封面
        if (cover != null) {
            Picture picture = pictureMapper.selectById(cover);
            ExcUtils.throwIfTrue(picture == null, ExceptionCode.PARAMETER_ERROR, "封面图片不存在");
        }

        // 查找该帖子
        Post post = postMapper.selectById(id);
        ExcUtils.throwIfTrue(post == null || post.getUserId() == null, ExceptionCode.PARAMETER_ERROR, "帖子不存在");
        ExcUtils.throwIfFalse(loginUser.getId().equals(post.getUserId()) || loginUser.getRole().equals(ADMIN), ExceptionCode.PARAMETER_ERROR, "只能修改自己的帖子");

        // 修改帖子
        BeanUtil.copyProperties(editPostRequest, post, CopyOptions.create().setIgnoreNullValue(true));
        postMapper.updateById(post);
    }

    @Override
    public IPage<PostListVO> getPostList(PostQueryRequest postQueryRequest) {
        Page<Post> page = new Page<>(postQueryRequest.getCurrent(), postQueryRequest.getPageSize());
        PostQueryWrapper queryWrapper = new PostQueryWrapper();
        BeanUtil.copyProperties(postQueryRequest, queryWrapper, CopyOptions.create().setIgnoreNullValue(true));
        IPage<Post> postPage = postMapper.selectPage(page, newQueryWrapper(queryWrapper));

        // 批量查询封面
        List<Long> coverIds = postPage.getRecords().stream()
                .map(Post::getCover)
                .filter(ObjectUtil::isNotNull)
                .collect(Collectors.toList());

        // 一次性构建 map
        Map<Long, String> coverUrlMap = new HashMap<>();
        if (CollUtil.isNotEmpty(coverIds)) {
            coverUrlMap = pictureMapper.selectByIds(coverIds).stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(Picture::getId, Picture::getUrl));
        }

        // 批量查询用户
        List<Long> userIds = postPage.getRecords().stream()
                .map(Post::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        Map<Long, User> userMap = new HashMap<>();
        if (CollUtil.isNotEmpty(userIds)) {
            userMap = userMapper.selectByIds(userIds).stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(User::getId, user -> user));
        }

        // 封装VO
        Map<Long, String> finalCoverUrlMap = coverUrlMap;
        Map<Long, User> finalUserMap = userMap;
        return postPage.convert(post -> {
            PostListVO vo = new PostListVO();
            BeanUtil.copyProperties(post, vo);
            vo.setUsername(finalUserMap.get(post.getUserId()).getUsername());
            vo.setAvatar(finalUserMap.get(post.getUserId()).getAvatar());
            vo.setUrl(finalCoverUrlMap.get(post.getCover()));
            return vo;
        });
    }

    @Override
    public QueryWrapper<Post> newQueryWrapper(PostQueryWrapper postQueryWrapper) {
        Long id = postQueryWrapper.getId();
        Long userId = postQueryWrapper.getUserId();
        String text = postQueryWrapper.getText();
        Integer status = postQueryWrapper.getStatus();
        Boolean hotPost = postQueryWrapper.getHotPost();
        Integer isPrivate = postQueryWrapper.getIsPrivate();
        String sortField = postQueryWrapper.getSortField();
        String sortOrder = postQueryWrapper.getSortOrder();

        QueryWrapper<Post> queryWrapper = new QueryWrapper<>();
        queryWrapper.like(ObjectUtil.isNotNull(id), "id", id);
        queryWrapper.like(ObjectUtil.isNotNull(userId), "user_id", userId);
        queryWrapper.eq(ObjectUtil.isNotNull(status), "status", status);
        queryWrapper.eq(ObjectUtil.isNotNull(isPrivate), "is_private", isPrivate);

        // 构建查询内容
        if (ObjectUtil.isNotNull(text)) {
            queryWrapper.like("title", text);
            queryWrapper.like("content", text);
        }

        // 构建热门查询
        if (ObjectUtil.isNotNull(hotPost)) {
            // 热门的定义：点赞数 * 0.3 + 收藏数 * 0.3 + 评论数 *0.2 + 点击数 * 0.2
            queryWrapper.orderByDesc("(likes_num * 0.3 + collects_num * 0.2 + comment_num * 0.3 + views_num * 0.2)");
        }

        queryWrapper.orderBy(ObjectUtil.isNotNull(sortField), "asc".equalsIgnoreCase(sortOrder), sortField);
        return queryWrapper;
    }
}




