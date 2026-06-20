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

    public SseEmitter register(String taskId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        // onTimeout 触发 onCompletion，用 remove(key, value) 防止误删新连接
        emitter.onCompletion(() -> emitters.remove(taskId, emitter));
        emitter.onTimeout(() -> {
            emitters.remove(taskId, emitter);
            try {
                emitter.send(SseEmitter.event()
                        .name("result")
                        .data(Map.of("taskId", taskId, "status", "TIMEOUT",
                                "errorMsg", "任务处理中，请稍后查看结果")));
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
        sendFinalEvent(taskId, Map.of("taskId", taskId, "status", "DONE",
                "result", result != null ? result : ""));
    }

    /**
     * 任务失败时推送错误
     */
    public void completeWithError(String taskId, String errorMsg) {
        sendFinalEvent(taskId, Map.of("taskId", taskId, "status", "FAILED",
                "errorMsg", errorMsg != null ? errorMsg : ""));
    }

    private void sendFinalEvent(String taskId, Map<String, String> data) {
        SseEmitter emitter = emitters.remove(taskId);
        if (emitter == null) return;
        try {
            emitter.send(SseEmitter.event().name("result").data(data));
            emitter.complete();
        } catch (IOException e) {
            log.debug("[SSE] send final event failed: taskId={}", taskId);
            emitter.complete();
        }
    }
}
