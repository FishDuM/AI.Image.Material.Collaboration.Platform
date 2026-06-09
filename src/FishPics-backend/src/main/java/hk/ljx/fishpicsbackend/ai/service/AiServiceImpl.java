package hk.ljx.fishpicsbackend.ai.service;

import cn.hutool.json.JSONUtil;
import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeConnectionProperties;
import hk.ljx.fishpicsbackend.ai.dto.AiDrawPictureDTO;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.task.entity.Task;
import hk.ljx.fishpicsbackend.task.service.TaskService;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import hk.ljx.fishpicsbackend.picture.entity.Picture;
import hk.ljx.fishpicsbackend.picture.service.PictureService;
import hk.ljx.fishpicsbackend.user.entity.User;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class AiServiceImpl implements AiService {

    @Resource
    private TaskService taskService;

    @Resource
    private DashScopeConnectionProperties dashScopeConnectionProperties;

    @Resource
    private PictureService pictureService;

    /**
     * 提交图片标签任务
     * 只有图片所有者或拥有 ai:manage 权限的管理员才能触发
     */
    @Override
    public String submitTagTask(Long pictureId) {
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(user == null, ExceptionCode.NOT_LOGIN);
        Picture picture = pictureService.getById(pictureId);
        ExcUtils.throwIfTrue(picture == null, "图片不存在");
        ExcUtils.throwIfTrue(picture.getUserId() == null, "图片数据异常");
        // 非本人且非管理员，拒绝
        hk.ljx.fishpicsbackend.common.context.LoginContext ctx = UserHolder.getLoginContext();
        ExcUtils.throwIfTrue(!user.getId().equals(picture.getUserId()) && (ctx == null || !ctx.hasSystemPerm("system:ai:manage")), ExceptionCode.UNAUTHORIZED);

        return taskService.submitTask("ai_tag", String.valueOf(pictureId), null, user.getId());
    }

    @Override
    public Task getTagResult(String taskId) {
        return taskService.getTaskByTaskId(taskId);
    }

    /**
     * 提交 AI 生图任务
     * 先检查 DashScope API Key 是否配置，然后把参数序列化成 JSON 交给 TaskService 异步处理
     */
    @Override
    public String submitDrawTask(AiDrawPictureDTO drawPictureDTO, Long userId) {
        ExcUtils.throwIfTrue(drawPictureDTO == null || drawPictureDTO.getDescription() == null,
                "画面描述不能为空");
        String apikey = dashScopeConnectionProperties.getApiKey();
        ExcUtils.throwIfTrue(apikey == null || apikey.isBlank(), ExceptionCode.SERVICE_UNAVAILABLE, "AI服务未配置，无法提交任务");
        String paramJson = JSONUtil.toJsonStr(drawPictureDTO);
        return taskService.submitTask("ai_draw", null, paramJson, userId);
    }

    @Override
    public Task getDrawResult(String taskId) {
        return taskService.getTaskByTaskId(taskId);
    }

    @Override
    public String getDownloadImageUrl(String taskId) {
        Task task = taskService.getTaskByTaskId(taskId);
        ExcUtils.throwIfTrue(task == null, ExceptionCode.NOT_FOUND, "任务不存在");

        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(user == null, ExceptionCode.NOT_LOGIN);
        ExcUtils.throwIfTrue(!user.getId().equals(task.getUserId()), ExceptionCode.UNAUTHORIZED);
        ExcUtils.throwIfTrue(!"ai_draw".equals(task.getBizType()), ExceptionCode.PARAMETER_ERROR, "任务类型不支持下载");
        ExcUtils.throwIfTrue(!"DONE".equals(task.getStatus()), ExceptionCode.PARAMETER_ERROR, "任务尚未完成");
        ExcUtils.throwIfTrue(task.getResult() == null || task.getResult().isBlank(), ExceptionCode.NOT_FOUND, "图片结果不存在");
        return task.getResult();
    }
}
