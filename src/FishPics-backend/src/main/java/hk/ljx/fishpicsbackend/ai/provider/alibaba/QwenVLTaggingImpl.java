package hk.ljx.fishpicsbackend.ai.provider.alibaba;

import cn.hutool.json.JSONUtil;
import hk.ljx.fishpicsbackend.ai.dto.TaggingResult;
import hk.ljx.fishpicsbackend.ai.interfaces.ImageTaggingService;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import java.net.URL;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.model.Media;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

@Slf4j
@Service
public class QwenVLTaggingImpl implements ImageTaggingService {

    private final ChatModel chatModel;

    public QwenVLTaggingImpl(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public TaggingResult analyzeImage(String imageUrl) {
        try {
            String systemPrompt = "你是一个专业的图片分析助手。请分析图片内容，返回JSON格式：{\"tags\": [\"标签1\", \"标签2\"], \"description\": \"图片描述文案\"}。标签用中文，5-10个。描述用中文，50-100字。只返回JSON，不要其他内容。";

            ChatResponse response = chatModel.call(
                new org.springframework.ai.chat.prompt.Prompt(
                    List.of(
                        new org.springframework.ai.chat.messages.SystemMessage(systemPrompt),
                        new org.springframework.ai.chat.messages.UserMessage(
                            "请分析这张图片",
                            List.of(new Media(MimeTypeUtils.IMAGE_PNG, new URL(imageUrl)))
                        )
                    )
                )
            );

            String text = response.getResult().getOutput().getText();

            String json = extractJson(text);
            TaggingResult taggingResult = JSONUtil.toBean(json, TaggingResult.class);
            log.info("AI tagging completed: {} tags, description length: {}",
                taggingResult.getTags() != null ? taggingResult.getTags().size() : 0,
                taggingResult.getDescription() != null ? taggingResult.getDescription().length() : 0);
            return taggingResult;

        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI tagging failed for image: {}", imageUrl, e);
            throw new BaseException(ExceptionCode.AI_SERVICE_ERROR.getCode(), "AI标注失败: " + e.getMessage());
        }
    }

    private String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return "{\"tags\": [], \"description\": \"\"}";
    }
}
