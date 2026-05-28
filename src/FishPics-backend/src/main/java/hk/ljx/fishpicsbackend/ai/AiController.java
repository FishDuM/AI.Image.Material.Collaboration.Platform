package hk.ljx.fishpicsbackend.ai;

import hk.ljx.fishpicsbackend.ai.vo.AiPictureMessage;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.response.ResUtils;
import hk.ljx.fishpicsbackend.common.response.Response;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai")
public class AiController {

    @Resource
    private AiService aiService;

    @PostMapping("/tags")
    public Response<AiPictureMessage> getTagsByPicture(@RequestParam Long id) throws Exception {
        ExcUtils.throwIfTrue(id == null, "图片ID不能为空");
        return ResUtils.success(aiService.getTagsByPicture(id));
    }
}
