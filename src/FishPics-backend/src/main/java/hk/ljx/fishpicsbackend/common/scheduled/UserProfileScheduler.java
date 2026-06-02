package hk.ljx.fishpicsbackend.common.scheduled;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import hk.ljx.fishpicsbackend.mapper.PostMapper;
import hk.ljx.fishpicsbackend.picture.entity.Picture;
import hk.ljx.fishpicsbackend.picture.entity.PictureChild;
import hk.ljx.fishpicsbackend.picture.service.PictureChildService;
import hk.ljx.fishpicsbackend.picture.service.PictureService;
import hk.ljx.fishpicsbackend.user.entity.UserInterestProfile;
import hk.ljx.fishpicsbackend.user.entity.UserPostCollect;
import hk.ljx.fishpicsbackend.user.entity.UserPostLikes;
import hk.ljx.fishpicsbackend.user.service.UserInterestProfileService;
import hk.ljx.fishpicsbackend.user.service.UserPostCollectService;
import hk.ljx.fishpicsbackend.user.service.UserPostLikesService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 用户兴趣画像定时刷新任务
 */
@Component
@Slf4j
public class UserProfileScheduler {

    @Resource
    private PostMapper postMapper;
    @Resource
    private UserPostLikesService userPostLikesService;
    @Resource
    private UserPostCollectService userPostCollectService;
    @Resource
    private PictureChildService pictureChildService;
    @Resource
    private PictureService pictureService;
    @Resource
    private UserInterestProfileService userInterestProfileService;

    @Scheduled(fixedRate = 1800_000)
    public void refreshUserProfiles() {
        List<Long> activeUserIds = postMapper.selectActiveUserIds();
        if (CollectionUtil.isEmpty(activeUserIds)) {
            return;
        }

        for (Long userId : activeUserIds) {
            try {
                Map<String, Integer> tagWeightMap = new HashMap<>();

                // 点赞帖子关联的图片标签 → 权重+3
                List<String> likedTags = getTagsByBehavior(userId, "like");
                likedTags.forEach(tag -> tagWeightMap.merge(tag, 3, Integer::sum));

                // 收藏帖子关联的图片标签 → 权重+5
                List<String> collectedTags = getTagsByBehavior(userId, "collect");
                collectedTags.forEach(tag -> tagWeightMap.merge(tag, 5, Integer::sum));

                if (tagWeightMap.isEmpty()) {
                    continue;
                }

                // 先删除旧画像，再批量插入新画像
                userInterestProfileService.remove(new LambdaQueryWrapper<UserInterestProfile>()
                        .eq(UserInterestProfile::getUserId, userId));

                List<UserInterestProfile> profiles = tagWeightMap.entrySet().stream()
                        .map(e -> {
                            UserInterestProfile p = new UserInterestProfile();
                            p.setUserId(userId);
                            p.setTag(e.getKey());
                            p.setWeight(e.getValue());
                            return p;
                        })
                        .collect(Collectors.toList());
                userInterestProfileService.saveBatch(profiles);
            } catch (Exception e) {
                log.error("刷新用户 {} 画像失败", userId, e);
            }
        }
        log.info("用户画像刷新完成，处理 {} 个用户", activeUserIds.size());
    }

    /**
     * 根据用户行为（点赞/收藏）获取关联的图片标签
     */
    private List<String> getTagsByBehavior(Long userId, String type) {
        // 1. 查询用户点赞/收藏的帖子ID
        List<Long> postIds;
        if ("like".equals(type)) {
            postIds = userPostLikesService.list(
                    new LambdaQueryWrapper<UserPostLikes>()
                            .eq(UserPostLikes::getUserId, userId)
            ).stream().map(UserPostLikes::getPostId).collect(Collectors.toList());
        } else {
            postIds = userPostCollectService.list(
                    new LambdaQueryWrapper<UserPostCollect>()
                            .eq(UserPostCollect::getUserId, userId)
            ).stream().map(UserPostCollect::getPostId).collect(Collectors.toList());
        }

        if (CollectionUtil.isEmpty(postIds)) {
            return Collections.emptyList();
        }

        // 2. 查询帖子关联的图片ID
        List<Long> pictureIds = pictureChildService.list(
                new LambdaQueryWrapper<PictureChild>()
                        .in(PictureChild::getPostId, postIds)
        ).stream().map(PictureChild::getPictureId).collect(Collectors.toList());

        if (CollectionUtil.isEmpty(pictureIds)) {
            return Collections.emptyList();
        }

        // 3. 查询图片标签，解析JSON数组
        List<String> tags = new ArrayList<>();
        pictureService.listByIds(pictureIds).stream()
                .map(Picture::getTags)
                .filter(Objects::nonNull)
                .forEach(tagStr -> tags.addAll(JSONUtil.toList(tagStr, String.class)));

        return tags;
    }
}
