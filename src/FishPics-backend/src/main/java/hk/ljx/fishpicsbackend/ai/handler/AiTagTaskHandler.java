package hk.ljx.fishpicsbackend.ai.handler;

import cn.hutool.json.JSONUtil;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import hk.ljx.fishpicsbackend.ai.vo.AiPictureMessage;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.task.entity.Task;
import hk.ljx.fishpicsbackend.task.handler.TaskHandler;
import hk.ljx.fishpicsbackend.picture.entity.Picture;
import hk.ljx.fishpicsbackend.picture.service.PictureService;
import jakarta.annotation.PostConstruct;
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
import java.util.concurrent.*;

@Slf4j
@Component
public class AiTagTaskHandler implements TaskHandler {

    /** AI 标签识别超时时间（秒） */
    private static final int AI_TIMEOUT_SECONDS = 120;

    @Resource
    private ChatModel chatModel;

    @Resource
    private PictureService pictureService;

    private static final String TAG_PROMPT = "你需要生成一个图片名称，长度不超过6个汉字。你需要生成一个图片描述，长度不超过100个汉字。你可以根据标签：'人物、动物、植物、美食、风景、建筑、物品、服饰、数码、家居、插画、二次元、实拍、文档、表情包'来描述图片的内容，最多选择不超过3个，最少也要有1个。";

    private ReactAgent tagAgent;

    /**
     * 初始化标签识别 Agent
     * 用 BeanOutputConverter 强制模型输出结构化 JSON（pictureName/tags/introduction），
     * 避免后续解析时拿到自由文本还要手动拆字段
     */
    @PostConstruct
    public void init() {
        BeanOutputConverter<AiPictureMessage> converter = new BeanOutputConverter<>(AiPictureMessage.class);
        String format = converter.getFormat();
        this.tagAgent = ReactAgent.builder()
                .name("chat_agent")
                .model(chatModel)
                .outputSchema(format)
                .systemPrompt(TAG_PROMPT)
                .saver(new MemorySaver())
                .build();
    }

    @Override
    public String getBizType() {
        return "ai_tag";
    }

    @Override
    public void execute(Task task) throws Exception {
        ExcUtils.throwIfTrue(task.getBizId() == null || task.getBizId().isBlank(), "任务 bizId 为空");
        Long pictureId;
        try {
            pictureId = Long.valueOf(task.getBizId());
        } catch (NumberFormatException e) {
            throw new RuntimeException("任务 bizId 格式错误: " + task.getBizId());
        }
        Picture picture = pictureService.getById(pictureId);
        if (picture == null) {
            throw new RuntimeException("图片不存在: " + pictureId);
        }

        if (picture.getUrl() == null || picture.getUrl().isBlank()) {
            throw new RuntimeException("图片 URL 为空: " + pictureId);
        }

        // 把图片 URL 作为多模态输入丢给 Agent，让它识别图片内容
        UserMessage userMessage = UserMessage.builder()
                .text("帮我识别这个图片")
                .media(Media.builder()
                        .mimeType(MimeTypeUtils.ALL)
                        .data(new URI(picture.getUrl())).build())
                .build();
        AssistantMessage response;
        CompletableFuture<AssistantMessage> future = CompletableFuture.supplyAsync(() -> {
            try {
                return tagAgent.call(userMessage);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        try {
            response = future.get(AI_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new RuntimeException("AI 标签识别超时（" + AI_TIMEOUT_SECONDS + "秒），请重试");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            throw new RuntimeException("AI 标签识别失败: " + (cause != null ? cause.getMessage() : e.getMessage()), cause);
        }
        if (response == null || response.getText() == null) {
            throw new RuntimeException("AI 标签识别返回结果为空");
        }
        log.info("AI tag result for picture {}: {}", pictureId, response.getText());

        // Agent 有时会在 JSON 外面包一层 markdown 代码块，需要提取其中的 JSON 内容
        String text = response.getText().strip();
        // 处理 ```json ... ``` 或 ``` ... ``` 包裹（兼容大小写和不完整情况）
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            text = firstNewline > 0 ? text.substring(firstNewline + 1) : text.substring(3);
            int lastFence = text.lastIndexOf("```");
            if (lastFence >= 0) {
                text = text.substring(0, lastFence);
            }
        }
        text = text.trim();

        task.setResult(text);
    }

    /**
     * 持久化阶段：把 AI 识别结果（名称、描述、标签）写回 picture 表
     * 和 execute 分开是因为持久化需要在事务内完成，而 AI 调用不应放在事务里
     */
    @Override
    public void persist(Task task) {
        ExcUtils.throwIfTrue(task.getBizId() == null || task.getBizId().isBlank(), "任务 bizId 为空");
        Long pictureId;
        try {
            pictureId = Long.valueOf(task.getBizId());
        } catch (NumberFormatException e) {
            throw new RuntimeException("任务 bizId 格式错误: " + task.getBizId());
        }
        Picture picture = pictureService.getById(pictureId);
        if (picture == null) {
            throw new RuntimeException("图片不存在: " + pictureId);
        }

        String text = task.getResult();
        AiPictureMessage aiResult = JSONUtil.toBean(text, AiPictureMessage.class);
        if (aiResult.getTags() != null && !aiResult.getTags().isEmpty()) {
            picture.setTags(JSONUtil.toJsonStr(aiResult.getTags()));
        }
        if (aiResult.getPictureName() != null && !aiResult.getPictureName().isBlank()) {
            picture.setPictureName(aiResult.getPictureName());
        }
        if (aiResult.getIntroduction() != null && !aiResult.getIntroduction().isBlank()) {
            picture.setIntroduction(aiResult.getIntroduction());
        }
        pictureService.updateById(picture);
    }
}
