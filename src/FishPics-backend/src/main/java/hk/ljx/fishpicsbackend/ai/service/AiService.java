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

    boolean isFeatureEnabled(String fieldName);

    Task getTaskByTaskId(String taskId);

    IPage<AiTaskVO> getAdminTasks(AiTaskQueryDTO queryDTO);

    AiStatsVO getAdminStats();

    AiConfigDTO getAdminConfig();

    Boolean updateAdminConfig(AiConfigDTO configDTO);
}
