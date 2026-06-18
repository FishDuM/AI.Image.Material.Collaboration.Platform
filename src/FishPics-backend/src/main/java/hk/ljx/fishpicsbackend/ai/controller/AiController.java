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
import hk.ljx.fishpicsbackend.common.annotation.AuditLog;
import hk.ljx.fishpicsbackend.common.annotation.RequireAdmin;
import hk.ljx.fishpicsbackend.common.annotation.RequireLogin;
import hk.ljx.fishpicsbackend.common.dto.IdRequest;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.utils.DownloadUtils;
import hk.ljx.fishpicsbackend.common.utils.LoginContextHelper;
import hk.ljx.fishpicsbackend.task.entity.Task;
import hk.ljx.fishpicsbackend.user.entity.User;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
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

    @RequireLogin
    @PostMapping("/tags")
    public Response<AiTaskSubmitVO> submitTagTask(@Valid @RequestBody IdRequest request) {
        ExcUtils.throwIfTrue(!aiService.isFeatureEnabled("taggingEnabled"),
                ExceptionCode.FORBIDDEN, "AI 标签功能已关闭");
        String taskId = aiService.submitTagTask(request.getId());
        return Response.ok(AiTaskSubmitVO.of(taskId));
    }

    @RequireLogin
    @GetMapping("/tags/result/{taskId}")
    public Response<Task> getTagResult(@PathVariable String taskId) {
        Task task = aiService.getTagResult(taskId);
        resolveTaskOwnership(task);
        return Response.ok(task);
    }

    @RequireLogin
    @PostMapping("/draw/submit")
    public Response<AiTaskSubmitVO> submitDrawTask(@Valid @RequestBody AiDrawPictureDTO drawPictureDTO) {
        ExcUtils.throwIfTrue(!aiService.isFeatureEnabled("generationEnabled"),
                ExceptionCode.FORBIDDEN, "AI 生图功能已关闭");
        User user = LoginContextHelper.requireUser();
        String taskId = aiService.submitDrawTask(drawPictureDTO, user.getId());
        return Response.ok(AiTaskSubmitVO.of(taskId));
    }

    @RequireLogin
    @GetMapping("/draw/result/{taskId}")
    public Response<Task> getDrawResult(@PathVariable String taskId) {
        Task task = aiService.getDrawResult(taskId);
        resolveTaskOwnership(task);
        return Response.ok(task);
    }

    /**
     * SSE 推送：AI 任务结果
     */
    @RequireLogin
    @GetMapping("/result-sse/{taskId}")
    public SseEmitter subscribeResult(@PathVariable String taskId) {
        Task task = aiService.getTaskByTaskId(taskId);
        resolveTaskOwnership(task);

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

    @RequireLogin
    @GetMapping("/download-image/{taskId}")
    public void downloadImage(@PathVariable String taskId, HttpServletResponse response) throws Exception {
        try (DownloadUtils.RemoteFileStream remoteFile =
                     DownloadUtils.openRemoteFile(aiService.getDownloadImageUrl(taskId), MAX_AI_DOWNLOAD_SIZE)) {
            response.setContentType(DownloadUtils.resolveContentType(remoteFile.getContentType()));
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + DownloadUtils.defaultFileName(remoteFile.getFileName()) + "\"");
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
    @AuditLog(module = "AI管理", operation = "查询任务列表")
    @PostMapping("/admin/tasks")
    public Response<IPage<AiTaskVO>> getTasks(@Valid @RequestBody AiTaskQueryDTO queryDTO) {
        return Response.ok(aiService.getAdminTasks(queryDTO));
    }

    @RequireAdmin
    @AuditLog(module = "AI管理", operation = "查询统计")
    @GetMapping("/admin/stats")
    public Response<AiStatsVO> getStats() {
        return Response.ok(aiService.getAdminStats());
    }

    @RequireAdmin
    @AuditLog(module = "AI管理", operation = "查询配置")
    @GetMapping("/admin/config")
    public Response<AiConfigDTO> getConfig() {
        return Response.ok(aiService.getAdminConfig());
    }

    @RequireAdmin
    @AuditLog(module = "AI管理", operation = "更新配置")
    @PostMapping("/admin/config")
    public Response<Boolean> updateConfig(@Valid @RequestBody AiConfigDTO configDTO) {
        aiService.updateAdminConfig(configDTO);
        return Response.ok(true);
    }

    /**
     * 校验任务存在性和所有权（getTagResult/getDrawResult/subscribeResult 共用）
     */
    private void resolveTaskOwnership(Task task) {
        ExcUtils.throwIfTrue(task == null, ExceptionCode.NOT_FOUND, "任务不存在");
        User user = LoginContextHelper.requireUser();
        ExcUtils.throwIfTrue(!user.getId().equals(task.getUserId()), ExceptionCode.UNAUTHORIZED);
    }
}
