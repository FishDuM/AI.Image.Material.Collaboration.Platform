package hk.ljx.fishpicsbackend.ai.interfaces;

import hk.ljx.fishpicsbackend.ai.dto.RecommendationRequest;
import hk.ljx.fishpicsbackend.ai.dto.RecommendationResult;

public interface ImageRecommendationService {
    RecommendationResult recommend(RecommendationRequest request);
}
