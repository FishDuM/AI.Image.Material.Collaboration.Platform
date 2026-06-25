package hk.ljx.fishpicsbackend.ai.handler;

import cn.hutool.json.JSONUtil;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeConnectionProperties;
import hk.ljx.fishpicsbackend.ai.component.AiQuotaManager;
import hk.ljx.fishpicsbackend.ai.dto.AiDrawPictureDTO;
import hk.ljx.fishpicsbackend.common.enums.PicturePromptEnum;
import hk.ljx.fishpicsbackend.common.enums.PictureSizeEnum;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.task.entity.Task;
import hk.ljx.fishpicsbackend.task.handler.TaskHandler;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Qualifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Component
public class AiDrawTaskHandler implements TaskHandler {

    private static final int AI_TIMEOUT_SECONDS = 180;

    @Resource
    private MultiModalConversation multiModalConversation;

    @Resource
    @Qualifier("aiTaskExecutor")
    private Executor aiTaskExecutor;

    @Resource
    private DashScopeConnectionProperties dashScopeConnectionProperties;

    @Resource
    private AiQuotaManager aiQuotaManager;

    @Override
    public String getBizType() {
        return "ai_draw";
    }

    @Override
    public void execute(Task task) throws Exception {
        ExcUtils.throwIfTrue(task.getParam() == null || task.getParam().isBlank(),
                ExceptionCode.PARAMETER_ERROR, "任务参数为空，无法执行AI生图");
        AiDrawPictureDTO dto = JSONUtil.toBean(task.getParam(), AiDrawPictureDTO.class);

        String description = dto.getDescription();
        String exclusion = dto.getExclusion();
        String style = dto.getStyle();
        String size = PictureSizeEnum.getSizeByCode(dto.getSize());

        String promptStyle = PicturePromptEnum.getPromptByCode(style);

        MultiModalMessage userMessage = MultiModalMessage.builder()
                .role(Role.USER.getValue())
                .content(List.of(Collections.singletonMap("text", description + promptStyle)))
                .build();

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("watermark", false);
        parameters.put("prompt_extend", true);
        if (exclusion != null) {
            parameters.put("negative_prompt", exclusion);
        }
        parameters.put("size", size);

        String apiKey = dashScopeConnectionProperties.getApiKey();
        ExcUtils.throwIfTrue(apiKey == null || apiKey.isBlank(),
                ExceptionCode.SERVICE_UNAVAILABLE, "AI 服务 API Key 未配置");
        MultiModalConversationParam param = MultiModalConversationParam.builder()
                .apiKey(apiKey)
                .model("qwen-image-2.0-pro")
                .messages(Collections.singletonList(userMessage))
                .parameters(parameters)
                .build();

        MultiModalConversation conv = multiModalConversation;
        CompletableFuture<MultiModalConversationResult> future = CompletableFuture.supplyAsync(() -> {
            try {
                return conv.call(param);
            } catch (Exception e) {
                throw new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "AI 生图执行失败", e);
            }
        }, aiTaskExecutor);
        MultiModalConversationResult result;
        try {
            result = future.get(AI_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new BaseException(ExceptionCode.SERVICE_UNAVAILABLE, "AI 生图超时（" + AI_TIMEOUT_SECONDS + "秒），请重试");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            throw new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR,
                    "AI 生图失败: " + (cause != null ? cause.getMessage() : e.getMessage()), cause);
        }
        ExcUtils.throwIfTrue(result == null || result.getOutput() == null,
                ExceptionCode.INTERNAL_SERVER_ERROR, "AI 生图返回结果为空");
        var choices = result.getOutput().getChoices();
        ExcUtils.throwIfTrue(choices == null || choices.isEmpty(),
                ExceptionCode.INTERNAL_SERVER_ERROR, "AI 生图结果为空(无 choices)");
        var content = choices.getFirst().getMessage().getContent();
        var imageEntry = content != null && !content.isEmpty() ? content.getFirst().get("image") : null;
        ExcUtils.throwIfTrue(imageEntry == null,
                ExceptionCode.INTERNAL_SERVER_ERROR, "AI 生图结果格式异常");
        String url = imageEntry.toString();
        log.info("AI 生图成功: {}", url);

        task.setResult(url);
    }

    @Override
    public void onFailed(Task task) {
        if (task.getUserId() != null) {
            try {
                aiQuotaManager.refund("draw", task.getUserId());
            } catch (Exception e) {
                log.warn("配额退还失败: taskId={}, err={}", task.getTaskId(), e.getMessage());
            }
        }
    }

}
