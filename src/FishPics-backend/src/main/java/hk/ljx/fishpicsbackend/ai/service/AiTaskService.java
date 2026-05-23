package hk.ljx.fishpicsbackend.ai.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import hk.ljx.fishpicsbackend.ai.dto.AiConfigUpdateRequest;
import hk.ljx.fishpicsbackend.ai.dto.EditingRequest;
import hk.ljx.fishpicsbackend.ai.dto.GenerationRequest;
import hk.ljx.fishpicsbackend.ai.dto.RecommendationRequest;
import hk.ljx.fishpicsbackend.entity.AiTask;
import hk.ljx.fishpicsbackend.entity.Picture;
import hk.ljx.fishpicsbackend.vo.ai.AiConfigVO;
import hk.ljx.fishpicsbackend.vo.ai.AiStatsVO;
import hk.ljx.fishpicsbackend.vo.ai.AiTaskVO;

public interface AiTaskService extends IService<AiTask> {

    void triggerTaggingAsync(Picture picture);

    Long submitGeneration(GenerationRequest request, Long userId);

    Long submitEditing(EditingRequest request, Long userId);

    Long submitManualTagging(Long pictureId, Long userId);

    Long submitRecommendation(RecommendationRequest request, Long userId);

    AiTaskVO getTaskVOById(Long id);

    IPage<AiTaskVO> getMyTasks(long current, long pageSize, Long userId);

    IPage<AiTaskVO> getAllTasks(long current, long pageSize, Integer type, Integer status, Long userId);

    AiStatsVO getStats();

    AiConfigVO getConfig();

    void updateConfig(AiConfigUpdateRequest request);
}
