package hk.ljx.fishpicsbackend.picture.service.impl;
import hk.ljx.fishpicsbackend.picture.entity.Picture;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import hk.ljx.fishpicsbackend.common.dto.PageRequest;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.utils.CosService;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import hk.ljx.fishpicsbackend.mapper.PictureMapper;
import hk.ljx.fishpicsbackend.picture.dto.DeleteByIdList;
import hk.ljx.fishpicsbackend.picture.dto.PictureMessage;
import hk.ljx.fishpicsbackend.picture.dto.PictureUpdateRequest;
import hk.ljx.fishpicsbackend.picture.service.PictureService;
import hk.ljx.fishpicsbackend.picture.vo.PictureAdminVO;
import hk.ljx.fishpicsbackend.picture.vo.PictureEditVO;
import hk.ljx.fishpicsbackend.picture.vo.PictureListVO;
import hk.ljx.fishpicsbackend.post.entity.Post;
import hk.ljx.fishpicsbackend.post.service.PostService;
import hk.ljx.fishpicsbackend.space.entity.Space;
import hk.ljx.fishpicsbackend.space.service.SpaceService;
import hk.ljx.fishpicsbackend.system.service.PicSystemService;
import hk.ljx.fishpicsbackend.user.entity.User;
import hk.ljx.fishpicsbackend.user.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static hk.ljx.fishpicsbackend.common.constants.UserConstants.ADMIN;

/**
 * @author 30574
 * @description 针对表【picture(图片表)】的数据库操作Service实现
 * @createDate 2026-04-13 21:24:49
 */
@Slf4j
@Service
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
        implements PictureService {

    @Resource
    private CosService cosService;

    @Resource
    private PictureMapper pictureMapper;

    @Lazy
    @Resource
    private SpaceService spaceService;

    @Lazy
    @Resource
    private UserService userService;

    @Lazy
    @Resource
    private PostService postService;

    @Resource
    private PicSystemService picSystemService;

    @Override
    public String uploadAvatar(MultipartFile file, Long id) {
        User userLogin = UserHolder.getUser();
        ExcUtils.throwIfTrue(ObjUtil.isEmpty(userLogin), ExceptionCode.NOT_LOGIN);
        // 只有自己或管理员可以修改头像
        ExcUtils.throwIfTrue(!userLogin.getId().equals(id) && !userLogin.getRole().equals(ADMIN), "没有权限");
        User user;
        if (id != null && !id.equals(userLogin.getId())) {
            user = userService.getById(id);
            ExcUtils.throwIfTrue(user == null, "该用户不存在");
        } else {
            user = userService.getById(userLogin.getId());
            ExcUtils.throwIfTrue(user == null, "用户不存在");
        }
        // 删除旧头像
        if (user.getAvatar() != null) {
            cosService.deletePictureByUrl(user.getAvatar());
        }
        String url = cosService.uploadAndGetImageUrl(file);
        user.setAvatar(url);
        ExcUtils.throwIfFalse(userService.updateById(user), ExceptionCode.DATABASE_ERROR, "上传失败，数据库错误");
        return url;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Picture uploadPicture(MultipartFile file, Long targetSpaceId) {
        User userLogin = UserHolder.getUser();
        Long userId = userLogin.getId();
        Integer level = userLogin.getLevel();
        ExcUtils.throwIfTrue(ObjUtil.isEmpty(userLogin) || userId == null, "请先登录");

        // 判断用户等级 0-普通 3MB，1-VIP 5MB，2-SVIP 20MB
        if (level == 0) {
            ExcUtils.throwIfTrue(file.getSize() > 1024 * 1024 * 3, "普通用户上传图片大小不能超过3MB");
        } else if (level == 1) {
            ExcUtils.throwIfTrue(file.getSize() > 1024 * 1024 * 5, "VIP用户上传图片大小不能超过5MB");
        } else if (level == 2) {
            ExcUtils.throwIfTrue(file.getSize() > 1024 * 1024 * 20, "SVIP用户上传图片大小不能超过20MB");
        }

        // 上传图片
        String key = cosService.uploadPicture(file);
        // 获取图片信息
        PictureMessage pictureMessage = cosService.getPictureMessage(key);
        Picture picture = new Picture();
        BeanUtil.copyProperties(pictureMessage, picture);
        picture.setUserId(userId);
        // BeanUtil 可能无法将 String size 转为 Long，手动转换兜底
        if (picture.getSize() == null && pictureMessage.getSize() != null) {
            picture.setSize(Long.parseLong(pictureMessage.getSize()));
        }
        ExcUtils.throwIfTrue(picture.getSize() == null, ExceptionCode.INTERNAL_SERVER_ERROR, "获取图片大小失败");

        long size = picture.getSize();
        Space space;
        if (targetSpaceId != null) {
            space = spaceService.getById(targetSpaceId);
            ExcUtils.throwIfTrue(space == null, "目标空间不存在");
        } else {
            List<hk.ljx.fishpicsbackend.space.vo.SpaceVO> spaceList = spaceService.listSpace(0);
            ExcUtils.throwIfTrue(spaceList == null || spaceList.isEmpty(), "私人空间不存在，请联系管理员");
            space = spaceService.getById(spaceList.get(0).getId());
        }

        long usedSize = space.getSize() != null ? space.getSize() : 0L;
        long storageSize = space.getStorageSize() != null ? space.getStorageSize() : 0L;
        long updateSize = usedSize + size;
        if (updateSize > storageSize) {
            cosService.deletePicture(key);
            throw new BaseException(ExceptionCode.UNAUTHORIZED, "空间磁盘不足，请升级空间或删除图片");
        }
        Boolean update = spaceService.update(space,
                new UpdateWrapper<Space>().set("size", updateSize).eq("id", space.getId()));
        ExcUtils.throwIfFalse(update, ExceptionCode.DATABASE_ERROR, "上传失败，数据库错误");
        picture.setSpaceId(space.getId());
        // 管理员上传直接通过，普通用户上传需要审核
        if (ADMIN.equals(userLogin.getRole())) {
            picture.setStatus(1);
        } else {
            picture.setStatus(2);
        }
        ExcUtils.throwIfTrue(pictureMapper.insert(picture) != 1, "上传失败，数据库错误");
        return picture;
    }

    @Override
    public IPage<PictureListVO> getPictureList(PageRequest pageRequest) {
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<Picture>()
                .eq("status", 1)
                .eq("is_private", 1)
                .isNotNull("url")
                .ne("url", "")
                .orderByDesc("create_time");
        Page<Picture> page = new Page<>(pageRequest.getCurrent(), pageRequest.getPageSize());
        IPage<Picture> picturePage = pictureMapper.selectPage(page, queryWrapper);
        return picturePage.convert(p -> new PictureListVO(p.getId(), p.getUrl(), StrUtil.split(p.getTags(), ",")));
    }

    @Override
    public IPage<PictureAdminVO> getAdminPictureList(PageRequest pageRequest) {
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<Picture>()
                .isNotNull("url")
                .ne("url", "")
                .orderByDesc("create_time");

        Page<Picture> page = new Page<>(pageRequest.getCurrent(), pageRequest.getPageSize());
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
                p.getIsPrivate(),
                StrUtil.split(p.getTags(), ",")));
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
    public String deletePicture(DeleteByIdList deleteByIdList) {
        List<Long> ids = deleteByIdList.getIds();
        ExcUtils.throwIfTrue(CollUtil.isEmpty(ids), "图片id不能为空");
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(ObjUtil.isEmpty(user), ExceptionCode.NOT_LOGIN);
        String role = user.getRole();
        // 帖子封面图禁止删除
        List<Post> posts = postService.list(new QueryWrapper<Post>().in("cover", ids));
        Set<Long> count = posts.stream().map(Post::getCover).collect(Collectors.toSet());
        if (!posts.isEmpty()) {
            posts.forEach(post -> {
                ids.remove(post.getCover());
            });
        }
        if (CollUtil.isEmpty(ids)) {
            return "所选的都为帖子封面图，请先删除帖子再删图片";
        }
        // 批量查询图片
        List<Picture> pictureList = pictureMapper.selectList(new QueryWrapper<Picture>().in("id", ids));
        ExcUtils.throwIfTrue(CollUtil.isEmpty(pictureList), "图片不存在");
        Set<Long> userIds = new HashSet<>();
        pictureList.forEach(picture -> userIds.add(picture.getUserId()));
        // 判断是否为图片的主人或者管理员
        ExcUtils.throwIfFalse(role.equals(ADMIN)
                || userIds.stream().findFirst().map(id -> id.equals(user.getId())).orElse(false) || userIds.size() != 1,
                ExceptionCode.UNAUTHORIZED, "没有权限删除图片");

        int i = pictureMapper.delete(new QueryWrapper<Picture>().in("id", ids));
        ExcUtils.throwIfTrue(i == 0, "删除失败");
        // 同步删除 COS 对象
        pictureList.forEach(picture -> cosService.deletePictureByUrl(picture.getUrl()));
        return !count.isEmpty() ? "删除成功，但有" + count.size() + "个图片为帖子封面无法删除" : "删除成功";
    }

    @Override
    public void updatePicture(PictureUpdateRequest request) {
        Long id = request.getId();
        ExcUtils.throwIfTrue(ObjUtil.isEmpty(id), "图片id不能为空");
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(user == null || user.getId() == null, ExceptionCode.UNAUTHORIZED, "用户未登录");
        Picture picture = pictureMapper.selectById(id);
        ExcUtils.throwIfTrue(picture == null || picture.getUserId() == null, ExceptionCode.NOT_FOUND, "图片不存在");
        ExcUtils.throwIfFalse(picture.getUserId().equals(user.getId()) || user.getRole().equals(ADMIN), ExceptionCode.UNAUTHORIZED, "没有权限编辑图片");

        String pictureName = request.getPictureName();
        String introduction = request.getIntroduction();
        List<String> tags = request.getTags();

        if (ObjUtil.isNotEmpty(pictureName)) {
            picture.setPictureName(pictureName);
        }
        if (ObjUtil.isNotEmpty(introduction)) {
            picture.setIntroduction(introduction);
        }
        if (ObjUtil.isNotEmpty(tags)) {
            List<String> typeList = picSystemService.getTypeList();
            boolean result = tags.stream().anyMatch(tag -> !typeList.contains(tag));
            ExcUtils.throwIfTrue(result, ExceptionCode.PARAMETER_ERROR, "标签不存在");
            picture.setTags(JSON.toJSONString(tags));
        }

        int i = pictureMapper.updateById(picture);
        ExcUtils.throwIfFalse(i > 0, ExceptionCode.INTERNAL_SERVER_ERROR, "更新失败");
    }

    /**
     * 编辑时图片信息回填
     *
     * @param id 图片id
     * @return 图片信息
     */
    @Override
    public PictureEditVO getPictureEditMessage(Long id) {
        ExcUtils.throwIfTrue(id == null, "图片id不能为空");
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(user == null || user.getId() == null, ExceptionCode.NOT_LOGIN);
        Picture picture = pictureMapper.selectById(id);
        ExcUtils.throwIfTrue(picture == null || picture.getUserId() == null, ExceptionCode.NOT_FOUND, "图片不存在");
        ExcUtils.throwIfTrue(!picture.getUserId().equals(user.getId()) && !user.getRole().equals(ADMIN), ExceptionCode.FORBIDDEN, "无权限");
        PictureEditVO pictureEditVO = new PictureEditVO();
        pictureEditVO.setPictureName(picture.getPictureName());
        pictureEditVO.setIntroduction(picture.getIntroduction());
        String tags = picture.getTags();
        List<String> tagList = JSONUtil.toList(tags, String.class);
        pictureEditVO.setTags(tagList);
        return pictureEditVO;
    }

}
