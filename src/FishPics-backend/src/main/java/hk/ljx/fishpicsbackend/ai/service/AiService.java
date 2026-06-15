package hk.ljx.fishpicsbackend.ai.service;

import hk.ljx.fishpicsbackend.ai.dto.AiDrawPictureDTO;
import hk.ljx.fishpicsbackend.task.entity.Task;

public interface AiService {

    String submitTagTask(Long pictureId);

    Task getTagResult(String taskId);

    String submitDrawTask(AiDrawPictureDTO drawPictureDTO, Long userId);

    Task getDrawResult(String taskId);

    String getDownloadImageUrl(String taskId);

    /**
     * 暴露到 Service 层，让其他端点能查 AiConfig 开关
     */
    boolean isFeatureEnabled(String fieldName);
}
