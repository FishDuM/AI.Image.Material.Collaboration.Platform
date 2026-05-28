package hk.ljx.fishpicsbackend.ai;

import com.alibaba.cloud.ai.dashscope.image.DashScopeImageOptions;
import hk.ljx.fishpicsbackend.ai.dto.AiDrawPictureDTO;
import hk.ljx.fishpicsbackend.ai.vo.AiPictureMessage;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.response.ResUtils;
import hk.ljx.fishpicsbackend.common.response.Response;
import jakarta.annotation.Resource;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/ai")
public class AiController {

    public static final String IMAGE_MODEL = "wanx2.1-t2i-turbo";

    @Resource
    private ImageModel imageModel;

    @Resource
    private AiService aiService;

    @PostMapping("/tags")
    public Response<AiPictureMessage> getTagsByPicture(@RequestParam Long id) {
        ExcUtils.throwIfTrue(id == null, "图片ID不能为空");
        return ResUtils.success(aiService.getTagsByPicture(id));
    }

    @PostMapping("/draw")
    public Response<String> drawPicture(@RequestBody AiDrawPictureDTO drawPictureDTO) {
        ExcUtils.throwIfTrue(drawPictureDTO == null, "参数不能为空");
        return ResUtils.success(aiService.drawPicture(drawPictureDTO));
    }
}
