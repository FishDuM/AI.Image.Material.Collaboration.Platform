package hk.ljx.fishpicsbackend.picture.service.impl;
import hk.ljx.fishpicsbackend.picture.entity.Picture;

import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.infra.DistributedLockService;
import hk.ljx.fishpicsbackend.common.utils.LoginContextHelper;
import hk.ljx.fishpicsbackend.common.utils.XssSanitizer;
import hk.ljx.fishpicsbackend.mapper.PictureMapper;
import hk.ljx.fishpicsbackend.mapper.SpaceTeamMemberMapper;
import static hk.ljx.fishpicsbackend.picture.constants.PictureConstants.*;
import hk.ljx.fishpicsbackend.picture.dto.AdminPictureListDTO;
import hk.ljx.fishpicsbackend.picture.dto.CheckUploadRequest;
import hk.ljx.fishpicsbackend.picture.dto.DeleteByIdListRequest;
import hk.ljx.fishpicsbackend.picture.dto.MergeChunksRequest;
import hk.ljx.fishpicsbackend.picture.dto.PictureQueryRequest;
import hk.ljx.fishpicsbackend.picture.dto.PictureUpdateRequest;
import hk.ljx.fishpicsbackend.picture.component.PictureDeleteManager;
import hk.ljx.fishpicsbackend.picture.component.PictureReplaceManager;
import hk.ljx.fishpicsbackend.picture.component.PictureTagManager;
import hk.ljx.fishpicsbackend.picture.component.PictureUploadManager;
import hk.ljx.fishpicsbackend.picture.service.PictureService;
import hk.ljx.fishpicsbackend.picture.vo.CheckUploadVO;
import hk.ljx.fishpicsbackend.picture.vo.PictureVO;
import hk.ljx.fishpicsbackend.picture.vo.UploadChunkVO;
import hk.ljx.fishpicsbackend.user.entity.User;
import hk.ljx.fishpicsbackend.user.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

import hk.ljx.fishpicsbackend.common.dto.PageRequest;
import hk.ljx.fishpicsbackend.picture.service.PicturePermissionUtil;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
        implements PictureService {

    @Resource
    private PictureMapper pictureMapper;

    @Lazy
    @Resource
    private UserService userService;

    @Resource
    private PictureUploadManager pictureUploadManager;

    @Resource
    private PictureDeleteManager pictureDeleteManager;

    @Resource
    private PictureReplaceManager pictureReplaceManager;

    @Resource
    private PictureTagManager pictureTagManager;

    @Resource
    private SpaceTeamMemberMapper spaceTeamMemberMapper;

    @Resource
    private DistributedLockService distributedLockService;

    private static final int MAX_PICTURE_NAME_LENGTH = 100;
    private static final int MAX_PICTURE_INTRO_LENGTH = 500;

    @Override
    public String uploadAvatar(MultipartFile file, Long id) {
        return pictureUploadManager.uploadAvatar(file, id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Picture uploadPicture(MultipartFile file, Long targetSpaceId) {
        return pictureUploadManager.uploadPicture(file, targetSpaceId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Picture savePictureByUrl(String url, Long targetSpaceId) {
        return pictureUploadManager.savePictureByUrl(url, targetSpaceId);
    }

    @Override
    public CheckUploadVO checkUpload(CheckUploadRequest request) {
        return pictureUploadManager.checkUpload(request);
    }

    @Override
    public UploadChunkVO uploadChunk(MultipartFile file, String md5, Integer chunkIndex) {
        return pictureUploadManager.uploadChunk(file, md5, chunkIndex);
    }

    @Override
    public PictureVO mergeChunks(MergeChunksRequest request) {
        return pictureUploadManager.mergeChunks(request);
    }

    @Override
    public IPage<PictureVO> getPictureList(PictureQueryRequest pictureQueryRequest) {
        LambdaQueryWrapper<Picture> queryWrapper = new LambdaQueryWrapper<Picture>()
                .eq(Picture::getIsSelected, SELECTED_FEATURED)
                .isNotNull(Picture::getUrl)
                .ne(Picture::getUrl, "")
                .orderByDesc(Picture::getCreateTime);

        if (StrUtil.isNotBlank(pictureQueryRequest.getTag())) {
            List<Long> pictureIdsWithTag = pictureTagManager.findPictureIdsByTag(pictureQueryRequest.getTag());
            if (pictureIdsWithTag.isEmpty()) {
                Page<Picture> emptyPage = new Page<>(pictureQueryRequest.getCurrent(), pictureQueryRequest.getPageSize());
                emptyPage.setRecords(Collections.emptyList());
                return emptyPage.convert(p -> PictureVO.ofList(p.getId(), p.getUrl(), Collections.emptyList()));
            }
            queryWrapper.in(Picture::getId, pictureIdsWithTag);
        }

        Page<Picture> page = new Page<>(pictureQueryRequest.getCurrent(), pictureQueryRequest.getPageSize());
        IPage<Picture> picturePage = pictureMapper.selectPage(page, queryWrapper);
        List<Long> pagePictureIds = picturePage.getRecords().stream().map(Picture::getId).collect(Collectors.toList());
        Map<Long, List<String>> tagsMap = pictureTagManager.batchLoadTags(pagePictureIds);
        return picturePage.convert(p -> PictureVO.ofList(p.getId(), p.getUrl(),
                tagsMap.getOrDefault(p.getId(), Collections.emptyList())));
    }

    @Override
    public IPage<PictureVO> getAdminPictureList(AdminPictureListDTO dto) {
        LambdaQueryWrapper<Picture> queryWrapper = new LambdaQueryWrapper<Picture>()
                .isNotNull(Picture::getUrl)
                .ne(Picture::getUrl, "")
                .orderByDesc(Picture::getCreateTime);

        Integer selected = dto.getSelected();
        if (selected != null) {
            queryWrapper.eq(Picture::getIsSelected, selected);
        }

        Page<Picture> page = new Page<>(dto.getCurrent(), dto.getPageSize());
        IPage<Picture> picturePage = pictureMapper.selectPage(page, queryWrapper);
        List<Long> pagePictureIds = picturePage.getRecords().stream().map(Picture::getId).collect(Collectors.toList());
        Map<Long, List<String>> tagsMap = pictureTagManager.batchLoadTags(pagePictureIds);
        return picturePage.convert(p -> PictureVO.ofAdmin(
                p.getId(),
                p.getUrl(),
                p.getWidth(),
                p.getHeight(),
                p.getSize(),
                p.getCreateTime(),
                p.getUserId(),
                p.getIsSelected(),
                tagsMap.getOrDefault(p.getId(), Collections.emptyList())));
    }

    @Override
    public void reviewPicture(Long pictureId, Integer selected) {
        ExcUtils.throwIfTrue(pictureId == null, "图片id不能为空");
        Picture picture = pictureMapper.selectById(pictureId);
        ExcUtils.throwIfTrue(picture == null, "图片不存在");
        if (selected != null) {
            ExcUtils.throwIfTrue((selected != SELECTED_NORMAL && selected != SELECTED_FEATURED), "精选值无效");
            if (selected == SELECTED_NORMAL && ExcUtils.eq(picture.getIsSelected(), SELECTED_PENDING)) {
                picture.setIsSelected(SELECTED_NORMAL);
            } else {
                picture.setIsSelected(selected);
            }
        }
        ExcUtils.throwIfTrue(pictureMapper.updateById(picture) != 1, "审核更新失败");
    }

    @Override
    public String deletePicture(DeleteByIdListRequest deleteByIdList) {
        return pictureDeleteManager.delete(deleteByIdList);
    }

    @Override
    public void updatePicture(PictureUpdateRequest request) {
        Long id = request.getId();
        ExcUtils.throwIfTrue(ObjUtil.isEmpty(id), "图片id不能为空");
        User user = LoginContextHelper.requireUser();
        Picture picture = pictureMapper.selectById(id);
        ExcUtils.throwIfTrue(picture == null || picture.getUserId() == null, ExceptionCode.NOT_FOUND, "图片不存在");
        PicturePermissionUtil.checkWrite(picture, "编辑", spaceTeamMemberMapper);

        request.setUrl(null);
        Long pictureId = request.getId();
        Integer isSelected = request.getIsSelected();
        if (isSelected != null) {
            if (isSelected == SELECTED_FEATURED) {
                ExcUtils.throwIfTrue(ExcUtils.eq(picture.getIsSelected(), SELECTED_FEATURED),
                        ExceptionCode.PARAMETER_ERROR, "该图片已经是精选");
                picture.setIsSelected(SELECTED_PENDING);
            } else if (isSelected == SELECTED_NORMAL) {
                picture.setIsSelected(SELECTED_NORMAL);
            }
            pictureMapper.updateById(picture);
            return;
        }

        String pictureName = request.getPictureName();
        String introduction = request.getIntroduction();
        List<String> tags = request.getTags();

        if (ObjUtil.isNotEmpty(pictureName)) {
            String cleanName = XssSanitizer.clean(pictureName);
            ExcUtils.throwIfTrue(cleanName.length() > MAX_PICTURE_NAME_LENGTH, ExceptionCode.PARAMETER_ERROR, "图片名称过长(最多 100 字符)");
            picture.setPictureName(cleanName);
        }
        if (ObjUtil.isNotEmpty(introduction)) {
            String cleanIntro = XssSanitizer.cleanRelaxed(introduction);
            ExcUtils.throwIfTrue(cleanIntro.length() > MAX_PICTURE_INTRO_LENGTH, ExceptionCode.PARAMETER_ERROR, "图片描述过长(最多 500 字符)");
            picture.setIntroduction(cleanIntro);
        }
        if (ObjUtil.isNotEmpty(tags)) {
            pictureTagManager.replaceValidatedTags(pictureId, tags);
        }

        int i = pictureMapper.updateById(picture);
        ExcUtils.throwIfFalse(i > 0, ExceptionCode.INTERNAL_SERVER_ERROR, "更新失败");
    }

    @Override
    public PictureVO replacePictureFile(Long pictureId, MultipartFile file) {
        return replacePictureFile(pictureId, file, false);
    }

    @Override
    public PictureVO replacePictureFile(Long pictureId, MultipartFile file, boolean requireCollabLock) {
        ExcUtils.throwIfTrue(pictureId == null, "图片ID不能为空");
        String lockKey = "lock:replace-picture:" + pictureId;
        if (!distributedLockService.tryLock(lockKey, 30)) {
            throw new hk.ljx.fishpicsbackend.common.exception.BaseException(ExceptionCode.CONFLICT, "图片正在替换，请稍后重试");
        }
        try {
            return pictureReplaceManager.replace(pictureId, file, requireCollabLock);
        } finally {
            distributedLockService.unlock(lockKey);
        }
    }

    @Override
    public PictureVO getPictureEditMessage(Long id) {
        ExcUtils.throwIfTrue(id == null, "图片id不能为空");
        LoginContextHelper.requireUser();
        Picture picture = pictureMapper.selectById(id);
        ExcUtils.throwIfTrue(picture == null || picture.getUserId() == null, ExceptionCode.NOT_FOUND, "图片不存在");
        PicturePermissionUtil.checkWrite(picture, "编辑", spaceTeamMemberMapper);

        List<String> tagList = pictureTagManager.loadTags(picture.getId());

        return PictureVO.ofDetail(
                picture.getId(),
                picture.getUrl(),
                picture.getPictureName(),
                picture.getIntroduction(),
                tagList
        );
    }

    @Override
    public IPage<PictureVO> getRecommendPictures(PageRequest pageRequest, Long userId) {
        LambdaQueryWrapper<Picture> queryWrapper = new LambdaQueryWrapper<Picture>()
                .eq(Picture::getIsSelected, SELECTED_FEATURED)
                .isNotNull(Picture::getUrl)
                .ne(Picture::getUrl, "")
                .orderByDesc(Picture::getCreateTime);

        Page<Picture> page = new Page<>(pageRequest.getCurrent(), pageRequest.getPageSize());
        IPage<Picture> picturePage = pictureMapper.selectPage(page, queryWrapper);
        List<Long> pagePictureIds = picturePage.getRecords().stream().map(Picture::getId).collect(Collectors.toList());
        Map<Long, List<String>> tagsMap = pictureTagManager.batchLoadTags(pagePictureIds);
        return picturePage.convert(p -> PictureVO.ofList(p.getId(), p.getUrl(),
                tagsMap.getOrDefault(p.getId(), Collections.emptyList())));
    }
}
