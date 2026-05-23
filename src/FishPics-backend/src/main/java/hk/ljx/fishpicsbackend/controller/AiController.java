package hk.ljx.fishpicsbackend.controller;

import cn.hutool.core.util.ObjUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import hk.ljx.fishpicsbackend.ai.dto.AiConfigUpdateRequest;
import hk.ljx.fishpicsbackend.ai.dto.EditingRequest;
import hk.ljx.fishpicsbackend.ai.dto.GenerationRequest;
import hk.ljx.fishpicsbackend.ai.dto.RecommendationRequest;
import hk.ljx.fishpicsbackend.ai.service.AiTaskService;
import hk.ljx.fishpicsbackend.common.annotation.AuthCheck;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.response.ResUtils;
import hk.ljx.fishpicsbackend.common.response.Response;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import hk.ljx.fishpicsbackend.entity.User;
import hk.ljx.fishpicsbackend.vo.ai.AiConfigVO;
import hk.ljx.fishpicsbackend.vo.ai.AiStatsVO;
import hk.ljx.fishpicsbackend.vo.ai.AiTaskVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static hk.ljx.fishpicsbackend.common.constants.UserConstants.ADMIN;

@RestController
@RequestMapping("/ai")
@Slf4j
public class AiController {

    @Resource
    private AiTaskService aiTaskService;

    // ---- 用户端 ----

    @PostMapping("/tagging")
    public Response<Long> triggerTagging(@RequestParam("pictureId") Long pictureId) {
        ExcUtils.throwIfTrue(ObjUtil.isEmpty(pictureId), "图片ID不能为空");
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(ObjUtil.isEmpty(user), "请先登录");
        ExcUtils.throwIfTrue(user.getLevel() == null || user.getLevel() < 1, "仅VIP和SVIP用户可使用AI功能");
        Long taskId = aiTaskService.submitManualTagging(pictureId, user.getId());
        return ResUtils.success(taskId);
    }

    @PostMapping("/edit")
    public Response<Long> submitEdit(@RequestBody EditingRequest request) {
        ExcUtils.throwIfTrue(ObjUtil.isEmpty(request.getImageUrl()), "图片URL不能为空");
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(ObjUtil.isEmpty(user), "请先登录");
        ExcUtils.throwIfTrue(user.getLevel() == null || user.getLevel() < 1, "仅VIP和SVIP用户可使用AI功能");
        Long taskId = aiTaskService.submitEditing(request, user.getId());
        return ResUtils.success(taskId);
    }

    @PostMapping("/generate")
    public Response<Long> submitGeneration(@RequestBody GenerationRequest request) {
        ExcUtils.throwIfTrue(ObjUtil.isEmpty(request.getPrompt()), "提示词不能为空");
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(ObjUtil.isEmpty(user), "请先登录");
        ExcUtils.throwIfTrue(user.getLevel() == null || user.getLevel() < 1, "仅VIP和SVIP用户可使用AI功能");
        Long taskId = aiTaskService.submitGeneration(request, user.getId());
        return ResUtils.success(taskId);
    }

    @PostMapping("/recommend")
    public Response<Long> getRecommendations(@RequestBody RecommendationRequest request) {
        ExcUtils.throwIfTrue(ObjUtil.isEmpty(request.getReferencePictureId()), "参考图片ID不能为空");
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(ObjUtil.isEmpty(user), "请先登录");
        ExcUtils.throwIfTrue(user.getLevel() == null || user.getLevel() < 1, "仅VIP和SVIP用户可使用AI功能");
        Long taskId = aiTaskService.submitRecommendation(request, user.getId());
        return ResUtils.success(taskId);
    }

    @GetMapping("/task/{id}")
    public Response<AiTaskVO> getTaskStatus(@PathVariable("id") Long id) {
        AiTaskVO vo = aiTaskService.getTaskVOById(id);
        ExcUtils.throwIfTrue(vo == null, "任务不存在");
        return ResUtils.success(vo);
    }

    @GetMapping("/task/my")
    public Response<IPage<AiTaskVO>> listMyTasks(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int pageSize) {
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(ObjUtil.isEmpty(user), "请先登录");
        ExcUtils.throwIfTrue(user.getLevel() == null || user.getLevel() < 1, "仅VIP和SVIP用户可使用AI功能");
        return ResUtils.success(aiTaskService.getMyTasks(current, pageSize, user.getId()));
    }

    // ---- 管理员 ----

    @AuthCheck(role = ADMIN)
    @GetMapping("/admin/tasks")
    public Response<IPage<AiTaskVO>> listAllTasks(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long userId) {
        return ResUtils.success(aiTaskService.getAllTasks(current, pageSize, type, status, userId));
    }

    @AuthCheck(role = ADMIN)
    @GetMapping("/admin/stats")
    public Response<AiStatsVO> getStats() {
        return ResUtils.success(aiTaskService.getStats());
    }

    @AuthCheck(role = ADMIN)
    @GetMapping("/admin/config")
    public Response<AiConfigVO> getConfig() {
        return ResUtils.success(aiTaskService.getConfig());
    }

    @AuthCheck(role = ADMIN)
    @PostMapping("/admin/config")
    public Response<Boolean> updateConfig(@RequestBody AiConfigUpdateRequest request) {
        aiTaskService.updateConfig(request);
        return ResUtils.success(true);
    }
}
