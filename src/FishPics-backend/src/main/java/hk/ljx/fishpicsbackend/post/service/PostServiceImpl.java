package hk.ljx.fishpicsbackend.post.service;
import hk.ljx.fishpicsbackend.post.entity.Post;

import hk.ljx.fishpicsbackend.mapper.PostMapper;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import hk.ljx.fishpicsbackend.common.cache.MultiLevelCacheManager;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import hk.ljx.fishpicsbackend.common.dto.PageRequest;
import hk.ljx.fishpicsbackend.post.dto.*;
import hk.ljx.fishpicsbackend.space.dto.SpacePictureList;
import hk.ljx.fishpicsbackend.picture.entity.Picture;
import hk.ljx.fishpicsbackend.picture.entity.PictureChild;
import hk.ljx.fishpicsbackend.picture.service.PictureChildService;
import hk.ljx.fishpicsbackend.picture.service.PictureService;
import hk.ljx.fishpicsbackend.space.entity.Space;
import hk.ljx.fishpicsbackend.space.service.SpaceService;
import hk.ljx.fishpicsbackend.user.entity.User;
import hk.ljx.fishpicsbackend.user.entity.UserPostCollect;
import hk.ljx.fishpicsbackend.user.service.UserPostCollectService;
import hk.ljx.fishpicsbackend.user.entity.UserPostLikes;
import hk.ljx.fishpicsbackend.user.service.UserPostLikesService;
import hk.ljx.fishpicsbackend.user.service.UserService;
import hk.ljx.fishpicsbackend.user.entity.UserInterestProfile;
import hk.ljx.fishpicsbackend.user.service.UserInterestProfileService;
import hk.ljx.fishpicsbackend.permission.service.PermissionService;
import com.alibaba.fastjson.JSON;
import hk.ljx.fishpicsbackend.picture.vo.PictureListByEditPostVO;
import hk.ljx.fishpicsbackend.picture.vo.PictureListVO;
import hk.ljx.fishpicsbackend.picture.vo.PicturePageVO;
import hk.ljx.fishpicsbackend.post.vo.PictureListPageVO;
import hk.ljx.fishpicsbackend.post.vo.PostDetailVO;
import hk.ljx.fishpicsbackend.post.vo.PostListVO;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
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
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static hk.ljx.fishpicsbackend.common.constants.RedisConstants.POST_LIST_CACHE_KEY;
import static hk.ljx.fishpicsbackend.common.constants.RedisConstants.POST_LIST_LOCK_KEY;
import static hk.ljx.fishpicsbackend.common.constants.RedisConstants.POST_LIST_CACHE_TTL;

// 帖子业务常量
import static hk.ljx.fishpicsbackend.common.constants.SysConstants.POST_MAX_PICTURE_COUNT;
import static hk.ljx.fishpicsbackend.common.constants.SysConstants.POST_STATUS_DRAFT;
import static hk.ljx.fishpicsbackend.common.constants.SysConstants.POST_STATUS_PENDING;
import static hk.ljx.fishpicsbackend.common.constants.SysConstants.POST_STATUS_PUBLISHED;
import static hk.ljx.fishpicsbackend.common.constants.SysConstants.POST_STATUS_REJECTED;
import static hk.ljx.fishpicsbackend.common.constants.SysConstants.POST_DEFAULT_COVER;
import static hk.ljx.fishpicsbackend.common.constants.SysConstants.CACHE_RETRY_MAX_POLLS;
import static hk.ljx.fishpicsbackend.common.constants.SysConstants.CACHE_RETRY_INTERVAL_MS;
import static hk.ljx.fishpicsbackend.common.constants.SysConstants.LOCK_WAIT_SECONDS;
import static hk.ljx.fishpicsbackend.common.constants.SysConstants.LOCK_LEASE_SECONDS;

/**
 * @author 30574
 * @description 针对表【post(帖子表)】的数据库操作Service实现
 * @createDate 2026-04-13 21:24:41
 */
@Slf4j
@Service
public class PostServiceImpl extends ServiceImpl<PostMapper, Post>
        implements PostService {

    @Resource
    private PictureChildService pictureChildService;

    @Resource
    private PictureService pictureService;

    @Resource
    private UserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private UserPostLikesService userPostLikesService;

    @Resource
    private UserPostCollectService userPostCollectService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private UserInterestProfileService userInterestProfileService;

    @Resource
    private PermissionService permissionService;

    @Resource
    private MultiLevelCacheManager cacheManager;

    @Resource
    private hk.ljx.fishpicsbackend.mapper.SpaceTeamMemberMapper spaceTeamMemberMapper;

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
        ExcUtils.throwIfTrue(imageId.size() > POST_MAX_PICTURE_COUNT, "最多只能上传 15 张图片");
        ExcUtils.throwIfTrue(ObjectUtil.isAllEmpty(title, content, cover, isPrivate), "参数不能为空");
        ExcUtils.throwIfTrue(imageId.size() < cover + 1 || imageId.get(cover) == null, "封面图片不存在");
        // 获取用户信息
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(user), ExceptionCode.NOT_LOGIN);
        Long userId = user.getId();

        // 判断是否为自己的图片并返回原图
        List<Picture> pictures = validatePictureOwnership(userId, imageId);

        // 保存帖子
        Post post = Post.builder().userId(userId).title(title).content(content).cover(imageId.get(cover))
                .isPrivate(isPrivate).status(2)
                .build();
        int insert = baseMapper.insert(post);
        ExcUtils.throwIfTrue(insert != 1, ExceptionCode.INTERNAL_SERVER_ERROR, "保存失败，数据库错误");
        Long postId = post.getId();

        // 批量设置子图片
        savePictureChildBatch(pictures, postId);

        // 清除帖子列表缓存
        clearPostListCache();
    }

    @Override
    public PostDetailVO getPost(Long id) {
        Post post = baseMapper.selectById(id);
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(post) || post.getId() == null, ExceptionCode.NOT_FOUND, "帖子不存在");

        // 增加浏览数（原子 SQL，无需加锁）
        baseMapper.update(null, new UpdateWrapper<Post>()
                .eq("id", id).setSql("views_num = views_num + 1"));

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

        // 查询当前用户的收藏/点赞状态
        User currentUser = UserHolder.getUser();
        if (currentUser != null) {
            boolean collected = userPostCollectService.count(
                    new QueryWrapper<UserPostCollect>().eq("user_id", currentUser.getId()).eq("post_id", id)) > 0;
            postDetailVO.setIsCollected(collected);

            boolean liked = userPostLikesService.count(
                    new QueryWrapper<UserPostLikes>().eq("user_id", currentUser.getId()).eq("post_id", id)) > 0;
            postDetailVO.setIsLiked(liked);
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
        ExcUtils.throwIfTrue(imageId.size() > POST_MAX_PICTURE_COUNT, ExceptionCode.PARAMETER_ERROR, "图片数量不能超过15张");
        ExcUtils.throwIfTrue(imageId.size() < cover + 1 || imageId.get(cover) == null, "封面图片不存在");

        // 判断是否是自己的帖子 || 是否为管理员
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(user), ExceptionCode.NOT_LOGIN);
        Long userId = user.getId();

        // 查找该帖子
        Post post = baseMapper.selectById(id);
        ExcUtils.throwIfTrue(post == null || post.getUserId() == null, ExceptionCode.PARAMETER_ERROR, "帖子不存在");
        ExcUtils.throwIfFalse(user.getId().equals(post.getUserId()) || permissionService.hasPermission(user.getId(), "post:review"),
                ExceptionCode.PARAMETER_ERROR, "只能修改自己的帖子");

        // 判断是不是自己的图片并返回原图id
        List<Picture> pictures = validatePictureOwnership(userId, imageId);
        // 批量删除旧图片（帖子可能没有子图片，不检查返回值）
        pictureChildService.remove(new LambdaQueryWrapper<PictureChild>().eq(PictureChild::getPostId, id));
        // 批量插入新图片
        savePictureChildBatch(pictures, id);

        // 更新帖子
        post = Post.builder().id(id).userId(post.getUserId()).title(title).content(content).cover(imageId.get(cover))
                .isPrivate(isPrivate)
                .build();
        int insert = baseMapper.updateById(post);
        ExcUtils.throwIfTrue(insert != 1, ExceptionCode.INTERNAL_SERVER_ERROR, "保存失败，数据库错误");

        // 清除帖子列表缓存
        clearPostListCache();
    }

    /**
     * 判断是否是自己的图片并返回原图
     *
     * @param userId  用户ID
     * @param imageId 图片ID列表
     * @return 图片列表
     */
    @Override
    public List<Picture> validatePictureOwnership(Long userId, List<Long> imageId) {
        // 1. 用户作为创建者的空间
        QueryWrapper<Space> ownedQuery = new QueryWrapper<>();
        ownedQuery.eq("user_id", userId);
        Set<Long> allSpaceIds = new HashSet<>(spaceService.list(ownedQuery).stream()
                .map(Space::getId).collect(Collectors.toList()));

        // 2. 用户作为团队成员的空间，合并去重
        List<Long> teamSpaceIds = spaceTeamMemberMapper.selectList(
                new QueryWrapper<hk.ljx.fishpicsbackend.space.entity.SpaceTeamMember>()
                        .eq("user_id", userId)
                        .select("space_id")
        ).stream().map(hk.ljx.fishpicsbackend.space.entity.SpaceTeamMember::getSpaceId)
                .collect(Collectors.toList());
        allSpaceIds.addAll(teamSpaceIds);
        List<Long> spaceIds = new ArrayList<>(allSpaceIds);

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
    @SuppressWarnings("unchecked")
    public IPage<PostListVO> getPostList(PostQueryRequest postQueryRequest) {
        // 1. 构建缓存键
        String cacheKey = buildPostListCacheKey(postQueryRequest);

        // 2. 先查多级缓存
        Object l1Cached = cacheManager.getPostListCache().get(cacheKey);
        if (l1Cached instanceof IPage) {
            log.debug("命中帖子列表缓存: {}", cacheKey);
            return (IPage<PostListVO>) l1Cached;
        }

        // 4. 缓存未命中，尝试获取分布式锁防击穿
        String lockKey = POST_LIST_LOCK_KEY + cacheKey;
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = false;
        try {
            // 尝试获取锁，不等待，leaseTime 5秒
            locked = lock.tryLock(0, 5, TimeUnit.SECONDS);
            if (locked) {
                log.debug("获取锁成功，查询数据库: {}", cacheKey);
                // 再次检查缓存（双重检查）
                Object doubleCheck = cacheManager.getPostListCache().get(cacheKey);
                if (doubleCheck instanceof IPage) {
                    log.debug("再次命中缓存: {}", cacheKey);
                    return (IPage<PostListVO>) doubleCheck;
                }
                // 查数据库
                Page<Post> page = new Page<>(postQueryRequest.getCurrent(), postQueryRequest.getPageSize());
                PostQueryWrapper queryWrapper = new PostQueryWrapper();
                BeanUtil.copyProperties(postQueryRequest, queryWrapper, CopyOptions.create().setIgnoreNullValue(true));
                IPage<Post> postPage = baseMapper.selectPage(page, newQueryWrapper(queryWrapper));
                IPage<PostListVO> result = convertToPostListVO(postPage);

                // 写多级缓存
                cacheManager.getPostListCache().put(cacheKey, result);
                return result;
            } else {
                // 5. 未获取到锁，循环等待读缓存（最多25次，每次100ms，共2.5秒）
                log.debug("未获取到锁，等待缓存写入: {}", cacheKey);
                for (int i = 0; i < 25; i++) {
                    Thread.sleep(100);
                    Object waited = cacheManager.getPostListCache().get(cacheKey);
                    if (waited instanceof IPage) {
                        log.debug("等待后命中缓存: {}", cacheKey);
                        return (IPage<PostListVO>) waited;
                    }
                }
                // 超时返回空数据（兜底）
                log.warn("等待超时，返回空数据: {}", cacheKey);
                return new Page<>(postQueryRequest.getCurrent(), postQueryRequest.getPageSize()).convert(p -> new PostListVO());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("获取锁被中断: {}", cacheKey);
            return new Page<>(postQueryRequest.getCurrent(), postQueryRequest.getPageSize()).convert(p -> new PostListVO());
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
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

        // 排序字段白名单，防止 SQL 注入
        Set<String> allowedSortFields = Set.of("id", "user_id", "title", "content", "cover", "status", "is_private",
                "likes_num", "collect_num", "comment_num", "views_num", "hot", "create_time", "update_time");
        boolean isSortFieldValid = sortField != null && allowedSortFields.contains(sortField);

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

        // 按图片标签筛选帖子：通过 picture + picture_child 子查询找到匹配的帖子 ID
        String tag = postQueryWrapper.getTag();
        if (StrUtil.isNotBlank(tag)) {
            List<Picture> pictures = pictureService.list(new QueryWrapper<Picture>().like("tags", tag));
            if (CollUtil.isEmpty(pictures)) {
                queryWrapper.eq("id", -1);
            } else {
                List<Long> pictureIds = pictures.stream().map(Picture::getId).collect(Collectors.toList());
                List<PictureChild> children = pictureChildService.list(new QueryWrapper<PictureChild>().in("picture_id", pictureIds));
                List<Long> postIds = children.stream().map(PictureChild::getPostId).distinct().collect(Collectors.toList());
                if (CollUtil.isEmpty(postIds)) {
                    queryWrapper.eq("id", -1);
                } else {
                    queryWrapper.in("id", postIds);
                }
            }
        }

        // 构建热门查询
        if (ObjectUtil.isNotNull(hotPost)) {
            // 热门的定义：点赞数 * 0.3 + 收藏数 * 0.3 + 评论数 *0.2 + 点击数 * 0.2
            queryWrapper.orderByDesc("hot");
        }

        queryWrapper.orderBy(isSortFieldValid, "asc".equalsIgnoreCase(sortOrder), sortField);
        return queryWrapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean likePost(Long id) {
        // 先校验参数，再查数据库
        ExcUtils.throwIfTrue(id == null, ExceptionCode.PARAMETER_ERROR, "帖子ID不能为空");
        Post post = baseMapper.selectById(id);
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(post), ExceptionCode.DATABASE_ERROR, "帖子不存在");

        // 获取用户
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(user) || user.getId() == null, "用户不存在");
        Long userId = user.getId();
        Long postId = post.getId();

        // Redisson 分布式锁，key 粒度：用户+帖子
        RLock lock = redissonClient.getLock("lock:like:" + postId + ":" + userId);
        boolean locked = false;
        try {
            locked = lock.tryLock(0, 10, TimeUnit.SECONDS);
            ExcUtils.throwIfTrue(!locked, "操作频繁，请稍后再试");

            Long likesNum = post.getLikesNum();

            // 获取用户是否点赞过
            QueryWrapper<UserPostLikes> queryWrapper = new QueryWrapper<UserPostLikes>().eq("user_id", userId).eq("post_id",
                    postId);
            UserPostLikes userPostLikes = userPostLikesService.getOne(queryWrapper);
            boolean liked;
            if (ObjectUtil.isEmpty(userPostLikes) || userPostLikes.getId() == null) {
                // 没点赞过，添加点赞
                UserPostLikes userPostLike = new UserPostLikes();
                userPostLike.setUserId(userId);
                userPostLike.setPostId(postId);
                // 查入数据
                boolean insert = userPostLikesService.save(userPostLike);
                ExcUtils.throwIfTrue(!insert, "点赞失败");

                QueryWrapper<Post> postQueryWrapper = new QueryWrapper<>();
                postQueryWrapper.eq("id", postId);
                postQueryWrapper.eq("likes_num", likesNum);
                post.setLikesNum(likesNum + 1);
                int i = baseMapper.update(post, postQueryWrapper);

                ExcUtils.throwIfTrue(i != 1, "点赞失败，数据库错误");

                liked = true;
            } else {
                // 点赞过，删除点赞
                boolean delete = userPostLikesService.removeById(userPostLikes.getId());
                ExcUtils.throwIfTrue(!delete, "取消点赞失败");

                QueryWrapper<Post> postQueryWrapper = new QueryWrapper<>();
                postQueryWrapper.eq("id", postId);
                postQueryWrapper.eq("likes_num", likesNum);
                post.setLikesNum(Math.max(0, likesNum - 1));
                int i = baseMapper.update(post, postQueryWrapper);
                ExcUtils.throwIfTrue(i != 1, "取消点赞失败，数据库错误");

                liked = false;
            }
            return liked;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("获取锁被中断: like:{}:{}", postId, userId);
            throw new RuntimeException("操作被中断", e);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean collectPost(Long id) {
        ExcUtils.throwIfTrue(id == null, ExceptionCode.PARAMETER_ERROR, "帖子ID不能为空");
        Post post = baseMapper.selectById(id);
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(post), ExceptionCode.DATABASE_ERROR, "帖子不存在");

        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(user) || user.getId() == null, "用户不存在");
        Long userId = user.getId();
        Long postId = post.getId();

        // Redisson 分布式锁，key 粒度：用户+帖子
        RLock lock = redissonClient.getLock("lock:collect:" + postId + ":" + userId);
        boolean locked = false;
        try {
            locked = lock.tryLock(0, 10, TimeUnit.SECONDS);
            ExcUtils.throwIfTrue(!locked, "操作频繁，请稍后再试");

            Long collectsNum = post.getCollectsNum();

            // 查询用户是否已收藏
            QueryWrapper<UserPostCollect> queryWrapper = new QueryWrapper<UserPostCollect>()
                    .eq("user_id", userId).eq("post_id", postId);
            UserPostCollect userPostCollect = userPostCollectService.getOne(queryWrapper);
            boolean collected;

            if (ObjectUtil.isEmpty(userPostCollect) || userPostCollect.getId() == null) {
                // 未收藏，添加收藏
                UserPostCollect newCollect = new UserPostCollect();
                newCollect.setUserId(userId);
                newCollect.setPostId(postId);
                boolean insert = userPostCollectService.save(newCollect);
                ExcUtils.throwIfTrue(!insert, "收藏失败");

                QueryWrapper<Post> postQueryWrapper = new QueryWrapper<>();
                postQueryWrapper.eq("id", postId);
                postQueryWrapper.eq("collects_num", collectsNum);
                post.setCollectsNum(collectsNum + 1);
                int i = baseMapper.update(post, postQueryWrapper);
                ExcUtils.throwIfTrue(i != 1, "收藏失败，数据库错误");

                collected = true;
            } else {
                // 已收藏，取消收藏
                boolean delete = userPostCollectService.removeById(userPostCollect.getId());
                ExcUtils.throwIfTrue(!delete, "取消收藏失败");

                QueryWrapper<Post> postQueryWrapper = new QueryWrapper<>();
                postQueryWrapper.eq("id", postId);
                postQueryWrapper.eq("collects_num", collectsNum);
                post.setCollectsNum(Math.max(0, collectsNum - 1));
                int i = baseMapper.update(post, postQueryWrapper);
                ExcUtils.throwIfTrue(i != 1, "取消收藏失败，数据库错误");

                collected = false;
            }
            return collected;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("获取锁被中断: collect:{}:{}", postId, userId);
            throw new RuntimeException("操作被中断", e);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
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

        IPage<Post> postPage = baseMapper.selectPage(page, queryWrapper);
        return convertToPostListVO(postPage);
    }

    @Override
    public IPage<PostListVO> getMyCollects(PageRequest pageRequest) {
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(user), ExceptionCode.NOT_LOGIN);
        Long userId = user.getId();

        Page<UserPostCollect> collectPage = new Page<>(pageRequest.getCurrent(), pageRequest.getPageSize());
        IPage<UserPostCollect> collectResult = userPostCollectService.page(collectPage,
                new QueryWrapper<UserPostCollect>()
                        .eq("user_id", userId)
                        .orderByDesc("create_time"));

        if (collectResult.getRecords().isEmpty()) {
            return new Page<>(pageRequest.getCurrent(), pageRequest.getPageSize()).convert(p -> new PostListVO());
        }

        List<Long> postIds = collectResult.getRecords().stream()
                .map(UserPostCollect::getPostId).collect(Collectors.toList());
        Map<Long, Post> postMap = baseMapper.selectBatchIds(postIds).stream()
                .collect(Collectors.toMap(Post::getId, p -> p));
        List<Post> posts = postIds.stream()
                .map(postMap::get).filter(Objects::nonNull).collect(Collectors.toList());

        Page<Post> postPage = new Page<>(collectResult.getCurrent(), collectResult.getSize(), collectResult.getTotal());
        postPage.setRecords(posts);
        return convertToPostListVO(postPage);
    }

    @Override
    public IPage<PostListVO> getMyLikes(PageRequest pageRequest) {
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(user), ExceptionCode.NOT_LOGIN);
        Long userId = user.getId();

        Page<UserPostLikes> likesPage = new Page<>(pageRequest.getCurrent(), pageRequest.getPageSize());
        IPage<UserPostLikes> likesResult = userPostLikesService.page(likesPage,
                new QueryWrapper<UserPostLikes>()
                        .eq("user_id", userId)
                        .orderByDesc("create_time"));

        if (likesResult.getRecords().isEmpty()) {
            return new Page<>(pageRequest.getCurrent(), pageRequest.getPageSize()).convert(p -> new PostListVO());
        }

        List<Long> postIds = likesResult.getRecords().stream()
                .map(UserPostLikes::getPostId).collect(Collectors.toList());
        Map<Long, Post> postMap = baseMapper.selectBatchIds(postIds).stream()
                .collect(Collectors.toMap(Post::getId, p -> p));
        List<Post> posts = postIds.stream()
                .map(postMap::get).filter(Objects::nonNull).collect(Collectors.toList());

        Page<Post> postPage = new Page<>(likesResult.getCurrent(), likesResult.getSize(), likesResult.getTotal());
        postPage.setRecords(posts);
        return convertToPostListVO(postPage);
    }

    @Override
    public PictureListPageVO getPictureList(GetPictureBySpaceRequest getPictureBySpaceRequest) {
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

        PictureListPageVO result = new PictureListPageVO();
        result.setRecords(editPostVOS);
        result.setTotal(pageResult.getTotal());
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
        IPage<Post> postPage = baseMapper.selectPage(page, queryWrapper);
        return convertToPostListVO(postPage);
    }

    @Override
    public void reviewPost(Long id, Integer status) {
        ExcUtils.throwIfTrue(id == null, ExceptionCode.PARAMETER_ERROR, "帖子ID不能为空");
        ExcUtils.throwIfTrue(status == null
                || (status != POST_STATUS_DRAFT && status != POST_STATUS_PUBLISHED && status != POST_STATUS_REJECTED),
                ExceptionCode.PARAMETER_ERROR, "状态值不合法");
        Post post = baseMapper.selectById(id);
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(post) || post.getId() == null, ExceptionCode.NOT_FOUND, "帖子不存在");
        post.setStatus(status);
        int i = baseMapper.updateById(post);
        ExcUtils.throwIfTrue(i != 1, ExceptionCode.DATABASE_ERROR, "审核失败");

        // 清除帖子列表缓存
        clearPostListCache();
    }

    @Override
    public void adminDeletePost(Long id) {
        Post post = baseMapper.selectById(id);
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(post) || post.getId() == null, ExceptionCode.NOT_FOUND, "帖子不存在");
        int i = baseMapper.deleteById(id);
        ExcUtils.throwIfTrue(i != 1, ExceptionCode.DATABASE_ERROR, "删除失败");

        // 清除帖子列表缓存
        clearPostListCache();
    }

    @Override
    public IPage<PostListVO> getRecommendPosts(PageRequest pageRequest, Long userId) {
        // 1. 查用户画像，取权重最高的5个标签
        List<UserInterestProfile> topProfiles = userInterestProfileService.list(
                new LambdaQueryWrapper<UserInterestProfile>()
                        .eq(UserInterestProfile::getUserId, userId)
                        .orderByDesc(UserInterestProfile::getWeight)
                        .last("LIMIT 5"));

        // 2. 冷启动：无画像 → fallback 热度排序
        if (CollectionUtils.isEmpty(topProfiles)) {
            Page<Post> page = new Page<>(pageRequest.getCurrent(), pageRequest.getPageSize());
            return convertToPostListVO(baseMapper.selectPage(page, new LambdaQueryWrapper<Post>()
                    .eq(Post::getStatus, POST_STATUS_PUBLISHED)
                    .orderByDesc(Post::getHot)));
        }

        // 3. 构建标签JSON数组
        List<String> tags = topProfiles.stream()
                .map(UserInterestProfile::getTag)
                .collect(Collectors.toList());
        String tagsJson = JSON.toJSONString(tags);

        Page<Post> page = new Page<>(pageRequest.getCurrent(), pageRequest.getPageSize());

        LambdaQueryWrapper<Post> lqw = new LambdaQueryWrapper<Post>()
                .eq(Post::getStatus, POST_STATUS_PUBLISHED)
                .apply("id IN (SELECT DISTINCT pc.post_id FROM picture_child pc "
                        + "JOIN picture pic ON pc.picture_id = pic.id "
                        + "WHERE pic.tags IS NOT NULL AND pic.tags != '' "
                        + "AND JSON_OVERLAPS(pic.tags, {0}))", tagsJson)
                .and(w -> w
                        .notInSql(Post::getId, "SELECT id FROM post WHERE user_id = " + userId)
                        .notInSql(Post::getId, "SELECT post_id FROM user_post_likes WHERE user_id = " + userId)
                        .notInSql(Post::getId, "SELECT post_id FROM user_post_collect WHERE user_id = " + userId))
                .orderByDesc(Post::getHot);

        return convertToPostListVO(baseMapper.selectPage(page, lqw));
    }

    /**
     * 将帖子分页结果转换为 PostListVO 分页结果
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
            User postUser = finalUserMap.get(post.getUserId());
            if (postUser != null) {
                vo.setUsername(postUser.getUsername());
                vo.setAvatar(postUser.getAvatar());
            }
            vo.setUrl(finalCoverUrlMap.get(post.getCover()));
            // 当前用户是否已收藏
            vo.setIsCollected(finalCollectedPostIds.contains(post.getId()));
            return vo;
        });
    }

    /**
     * 构建帖子列表缓存键
     * @param request 查询请求
     * @return 缓存键
     */
    private String buildPostListCacheKey(PostQueryRequest request) {
        StringBuilder keyBuilder = new StringBuilder(POST_LIST_CACHE_KEY);

        if (request == null) {
            keyBuilder.append("default:1:10");
            return keyBuilder.toString();
        }

        // 排序字段
        String sortField = ObjectUtil.isNotEmpty(request.getSortField())
                ? request.getSortField() : "default";
        keyBuilder.append(sortField).append(":");

        // 排序方式
        keyBuilder.append(request.getSortOrder() != null ? request.getSortOrder() : "desc").append(":");

        // 状态筛选
        keyBuilder.append(request.getStatus() != null ? request.getStatus() : "all").append(":");

        // 分页参数
        keyBuilder.append(request.getCurrent() > 0 ? request.getCurrent() : 1).append(":");
        keyBuilder.append(request.getPageSize() > 0 ? request.getPageSize() : 10);

        return keyBuilder.toString();
    }

    /**
     * 清除帖子列表缓存
     * 在帖子发布、审核、删除时调用
     */
    public void clearPostListCache() {
        try {
            ScanOptions options = ScanOptions.scanOptions()
                    .match(POST_LIST_CACHE_KEY + "*")
                    .count(100)
                    .build();
            List<String> keys = new ArrayList<>();
            try (Cursor<String> cursor = stringRedisTemplate.scan(options)) {
                while (cursor.hasNext()) {
                    keys.add(cursor.next());
                }
            }
            if (!keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
                log.info("清除帖子列表L2缓存，共 {} 个", keys.size());
            }
        } catch (Exception e) {
            log.warn("清除帖子列表L2缓存失败", e);
        }
        cacheManager.getPostListCache().evictAllL1();
    }
}
