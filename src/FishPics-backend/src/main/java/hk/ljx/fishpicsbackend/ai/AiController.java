package hk.ljx.fishpicsbackend.ai;

import hk.ljx.fishpicsbackend.ai.dto.AiDrawPictureDTO;
import hk.ljx.fishpicsbackend.ai.vo.AiPictureMessage;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.response.ResUtils;
import hk.ljx.fishpicsbackend.common.response.Response;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai")
public class AiController {

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
