package hk.ljx.fishpicsbackend.space.component;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import hk.ljx.fishpicsbackend.collab.CollabSessionRegistry;
import hk.ljx.fishpicsbackend.common.cache.RedisCacheManager;
import hk.ljx.fishpicsbackend.common.constants.SpaceConstants;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.utils.XssSanitizer;
import hk.ljx.fishpicsbackend.mapper.PictureMapper;
import hk.ljx.fishpicsbackend.mapper.PictureShareMapper;
import hk.ljx.fishpicsbackend.mapper.PictureShareItemMapper;
import hk.ljx.fishpicsbackend.mapper.SpaceMapper;
import hk.ljx.fishpicsbackend.mapper.SpaceTeamMemberMapper;
import hk.ljx.fishpicsbackend.mapper.UserMapper;
import hk.ljx.fishpicsbackend.picture.entity.Picture;
import hk.ljx.fishpicsbackend.picture.entity.PictureShare;
import hk.ljx.fishpicsbackend.picture.entity.PictureShareItem;
import hk.ljx.fishpicsbackend.picture.service.FileResourceService;
import hk.ljx.fishpicsbackend.space.dto.SpaceAdminUpdateRequest;
import hk.ljx.fishpicsbackend.space.dto.SpaceQueryWrapper;
import hk.ljx.fishpicsbackend.space.entity.Space;
import hk.ljx.fishpicsbackend.space.entity.SpaceTeamMember;
import hk.ljx.fishpicsbackend.space.vo.SpaceVO;
import hk.ljx.fishpicsbackend.user.entity.User;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
public class SpaceAdminManager {
    @Resource
    private SpaceMapper spaceMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private SpaceTeamMemberMapper spaceTeamMemberMapper;

    @Resource
    private PictureShareMapper pictureShareMapper;

    @Resource
    private PictureShareItemMapper pictureShareItemMapper;

    @Resource
    private PictureMapper pictureMapper;

    @Resource
    private FileResourceService fileResourceService;

    @Resource
    private CollabSessionRegistry collabSessionRegistry;

        @Resource
    private SpaceVOAssembler spaceVOAssembler;

    @Resource
    private RedisCacheManager cacheManager;

    public IPage<SpaceVO> list(SpaceQueryWrapper request) {
        QueryWrapper<Space> queryWrapper = buildQueryWrapper(request);
        int pageSize = Math.min(Math.max(request.getPageSize(), 1), 100);
        Page<Space> page = new Page<>(request.getCurrent(), pageSize);
        Page<Space> spacePage = spaceMapper.selectPage(page, queryWrapper);
        List<Space> spaces = spacePage.getRecords();
        if (CollUtil.isEmpty(spaces)) {
            Page<SpaceVO> emptyPage = new Page<>(request.getCurrent(), request.getPageSize(), spacePage.getTotal());
            emptyPage.setRecords(new ArrayList<>());
            return emptyPage;
        }

        List<Long> spaceIds = spaces.stream().map(Space::getId).collect(Collectors.toList());
        Map<Long, List<SpaceTeamMember>> membersBySpaceId = loadMembersBySpaceId(spaceIds);
        Map<Long, User> userMap = loadUsers(spaces, membersBySpaceId);
        Map<Long, Long> pictureCountMap = spaceVOAssembler.pictureCountMap(spaceIds);

        List<SpaceVO> records = spaces.stream()
                .map(space -> {
                    List<SpaceTeamMember> teamMembers = ExcUtils.eq(space.getType(), SpaceConstants.SPACE_TYPE_TEAM)
                            ? membersBySpaceId.getOrDefault(space.getId(), Collections.emptyList())
                            : null;
                    return spaceVOAssembler.build(space, userMap, pictureCountMap, teamMembers,
                            SpaceVOAssembler.MAX_TEAM_MEMBER_DISPLAY);
                })
                .collect(Collectors.toList());

        Page<SpaceVO> voPage = new Page<>(request.getCurrent(), request.getPageSize(), spacePage.getTotal());
        voPage.setRecords(records);
        return voPage;
    }

    public Boolean update(SpaceAdminUpdateRequest request) {
        Long id = request.getId();
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(id), ExceptionCode.PARAMETER_ERROR, "空间ID不能为空");
        Space space = spaceMapper.selectById(id);
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(space), ExceptionCode.PARAMETER_ERROR, "空间不存在");

        Space updateObj = new Space();
        updateObj.setId(id);
        if (request.getName() != null) {
            updateObj.setName(XssSanitizer.clean(request.getName()));
        }
        if (request.getIntroduction() != null) {
            updateObj.setIntroduction(XssSanitizer.cleanRelaxed(request.getIntroduction()));
        }
        if (request.getLevel() != null) {
            updateObj.setLevel(request.getLevel());
        }
        if (request.getStorageSize() != null) {
            updateObj.setStorageSize(request.getStorageSize());
        }
        int updated = spaceMapper.updateById(updateObj);
        ExcUtils.throwIfTrue(updated <= 0, ExceptionCode.DATABASE_ERROR, "更新失败");
        cacheManager.getSpaceDetailCache().evict(String.valueOf(id));
        return true;
    }

    public Boolean delete(Long id) {
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(id), ExceptionCode.PARAMETER_ERROR, "空间ID不能为空");
        Space space = spaceMapper.selectById(id);
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(space), ExceptionCode.PARAMETER_ERROR, "空间不存在");

        List<Picture> pictures = pictureMapper.selectList(
                new LambdaQueryWrapper<Picture>().eq(Picture::getSpaceId, id));
        releasePictureResources(pictures);
        disconnectSpaceAfterCommit(id, "空间已被删除");
        deletePictureShares(id, pictures);

        spaceTeamMemberMapper.delete(new LambdaQueryWrapper<SpaceTeamMember>().eq(SpaceTeamMember::getSpaceId, id));
        pictureMapper.delete(new LambdaQueryWrapper<Picture>().eq(Picture::getSpaceId, id));
        int deleted = spaceMapper.deleteById(id);
        ExcUtils.throwIfTrue(deleted <= 0, ExceptionCode.DATABASE_ERROR, "删除失败");
        cacheManager.getSpaceDetailCache().evict(String.valueOf(id));
        return true;
    }

    public Boolean setStatus(Long id, Integer status) {
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(id), ExceptionCode.PARAMETER_ERROR, "空间ID不能为空");
        ExcUtils.throwIfTrue(status == null || (status != SpaceConstants.SPACE_STATUS_DISABLED && status != SpaceConstants.SPACE_STATUS_ENABLED),
                ExceptionCode.PARAMETER_ERROR, "无效的状态值");
        Space space = spaceMapper.selectById(id);
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(space), ExceptionCode.PARAMETER_ERROR, "空间不存在");
        int updated = spaceMapper.update(null,
                new LambdaUpdateWrapper<Space>()
                        .eq(Space::getId, id)
                        .set(Space::getStatus, status));
        ExcUtils.throwIfTrue(updated <= 0, ExceptionCode.DATABASE_ERROR, "更新失败");
        cacheManager.getSpaceDetailCache().evict(String.valueOf(id));

        if (ExcUtils.eq(status, SpaceConstants.SPACE_STATUS_DISABLED)) {
            disconnectSpaceNow(id, "空间已被禁用");
        }
        return true;
    }

    private QueryWrapper<Space> buildQueryWrapper(SpaceQueryWrapper request) {
        Set<String> allowedSortFields = Set.of("id", "introduction", "type", "user_id", "storage_size",
                "level", "name", "size", "create_time", "update_time");
        QueryWrapper<Space> queryWrapper = new QueryWrapper<>();
        queryWrapper
                .eq(!ObjectUtil.isEmpty(request.getId()), "id", request.getId())
                .eq(!ObjectUtil.isEmpty(request.getIntroduction()), "introduction", request.getIntroduction())
                .eq(!ObjectUtil.isEmpty(request.getType()), "type", request.getType())
                .eq(!ObjectUtil.isEmpty(request.getUserId()), "user_id", request.getUserId())
                .eq(!ObjectUtil.isEmpty(request.getStorageSize()), "storage_size", request.getStorageSize())
                .eq(!ObjectUtil.isEmpty(request.getLevel()), "level", request.getLevel())
                .eq(!ObjectUtil.isEmpty(request.getName()), "name", request.getName());
        String sortField = request.getSortField();
        queryWrapper.orderBy(sortField != null && allowedSortFields.contains(sortField),
                "ascend".equals(request.getSortOrder()), sortField);
        return queryWrapper;
    }

    private Map<Long, List<SpaceTeamMember>> loadMembersBySpaceId(List<Long> spaceIds) {
        List<SpaceTeamMember> members = spaceTeamMemberMapper.selectList(
                new LambdaQueryWrapper<SpaceTeamMember>().in(SpaceTeamMember::getSpaceId, spaceIds));
        return members.stream().collect(Collectors.groupingBy(SpaceTeamMember::getSpaceId));
    }

    private Map<Long, User> loadUsers(List<Space> spaces, Map<Long, List<SpaceTeamMember>> membersBySpaceId) {
        Set<Long> userIds = new HashSet<>();
        spaces.stream().map(Space::getUserId).filter(Objects::nonNull).forEach(userIds::add);
        membersBySpaceId.values().stream()
                .flatMap(List::stream)
                .map(SpaceTeamMember::getUserId)
                .filter(Objects::nonNull)
                .forEach(userIds::add);
        if (userIds.isEmpty()) {
            return new HashMap<>();
        }
        return userMapper.selectByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));
    }

    private void releasePictureResources(List<Picture> pictures) {
        for (Picture picture : pictures) {
            Long resourceId = picture.getResourceId();
            if (resourceId == null) continue;
            fileResourceService.decrementRefCount(resourceId);
        }
    }

    private void deletePictureShares(Long spaceId, List<Picture> pictures) {
        try {
            List<Long> pictureIds = pictures.stream()
                    .map(Picture::getId)
                    .filter(Objects::nonNull)
                    .toList();
            if (pictureIds.isEmpty()) return;
            // 先删 PictureShareItem，再删 PictureShare（和 PictureDeleteManager.deleteRelations 一致）
            int shareItemCount = pictureShareItemMapper.delete(
                    new LambdaQueryWrapper<PictureShareItem>().in(PictureShareItem::getPictureId, pictureIds));
            int deleted = pictureShareMapper.delete(
                    new LambdaQueryWrapper<PictureShare>().in(PictureShare::getPictureId, pictureIds));
            log.info("[SpaceAdminManager] 已删除图片分享: spaceId={}, shareItems={}, shares={}", spaceId, shareItemCount, deleted);
        } catch (Exception e) {
            log.warn("[SpaceAdminManager] 删除图片分享失败: spaceId={}", spaceId, e);
        }
    }

    private void disconnectSpaceAfterCommit(Long spaceId, String reason) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                disconnectSpaceNow(spaceId, reason);
            }
        });
    }

    private void disconnectSpaceNow(Long spaceId, String reason) {
        try {
            Set<Long> onlineUserIds = collabSessionRegistry.getOnlineUserIds(spaceId);
            for (Long userId : onlineUserIds) {
                collabSessionRegistry.disconnectUserInSpace(userId, spaceId, reason);
            }
            collabSessionRegistry.clearAllPictureStates(spaceId);
            log.info("[SpaceAdminManager] 断开空间会话: spaceId={}, affectedUsers={}",
                    spaceId, onlineUserIds.size());
        } catch (Exception e) {
            log.warn("[SpaceAdminManager] 断开空间会话失败: spaceId={}", spaceId, e);
        }
    }
}
