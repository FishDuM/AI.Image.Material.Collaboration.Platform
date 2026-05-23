package hk.ljx.fishpicsbackend.ai.interfaces;

import hk.ljx.fishpicsbackend.ai.dto.EditingRequest;
import hk.ljx.fishpicsbackend.ai.dto.EditingResult;

public interface ImageEditingService {
    EditingResult editImage(EditingRequest request);
}
