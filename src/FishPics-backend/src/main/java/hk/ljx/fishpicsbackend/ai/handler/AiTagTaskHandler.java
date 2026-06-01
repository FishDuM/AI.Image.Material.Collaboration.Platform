package hk.ljx.fishpicsbackend.ai.handler;

import cn.hutool.json.JSONUtil;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import hk.ljx.fishpicsbackend.ai.vo.AiPictureMessage;
import hk.ljx.fishpicsbackend.task.entity.Task;
import hk.ljx.fishpicsbackend.task.handler.TaskHandler;
import hk.ljx.fishpicsbackend.picture.entity.Picture;
import hk.ljx.fishpicsbackend.picture.service.PictureService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.content.Media;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;

import java.net.URI;

@Slf4j
@Component
public class AiTagTaskHandler implements TaskHandler {

    @Resource
    private ChatModel chatModel;

    @Resource
    private PictureService pictureService;

    private static final String TAG_PROMPT = "你需要生成一个图片名称，长度不超过6个汉字。你需要生成一个图片描述，长度不超过100个汉字。你可以根据标签：'人物、动物、植物、美食、风景、建筑、物品、服饰、数码、家居、插画、二次元、实拍、文档、表情包'来描述图片的内容，最多选择不超过3个，最少也要有1个。";

    @Override
    public String getBizType() {
        return "ai_tag";
    }

    @Override
    public void execute(Task task) throws Exception {
        Long pictureId = Long.valueOf(task.getBizId());
        Picture picture = pictureService.getById(pictureId);
        if (picture == null) {
            throw new RuntimeException("图片不存在: " + pictureId);
        }

        BeanOutputConverter<AiPictureMessage> converter = new BeanOutputConverter<>(AiPictureMessage.class);
        String format = converter.getFormat();
        ReactAgent agent = ReactAgent.builder()
                .name("chat_agent")
                .model(chatModel)
                .outputSchema(format)
                .systemPrompt(TAG_PROMPT)
                .saver(new MemorySaver())
                .build();

        UserMessage userMessage = UserMessage.builder()
                .text("帮我识别这个图片")
                .media(Media.builder()
                        .mimeType(MimeTypeUtils.ALL)
                        .data(new URI(picture.getUrl())).build())
                .build();
        AssistantMessage response = agent.call(userMessage);
        log.info("AI tag result for picture {}: {}", pictureId, response.getText());

        String text = response.getText();
        if (text.startsWith("```")) {
            text = text.replaceAll("(?s)^```(?:json)?\\s*|\\s*```$", "");
        }

        task.setResult(text);
    }

    @Override
    public void persist(Task task) {
        Long pictureId = Long.valueOf(task.getBizId());
        Picture picture = pictureService.getById(pictureId);
        if (picture == null) {
            throw new RuntimeException("图片不存在: " + pictureId);
        }

        String text = task.getResult();
        AiPictureMessage aiResult = JSONUtil.toBean(text, AiPictureMessage.class);
        picture.setTags(JSONUtil.toJsonStr(aiResult.getTags()));
        picture.setPictureName(aiResult.getPictureName());
        picture.setIntroduction(aiResult.getIntroduction());
        pictureService.updateById(picture);
    }
}
