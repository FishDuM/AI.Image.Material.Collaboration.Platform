package hk.ljx.fishpicsbackend.ai;

import cn.hutool.json.JSONUtil;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import hk.ljx.fishpicsbackend.ai.dto.AiDrawPictureDTO;
import hk.ljx.fishpicsbackend.ai.vo.AiPictureMessage;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import hk.ljx.fishpicsbackend.picture.Picture;
import hk.ljx.fishpicsbackend.picture.PictureService;
import hk.ljx.fishpicsbackend.user.User;
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

import static hk.ljx.fishpicsbackend.common.constants.UserConstants.ADMIN;

@Slf4j
@Service
public class AiServiceImpl implements AiService {

    @Resource
    private ChatModel chatModel;

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
                            .mimeType(MimeTypeUtils.IMAGE_JPEG)
                            .data(new URI(picture.getUrl())).build()).build();
            response = agent.call(userMessage);
            log.info("生成图片信息成功: {}", response.getText());
            aiPictureMessage = JSONUtil.toBean(response.getText(), AiPictureMessage.class);
            picture.setTags(JSONUtil.toJsonStr(aiPictureMessage.getTags()));
            picture.setPictureName(aiPictureMessage.getPictureName());
            picture.setIntroduction(aiPictureMessage.getIntroduction());
        } catch (Exception e) {
            log.error("生成图片信息失败: {}", e.getMessage());
            ExcUtils.error(ExceptionCode.AI_DRAW_ERROR, e.getMessage());
        }
        return aiPictureMessage;
    }

    /**
     * ai 文生图
     *
     * @param drawPictureDTO 文生图参数
     * @return 图片链接
     */
    @Override
    public String drawPicture(AiDrawPictureDTO drawPictureDTO) {
        return null;
    }
}
