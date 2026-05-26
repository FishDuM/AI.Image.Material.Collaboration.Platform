package hk.ljx.fishpicsbackend.comment;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import hk.ljx.fishpicsbackend.comment.dto.CommentQueryRequest;
import hk.ljx.fishpicsbackend.comment.dto.CreateCommentRequest;
import hk.ljx.fishpicsbackend.comment.Comment;
import hk.ljx.fishpicsbackend.post.Post;
import hk.ljx.fishpicsbackend.user.User;
import hk.ljx.fishpicsbackend.mapper.CommentMapper;
import hk.ljx.fishpicsbackend.comment.CommentService;
import hk.ljx.fishpicsbackend.post.PostService;
import hk.ljx.fishpicsbackend.user.UserService;
import hk.ljx.fishpicsbackend.comment.vo.CommentVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

import static hk.ljx.fishpicsbackend.common.constants.UserConstants.ADMIN;

/**
* @author 30574
* @description 针对表【comment(评论表)】的数据库操作Service实现
* @createDate 2026-04-13 21:24:56
*/
@Slf4j
@Service
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment>
    implements CommentService {

    @Resource
    private UserService userService;

    @Resource
    private PostService postService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createComment(CreateCommentRequest req) {
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(req.getPostId()), "帖子ID不能为空");
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(req.getContent()), "评论内容不能为空");

        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(user), ExceptionCode.NOT_LOGIN);

        // 校验父评论（若为回复）
        if (req.getParentId() != null) {
            Comment parent = baseMapper.selectById(req.getParentId());
            ExcUtils.throwIfTrue(ObjectUtil.isEmpty(parent), "父评论不存在");
            ExcUtils.throwIfTrue(!Objects.equals(parent.getPostId(), req.getPostId()), "父评论不属于该帖子");
        }

        // 校验帖子存在
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(postService.getById(req.getPostId())), "帖子不存在");

        Comment comment = new Comment();
        comment.setUserId(user.getId());
        comment.setPostId(req.getPostId());
        comment.setContent(req.getContent());
        comment.setParentId(req.getParentId());
        comment.setToUserId(req.getToUserId());
        comment.setStatus(2); // 待审核
        comment.setCreateTime(new Date());

        baseMapper.insert(comment);

        LambdaUpdateWrapper<Post> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Post::getId, req.getPostId())
                .setSql("comment_num = comment_num + 1");
        postService.update(updateWrapper);

        return comment.getId();
    }

    @Override
    public IPage<CommentVO> getCommentPage(CommentQueryRequest req) {
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(req.getPostId()), "帖子ID不能为空");

        User currentUser = UserHolder.getUser();
        boolean isNotAdmin = currentUser == null || !ADMIN.equals(currentUser.getRole());
        Long currentUserId = currentUser != null ? currentUser.getId() : null;

        // 分页查询一级评论（parentId IS NULL）
        Page<Comment> page = new Page<>(req.getCurrent(), req.getPageSize());
        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Comment::getPostId, req.getPostId())
                .isNull(Comment::getParentId)
                .orderByDesc(Comment::getCreateTime);

        if (isNotAdmin) {
            Long uid = currentUserId;
            wrapper.and(w -> w.eq(Comment::getStatus, 1)
                    .or(w2 -> w2.eq(Comment::getUserId, uid).ne(Comment::getStatus, 0)));
        }

        IPage<Comment> commentPage = baseMapper.selectPage(page, wrapper);

        // 收集一级评论ID，批量查询回复
        List<Long> parentIds = commentPage.getRecords().stream()
                .map(Comment::getId)
                .collect(Collectors.toList());

        final List<Comment> allReplies;
        if (CollUtil.isNotEmpty(parentIds)) {
            LambdaQueryWrapper<Comment> replyWrapper = new LambdaQueryWrapper<>();
            replyWrapper.in(Comment::getParentId, parentIds)
                    .orderByAsc(Comment::getCreateTime);
            if (isNotAdmin) {
                Long uid = currentUserId;
                replyWrapper.and(w -> w.eq(Comment::getStatus, 1)
                        .or(w2 -> w2.eq(Comment::getUserId, uid).ne(Comment::getStatus, 0)));
            }
            allReplies = baseMapper.selectList(replyWrapper);
        } else {
            allReplies = Collections.emptyList();
        }

        // 收集所有 userId 和 toUserId，批量查用户
        final List<Comment> finalReplies = allReplies;
        Set<Long> userIds = new HashSet<>();
        commentPage.getRecords().forEach(c -> userIds.add(c.getUserId()));
        finalReplies.forEach(r -> {
            userIds.add(r.getUserId());
            if (r.getToUserId() != null) {
                userIds.add(r.getToUserId().longValue());
            }
        });

        final Map<Long, User> userMap;
        if (CollUtil.isNotEmpty(userIds)) {
            userMap = userService.listByIds(userIds).stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(User::getId, u -> u));
        } else {
            userMap = Collections.emptyMap();
        }

        // 按 parentId 分组回复
        final Map<Long, List<Comment>> replyMap = finalReplies.stream()
                .collect(Collectors.groupingBy(Comment::getParentId));

        return commentPage.convert(comment -> toVO(comment, replyMap, userMap));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(Long commentId) {
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(user), ExceptionCode.NOT_LOGIN);

        Comment comment = baseMapper.selectById(commentId);
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(comment), "评论不存在");
        ExcUtils.throwIfTrue(!Objects.equals(comment.getUserId(), user.getId()), "只能删除自己的评论");

        boolean wasActive = !comment.getStatus().equals(0);

        comment.setStatus(0);
        baseMapper.updateById(comment);

        if (wasActive) {
            LambdaUpdateWrapper<Post> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(Post::getId, comment.getPostId())
                    .setSql("comment_num = GREATEST(comment_num - 1, 0)");
            postService.update(updateWrapper);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewComment(Long commentId, Integer status) {
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(user), ExceptionCode.NOT_LOGIN);
        ExcUtils.throwIfTrue(!ADMIN.equals(user.getRole()), ExceptionCode.FORBIDDEN, "无权操作");
        ExcUtils.throwIfTrue(!status.equals(1) && !status.equals(0), "状态值无效");

        Comment comment = baseMapper.selectById(commentId);
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(comment), "评论不存在");

        Integer oldStatus = comment.getStatus();
        comment.setStatus(status);
        baseMapper.updateById(comment);

        // 审核改变评论状态时，同步更新帖子评论数
        if (oldStatus.equals(0) && !status.equals(0)) {
            LambdaUpdateWrapper<Post> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(Post::getId, comment.getPostId())
                    .setSql("comment_num = comment_num + 1");
            postService.update(updateWrapper);
        } else if (!oldStatus.equals(0) && status.equals(0)) {
            LambdaUpdateWrapper<Post> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(Post::getId, comment.getPostId())
                    .setSql("comment_num = GREATEST(comment_num - 1, 0)");
            postService.update(updateWrapper);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminDeleteComment(Long commentId) {
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(user), ExceptionCode.NOT_LOGIN);
        ExcUtils.throwIfTrue(!ADMIN.equals(user.getRole()), ExceptionCode.FORBIDDEN, "无权操作");

        Comment comment = baseMapper.selectById(commentId);
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(comment), "评论不存在");

        int deletedActiveCount = 0;

        // 一级评论连带删除所有二级回复
        if (comment.getParentId() == null) {
            LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Comment::getParentId, commentId);
            List<Comment> replies = baseMapper.selectList(wrapper);
            deletedActiveCount += (int) replies.stream().filter(r -> !r.getStatus().equals(0)).count();
            baseMapper.delete(wrapper);
        }

        if (!comment.getStatus().equals(0)) {
            deletedActiveCount++;
        }

        baseMapper.deleteById(commentId);

        if (deletedActiveCount > 0) {
            LambdaUpdateWrapper<Post> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(Post::getId, comment.getPostId())
                    .setSql("comment_num = GREATEST(comment_num - " + deletedActiveCount + ", 0)");
            postService.update(updateWrapper);
        }
    }

    @Override
    public IPage<CommentVO> getAdminCommentPage(CommentQueryRequest req) {
        Page<Comment> page = new Page<>(req.getCurrent(), req.getPageSize());
        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNull(Comment::getParentId)
                .orderByDesc(Comment::getCreateTime);

        if (req.getStatus() != null) {
            wrapper.eq(Comment::getStatus, req.getStatus());
        }

        IPage<Comment> commentPage = baseMapper.selectPage(page, wrapper);

        // 收集一级评论ID，批量查询回复
        List<Long> parentIds = commentPage.getRecords().stream()
                .map(Comment::getId)
                .collect(Collectors.toList());

        final List<Comment> allReplies;
        if (CollUtil.isNotEmpty(parentIds)) {
            allReplies = baseMapper.selectList(new LambdaQueryWrapper<Comment>()
                    .in(Comment::getParentId, parentIds)
                    .orderByAsc(Comment::getCreateTime));
        } else {
            allReplies = Collections.emptyList();
        }

        // 收集帖子ID，批量查帖子
        Set<Long> postIds = new HashSet<>();
        commentPage.getRecords().forEach(c -> postIds.add(c.getPostId()));
        allReplies.forEach(r -> postIds.add(r.getPostId()));

        final Map<Long, Post> postMap;
        if (CollUtil.isNotEmpty(postIds)) {
            postMap = postService.listByIds(postIds).stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(Post::getId, p -> p));
        } else {
            postMap = Collections.emptyMap();
        }

        // 收集所有 userId 和 toUserId
        Set<Long> userIds = new HashSet<>();
        commentPage.getRecords().forEach(c -> userIds.add(c.getUserId()));
        allReplies.forEach(r -> {
            userIds.add(r.getUserId());
            if (r.getToUserId() != null) {
                userIds.add(r.getToUserId().longValue());
            }
        });

        final Map<Long, User> userMap;
        if (CollUtil.isNotEmpty(userIds)) {
            userMap = userService.listByIds(userIds).stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(User::getId, u -> u));
        } else {
            userMap = Collections.emptyMap();
        }

        final Map<Long, List<Comment>> replyMap = allReplies.stream()
                .collect(Collectors.groupingBy(Comment::getParentId));

        return commentPage.convert(comment -> {
            CommentVO vo = toVO(comment, replyMap, userMap);
            Post post = postMap.get(comment.getPostId());
            if (post != null) {
                vo.setPostTitle(post.getTitle());
            }
            return vo;
        });
    }

    private CommentVO toVO(Comment comment, Map<Long, List<Comment>> replyMap, Map<Long, User> userMap) {
        CommentVO vo = new CommentVO();
        BeanUtil.copyProperties(comment, vo);

        User commentUser = userMap.get(comment.getUserId());
        if (commentUser != null) {
            vo.setUsername(commentUser.getUsername());
            vo.setAvatar(commentUser.getAvatar());
        }

        if (comment.getToUserId() != null) {
            User toUser = userMap.get(comment.getToUserId().longValue());
            if (toUser != null) {
                vo.setToUsername(toUser.getUsername());
            }
        }

        // 嵌套回复
        List<Comment> replies = replyMap.getOrDefault(comment.getId(), Collections.emptyList());
        if (CollUtil.isNotEmpty(replies)) {
            vo.setReplies(replies.stream().map(r -> toVO(r, replyMap, userMap)).collect(Collectors.toList()));
        }

        return vo;
    }
}
