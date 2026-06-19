package hk.ljx.fishpicsbackend.ai.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import hk.ljx.fishpicsbackend.ai.dto.AiConfigDTO;
import hk.ljx.fishpicsbackend.ai.dto.AiDrawPictureDTO;
import hk.ljx.fishpicsbackend.ai.dto.AiTaskQueryDTO;
import hk.ljx.fishpicsbackend.ai.vo.AiStatsVO;
import hk.ljx.fishpicsbackend.ai.vo.AiTaskVO;
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

    /**
     * 按 taskId 查询任务（供 SSE 等跨方法场景使用）
     */
    Task getTaskByTaskId(String taskId);

    IPage<AiTaskVO> getAdminTasks(AiTaskQueryDTO queryDTO);

    AiStatsVO getAdminStats();

    AiConfigDTO getAdminConfig();

    Boolean updateAdminConfig(AiConfigDTO configDTO);
}
