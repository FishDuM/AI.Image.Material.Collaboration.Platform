package hk.ljx.fishpicsbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import hk.ljx.fishpicsbackend.dto.base.PageRequest;
import hk.ljx.fishpicsbackend.dto.post.*;
import hk.ljx.fishpicsbackend.entity.Post;
import com.baomidou.mybatisplus.extension.service.IService;
import hk.ljx.fishpicsbackend.vo.picture.PictureListByEditPostVO;
import hk.ljx.fishpicsbackend.vo.post.PostDetailVO;
import hk.ljx.fishpicsbackend.vo.post.PostListVO;

import javax.servlet.http.HttpServletRequest;
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
    void uploadPost(UploadPostRequest uploadPostRequest, HttpServletRequest request);

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
     * @param request         request
     */
    void editPost(EditPostRequest editPostRequest, HttpServletRequest request);

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
     * @param id      帖子id
     * @param request request
     */
    void likePost(Long id, HttpServletRequest request);

    /**
     * 获取本人发布的帖子列表（分页）
     *
     * @param pageRequest 分页参数
     * @param request     request
     * @return 帖子列表
     */
    IPage<PostListVO> getMyPosts(PageRequest pageRequest, HttpServletRequest request);

    /**
     * 获取本人收藏的帖子列表（分页）
     *
     * @param pageRequest 分页参数
     * @param request     request
     * @return 帖子列表
     */
    IPage<PostListVO> getMyCollects(PageRequest pageRequest, HttpServletRequest request);

    /**
     * 获取本人点赞的帖子列表（分页）
     *
     * @param pageRequest 分页参数
     * @param request     request
     * @return 帖子列表
     */
    IPage<PostListVO> getMyLikes(PageRequest pageRequest, HttpServletRequest request);

    /**
     * 获取空间图片（去除帖子已有图片）
     *
     * @param getPictureBySpaceRequest request
     * @return 图片列表
     */
    List<PictureListByEditPostVO> getPictureList(GetPictureBySpaceRequest getPictureBySpaceRequest, HttpServletRequest request);
}
