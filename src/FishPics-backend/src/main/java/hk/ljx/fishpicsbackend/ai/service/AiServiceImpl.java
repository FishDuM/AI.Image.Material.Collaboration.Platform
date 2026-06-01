package hk.ljx.fishpicsbackend.ai.service;

import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeConnectionProperties;
import hk.ljx.fishpicsbackend.ai.dto.AiDrawPictureDTO;
import hk.ljx.fishpicsbackend.common.enums.PicturePromptEnum;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.task.entity.Task;
import hk.ljx.fishpicsbackend.task.service.TaskService;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import hk.ljx.fishpicsbackend.picture.entity.Picture;
import hk.ljx.fishpicsbackend.picture.service.PictureService;
import hk.ljx.fishpicsbackend.user.entity.User;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

import static hk.ljx.fishpicsbackend.common.constants.UserConstants.ADMIN;

@Slf4j
@Service
public class AiServiceImpl implements AiService {

    @Resource
    private TaskService taskService;

    @Resource
    private DashScopeConnectionProperties dashScopeConnectionProperties;

    @Resource
    private PictureService pictureService;

    @Override
    public String submitTagTask(Long pictureId) {
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(user == null, ExceptionCode.NOT_LOGIN);
        Picture picture = pictureService.getById(pictureId);
        ExcUtils.throwIfTrue(picture == null, "图片不存在");
        ExcUtils.throwIfTrue(!picture.getUserId().equals(user.getId()) && !user.getRole().equals(ADMIN), ExceptionCode.UNAUTHORIZED);

        return taskService.submitTask("ai_tag", String.valueOf(pictureId), null, user.getId());
    }

    @Override
    public Task getTagResult(String taskId) {
        return taskService.getTaskByTaskId(taskId);
    }

    @Override
    public String drawPicture(AiDrawPictureDTO drawPictureDTO) {
        String description = drawPictureDTO.getDescription();
        String exclusion = drawPictureDTO.getExclusion();
        String width = drawPictureDTO.getWidth();
        String height = drawPictureDTO.getHeight();
        String style = drawPictureDTO.getStyle();

        MultiModalConversation conv = new MultiModalConversation();

        String promptStyle = PicturePromptEnum.getPromptByCode(style);

        MultiModalMessage userMessage = MultiModalMessage.builder()
                .role(Role.USER.getValue())
                .content(List.of(Collections.singletonMap("text", description + promptStyle))).build();

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("watermark", false);
        parameters.put("prompt_extend", true);
        if (exclusion != null) {
            parameters.put("negative_prompt", exclusion);
        }
        parameters.put("size", width + "*" + height);

        String apikey = dashScopeConnectionProperties.getApiKey();
        MultiModalConversationParam param = MultiModalConversationParam.builder()
                .apiKey(apikey)
                .model("qwen-image-2.0-pro")
                .messages(Collections.singletonList(userMessage))
                .parameters(parameters)
                .build();
        MultiModalConversationResult result;
        try {
            result = conv.call(param);
        } catch (NoApiKeyException e) {
            log.error("生图失败，apiKey无效");
            ExcUtils.error(ExceptionCode.SERVICE_UNAVAILABLE);
            return null;
        } catch (UploadFileException e) {
            log.error("生图失败，上传文件失败");
            ExcUtils.error(ExceptionCode.SERVICE_UNAVAILABLE);
            return null;
        }
        String url = result.getOutput().getChoices().getFirst().getMessage().getContent().getFirst().get("image").toString();
        log.info("生图成功: {}", url);
        return url;
    }
}
