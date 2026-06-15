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

    /**
     * 管理后台：分页查询 AI 任务
     */
    IPage<AiTaskVO> getAdminTasks(AiTaskQueryDTO queryDTO);

    /**
     * 管理后台：AI 任务统计
     */
    AiStatsVO getAdminStats();

    /**
     * 管理后台：获取 AI 配置
     */
    AiConfigDTO getAdminConfig();

    /**
     * 管理后台：更新 AI 配置
     */
    Boolean updateAdminConfig(AiConfigDTO configDTO);
}
