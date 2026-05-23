package hk.ljx.fishpicsbackend.ai.provider.alibaba;

import com.alibaba.cloud.ai.dashscope.image.DashScopeImageOptions;
import hk.ljx.fishpicsbackend.ai.dto.GenerationRequest;
import hk.ljx.fishpicsbackend.ai.dto.GenerationResult;
import hk.ljx.fishpicsbackend.ai.interfaces.ImageGenerationService;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class WanxiangGenerationImpl implements ImageGenerationService {

    private final ImageModel imageModel;

    @Value("${ai.dashscope.generation-model}")
    private String generationModel;

    public WanxiangGenerationImpl(ImageModel imageModel) {
        this.imageModel = imageModel;
    }

    @Override
    public GenerationResult generateImage(GenerationRequest request) {
        try {
            Integer n = request.getNumImages() != null ? request.getNumImages() : 1;

            DashScopeImageOptions.Builder optionsBuilder = DashScopeImageOptions.builder()
                .withModel(generationModel)
                .withN(n)
                .withNegativePrompt(request.getNegativePrompt());

            if (request.getWidth() != null && request.getHeight() != null) {
                optionsBuilder.withWidth(request.getWidth()).withHeight(request.getHeight());
            }

            DashScopeImageOptions options = optionsBuilder.build();

            ImagePrompt prompt = new ImagePrompt(request.getPrompt(), options);
            ImageResponse response = imageModel.call(prompt);

            List<String> urls = new ArrayList<>();
            for (org.springframework.ai.image.ImageGeneration gen : response.getResults()) {
                Image img = gen.getOutput();
                if (img.getUrl() != null) {
                    urls.add(img.getUrl());
                }
            }

            return GenerationResult.builder()
                .taskId(null)
                .status(urls.isEmpty() ? 0 : 1)
                .resultUrls(urls)
                .build();
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI generation failed for prompt: {}", request.getPrompt(), e);
            throw new BaseException(ExceptionCode.AI_SERVICE_ERROR.getCode(), "AI生图失败: " + e.getMessage());
        }
    }
}
