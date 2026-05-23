package hk.ljx.fishpicsbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import hk.ljx.fishpicsbackend.dto.base.PageRequest;
import hk.ljx.fishpicsbackend.dto.post.*;
import hk.ljx.fishpicsbackend.entity.Picture;
import hk.ljx.fishpicsbackend.entity.Post;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.Map;
import hk.ljx.fishpicsbackend.vo.picture.PictureListByEditPostVO;
import hk.ljx.fishpicsbackend.vo.post.PostDetailVO;
import hk.ljx.fishpicsbackend.vo.post.PostListVO;

import java.util.List;

/**
 * @author 30574
 * @description 针对表【post(帖子表)】的数据库操作Service
 * @createDate 2026-04-13 21:24:41
 */
public interface PostService extends IService<Post> {

    /**
     * 上传帖子
     *
     * @param uploadPostRequest 帖子内容
     */
    void uploadPost(UploadPostRequest uploadPostRequest);

    /**
     * 获取帖子详情
     *
     * @param id 帖子 id
     * @return 帖子详情
     */
    PostDetailVO getPost(Long id);

    /**
     * 编辑帖子
     *
     * @param editPostRequest 帖子内容
     */
    void editPost(EditPostRequest editPostRequest);

    /**
     * 判断是否是自己的图片并返回原图
     *
     * @param userId  用户ID
     * @param imageId 图片ID列表
     * @return 图片列表
     */
    List<Picture> isMyPicture(Long userId, List<Long> imageId);

    /**
     * 批量保存子图片
     *
     * @param pictures 图片列表
     * @param postId   帖子ID
     */
    void savePictureChildBatch(List<Picture> pictures, Long postId);

    /**
     * 获取帖子列表
     *
     * @param postQueryRequest 查询条件
     * @return 帖子列表
     */
    IPage<PostListVO> getPostList(PostQueryRequest postQueryRequest);

    /**
     * 构造帖子查询对象
     *
     * @param postQueryWrapper 查询条件
     * @return 查询对象
     */
    QueryWrapper<Post> newQueryWrapper(PostQueryWrapper postQueryWrapper);

    /**
     * 点赞帖子
     *
     * @param id 帖子id
     */
    void likePost(Long id);

    /**
     * 收藏/取消收藏帖子（toggle）
     *
     * @param id 帖子id
     * @return 当前收藏状态（true-已收藏, false-已取消）
     */
    boolean collectPost(Long id);

    /**
     * 获取本人发布的帖子列表（分页）
     *
     * @param pageRequest 分页参数
     * @return 帖子列表
     */
    IPage<PostListVO> getMyPosts(PageRequest pageRequest);

    /**
     * 获取本人收藏的帖子列表（分页）
     *
     * @param pageRequest 分页参数
     * @return 帖子列表
     */
    IPage<PostListVO> getMyCollects(PageRequest pageRequest);

    /**
     * 获取本人点赞的帖子列表（分页）
     *
     * @param pageRequest 分页参数
     * @return 帖子列表
     */
    IPage<PostListVO> getMyLikes(PageRequest pageRequest);

    /**
     * 获取空间图片（去除帖子已有图片）
     *
     * @param getPictureBySpaceRequest request
     * @return 图片列表
     */
    Map<String, Object> getPictureList(GetPictureBySpaceRequest getPictureBySpaceRequest);

    /**
     * 管理员分页查看所有帖子
     *
     * @param req 查询条件
     * @return 帖子列表
     */
    IPage<PostListVO> getAdminPostPage(PostQueryRequest req);

    /**
     * 管理员审核帖子
     *
     * @param id     帖子ID
     * @param status 目标状态
     */
    void reviewPost(Long id, Integer status);

    /**
     * 管理员删除帖子
     *
     * @param id 帖子ID
     */
    void adminDeletePost(Long id);
}
