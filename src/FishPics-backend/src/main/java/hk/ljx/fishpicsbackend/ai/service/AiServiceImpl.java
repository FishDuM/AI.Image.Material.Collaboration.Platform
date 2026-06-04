package hk.ljx.fishpicsbackend.ai.service;

import cn.hutool.json.JSONUtil;
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
import hk.ljx.fishpicsbackend.common.enums.PictureSizeEnum;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.task.entity.Task;
import hk.ljx.fishpicsbackend.task.service.TaskService;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import hk.ljx.fishpicsbackend.picture.entity.Picture;
import hk.ljx.fishpicsbackend.picture.service.PictureService;
import hk.ljx.fishpicsbackend.user.entity.User;
import hk.ljx.fishpicsbackend.permission.service.PermissionService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class AiServiceImpl implements AiService {

    @Resource
    private TaskService taskService;

    @Resource
    private DashScopeConnectionProperties dashScopeConnectionProperties;

    @Resource
    private PictureService pictureService;

    @Resource
    private PermissionService permissionService;

    @Override
    public String submitTagTask(Long pictureId) {
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(user == null, ExceptionCode.NOT_LOGIN);
        Picture picture = pictureService.getById(pictureId);
        ExcUtils.throwIfTrue(picture == null, "图片不存在");
        ExcUtils.throwIfTrue(!picture.getUserId().equals(user.getId()) && !permissionService.hasPermission(user.getId(), "ai:config"), ExceptionCode.UNAUTHORIZED);

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
        String style = drawPictureDTO.getStyle();
        String size = PictureSizeEnum.getSizeByCode(drawPictureDTO.getSize());

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
        parameters.put("size", size);

        String apikey = dashScopeConnectionProperties.getApiKey();
        ExcUtils.throwIfTrue(apikey == null || apikey.isBlank(), ExceptionCode.SERVICE_UNAVAILABLE, "AI服务未配置API Key");
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
            log.error("生图失败，apiKey无效", e);
            throw new hk.ljx.fishpicsbackend.common.exception.BaseException(
                    ExceptionCode.SERVICE_UNAVAILABLE.getCode(), "AI服务apiKey无效");
        } catch (UploadFileException e) {
            log.error("生图失败，上传文件失败", e);
            throw new hk.ljx.fishpicsbackend.common.exception.BaseException(
                    ExceptionCode.SERVICE_UNAVAILABLE.getCode(), "AI服务文件上传失败");
        }
        String url = result.getOutput().getChoices().getFirst().getMessage().getContent().getFirst().get("image").toString();
        log.info("生图成功: {}", url);
        return url;
    }

    @Override
    public String submitDrawTask(AiDrawPictureDTO drawPictureDTO, Long userId) {
        ExcUtils.throwIfTrue(drawPictureDTO == null || drawPictureDTO.getDescription() == null,
                "画面描述不能为空");
        String apikey = dashScopeConnectionProperties.getApiKey();
        ExcUtils.throwIfTrue(apikey == null || apikey.isBlank(), ExceptionCode.SERVICE_UNAVAILABLE, "AI服务未配置，无法提交任务");
        String paramJson = JSONUtil.toJsonStr(drawPictureDTO);
        return taskService.submitTask("ai_draw", null, paramJson, userId);
    }

    @Override
    public Task getDrawResult(String taskId) {
        return taskService.getTaskByTaskId(taskId);
    }
}
