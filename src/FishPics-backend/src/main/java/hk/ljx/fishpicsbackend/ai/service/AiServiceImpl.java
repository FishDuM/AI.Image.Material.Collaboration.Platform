package hk.ljx.fishpicsbackend.ai.service;

import cn.hutool.json.JSONUtil;
import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeConnectionProperties;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import hk.ljx.fishpicsbackend.ai.dto.AiDrawPictureDTO;
import hk.ljx.fishpicsbackend.ai.vo.AiPictureMessage;
import hk.ljx.fishpicsbackend.common.enums.PicturePromptEnum;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import hk.ljx.fishpicsbackend.picture.entity.Picture;
import hk.ljx.fishpicsbackend.picture.service.PictureService;
import hk.ljx.fishpicsbackend.user.entity.User;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.content.Media;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import java.net.URI;
import java.util.*;

import static hk.ljx.fishpicsbackend.common.constants.UserConstants.ADMIN;

@Slf4j
@Service
public class AiServiceImpl implements AiService {

    @Resource
    private ChatModel chatModel;

    @Resource
    private DashScopeConnectionProperties dashScopeConnectionProperties;

    @Resource
    private PictureService pictureService;

    private static final String TAG_PROMPT = "你需要生成一个图片名称，长度不超过6个汉字。你需要生成一个图片描述，长度不超过100个汉字。你可以根据标签：'人物、动物、植物、美食、风景、建筑、物品、服饰、数码、家居、插画、二次元、实拍、文档、表情包'来描述图片的内容，最多选择不超过3个，最少也要有1个。";

    /**
     * 使用 ai 识别出图片的标签
     *
     * @param id 图片 id
     * @return 标签
     */
    @Override
    public AiPictureMessage getTagsByPicture(Long id) {
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(user == null, ExceptionCode.NOT_LOGIN);
        Picture picture = pictureService.getById(id);
        ExcUtils.throwIfTrue(picture == null, "图片不存在");
        ExcUtils.throwIfTrue(!picture.getUserId().equals(user.getId())  && !user.getRole().equals(ADMIN), ExceptionCode.UNAUTHORIZED);

        // 标签识别 agent
        BeanOutputConverter<AiPictureMessage> converter = new BeanOutputConverter<>(AiPictureMessage.class);
        String format = converter.getFormat();
        ReactAgent agent = ReactAgent.builder()
                .name("chat_agent")
                .model(chatModel)
                .outputSchema(format)
                .systemPrompt(TAG_PROMPT)
                .saver(new MemorySaver())
                .build();
        AssistantMessage response;
        AiPictureMessage aiPictureMessage = null;
        try {
            UserMessage userMessage = UserMessage.builder()
                    .text("帮我识别这个图片")
                    .media(Media.builder()
                            .mimeType(MimeTypeUtils.ALL)
                            .data(new URI(picture.getUrl())).build()).build();
            response = agent.call(userMessage);
            log.info("生成图片信息成功: {}", response.getText());
            String text = response.getText();
            if (text.startsWith("```")) {
                text = text.replaceAll("(?s)^```(?:json)?\\s*|\\s*```$", "");
            }
            aiPictureMessage = JSONUtil.toBean(text, AiPictureMessage.class);
            picture.setTags(JSONUtil.toJsonStr(aiPictureMessage.getTags()));
            picture.setPictureName(aiPictureMessage.getPictureName());
            picture.setIntroduction(aiPictureMessage.getIntroduction());
            return aiPictureMessage;
        } catch (Exception e) {
            log.error("生成图片信息失败: {}", e.getMessage());
            ExcUtils.error(ExceptionCode.AI_DRAW_ERROR, e.getMessage());
        }
        return null;
    }

    /**
     * ai 文生图
     *
     * @param drawPictureDTO 文生图参数
     * @return 图片链接
     */
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
        MultiModalConversationResult result = null;
        try {
            result = conv.call(param);
        } catch (NoApiKeyException e) {
            log.error("生图失败，apiKey无效");
            ExcUtils.error(ExceptionCode.SERVICE_UNAVAILABLE);
        } catch (UploadFileException e) {
            log.error("生图失败，上传文件失败");
            ExcUtils.error(ExceptionCode.SERVICE_UNAVAILABLE);
        }
        if (result == null) {
            log.error("生图失败，result为空");
            ExcUtils.error(ExceptionCode.SERVICE_UNAVAILABLE);
        }
        String url = result.getOutput().getChoices().getFirst().getMessage().getContent().getFirst().get("image").toString();
        log.info("生图成功: {}, URL: {}", JSONUtil.toJsonStr(result), url);
        return url;
    }
}
