package hk.ljx.fishpicsbackend.ai.interfaces;

import hk.ljx.fishpicsbackend.ai.dto.TaggingResult;

public interface ImageTaggingService {
    TaggingResult analyzeImage(String imageUrl);
}
