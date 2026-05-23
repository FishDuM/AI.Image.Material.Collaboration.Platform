package hk.ljx.fishpicsbackend.ai.provider.alibaba;

import com.alibaba.cloud.ai.dashscope.image.DashScopeImageOptions;
import hk.ljx.fishpicsbackend.ai.dto.EditingRequest;
import hk.ljx.fishpicsbackend.ai.dto.EditingResult;
import hk.ljx.fishpicsbackend.ai.interfaces.ImageEditingService;
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
public class WanxiangEditingImpl implements ImageEditingService {

    private final ImageModel imageModel;

    @Value("${ai.dashscope.background-removal-model}")
    private String backgroundRemovalModel;

    @Value("${ai.dashscope.style-transfer-model}")
    private String styleTransferModel;

    public WanxiangEditingImpl(ImageModel imageModel) {
        this.imageModel = imageModel;
    }

    @Override
    public EditingResult editImage(EditingRequest request) {
        try {
            DashScopeImageOptions options;
            String promptText;
            if ("background_removal".equals(request.getEditType())) {
                options = DashScopeImageOptions.builder()
                    .withModel(backgroundRemovalModel)
                    .withRefImg(request.getImageUrl())
                    .build();
                promptText = "Remove background";
            } else if ("style_transfer".equals(request.getEditType())) {
                String style = request.getOptions() != null
                    ? (String) request.getOptions().getOrDefault("style", "anime")
                    : "anime";
                options = DashScopeImageOptions.builder()
                    .withModel(styleTransferModel)
                    .withRefImg(request.getImageUrl())
                    .withStyle(style)
                    .build();
                promptText = "Style transfer to " + style + " style";
            } else {
                throw new BaseException(ExceptionCode.PARAMETER_ERROR.getCode(), "不支持的编辑类型: " + request.getEditType());
            }

            ImagePrompt prompt = new ImagePrompt(promptText, options);
            ImageResponse response = imageModel.call(prompt);

            List<String> urls = new ArrayList<>();
            for (org.springframework.ai.image.ImageGeneration gen : response.getResults()) {
                Image img = gen.getOutput();
                if (img.getUrl() != null) {
                    urls.add(img.getUrl());
                }
            }

            return EditingResult.builder()
                .taskId(null)
                .status(urls.isEmpty() ? 0 : 1)
                .resultUrls(urls)
                .build();
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI editing failed: {}", request.getImageUrl(), e);
            throw new BaseException(ExceptionCode.AI_SERVICE_ERROR.getCode(), "AI修图失败: " + e.getMessage());
        }
    }
}
