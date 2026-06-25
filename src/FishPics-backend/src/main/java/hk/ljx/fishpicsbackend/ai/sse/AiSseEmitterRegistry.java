package hk.ljx.fishpicsbackend.ai.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class AiSseEmitterRegistry {

    private static final long SSE_TIMEOUT = 180_000L;

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(String taskId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        emitter.onCompletion(() -> emitters.remove(taskId, emitter));
        emitter.onTimeout(() -> {
            emitters.remove(taskId, emitter);
            try {
                emitter.send(SseEmitter.event()
                        .name("result")
                        .data(Map.of("taskId", taskId, "status", "TIMEOUT",
                                "errorMsg", "任务处理中，请稍后查看结果")));
            } catch (Exception ignored) {}
            log.debug("[SSE] 推送超时: taskId={}", taskId);
        });
        emitter.onError(e -> {
            emitters.remove(taskId, emitter);
            log.debug("[SSE] 推送错误: taskId={}, msg={}", taskId, e.getMessage());
        });
        emitters.put(taskId, emitter);
        return emitter;
    }

    public void completeWithResult(String taskId, String result) {
        sendFinalEvent(taskId, Map.of("taskId", taskId, "status", "DONE",
                "result", result != null ? result : ""));
    }

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
            log.debug("[SSE] 发送最终事件失败: taskId={}", taskId);
            emitter.complete();
        }
    }
}
