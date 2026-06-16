package hk.ljx.fishpicsbackend.ai.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 任务 SSE 推送管理器
 * 替代前端轮询，任务完成/失败时主动推送结果
 */
@Slf4j
@Component
public class AiSseEmitterRegistry {

    /** SSE 超时时间：3 分钟（AI 绘图最长约 180s） */
    private static final long SSE_TIMEOUT = 180_000L;

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * 注册一个 SSE 连接
     */
    public SseEmitter register(String taskId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        // 条件删除：只删除自己注册的 emitter 实例，避免 onTimeout→onCompletion 重入误删新连接
        emitter.onCompletion(() -> emitters.remove(taskId, emitter));
        emitter.onTimeout(() -> {
            emitters.remove(taskId, emitter);
            try {
                emitter.send(SseEmitter.event()
                        .name("result")
                        .data(Map.of("taskId", taskId, "status", "FAILED",
                                "errorMsg", "等待超时，请重试")));
                // 不调用 emitter.complete()，避免触发 onCompletion 回调导致重入误删
            } catch (Exception ignored) {}
            log.debug("[SSE] emitter timeout: taskId={}", taskId);
        });
        emitter.onError(e -> {
            emitters.remove(taskId, emitter);
            log.debug("[SSE] emitter error: taskId={}, msg={}", taskId, e.getMessage());
        });
        emitters.put(taskId, emitter);
        return emitter;
    }

    /**
     * 任务完成时推送结果
     */
    public void completeWithResult(String taskId, String result) {
        SseEmitter emitter = emitters.remove(taskId);
        if (emitter == null) return;
        try {
            emitter.send(SseEmitter.event()
                    .name("result")
                    .data(Map.of("taskId", taskId, "status", "DONE", "result", result != null ? result : "")));
            emitter.complete();
        } catch (IOException e) {
            log.debug("[SSE] send result failed: taskId={}", taskId);
        }
    }

    /**
     * 任务失败时推送错误
     */
    public void completeWithError(String taskId, String errorMsg) {
        SseEmitter emitter = emitters.remove(taskId);
        if (emitter == null) return;
        try {
            emitter.send(SseEmitter.event()
                    .name("result")
                    .data(Map.of("taskId", taskId, "status", "FAILED", "errorMsg", errorMsg != null ? errorMsg : "")));
            emitter.complete();
        } catch (IOException e) {
            log.debug("[SSE] send error failed: taskId={}", taskId);
        }
    }
}
