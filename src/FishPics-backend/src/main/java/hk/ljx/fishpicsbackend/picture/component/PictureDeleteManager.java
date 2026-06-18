package hk.ljx.fishpicsbackend.picture.component;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.infra.CosService;
import hk.ljx.fishpicsbackend.common.utils.LoginContextHelper;
import hk.ljx.fishpicsbackend.mapper.PictureMapper;
import hk.ljx.fishpicsbackend.mapper.PictureShareMapper;
import hk.ljx.fishpicsbackend.mapper.PictureTagMapper;
import hk.ljx.fishpicsbackend.mapper.SpaceMapper;
import hk.ljx.fishpicsbackend.mapper.SpaceTeamMemberMapper;
import hk.ljx.fishpicsbackend.picture.dto.DeleteByIdListRequest;
import hk.ljx.fishpicsbackend.picture.entity.Picture;
import hk.ljx.fishpicsbackend.picture.entity.PictureShare;
import hk.ljx.fishpicsbackend.picture.entity.PictureTag;
import hk.ljx.fishpicsbackend.picture.service.FileResourceService;
import hk.ljx.fishpicsbackend.picture.service.PicturePermissionUtil;
import hk.ljx.fishpicsbackend.space.entity.Space;
import hk.ljx.fishpicsbackend.user.entity.User;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class PictureDeleteManager {

    @Resource
    private PictureMapper pictureMapper;

    @Resource
    private PictureShareMapper pictureShareMapper;

    @Resource
    private PictureTagMapper pictureTagMapper;

    @Resource
    private SpaceTeamMemberMapper spaceTeamMemberMapper;

    @Resource
    private FileResourceService fileResourceService;

    @Resource
    private SpaceMapper spaceMapper;

    @Resource
    private SpaceQuotaManager quotaManager;

    @Resource
    private CosService cosService;

    @Transactional(rollbackFor = Exception.class)
    public String delete(DeleteByIdListRequest request) {
        List<Long> ids = request.getIds();
        ExcUtils.throwIfTrue(CollUtil.isEmpty(ids), "图片id不能为空");
        User user = LoginContextHelper.requireUser();

        List<Picture> pictures = pictureMapper.selectList(new LambdaQueryWrapper<Picture>().in(Picture::getId, ids));
        ExcUtils.throwIfTrue(CollUtil.isEmpty(pictures), "图片不存在");

        List<Long> deletableIds = PicturePermissionUtil.filterDeletableIds(pictures, ids, spaceTeamMemberMapper);
        if (deletableIds.isEmpty()) {
            throw new BaseException(ExceptionCode.FORBIDDEN, "没有可删除的图片");
        }

        ExcUtils.throwIfTrue(
                pictureMapper.delete(new LambdaQueryWrapper<Picture>().in(Picture::getId, deletableIds)) == 0,
                "删除失败");

        pictures = pictures.stream()
                .filter(picture -> deletableIds.contains(picture.getId()))
                .collect(Collectors.toList());

        deleteRelations(deletableIds);
        releaseSpaceQuota(pictures);
        releaseFileResourcesAfterDelete(pictures);
        return "删除成功";
    }

    private void deleteRelations(List<Long> pictureIds) {
        int shareCount = pictureShareMapper.delete(new LambdaQueryWrapper<PictureShare>()
                .in(PictureShare::getPictureId, pictureIds));
        if (shareCount > 0) {
            log.info("deleted picture shares: count={}", shareCount);
        }

        int tagCount = pictureTagMapper.delete(new LambdaQueryWrapper<PictureTag>()
                .in(PictureTag::getPictureId, pictureIds));
        if (tagCount > 0) {
            log.info("deleted picture tags: count={}", tagCount);
        }
    }

    private void releaseSpaceQuota(List<Picture> pictures) {
        Map<Long, Long> spaceSizeMap = new HashMap<>();
        pictures.forEach(picture -> {
            if (picture.getSpaceId() != null && picture.getSize() != null) {
                spaceSizeMap.merge(picture.getSpaceId(), picture.getSize(), Long::sum);
            } else {
                log.warn("skip quota release: pictureId={}, spaceId={}, size={}",
                        picture.getId(), picture.getSpaceId(), picture.getSize());
            }
        });

        spaceSizeMap.forEach((spaceId, deletedSize) -> {
            Space space = spaceMapper.selectById(spaceId);
            if (space != null) {
                quotaManager.release(space, deletedSize);
            }
        });
    }

    private void releaseFileResourcesAfterDelete(List<Picture> pictures) {
        List<String> legacyCosUrls = new ArrayList<>();
        for (Picture picture : pictures) {
            if (picture.getResourceId() != null) {
                try {
                    int newCount = fileResourceService.decrementRefCount(picture.getResourceId());
                    if (newCount == 0) {
                        log.info("picture resource ref_count reached zero: resourceId={}", picture.getResourceId());
                    }
                } catch (Exception e) {
                    log.warn("failed to release picture resource: pictureId={}, resourceId={}",
                            picture.getId(), picture.getResourceId(), e);
                }
            } else {
                legacyCosUrls.add(picture.getUrl());
            }
        }
        deleteLegacyCosFilesAfterCommit(legacyCosUrls);
    }

    private void deleteLegacyCosFilesAfterCommit(List<String> legacyCosUrls) {
        if (legacyCosUrls.isEmpty()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (String url : legacyCosUrls) {
                    try {
                        cosService.deletePictureByUrl(url);
                    } catch (Exception e) {
                        log.error("failed to delete legacy COS file: url={}", url, e);
                    }
                }
            }
        });
    }
}
