package hk.ljx.fishpicsbackend.ai.controller;

import hk.ljx.fishpicsbackend.ai.dto.AiDrawPictureDTO;
import hk.ljx.fishpicsbackend.ai.service.AiService;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.response.ResUtils;
import hk.ljx.fishpicsbackend.common.response.Response;
import hk.ljx.fishpicsbackend.task.entity.Task;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/ai")
@Slf4j
public class AiController {

    @Resource
    private AiService aiService;

    @PostMapping("/tags")
    public Response<Map<String, String>> submitTagTask(@RequestParam Long id) {
        ExcUtils.throwIfTrue(id == null, "图片ID不能为空");
        String taskId = aiService.submitTagTask(id);
        Map<String, String> result = new HashMap<>();
        result.put("taskId", taskId);
        result.put("status", "PENDING");
        return ResUtils.success(result);
    }

    @GetMapping("/tags/result/{taskId}")
    public Response<Task> getTagResult(@PathVariable String taskId) {
        return ResUtils.success(aiService.getTagResult(taskId));
    }

    @PostMapping("/draw")
    public Response<String> drawPicture(@RequestBody AiDrawPictureDTO drawPictureDTO) {
        ExcUtils.throwIfTrue(drawPictureDTO == null, "参数不能为空");
        return ResUtils.success(aiService.drawPicture(drawPictureDTO));
    }
}
