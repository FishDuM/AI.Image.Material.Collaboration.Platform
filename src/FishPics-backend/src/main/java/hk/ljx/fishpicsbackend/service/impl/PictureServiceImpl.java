package hk.ljx.fishpicsbackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.dto.picture.DeleteByIdList;
import hk.ljx.fishpicsbackend.dto.picture.PictureMessage;
import hk.ljx.fishpicsbackend.entity.Picture;
import hk.ljx.fishpicsbackend.entity.Post;
import hk.ljx.fishpicsbackend.entity.Space;
import hk.ljx.fishpicsbackend.entity.User;
import hk.ljx.fishpicsbackend.mapper.PostMapper;
import hk.ljx.fishpicsbackend.mapper.SpaceMapper;
import hk.ljx.fishpicsbackend.mapper.UserMapper;
import hk.ljx.fishpicsbackend.service.*;
import hk.ljx.fishpicsbackend.mapper.PictureMapper;
import hk.ljx.fishpicsbackend.vo.picture.PictureAdminVO;
import hk.ljx.fishpicsbackend.vo.picture.PictureListVO;
import hk.ljx.fishpicsbackend.vo.picture.PicturePostVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    @Resource
    private SpaceService spaceService;

    @Resource
    private SpaceMapper spaceMapper;

    @Resource
    private LoginUser loginUser;
    @Autowired
    private PostMapper postMapper;

    @Override
    public String uploadAvatar(MultipartFile file, Long id, HttpServletRequest request) {
        User userLogin = loginUser.getLoginUser(request);
        ExcUtils.throwIfTrue(userLogin == null || userLogin.getId() == null, "请先登录");
        User user = userMapper.selectById(userLogin.getId());
        ExcUtils.throwIfTrue(user == null, "用户不存在");
        // 只有自己或管理员可以修改头像
        ExcUtils.throwIfTrue(!userLogin.getId().equals(id) && !userLogin.getRole().equals(ADMIN), "没有权限");
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
        User userLogin = loginUser.getLoginUser(request);
        Long userId = userLogin.getId();
        ExcUtils.throwIfTrue(ObjUtil.isEmpty(userLogin) || userId == null, "请先登录");
        // 上传图片
        String key = cosService.uploadPicture(file);
        // 获取图片信息
        PictureMessage pictureMessage = cosService.getPictureMessage(key);
        Picture picture = new Picture();
        BeanUtil.copyProperties(pictureMessage, picture);
        picture.setUserId(userId);
        // 判断私人空间磁盘是否充足
        Long size = picture.getSize();
        List<Space> spaceList = spaceService.listSpace(0, request);
        ExcUtils.throwIfTrue(spaceList == null || spaceList.isEmpty(), "私人空间不存在，请联系管理员");
        Space space = spaceList.get(0);
        Long usedSize = space.getSize();
        Long storageSize = space.getStorageSize();
        long updateSize = usedSize + size;
        if (updateSize > storageSize){
            cosService.deletePicture(key);
            throw new BaseException(ExceptionCode.UNAUTHORIZED,"私人空间磁盘不足，请升级空间或删除图片");
        }
        int update = spaceMapper.update(space, new UpdateWrapper<Space>().set("size", updateSize).eq("id", space.getId()));
        ExcUtils.throwIfTrue(update <= 0, "上传失败，数据库错误");
        picture.setSpaceId(space.getId());
        // 管理员上传直接通过，普通用户上传需要审核
        if (ADMIN.equals(userLogin.getRole())) {
            picture.setStatus(1);
        } else {
            picture.setStatus(2);
        }
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
    public IPage<PictureListVO> getPictureList(int current, int pageSize, int flag) {
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<Picture>()
                .eq("status", flag)
                .eq("is_private", 1)
                .isNotNull("url")
                .ne("url", "")
                .orderByDesc("create_time");
        Page<Picture> page = new Page<>(current, pageSize);
        IPage<Picture> picturePage = pictureMapper.selectPage(page, queryWrapper);
        return picturePage.convert(p -> new PictureListVO(p.getId(), p.getUrl()));
    }

    @Override
    public IPage<PictureAdminVO> getAdminPictureList(int current, int pageSize, Integer status) {
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<Picture>()
                .isNotNull("url")
                .ne("url", "")
                .orderByDesc("create_time");
        if (status == 0 || status == 1 || status == 2) {
            queryWrapper.eq("status", status);
        }
        if (status == 4) {
            queryWrapper.eq("is_private", 1);
        }

        Page<Picture> page = new Page<>(current, pageSize);
        IPage<Picture> picturePage = pictureMapper.selectPage(page, queryWrapper);
        return picturePage.convert(p -> new PictureAdminVO(
                p.getId(),
                p.getUrl(),
                p.getWidth(),
                p.getHeight(),
                p.getSize(),
                p.getStatus(),
                p.getCreateTime(),
                p.getUserId(),
                p.getIsPrivate()
        ));
    }

    @Override
    public void reviewPicture(Long pictureId, Integer status, Integer selected) {
        ExcUtils.throwIfTrue(pictureId == null, "图片id不能为空");
        Picture picture = pictureMapper.selectById(pictureId);
        ExcUtils.throwIfTrue(picture == null, "图片不存在");
        if (status != null) {
            ExcUtils.throwIfTrue((status != 0 && status != 1), "状态值无效");
            picture.setStatus(status);
        }
        if (selected != null) {
            ExcUtils.throwIfTrue((selected != 0 && selected != 1), "精选值无效");
            picture.setIsPrivate(selected);
        }
        ExcUtils.throwIfTrue(pictureMapper.updateById(picture) != 1, "审核更新失败");
    }

    @Override
    public String deletePicture(DeleteByIdList deleteByIdList, HttpServletRequest request) {
        List<Long> ids = deleteByIdList.getIds();
        ExcUtils.throwIfTrue(CollUtil.isEmpty(ids), "图片id不能为空");

        User user = loginUser.getLoginUser(request);
        String role = user.getRole();
        // 批量查询图片
        List<Picture> pictureList = pictureMapper.selectList(new QueryWrapper<Picture>().in("id", ids));
        ExcUtils.throwIfTrue(CollUtil.isEmpty(pictureList), "图片不存在");
        Set<Long> userIds = new HashSet<>();
        pictureList.forEach(picture -> userIds.add(picture.getUserId()));
        // 判断是否为图片的主人或者管理员
        ExcUtils.throwIfFalse(role.equals(ADMIN) || userIds.stream().findFirst().map(id -> id.equals(user.getId())).orElse(false) || userIds.size() != 1, ExceptionCode.UNAUTHORIZED, "没有权限删除图片");
        // 帖子封面图禁止删除
        List<Post> posts = postMapper.selectList(new QueryWrapper<Post>().in("cover", ids));
        if (!posts.isEmpty()){
            posts.forEach(post -> {
                ids.remove(post.getCover());
            });
        }
        int i = pictureMapper.delete(new QueryWrapper<Picture>().in("id", ids));
        ExcUtils.throwIfTrue(i == 0, "删除失败");
        pictureList.forEach(picture -> cosService.deletePictureByUrl(picture.getUrl()));
        return !posts.isEmpty() ? "删除成功，但有"+ posts.size() + "个图片为帖子封面无法删除" : "删除成功";
    }
}




