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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class AiDrawTaskHandler implements TaskHandler {

    @Resource
    private DashScopeConnectionProperties dashScopeConnectionProperties;

    @Override
    public String getBizType() {
        return "ai_draw";
    }

    @Override
    public void execute(Task task) throws Exception {
        AiDrawPictureDTO dto = JSONUtil.toBean(task.getParam(), AiDrawPictureDTO.class);

        String description = dto.getDescription();
        String exclusion = dto.getExclusion();
        String style = dto.getStyle();
        String size = PictureSizeEnum.getSizeByCode(dto.getSize());

        MultiModalConversation conv = new MultiModalConversation();
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
        MultiModalConversationParam param = MultiModalConversationParam.builder()
                .apiKey(apiKey)
                .model("qwen-image-2.0-pro")
                .messages(Collections.singletonList(userMessage))
                .parameters(parameters)
                .build();

        MultiModalConversationResult result = conv.call(param);
        String url = result.getOutput().getChoices().getFirst().getMessage().getContent().getFirst().get("image").toString();
        log.info("ai draw success: {}", url);

        task.setResult(url);
    }

    @Override
    public void persist(Task task) {
        // 生图结果不自动入库，只通过 WebSocket 返回 URL 给前端
    }
}
