package hk.ljx.fishpicsbackend.ai.handler;

import cn.hutool.json.JSONUtil;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import hk.ljx.fishpicsbackend.ai.vo.AiPictureMessage;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.utils.XssSanitizer;
import hk.ljx.fishpicsbackend.mapper.PictureTagMapper;
import hk.ljx.fishpicsbackend.picture.entity.PictureTag;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;

import java.net.URI;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class AiTagTaskHandler implements TaskHandler {

    /** AI 标签识别超时时间（秒） */
    private static final int AI_TIMEOUT_SECONDS = 120;
    private static final int MAX_AI_NAME_LENGTH = 6;
    private static final int MAX_AI_INTRO_LENGTH = 100;
    private static final int MAX_AI_TAG_COUNT = 3;
    private static final int MAX_AI_TAG_LENGTH = 20;

    @Resource
    private ChatModel chatModel;

    @Resource
    private PictureService pictureService;

    @Resource
    private PictureTagMapper pictureTagMapper;

    @Resource
    @Qualifier("aiTaskExecutor")
    private Executor aiTaskExecutor;

    private static final String TAG_PROMPT = "你需要生成一个图片名称，长度不超过6个汉字。你需要生成一个图片描述，长度不超过100个汉字。你可以根据标签：'人物、动物、植物、美食、风景、建筑、物品、服饰、数码、家居、插画、二次元、实拍、文档、表情包'来描述图片的内容，最多选择不超过3个，最少也要有1个。";

    private ReactAgent tagAgent;

    /**
     * 初始化标签识别 Agent
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
        Picture picture = resolvePictureFromTask(task);
        ExcUtils.throwIfTrue(picture.getUrl() == null || picture.getUrl().isBlank(),
                ExceptionCode.INTERNAL_SERVER_ERROR, "图片 URL 为空: " + picture.getId());

        // 把图片 URL 作为多模态输入丢给 Agent
        UserMessage userMessage = UserMessage.builder()
                .text("帮我识别这个图片")
                .media(Media.builder()
                        .mimeType(MimeTypeUtils.ALL)
                        .data(new URI(picture.getUrl())).build())
                .build();
        CompletableFuture<AssistantMessage> future = CompletableFuture.supplyAsync(() -> {
            try {
                return tagAgent.call(userMessage);
            } catch (Exception e) {
                throw new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "AI 标签识别执行失败", e);
            }
        }, aiTaskExecutor);
        AssistantMessage response;
        try {
            response = future.get(AI_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new BaseException(ExceptionCode.SERVICE_UNAVAILABLE, "AI 标签识别超时（" + AI_TIMEOUT_SECONDS + "秒），请重试");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            throw new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR,
                    "AI 标签识别失败: " + (cause != null ? cause.getMessage() : e.getMessage()), cause);
        }
        ExcUtils.throwIfTrue(response == null || response.getText() == null,
                ExceptionCode.INTERNAL_SERVER_ERROR, "AI 标签识别返回结果为空");
        log.info("AI tag result for picture {}: {}", picture.getId(), response.getText());

        // AI 有时返回 markdown 代码块包裹的 JSON，需要剥掉
        String text = response.getText().strip();
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            text = firstNewline > 0 ? text.substring(firstNewline + 1) : text.substring(3);
            int lastFence = text.lastIndexOf("```");
            if (lastFence >= 0) {
                text = text.substring(0, lastFence);
            }
            text = text.trim();
        }
        // fallback: 不是合法 JSON，试着找第一个 { 到最后一个 }
        if (!text.startsWith("{")) {
            int firstBrace = text.indexOf('{');
            int lastBrace = text.lastIndexOf('}');
            if (firstBrace >= 0 && lastBrace > firstBrace) {
                text = text.substring(firstBrace, lastBrace + 1);
            }
        }

        task.setResult(text);
    }

    // 和 execute 分开，因为持久化要在事务里跑
    @Override
    public void persist(Task task) {
        Picture picture = resolvePictureFromTask(task);
        Long pictureId = picture.getId();

        String text = task.getResult();
        AiPictureMessage aiResult = JSONUtil.toBean(text, AiPictureMessage.class);
        // XSS 清洗
        // 只填充用户没手动填过的字段，不覆盖已有数据
        if (aiResult.getTags() != null && !aiResult.getTags().isEmpty()) {
            // 只有图片尚无标签时才写入 AI 标签
            Long existingTagCount = pictureTagMapper.selectCount(
                    new LambdaQueryWrapper<PictureTag>()
                            .eq(PictureTag::getPictureId, pictureId));
            if (existingTagCount == 0) {
                List<String> safeTags = aiResult.getTags().stream()
                        .map(XssSanitizer::clean)
                        .map(tag -> truncate(tag, MAX_AI_TAG_LENGTH))
                        .filter(cn.hutool.core.util.StrUtil::isNotBlank)
                        .distinct()
                        .limit(MAX_AI_TAG_COUNT)
                        .collect(Collectors.toList());
                for (String tag : safeTags) {
                    PictureTag pt = new PictureTag();
                    pt.setPictureId(pictureId);
                    pt.setTagName(tag);
                    pictureTagMapper.insert(pt);
                }
            }
        }
        if (aiResult.getPictureName() != null && !aiResult.getPictureName().isBlank()
                && (picture.getPictureName() == null || picture.getPictureName().isBlank())) {
            String cleanName = XssSanitizer.clean(aiResult.getPictureName());
            cleanName = truncate(cleanName, MAX_AI_NAME_LENGTH);
            picture.setPictureName(cleanName);
        }
        if (aiResult.getIntroduction() != null && !aiResult.getIntroduction().isBlank()
                && (picture.getIntroduction() == null || picture.getIntroduction().isBlank())) {
            String cleanIntro = XssSanitizer.cleanRelaxed(aiResult.getIntroduction());
            cleanIntro = truncate(cleanIntro, MAX_AI_INTRO_LENGTH);
            picture.setIntroduction(cleanIntro);
        }
        pictureService.updateById(picture);
    }

    private Picture resolvePictureFromTask(Task task) {
        ExcUtils.throwIfTrue(task.getBizId() == null || task.getBizId().isBlank(),
                ExceptionCode.PARAMETER_ERROR, "任务 bizId 为空");
        Long pictureId;
        try {
            pictureId = Long.valueOf(task.getBizId());
        } catch (NumberFormatException e) {
            throw new BaseException(ExceptionCode.PARAMETER_ERROR, "任务 bizId 格式错误: " + task.getBizId(), e);
        }
        Picture picture = pictureService.getById(pictureId);
        ExcUtils.throwIfTrue(picture == null, ExceptionCode.NOT_FOUND, "图片不存在: " + pictureId);
        return picture;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
