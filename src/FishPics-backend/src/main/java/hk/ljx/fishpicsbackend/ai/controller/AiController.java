package hk.ljx.fishpicsbackend.ai.controller;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import hk.ljx.fishpicsbackend.ai.dto.AiConfigDTO;
import hk.ljx.fishpicsbackend.ai.dto.AiDrawPictureDTO;
import hk.ljx.fishpicsbackend.ai.dto.AiTaskQueryDTO;
import hk.ljx.fishpicsbackend.ai.service.AiService;
import hk.ljx.fishpicsbackend.ai.vo.AiStatsVO;
import hk.ljx.fishpicsbackend.ai.vo.AiTaskSubmitVO;
import hk.ljx.fishpicsbackend.ai.vo.AiTaskVO;
import hk.ljx.fishpicsbackend.common.annotation.RequireAdmin;
import hk.ljx.fishpicsbackend.common.constants.SysConstants;
import hk.ljx.fishpicsbackend.common.dto.IdRequest;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.response.ResUtils;
import hk.ljx.fishpicsbackend.common.response.Response;
import hk.ljx.fishpicsbackend.common.utils.DownloadUtils;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import hk.ljx.fishpicsbackend.mapper.PicSystemMapper;
import hk.ljx.fishpicsbackend.mapper.TaskMapper;
import hk.ljx.fishpicsbackend.system.entity.PicSystem;
import hk.ljx.fishpicsbackend.task.entity.Task;
import hk.ljx.fishpicsbackend.user.entity.User;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

@RestController
@RequestMapping("/ai")
@Slf4j
public class AiController {

    private static final long MAX_AI_DOWNLOAD_SIZE = 50L * 1024 * 1024;

    private final ReentrantLock configLock = new ReentrantLock();

    @Resource
    private AiService aiService;

    @Resource
    private TaskMapper taskMapper;

    @Resource
    private PicSystemMapper picSystemMapper;

    @PostMapping("/tags")
    public Response<AiTaskSubmitVO> submitTagTask(@Valid @RequestBody IdRequest request) {
        ExcUtils.throwIfTrue(request.getId() == null, "图片ID不能为空");
        String taskId = aiService.submitTagTask(request.getId());
        AiTaskSubmitVO vo = new AiTaskSubmitVO();
        vo.setTaskId(taskId);
        vo.setStatus("PENDING");
        return ResUtils.success(vo);
    }

    @GetMapping("/tags/result/{taskId}")
    public Response<Task> getTagResult(@PathVariable String taskId) {
        Task task = aiService.getTagResult(taskId);
        ExcUtils.throwIfTrue(task == null, ExceptionCode.NOT_FOUND, "任务不存在");
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(user == null, ExceptionCode.NOT_LOGIN);
        ExcUtils.throwIfTrue(!user.getId().equals(task.getUserId()), ExceptionCode.UNAUTHORIZED);
        return ResUtils.success(task);
    }

    @PostMapping("/draw/submit")
    public Response<AiTaskSubmitVO> submitDrawTask(@Valid @RequestBody AiDrawPictureDTO drawPictureDTO) {
        ExcUtils.throwIfTrue(drawPictureDTO == null, "参数不能为空");
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(user == null, ExceptionCode.NOT_LOGIN);
        String taskId = aiService.submitDrawTask(drawPictureDTO, user.getId());
        AiTaskSubmitVO vo = new AiTaskSubmitVO();
        vo.setTaskId(taskId);
        vo.setStatus("PENDING");
        return ResUtils.success(vo);
    }

    @GetMapping("/draw/result/{taskId}")
    public Response<Task> getDrawResult(@PathVariable String taskId) {
        Task task = aiService.getDrawResult(taskId);
        ExcUtils.throwIfTrue(task == null, ExceptionCode.NOT_FOUND, "任务不存在");
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(user == null, ExceptionCode.NOT_LOGIN);
        ExcUtils.throwIfTrue(!user.getId().equals(task.getUserId()), ExceptionCode.UNAUTHORIZED);
        return ResUtils.success(task);
    }

    @GetMapping("/download-image/{taskId}")
    public void downloadImage(@PathVariable String taskId, HttpServletResponse response) throws Exception {
        try (DownloadUtils.RemoteFileStream remoteFile =
                     DownloadUtils.openRemoteFile(aiService.getDownloadImageUrl(taskId), MAX_AI_DOWNLOAD_SIZE)) {
            response.setContentType(resolveContentType(remoteFile.getContentType()));
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + sanitizeFileName(remoteFile.getFileName()) + "\"");
            if (remoteFile.getContentLength() != null && remoteFile.getContentLength() >= 0) {
                response.setContentLengthLong(remoteFile.getContentLength());
            }
            DownloadUtils.copyToOutputWithLimit(
                    remoteFile.getInputStream(),
                    response.getOutputStream(),
                    MAX_AI_DOWNLOAD_SIZE
            );
        }
    }

    @RequireAdmin
    @PostMapping("/admin/tasks")
    public Response<IPage<AiTaskVO>> getTasks(@Valid @RequestBody AiTaskQueryDTO queryDTO) {
        int current = Math.max(queryDTO.getCurrent(), 1);
        int pageSize = Math.min(Math.max(queryDTO.getPageSize(), 1), 100);
        Page<Task> page = new Page<>(current, pageSize);
        LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<>();

        if (queryDTO.getType() != null) {
            String bizType = switch (queryDTO.getType()) {
                case 0 -> "ai_tag";
                case 2 -> "ai_draw";
                default -> null;
            };
            if (bizType != null) {
                wrapper.eq(Task::getBizType, bizType);
            }
        }

        if (queryDTO.getStatus() != null) {
            switch (queryDTO.getStatus()) {
                case 0 -> wrapper.in(Task::getStatus, "PENDING", "PROCESSING");
                case 1 -> wrapper.eq(Task::getStatus, "DONE");
                case 2 -> wrapper.eq(Task::getStatus, "FAILED");
                default -> {
                }
            }
        }

        wrapper.orderByDesc(Task::getCreateTime);
        IPage<Task> taskPage = taskMapper.selectPage(page, wrapper);

        List<AiTaskVO> voList = new ArrayList<>();
        for (Task task : taskPage.getRecords()) {
            AiTaskVO vo = new AiTaskVO();
            vo.setId(task.getId());
            vo.setUserId(task.getUserId());
            vo.setSubType(task.getBizType());
            vo.setPictureId(task.getBizId());
            vo.setCreateTime(task.getCreateTime());
            vo.setErrorMsg(task.getErrorMsg());
            vo.setType(switch (task.getBizType()) {
                case "ai_tag" -> 0;
                case "ai_draw" -> 2;
                default -> -1;
            });
            vo.setStatus(switch (task.getStatus()) {
                case "DONE" -> 1;
                case "FAILED" -> 2;
                default -> 0;
            });
            voList.add(vo);
        }

        Page<AiTaskVO> result = new Page<>(taskPage.getCurrent(), taskPage.getSize(), taskPage.getTotal());
        result.setRecords(voList);
        return ResUtils.success(result);
    }

    @RequireAdmin
    @GetMapping("/admin/stats")
    public Response<AiStatsVO> getStats() {
        AiStatsVO stats = new AiStatsVO();
        stats.setTotalTasks(taskMapper.selectCount(null));
        stats.setSuccessTasks(taskMapper.selectCount(new LambdaQueryWrapper<Task>().eq(Task::getStatus, "DONE")));
        stats.setFailedTasks(taskMapper.selectCount(new LambdaQueryWrapper<Task>().eq(Task::getStatus, "FAILED")));
        stats.setProcessingTasks(taskMapper.selectCount(new LambdaQueryWrapper<Task>().in(Task::getStatus, "PENDING", "PROCESSING")));

        Map<String, Long> typeCounts = new HashMap<>();
        typeCounts.put("0", taskMapper.selectCount(new LambdaQueryWrapper<Task>().eq(Task::getBizType, "ai_tag")));
        typeCounts.put("2", taskMapper.selectCount(new LambdaQueryWrapper<Task>().eq(Task::getBizType, "ai_draw")));
        stats.setTypeCounts(typeCounts);
        return ResUtils.success(stats);
    }

    @RequireAdmin
    @GetMapping("/admin/config")
    public Response<AiConfigDTO> getConfig() {
        LambdaQueryWrapper<PicSystem> wrapper = new LambdaQueryWrapper<PicSystem>()
                .eq(PicSystem::getSyskey, SysConstants.AI_CONFIG_KEY);
        PicSystem record = picSystemMapper.selectOne(wrapper);

        AiConfigDTO config;
        if (record == null || record.getSysvalue() == null) {
            config = new AiConfigDTO();
            config.setTaggingEnabled(true);
            config.setEditingEnabled(true);
            config.setGenerationEnabled(true);
            config.setRecommendationEnabled(true);
        } else {
            config = JSONUtil.toBean(record.getSysvalue(), AiConfigDTO.class);
        }
        return ResUtils.success(config);
    }

    @RequireAdmin
    @PostMapping("/admin/config")
    public Response<Boolean> updateConfig(@Valid @RequestBody AiConfigDTO configDTO) {
        configLock.lock();
        try {
            LambdaQueryWrapper<PicSystem> wrapper = new LambdaQueryWrapper<PicSystem>()
                    .eq(PicSystem::getSyskey, SysConstants.AI_CONFIG_KEY);
            PicSystem record = picSystemMapper.selectOne(wrapper);

            AiConfigDTO config;
            if (record == null || record.getSysvalue() == null) {
                config = new AiConfigDTO();
                config.setTaggingEnabled(true);
                config.setEditingEnabled(true);
                config.setGenerationEnabled(true);
                config.setRecommendationEnabled(true);
                record = new PicSystem();
                record.setSyskey(SysConstants.AI_CONFIG_KEY);
            } else {
                config = JSONUtil.toBean(record.getSysvalue(), AiConfigDTO.class);
            }

            if (configDTO.getTaggingEnabled() != null) config.setTaggingEnabled(configDTO.getTaggingEnabled());
            if (configDTO.getEditingEnabled() != null) config.setEditingEnabled(configDTO.getEditingEnabled());
            if (configDTO.getGenerationEnabled() != null) config.setGenerationEnabled(configDTO.getGenerationEnabled());
            if (configDTO.getRecommendationEnabled() != null) config.setRecommendationEnabled(configDTO.getRecommendationEnabled());

            record.setSysvalue(JSONUtil.toJsonStr(config));
            picSystemMapper.insertOrUpdate(record);
            return ResUtils.success(true);
        } finally {
            configLock.unlock();
        }
    }

    private String resolveContentType(String contentType) {
        return (contentType == null || contentType.isBlank())
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE : contentType;
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "image";
        }
        return fileName.replace("\\", "_").replace("\"", "_");
    }
}
