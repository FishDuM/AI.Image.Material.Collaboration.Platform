package hk.ljx.fishpicsbackend.ai.provider.alibaba;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import hk.ljx.fishpicsbackend.ai.dto.RecommendationRequest;
import hk.ljx.fishpicsbackend.ai.dto.RecommendationResult;
import hk.ljx.fishpicsbackend.ai.dto.TaggingResult;
import hk.ljx.fishpicsbackend.ai.interfaces.ImageRecommendationService;
import hk.ljx.fishpicsbackend.ai.interfaces.ImageTaggingService;
import hk.ljx.fishpicsbackend.entity.Picture;
import hk.ljx.fishpicsbackend.mapper.PictureMapper;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class QwenVLRecommendationImpl implements ImageRecommendationService {

    @Resource
    private ImageTaggingService imageTaggingService;

    @Resource
    private PictureMapper pictureMapper;

    @Override
    public RecommendationResult recommend(RecommendationRequest request) {
        int limit = request.getLimit() != null ? request.getLimit() : 10;

        Picture reference = pictureMapper.selectById(request.getReferencePictureId());
        if (reference == null) {
            return RecommendationResult.builder()
                .pictureIds(List.of())
                .scores(List.of())
                .build();
        }

        // If the reference has AI tags, use them for querying
        List<Picture> similar = new ArrayList<>();
        if (reference.getTags() != null && !reference.getTags().isEmpty()) {
            QueryWrapper<Picture> wrapper = new QueryWrapper<>();
            wrapper.eq("status", 1);
            wrapper.ne("id", reference.getId());
            wrapper.orderByDesc("create_time");
            List<Picture> candidates = pictureMapper.selectList(wrapper);

            String[] refTags = reference.getTags().split(",");
            for (Picture candidate : candidates) {
                if (candidate.getTags() != null) {
                    for (String refTag : refTags) {
                        if (candidate.getTags().contains(refTag.trim())) {
                            similar.add(candidate);
                            break;
                        }
                    }
                }
                if (similar.size() >= limit) break;
            }
        }

        // If not enough similar by tags, fallback to analyze reference and search
        if (similar.size() < limit && reference.getUrl() != null) {
            try {
                TaggingResult tags = imageTaggingService.analyzeImage(reference.getUrl());
                QueryWrapper<Picture> wrapper = new QueryWrapper<>();
                wrapper.eq("status", 1);
                wrapper.ne("id", reference.getId());
                wrapper.orderByDesc("create_time");
                List<Picture> all = pictureMapper.selectList(wrapper);

                for (Picture candidate : all) {
                    if (candidate.getTags() != null && tags.getTags() != null) {
                        for (String tag : tags.getTags()) {
                            if (candidate.getTags().contains(tag)) {
                                if (!similar.contains(candidate)) {
                                    similar.add(candidate);
                                }
                                break;
                            }
                        }
                    }
                    if (similar.size() >= limit) break;
                }
            } catch (Exception e) {
                log.warn("AI recommendation fallback failed for picture {}", reference.getId(), e);
            }
        }

        return RecommendationResult.builder()
            .pictureIds(similar.stream().map(Picture::getId).collect(Collectors.toList()))
            .scores(similar.stream().map(p -> 1.0).collect(Collectors.toList()))
            .build();
    }
}
