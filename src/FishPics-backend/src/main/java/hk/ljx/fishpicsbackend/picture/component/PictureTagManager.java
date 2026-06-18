package hk.ljx.fishpicsbackend.picture.component;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.utils.XssSanitizer;
import hk.ljx.fishpicsbackend.mapper.PictureTagMapper;
import hk.ljx.fishpicsbackend.picture.entity.PictureTag;
import hk.ljx.fishpicsbackend.system.service.PicSystemService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class PictureTagManager {

    private static final int MAX_TAG_COUNT = 10;

    @Resource
    private PictureTagMapper pictureTagMapper;

    @Resource
    private PicSystemService picSystemService;

    public List<String> loadTags(Long pictureId) {
        return pictureTagMapper.selectList(
                        new LambdaQueryWrapper<PictureTag>()
                                .eq(PictureTag::getPictureId, pictureId))
                .stream()
                .map(PictureTag::getTagName)
                .collect(Collectors.toList());
    }

    public Map<Long, List<String>> batchLoadTags(List<Long> pictureIds) {
        if (pictureIds == null || pictureIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return pictureTagMapper.selectList(
                        new LambdaQueryWrapper<PictureTag>()
                                .in(PictureTag::getPictureId, pictureIds))
                .stream()
                .collect(Collectors.groupingBy(
                        PictureTag::getPictureId,
                        Collectors.mapping(PictureTag::getTagName, Collectors.toList())));
    }

    public List<Long> findPictureIdsByTag(String tag) {
        return pictureTagMapper.selectList(
                        new LambdaQueryWrapper<PictureTag>()
                                .like(PictureTag::getTagName, tag)
                                .select(PictureTag::getPictureId))
                .stream()
                .map(PictureTag::getPictureId)
                .distinct()
                .collect(Collectors.toList());
    }

    public void replaceValidatedTags(Long pictureId, List<String> tags) {
        ExcUtils.throwIfTrue(tags.size() > MAX_TAG_COUNT,
                ExceptionCode.PARAMETER_ERROR, "标签数量不能超过 10 个");
        Set<String> systemTags = new HashSet<>(picSystemService.getTypeList());
        List<String> safeTags = tags.stream()
                .map(XssSanitizer::clean)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toList());
        ExcUtils.throwIfTrue(safeTags.stream().anyMatch(tag -> !systemTags.contains(tag)),
                ExceptionCode.PARAMETER_ERROR, "标签不存在");
        replaceTags(pictureId, safeTags);
    }

    public void replaceTags(Long pictureId, List<String> tags) {
        pictureTagMapper.delete(
                new LambdaQueryWrapper<PictureTag>()
                        .eq(PictureTag::getPictureId, pictureId));
        for (String tag : tags) {
            PictureTag pictureTag = new PictureTag();
            pictureTag.setPictureId(pictureId);
            pictureTag.setTagName(tag);
            pictureTagMapper.insert(pictureTag);
        }
    }
}
