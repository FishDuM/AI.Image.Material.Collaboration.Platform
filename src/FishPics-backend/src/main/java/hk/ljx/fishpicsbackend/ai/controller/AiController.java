package hk.ljx.fishpicsbackend.ai.controller;

import hk.ljx.fishpicsbackend.common.response.Response;
import com.baomidou.mybatisplus.core.metadata.IPage;
import hk.ljx.fishpicsbackend.ai.dto.AiConfigDTO;
import hk.ljx.fishpicsbackend.ai.dto.AiDrawPictureDTO;
import hk.ljx.fishpicsbackend.ai.dto.AiTaskQueryDTO;
import hk.ljx.fishpicsbackend.ai.service.AiService;
import hk.ljx.fishpicsbackend.ai.sse.AiSseEmitterRegistry;
import hk.ljx.fishpicsbackend.ai.vo.AiStatsVO;
import hk.ljx.fishpicsbackend.ai.vo.AiTaskSubmitVO;
import hk.ljx.fishpicsbackend.ai.vo.AiTaskVO;
import hk.ljx.fishpicsbackend.common.annotation.RequireAdmin;
import hk.ljx.fishpicsbackend.common.dto.IdRequest;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.utils.DownloadUtils;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/ai")
@Slf4j
public class AiController {

    private static final long MAX_AI_DOWNLOAD_SIZE = 50L * 1024 * 1024;

    @Resource
    private AiService aiService;

    @Resource
    private AiSseEmitterRegistry sseEmitterRegistry;

    @PostMapping("/tags")
    public Response<AiTaskSubmitVO> submitTagTask(@Valid @RequestBody IdRequest request) {
        ExcUtils.throwIfTrue(request.getId() == null, "图片ID不能为空");
        ExcUtils.throwIfTrue(!aiService.isFeatureEnabled("taggingEnabled"),
                ExceptionCode.FORBIDDEN, "AI 标签功能已关闭");
        String taskId = aiService.submitTagTask(request.getId());
        AiTaskSubmitVO vo = new AiTaskSubmitVO();
        vo.setTaskId(taskId);
        vo.setStatus("PENDING");
        return Response.ok(vo);
    }

    @GetMapping("/tags/result/{taskId}")
    public Response<Task> getTagResult(@PathVariable String taskId) {
        Task task = aiService.getTagResult(taskId);
        ExcUtils.throwIfTrue(task == null, ExceptionCode.NOT_FOUND, "任务不存在");
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(user == null, ExceptionCode.NOT_LOGIN);
        ExcUtils.throwIfTrue(!user.getId().equals(task.getUserId()), ExceptionCode.UNAUTHORIZED);
        return Response.ok(task);
    }

    @PostMapping("/draw/submit")
    public Response<AiTaskSubmitVO> submitDrawTask(@Valid @RequestBody AiDrawPictureDTO drawPictureDTO) {
        ExcUtils.throwIfTrue(drawPictureDTO == null, "参数不能为空");
        ExcUtils.throwIfTrue(!aiService.isFeatureEnabled("generationEnabled"),
                ExceptionCode.FORBIDDEN, "AI 生图功能已关闭");
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(user == null, ExceptionCode.NOT_LOGIN);
        String taskId = aiService.submitDrawTask(drawPictureDTO, user.getId());
        AiTaskSubmitVO vo = new AiTaskSubmitVO();
        vo.setTaskId(taskId);
        vo.setStatus("PENDING");
        return Response.ok(vo);
    }

    @GetMapping("/draw/result/{taskId}")
    public Response<Task> getDrawResult(@PathVariable String taskId) {
        Task task = aiService.getDrawResult(taskId);
        ExcUtils.throwIfTrue(task == null, ExceptionCode.NOT_FOUND, "任务不存在");
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(user == null, ExceptionCode.NOT_LOGIN);
        ExcUtils.throwIfTrue(!user.getId().equals(task.getUserId()), ExceptionCode.UNAUTHORIZED);
        return Response.ok(task);
    }

    /**
     * SSE 推送：AI 任务结果
     */
    @GetMapping("/result-sse/{taskId}")
    public SseEmitter subscribeResult(@PathVariable String taskId) {
        Task task = aiService.getTaskByTaskId(taskId);
        ExcUtils.throwIfTrue(task == null, ExceptionCode.NOT_FOUND, "任务不存在");
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(user == null, ExceptionCode.NOT_LOGIN);
        ExcUtils.throwIfTrue(!user.getId().equals(task.getUserId()), ExceptionCode.UNAUTHORIZED);

        // 任务已完成，直接返回
        if ("DONE".equals(task.getStatus())) {
            SseEmitter emitter = new SseEmitter(5_000L);
            try {
                emitter.send(SseEmitter.event().name("result")
                        .data(Map.of("taskId", taskId, "status", "DONE",
                                "result", task.getResult() != null ? task.getResult() : "")));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
            return emitter;
        }
        if ("FAILED".equals(task.getStatus())) {
            SseEmitter emitter = new SseEmitter(5_000L);
            try {
                emitter.send(SseEmitter.event().name("result")
                        .data(Map.of("taskId", taskId, "status", "FAILED",
                                "errorMsg", task.getErrorMsg() != null ? task.getErrorMsg() : "")));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
            return emitter;
        }

        // 任务仍在处理，注册 SSE 等待推送
        return sseEmitterRegistry.register(taskId);
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
        return Response.ok(aiService.getAdminTasks(queryDTO));
    }

    @RequireAdmin
    @GetMapping("/admin/stats")
    public Response<AiStatsVO> getStats() {
        return Response.ok(aiService.getAdminStats());
    }

    @RequireAdmin
    @GetMapping("/admin/config")
    public Response<AiConfigDTO> getConfig() {
        return Response.ok(aiService.getAdminConfig());
    }

    @RequireAdmin
    @PostMapping("/admin/config")
    public Response<Boolean> updateConfig(@Valid @RequestBody AiConfigDTO configDTO) {
        aiService.updateAdminConfig(configDTO);
        return Response.ok(true);
    }

    private String resolveContentType(String contentType) {
        return (contentType == null || contentType.isBlank())
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE : contentType;
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "image";
        }
        return fileName
                .replace("\\", "_")
                .replace("\"", "_")
                .replace("\r", "_")
                .replace("\n", "_")
                .replace("<", "_")
                .replace(">", "_");
    }
}
