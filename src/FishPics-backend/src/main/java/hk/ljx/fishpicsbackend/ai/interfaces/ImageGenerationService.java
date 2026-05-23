package hk.ljx.fishpicsbackend.ai.interfaces;

import hk.ljx.fishpicsbackend.ai.dto.GenerationRequest;
import hk.ljx.fishpicsbackend.ai.dto.GenerationResult;

public interface ImageGenerationService {
    GenerationResult generateImage(GenerationRequest request);
}
