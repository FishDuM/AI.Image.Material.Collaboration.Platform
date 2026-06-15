package hk.ljx.fishpicsbackend.ai.handler;

import cn.hutool.json.JSONUtil;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeConnectionProperties;
import hk.ljx.fishpicsbackend.ai.dto.AiDrawPictureDTO;
import hk.ljx.fishpicsbackend.common.enums.PicturePromptEnum;
import hk.ljx.fishpicsbackend.common.enums.PictureSizeEnum;
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

    /** AI 生图超时时间（秒） */
    private static final int AI_TIMEOUT_SECONDS = 180;

    @Resource
    private MultiModalConversation multiModalConversation;

    @Resource
    @Qualifier("aiTaskExecutor")
    private java.util.concurrent.Executor aiTaskExecutor;

    @Resource
    private DashScopeConnectionProperties dashScopeConnectionProperties;

    @Override
    public String getBizType() {
        return "ai_draw";
    }

    @Override
    public void execute(Task task) throws Exception {
        if (task.getParam() == null || task.getParam().isBlank()) {
            throw new RuntimeException("任务参数为空，无法执行AI生图");
        }
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
        if (apiKey == null || apiKey.isBlank()) {
            throw new RuntimeException("AI 服务 API Key 未配置");
        }
        MultiModalConversationParam param = MultiModalConversationParam.builder()
                .apiKey(apiKey)
                .model("qwen-image-2.0-pro")
                .messages(Collections.singletonList(userMessage))
                .parameters(parameters)
                .build();

        MultiModalConversationResult result;
        MultiModalConversation conv = multiModalConversation;
        CompletableFuture<MultiModalConversationResult> future = CompletableFuture.supplyAsync(() -> {
            try {
                return conv.call(param);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, aiTaskExecutor);
        try {
            result = future.get(AI_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new RuntimeException("AI 生图超时（" + AI_TIMEOUT_SECONDS + "秒），请重试");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            throw new RuntimeException("AI 生图失败: " + (cause != null ? cause.getMessage() : e.getMessage()), cause);
        }
        // 逐层校验返回结果，拿到生成的图片 URL
        if (result == null || result.getOutput() == null
                || result.getOutput().getChoices() == null
                || result.getOutput().getChoices().isEmpty()) {
            throw new RuntimeException("AI 生图返回结果为空");
        }
        var choice = result.getOutput().getChoices().getFirst();
        if (choice.getMessage() == null || choice.getMessage().getContent() == null
                || choice.getMessage().getContent().isEmpty()) {
            throw new RuntimeException("AI 生图返回内容为空");
        }
        var imageEntry = choice.getMessage().getContent().getFirst().get("image");
        if (imageEntry == null) {
            throw new RuntimeException("AI 生图返回结果中无图片 URL");
        }
        String url = imageEntry.toString();
        log.info("ai draw success: {}", url);

        task.setResult(url);
    }

    @Override
    public void persist(Task task) {
        // 生图结果不自动入库，前端通过轮询获取 URL
    }
}
