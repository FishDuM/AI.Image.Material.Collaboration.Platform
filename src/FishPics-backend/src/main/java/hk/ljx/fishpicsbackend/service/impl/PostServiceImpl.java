package hk.ljx.fishpicsbackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import hk.ljx.fishpicsbackend.dto.base.PageRequest;
import hk.ljx.fishpicsbackend.dto.post.*;
import hk.ljx.fishpicsbackend.dto.space.SpacePictureList;
import hk.ljx.fishpicsbackend.entity.*;
import hk.ljx.fishpicsbackend.service.*;
import hk.ljx.fishpicsbackend.mapper.PostMapper;
import hk.ljx.fishpicsbackend.vo.picture.PictureListByEditPostVO;
import hk.ljx.fishpicsbackend.vo.picture.PictureListVO;
import hk.ljx.fishpicsbackend.vo.picture.PicturePageVO;
import hk.ljx.fishpicsbackend.vo.post.PostDetailVO;
import hk.ljx.fishpicsbackend.vo.post.PostListVO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static hk.ljx.fishpicsbackend.common.constants.RedisConstants.COLLECT_POST_KEY;
import static hk.ljx.fishpicsbackend.common.constants.RedisConstants.LIKE_POST_KEY;
import static hk.ljx.fishpicsbackend.common.constants.UserConstants.ADMIN;

/**
 * @author 30574
 * @description 针对表【post(帖子表)】的数据库操作Service实现
 * @createDate 2026-04-13 21:24:41
 */
@Service
public class PostServiceImpl extends ServiceImpl<PostMapper, Post>
        implements PostService {

    @Resource
    private PostMapper postMapper;

    @Resource
    private PictureChildService pictureChildService;

    @Resource
    private PictureService pictureService;

    @Resource
    private UserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UserPostLikesService userPostLikesService;

    @Resource
    private UserPostCollectService userPostCollectService;

    @Resource
    private SpaceService spaceService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void uploadPost(UploadPostRequest uploadPostRequest) {
        // 校验参数
        List<Long> imageId = uploadPostRequest.getImageId();
        String title = uploadPostRequest.getTitle();
        String content = uploadPostRequest.getContent();
        Integer cover = uploadPostRequest.getCover();
        Integer isPrivate = uploadPostRequest.getIsPrivate();

        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(imageId), "图片不能为空");
        ExcUtils.throwIfTrue(imageId.size() > 15, "最多只能上传 15 张图片");
        ExcUtils.throwIfTrue(ObjectUtil.isAllEmpty(title, content, cover, isPrivate), "参数不能为空");
        ExcUtils.throwIfTrue(imageId.size() < cover + 1 || imageId.get(cover) == null, "封面图片不存在");
        // 获取用户信息
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(user), ExceptionCode.NOT_LOGIN);
        Long userId = user.getId();

        // 判断是否为自己的图片并返回原图
        List<Picture> pictures = isMyPicture(userId, imageId);

        // 保存帖子
        Post post = Post.builder().userId(userId).title(title).content(content).cover(imageId.get(cover))
                .isPrivate(isPrivate).status(2)
                .build();
        int insert = postMapper.insert(post);
        ExcUtils.throwIfTrue(insert != 1, ExceptionCode.INTERNAL_SERVER_ERROR, "保存失败，数据库错误");
        Long postId = post.getId();

        // 批量设置子图片
        savePictureChildBatch(pictures, postId);
    }

    @Override
    public PostDetailVO getPost(Long id) {
        Post post = postMapper.selectById(id);
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(post) || post.getId() == null, ExceptionCode.NOT_FOUND, "帖子不存在");
        PostDetailVO postDetailVO = new PostDetailVO();
        BeanUtil.copyProperties(post, postDetailVO);
        // 获取子图片列表
        List<Long> pictureIds = pictureChildService
                .list(new LambdaQueryWrapper<PictureChild>().eq(PictureChild::getPostId, id)
                        .orderByAsc(PictureChild::getSortNum))
                .stream().map(PictureChild::getPictureId).collect(Collectors.toList());

        // todo: 后续根据用户等级判断可上传的图片数

        // 获取图片 url 列表（按 pictureIds 顺序排列），同时过滤已删除图片，保持两个列表一一对应
        Map<Long, String> urlMap = pictureService.list(new LambdaQueryWrapper<Picture>().in(Picture::getId, pictureIds))
                .stream().collect(Collectors.toMap(Picture::getId, Picture::getUrl));
        List<Long> validPictureIds = new ArrayList<>();
        List<String> pictureUrls = new ArrayList<>();
        for (Long pid : pictureIds) {
            String url = urlMap.get(pid);
            if (url != null) {
                validPictureIds.add(pid);
                pictureUrls.add(url);
            }
        }
        // 获取发帖者信息
        User user = userService.getById(post.getUserId());
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(user) || user.getId() == null, ExceptionCode.NOT_FOUND, "用户不存在");
        postDetailVO.setAvatar(user.getAvatar());
        postDetailVO.setUsername(user.getUsername());

        postDetailVO.setPictureUrl(pictureUrls);
        postDetailVO.setPictureIds(validPictureIds);

        // 查询当前用户是否已收藏
        User currentUser = UserHolder.getUser();
        if (currentUser != null) {
            boolean collected = userPostCollectService.count(
                    new QueryWrapper<UserPostCollect>().eq("user_id", currentUser.getId()).eq("post_id", id)) > 0;
            postDetailVO.setIsCollected(collected);
        }

        return postDetailVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void editPost(EditPostRequest editPostRequest) {
        Long id = editPostRequest.getId();
        List<Long> imageId = editPostRequest.getImageId();
        Integer cover = editPostRequest.getCover();
        String title = editPostRequest.getTitle();
        String content = editPostRequest.getContent();
        Integer isPrivate = editPostRequest.getIsPrivate();

        ExcUtils.throwIfTrue(ObjectUtil.isAllEmpty(title, content, isPrivate), ExceptionCode.PARAMETER_ERROR,
                "标题、内容、是否私密不能为空");
        ExcUtils.throwIfTrue(id == null, ExceptionCode.PARAMETER_ERROR, "帖子ID不能为空");
        ExcUtils.throwIfTrue(imageId == null || imageId.isEmpty(), ExceptionCode.PARAMETER_ERROR, "图片不能为空");
        ExcUtils.throwIfTrue(imageId.size() > 15, ExceptionCode.PARAMETER_ERROR, "图片数量不能超过15张");
        ExcUtils.throwIfTrue(imageId.size() < cover + 1 || imageId.get(cover) == null, "封面图片不存在");

        // 判断是否是自己的帖子 || 是否为管理员
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(user), ExceptionCode.NOT_LOGIN);
        Long userId = user.getId();

        // 查找该帖子
        Post post = postMapper.selectById(id);
        ExcUtils.throwIfTrue(post == null || post.getUserId() == null, ExceptionCode.PARAMETER_ERROR, "帖子不存在");
        ExcUtils.throwIfFalse(user.getId().equals(post.getUserId()) || user.getRole().equals(ADMIN),
                ExceptionCode.PARAMETER_ERROR, "只能修改自己的帖子");

        // 判断是不是自己的图片并返回原图id
        List<Picture> pictures = isMyPicture(userId, imageId);
        // 批量删除旧图片
        boolean remove = pictureChildService
                .remove(new LambdaQueryWrapper<PictureChild>().eq(PictureChild::getPostId, id));
        ExcUtils.throwIfFalse(remove, ExceptionCode.DATABASE_ERROR, "旧图片删除失败");
        // 批量插入新图片
        savePictureChildBatch(pictures, id);

        // 更新帖子
        post = Post.builder().id(id).userId(post.getUserId()).title(title).content(content).cover(imageId.get(cover))
                .isPrivate(isPrivate)
                .build();
        int insert = postMapper.updateById(post);
        ExcUtils.throwIfTrue(insert != 1, ExceptionCode.INTERNAL_SERVER_ERROR, "保存失败，数据库错误");
    }

    /**
     * 判断是否是自己的图片并返回原图
     *
     * @param userId  用户ID
     * @param imageId 图片ID列表
     * @return 图片列表
     */
    @Override
    public List<Picture> isMyPicture(Long userId, List<Long> imageId) {
        QueryWrapper<Space> spaceQueryWrapper = new QueryWrapper<>();
        spaceQueryWrapper.eq("user_id", userId).or().eq("type", 1).like("team_users_id", userId);
        List<Long> spaceIds = spaceService.list(spaceQueryWrapper).stream().map(Space::getId)
                .collect(Collectors.toList());
        // 校验图片是否存在
        LambdaQueryWrapper<Picture> pictureQueryWrapper = new LambdaQueryWrapper<>();
        pictureQueryWrapper.in(Picture::getId, imageId).in(Picture::getSpaceId, spaceIds);
        List<Picture> pictures = pictureService.list(pictureQueryWrapper);
        ExcUtils.throwIfTrue(imageId.size() != pictures.size(), "有图片不存在");
        return pictures;
    }

    /**
     * 批量保存子图片
     *
     * @param pictures 图片列表
     * @param postId   帖子ID
     */
    @Override
    public void savePictureChildBatch(List<Picture> pictures, Long postId) {
        ArrayList<PictureChild> pictureChildren = new ArrayList<>();
        // 批量设置子图片
        for (int i = 0; i < pictures.size(); i++) {
            PictureChild pictureChild = new PictureChild();
            pictureChild.setPictureId(pictures.get(i).getId());
            pictureChild.setPostId(postId);
            pictureChild.setSortNum(i + 1);
            pictureChildren.add(pictureChild);
        }
        boolean result = pictureChildService.saveBatch(pictureChildren);
        ExcUtils.throwIfFalse(result, ExceptionCode.INTERNAL_SERVER_ERROR, "保存失败，数据库错误");
    }

    @Override
    public IPage<PostListVO> getPostList(PostQueryRequest postQueryRequest) {
        Page<Post> page = new Page<>(postQueryRequest.getCurrent(), postQueryRequest.getPageSize());
        PostQueryWrapper queryWrapper = new PostQueryWrapper();
        BeanUtil.copyProperties(postQueryRequest, queryWrapper, CopyOptions.create().setIgnoreNullValue(true));
        IPage<Post> postPage = postMapper.selectPage(page, newQueryWrapper(queryWrapper));

        // 批量查询封面
        return convertToPostListVO(postPage);
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
        queryWrapper.eq(ObjectUtil.isNotNull(id), "id", id);
        queryWrapper.eq(ObjectUtil.isNotNull(userId), "user_id", userId);
        queryWrapper.eq(ObjectUtil.isNotNull(status), "status", status);
        queryWrapper.eq(ObjectUtil.isNotNull(isPrivate), "is_private", isPrivate);

        // 构建查询内容
        if (ObjectUtil.isNotNull(text)) {
            queryWrapper.and(wrapper -> wrapper.like("title", text)
                    .or()
                    .like("content", text));
        }

        // 构建热门查询
        if (ObjectUtil.isNotNull(hotPost)) {
            // 热门的定义：点赞数 * 0.3 + 收藏数 * 0.3 + 评论数 *0.2 + 点击数 * 0.2
            queryWrapper.orderByDesc("hot");
        }

        queryWrapper.orderBy(ObjectUtil.isNotNull(sortField), "asc".equalsIgnoreCase(sortOrder), sortField);
        return queryWrapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void likePost(Long id) {
        // 获取帖子 id
        Post post = postMapper.selectById(id);
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(post) || id == null, ExceptionCode.DATABASE_ERROR, "帖子不存在");

        // 获取用户
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(user) || user.getId() == null, "用户不存在");
        Long userId = user.getId();
        Long postId = post.getId();

        // 获取 Redis 锁
        Boolean lock = stringRedisTemplate.opsForValue().setIfAbsent(LIKE_POST_KEY + userId, "1", 10, TimeUnit.SECONDS);
        // 获取锁失败
        ExcUtils.throwIfTrue(Boolean.FALSE.equals(lock), "操作频繁，请稍后再试");
        // 成功则继续执行
        Long likesNum = post.getLikesNum();

        // 获取用户是否点赞过
        QueryWrapper<UserPostLikes> queryWrapper = new QueryWrapper<UserPostLikes>().eq("user_id", userId).eq("post_id",
                postId);
        UserPostLikes userPostLikes = userPostLikesService.getOne(queryWrapper);
        if (ObjectUtil.isEmpty(userPostLikes) || userPostLikes.getId() == null) {
            // 没点赞过，添加点赞
            UserPostLikes userPostLike = new UserPostLikes();
            userPostLike.setUserId(userId);
            userPostLike.setPostId(postId);
            // 查入数据
            boolean insert = userPostLikesService.save(userPostLike);
            ExcUtils.throwIfTrue(!insert, "点赞失败");

            try {
                QueryWrapper<Post> postQueryWrapper = new QueryWrapper<>();
                postQueryWrapper.eq("id", postId);
                postQueryWrapper.eq("likes_num", likesNum);
                post.setLikesNum(likesNum + 1);
                int i = postMapper.update(post, postQueryWrapper);

                ExcUtils.throwIfTrue(i != 1, "点赞失败，数据库错误");
            } finally {
                // 释放锁
                stringRedisTemplate.delete(LIKE_POST_KEY + userId);
            }

        } else {
            // 点赞过，删除点赞
            try {
                boolean delete = userPostLikesService.removeById(userPostLikes.getId());
                ExcUtils.throwIfTrue(!delete, "取消点赞失败");
            } finally {
                // 释放锁
                stringRedisTemplate.delete(LIKE_POST_KEY + userId);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean collectPost(Long id) {
        Post post = postMapper.selectById(id);
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(post) || id == null, ExceptionCode.DATABASE_ERROR, "帖子不存在");

        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(user) || user.getId() == null, "用户不存在");
        Long userId = user.getId();
        Long postId = post.getId();

        // 获取 Redis 分布式锁
        Boolean lock = stringRedisTemplate.opsForValue().setIfAbsent(COLLECT_POST_KEY + userId, "1", 10, TimeUnit.SECONDS);
        ExcUtils.throwIfTrue(Boolean.FALSE.equals(lock), "操作频繁，请稍后再试");

        Long collectsNum = post.getCollectsNum();

        // 查询用户是否已收藏
        QueryWrapper<UserPostCollect> queryWrapper = new QueryWrapper<UserPostCollect>()
                .eq("user_id", userId).eq("post_id", postId);
        UserPostCollect userPostCollect = userPostCollectService.getOne(queryWrapper);
        if (ObjectUtil.isEmpty(userPostCollect) || userPostCollect.getId() == null) {
            // 未收藏，添加收藏
            UserPostCollect newCollect = new UserPostCollect();
            newCollect.setUserId(userId);
            newCollect.setPostId(postId);
            boolean insert = userPostCollectService.save(newCollect);
            ExcUtils.throwIfTrue(!insert, "收藏失败");

            try {
                QueryWrapper<Post> postQueryWrapper = new QueryWrapper<>();
                postQueryWrapper.eq("id", postId);
                postQueryWrapper.eq("collects_num", collectsNum);
                post.setCollectsNum(collectsNum + 1);
                int i = postMapper.update(post, postQueryWrapper);
                ExcUtils.throwIfTrue(i != 1, "收藏失败，数据库错误");
            } finally {
                stringRedisTemplate.delete(COLLECT_POST_KEY + userId);
            }
            return true;
        } else {
            // 已收藏，取消收藏
            try {
                boolean delete = userPostCollectService.removeById(userPostCollect.getId());
                ExcUtils.throwIfTrue(!delete, "取消收藏失败");

                QueryWrapper<Post> postQueryWrapper = new QueryWrapper<>();
                postQueryWrapper.eq("id", postId);
                postQueryWrapper.eq("collects_num", collectsNum);
                post.setCollectsNum(Math.max(0, collectsNum - 1));
                int i = postMapper.update(post, postQueryWrapper);
                ExcUtils.throwIfTrue(i != 1, "取消收藏失败，数据库错误");
            } finally {
                stringRedisTemplate.delete(COLLECT_POST_KEY + userId);
            }
            return false;
        }
    }

    /**
     * 获取本人发布的帖子列表（分页）
     * 用户可以查看自己发布的所有帖子，与社区广场查询逻辑保持一致
     */
    @Override
    public IPage<PostListVO> getMyPosts(PageRequest pageRequest) {
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(user), ExceptionCode.NOT_LOGIN);
        Long userId = user.getId();

        Page<Post> page = new Page<>(pageRequest.getCurrent(), pageRequest.getPageSize());
        QueryWrapper<Post> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);
        queryWrapper.orderByDesc("create_time");

        IPage<Post> postPage = postMapper.selectPage(page, queryWrapper);
        return convertToPostListVO(postPage);
    }

    // getMyCollects方法修改
    @Override
    public IPage<PostListVO> getMyCollects(PageRequest pageRequest) {
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(user), ExceptionCode.NOT_LOGIN);
        Long userId = user.getId();

        // 第一步：查询用户的收藏帖子ID列表
        List<Long> collectPostIds = userPostCollectService.list(
                new QueryWrapper<UserPostCollect>().eq("user_id", userId)).stream()
                .map(UserPostCollect::getPostId)
                .collect(Collectors.toList());

        // 如果没有收藏，直接返回空分页
        if (CollectionUtils.isEmpty(collectPostIds)) {
            return new Page<>(pageRequest.getCurrent(), pageRequest.getPageSize()).convert(p -> new PostListVO());
        }

        // 第二步：查询这些帖子
        Page<Post> page = new Page<>(pageRequest.getCurrent(), pageRequest.getPageSize());
        QueryWrapper<Post> queryWrapper = new QueryWrapper<>();
        queryWrapper.in("id", collectPostIds); // 使用in方法，参数安全
        queryWrapper.orderByDesc("create_time");

        IPage<Post> postPage = postMapper.selectPage(page, queryWrapper);
        return convertToPostListVO(postPage);
    }

    // getMyLikes方法同理
    @Override
    public IPage<PostListVO> getMyLikes(PageRequest pageRequest) {
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(user), ExceptionCode.NOT_LOGIN);
        Long userId = user.getId();

        // 第一步：查询用户的点赞帖子ID列表
        List<Long> likePostIds = userPostLikesService.list(
                new QueryWrapper<UserPostLikes>().eq("user_id", userId)).stream()
                .map(UserPostLikes::getPostId)
                .collect(Collectors.toList());

        if (CollectionUtils.isEmpty(likePostIds)) {
            return new Page<>(pageRequest.getCurrent(), pageRequest.getPageSize()).convert(p -> new PostListVO());
        }

        // 第二步：查询这些帖子
        Page<Post> page = new Page<>(pageRequest.getCurrent(), pageRequest.getPageSize());
        QueryWrapper<Post> queryWrapper = new QueryWrapper<>();
        queryWrapper.in("id", likePostIds);
        queryWrapper.orderByDesc("create_time");

        IPage<Post> postPage = postMapper.selectPage(page, queryWrapper);
        return convertToPostListVO(postPage);
    }

    @Override
    public Map<String, Object> getPictureList(GetPictureBySpaceRequest getPictureBySpaceRequest) {
        Long spaceId = getPictureBySpaceRequest.getSpaceId();
        int current = getPictureBySpaceRequest.getCurrent();
        int pageSize = getPictureBySpaceRequest.getPageSize();
        String sortField = getPictureBySpaceRequest.getSortField();
        String sortOrder = getPictureBySpaceRequest.getSortOrder();

        List<Long> pictureIds = getPictureBySpaceRequest.getPictureIds();
        HashSet<Long> picIds = new HashSet<>(pictureIds);

        ExcUtils.throwIfTrue(spaceId == null, "spaceId不能为空");

        SpacePictureList spacePictures = new SpacePictureList();
        spacePictures.setSpaceId(spaceId);
        spacePictures.setCurrent(current);
        spacePictures.setPageSize(pageSize);
        spacePictures.setSortField(sortField);
        spacePictures.setSortOrder(sortOrder);
        PicturePageVO pageResult = spaceService.pictureList(spacePictures);

        ArrayList<PictureListByEditPostVO> editPostVOS = new ArrayList<>();
        for (PictureListVO pictureListVO : pageResult.getRecords()) {
            PictureListByEditPostVO editPostVO = new PictureListByEditPostVO();
            editPostVO.setId(pictureListVO.getId());
            editPostVO.setUrl(pictureListVO.getUrl());
            editPostVO.setFlag(!picIds.contains(pictureListVO.getId()));
            editPostVOS.add(editPostVO);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("records", editPostVOS);
        result.put("total", pageResult.getTotal());
        return result;
    }

    @Override
    public IPage<PostListVO> getAdminPostPage(PostQueryRequest req) {
        Page<Post> page = new Page<>(req.getCurrent(), req.getPageSize());
        QueryWrapper<Post> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(ObjectUtil.isNotNull(req.getStatus()), "status", req.getStatus());
        if (ObjectUtil.isNotNull(req.getText())) {
            queryWrapper.and(wrapper -> wrapper.like("title", req.getText()).or().like("content", req.getText()));
        }
        queryWrapper.orderByDesc("create_time");
        IPage<Post> postPage = postMapper.selectPage(page, queryWrapper);
        return convertToPostListVO(postPage);
    }

    @Override
    public void reviewPost(Long id, Integer status) {
        Post post = postMapper.selectById(id);
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(post) || post.getId() == null, ExceptionCode.NOT_FOUND, "帖子不存在");
        post.setStatus(status);
        int i = postMapper.updateById(post);
        ExcUtils.throwIfTrue(i != 1, ExceptionCode.DATABASE_ERROR, "审核失败");
    }

    @Override
    public void adminDeletePost(Long id) {
        Post post = postMapper.selectById(id);
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(post) || post.getId() == null, ExceptionCode.NOT_FOUND, "帖子不存在");
        int i = postMapper.deleteById(id);
        ExcUtils.throwIfTrue(i != 1, ExceptionCode.DATABASE_ERROR, "删除失败");
    }

    /**
     * 将帖子分页结果转换为 PostListVO 分页结果
     * 批量查询封面图片和用户信息，减少数据库查询次数
     *
     * @param postPage 帖子分页数据
     * @return PostListVO 分页数据
     */
    private IPage<PostListVO> convertToPostListVO(IPage<Post> postPage) {
        // 批量查询封面
        List<Long> coverIds = postPage.getRecords().stream()
                .map(Post::getCover)
                .filter(ObjectUtil::isNotNull)
                .collect(Collectors.toList());

        Map<Long, String> coverUrlMap = new HashMap<>();
        if (CollUtil.isNotEmpty(coverIds)) {
            coverUrlMap = pictureService.listByIds(coverIds).stream()
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
            userMap = userService.listByIds(userIds).stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(User::getId, user -> user));
        }

        // 批量查询当前用户的收藏状态
        HashSet<Long> collectedPostIds = new HashSet<>();
        User currentUser = UserHolder.getUser();
        if (currentUser != null) {
            List<Long> postIds = postPage.getRecords().stream().map(Post::getId).collect(Collectors.toList());
            if (CollUtil.isNotEmpty(postIds)) {
                collectedPostIds = userPostCollectService.list(
                        new QueryWrapper<UserPostCollect>().select("post_id")
                                .eq("user_id", currentUser.getId())
                                .in("post_id", postIds))
                        .stream().map(UserPostCollect::getPostId).collect(Collectors.toCollection(HashSet::new));
            }
        }

        // 封装VO
        Map<Long, String> finalCoverUrlMap = coverUrlMap;
        Map<Long, User> finalUserMap = userMap;
        HashSet<Long> finalCollectedPostIds = collectedPostIds;
        return postPage.convert(post -> {
            PostListVO vo = new PostListVO();
            BeanUtil.copyProperties(post, vo);
            // 帖子关联的用户可能已被删除，需要做空指针保护
            User postUser = finalUserMap.get(post.getUserId());
            if (postUser != null) {
                vo.setUsername(postUser.getUsername());
                vo.setAvatar(postUser.getAvatar());
            }
            // 帖子关联的封面图片可能已被删除，使用 getOrDefault 避免空指针
            vo.setUrl(finalCoverUrlMap.getOrDefault(post.getCover(), null));
            // 当前用户是否已收藏
            vo.setIsCollected(finalCollectedPostIds.contains(post.getId()));
            return vo;
        });
    }
}
