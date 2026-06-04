package hk.ljx.fishpicsbackend.ai.controller;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import hk.ljx.fishpicsbackend.ai.dto.AiConfigDTO;
import hk.ljx.fishpicsbackend.ai.dto.AiDrawPictureDTO;
import hk.ljx.fishpicsbackend.ai.dto.AiTaskQueryDTO;
import hk.ljx.fishpicsbackend.ai.service.AiService;
import hk.ljx.fishpicsbackend.ai.vo.AiStatsVO;
import hk.ljx.fishpicsbackend.ai.vo.AiTaskSubmitVO;
import hk.ljx.fishpicsbackend.ai.vo.AiTaskVO;
import hk.ljx.fishpicsbackend.common.dto.IdRequest;
import hk.ljx.fishpicsbackend.common.annotation.AuthCheck;
import hk.ljx.fishpicsbackend.common.constants.SysConstants;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.response.ResUtils;
import hk.ljx.fishpicsbackend.common.response.Response;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import hk.ljx.fishpicsbackend.mapper.PicSystemMapper;
import hk.ljx.fishpicsbackend.system.entity.PicSystem;
import hk.ljx.fishpicsbackend.task.entity.Task;
import hk.ljx.fishpicsbackend.mapper.TaskMapper;
import hk.ljx.fishpicsbackend.user.entity.User;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ai")
@Slf4j
public class AiController {

    @Resource
    private AiService aiService;

    @Resource
    private TaskMapper taskMapper;

    @Resource
    private PicSystemMapper picSystemMapper;

    @PostMapping("/tags")
    public Response<AiTaskSubmitVO> submitTagTask(@RequestBody IdRequest request) {
        ExcUtils.throwIfTrue(request.getId() == null, "图片ID不能为空");
        String taskId = aiService.submitTagTask(request.getId());
        AiTaskSubmitVO vo = new AiTaskSubmitVO();
        vo.setTaskId(taskId);
        vo.setStatus("PENDING");
        return ResUtils.success(vo);
    }

    @GetMapping("/tags/result/{taskId}")
    public Response<Task> getTagResult(@PathVariable String taskId) {
        Task task = aiService.getTagResult(taskId);
        // 校验任务归属：只能查看自己的任务
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(user == null, ExceptionCode.NOT_LOGIN);
        ExcUtils.throwIfTrue(task != null && !user.getId().equals(task.getUserId()), ExceptionCode.UNAUTHORIZED);
        return ResUtils.success(task);
    }

    @PostMapping("/draw")
    public Response<String> drawPicture(@RequestBody AiDrawPictureDTO drawPictureDTO) {
        ExcUtils.throwIfTrue(drawPictureDTO == null, "参数不能为空");
        return ResUtils.success(aiService.drawPicture(drawPictureDTO));
    }

    @PostMapping("/draw/submit")
    public Response<AiTaskSubmitVO> submitDrawTask(@RequestBody AiDrawPictureDTO drawPictureDTO) {
        ExcUtils.throwIfTrue(drawPictureDTO == null, "参数不能为空");
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(user == null, ExceptionCode.NOT_LOGIN);
        String taskId = aiService.submitDrawTask(drawPictureDTO, user.getId());
        AiTaskSubmitVO vo = new AiTaskSubmitVO();
        vo.setTaskId(taskId);
        vo.setStatus("PENDING");
        return ResUtils.success(vo);
    }

    @GetMapping("/draw/result/{taskId}")
    public Response<Task> getDrawResult(@PathVariable String taskId) {
        Task task = aiService.getDrawResult(taskId);
        // 校验任务归属：只能查看自己的任务
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(user == null, ExceptionCode.NOT_LOGIN);
        ExcUtils.throwIfTrue(task != null && !user.getId().equals(task.getUserId()), ExceptionCode.UNAUTHORIZED);
        return ResUtils.success(task);
    }

    // ==================== 管理后台接口 ====================

    @AuthCheck(permission = "ai:tasks")
    @PostMapping("/admin/tasks")
    public Response<IPage<AiTaskVO>> getTasks(@RequestBody AiTaskQueryDTO queryDTO) {
        Page<Task> page = new Page<>(queryDTO.getCurrent(), queryDTO.getPageSize());
        QueryWrapper<Task> wrapper = new QueryWrapper<>();

        // type 映射: 0→ai_tag, 2→ai_draw
        if (queryDTO.getType() != null) {
            String bizType = switch (queryDTO.getType()) {
                case 0 -> "ai_tag";
                case 2 -> "ai_draw";
                default -> null;
            };
            if (bizType != null) {
                wrapper.eq("biz_type", bizType);
            }
        }

        // status 映射: 0→PENDING/PROCESSING, 1→DONE, 2→FAILED
        if (queryDTO.getStatus() != null) {
            switch (queryDTO.getStatus()) {
                case 0 -> wrapper.in("status", "PENDING", "PROCESSING");
                case 1 -> wrapper.eq("status", "DONE");
                case 2 -> wrapper.eq("status", "FAILED");
            }
        }

        wrapper.orderByDesc("create_time");
        IPage<Task> taskPage = taskMapper.selectPage(page, wrapper);

        // Task → AiTaskVO 转换
        List<AiTaskVO> voList = new ArrayList<>();
        for (Task task : taskPage.getRecords()) {
            AiTaskVO vo = new AiTaskVO();
            vo.setId(task.getId());
            vo.setUserId(task.getUserId());
            vo.setSubType(task.getBizType());
            vo.setPictureId(task.getBizId());
            vo.setCreateTime(task.getCreateTime());
            vo.setErrorMsg(task.getErrorMsg());

            // bizType → type 数字
            vo.setType(switch (task.getBizType()) {
                case "ai_tag" -> 0;
                case "ai_draw" -> 2;
                default -> -1;
            });

            // status 字符串 → 数字
            vo.setStatus(switch (task.getStatus()) {
                case "DONE" -> 1;
                case "FAILED" -> 2;
                default -> 0;
            });

            voList.add(vo);
        }

        Page<AiTaskVO> result = new Page<>(taskPage.getCurrent(), taskPage.getSize(), taskPage.getTotal());
        result.setRecords(voList);
        return ResUtils.success(result);
    }

    @AuthCheck(permission = "ai:stats")
    @GetMapping("/admin/stats")
    public Response<AiStatsVO> getStats() {
        AiStatsVO stats = new AiStatsVO();

        stats.setTotalTasks(taskMapper.selectCount(null));
        stats.setSuccessTasks(taskMapper.selectCount(
                new QueryWrapper<Task>().eq("status", "DONE")));
        stats.setFailedTasks(taskMapper.selectCount(
                new QueryWrapper<Task>().eq("status", "FAILED")));
        stats.setProcessingTasks(taskMapper.selectCount(
                new QueryWrapper<Task>().in("status", "PENDING", "PROCESSING")));

        Map<String, Long> typeCounts = new HashMap<>();
        typeCounts.put("0", taskMapper.selectCount(
                new QueryWrapper<Task>().eq("biz_type", "ai_tag")));
        typeCounts.put("2", taskMapper.selectCount(
                new QueryWrapper<Task>().eq("biz_type", "ai_draw")));
        stats.setTypeCounts(typeCounts);

        return ResUtils.success(stats);
    }

    @AuthCheck(permission = "ai:config")
    @GetMapping("/admin/config")
    public Response<AiConfigDTO> getConfig() {
        QueryWrapper<PicSystem> wrapper = new QueryWrapper<PicSystem>()
                .eq("syskey", SysConstants.AI_CONFIG_KEY);
        PicSystem record = picSystemMapper.selectOne(wrapper);

        AiConfigDTO config;
        if (record == null || record.getSysvalue() == null) {
            // 默认全部开启
            config = new AiConfigDTO();
            config.setTaggingEnabled(true);
            config.setEditingEnabled(true);
            config.setGenerationEnabled(true);
            config.setRecommendationEnabled(true);
        } else {
            config = JSONUtil.toBean(record.getSysvalue(), AiConfigDTO.class);
        }
        return ResUtils.success(config);
    }

    @AuthCheck(permission = "ai:config")
    @PostMapping("/admin/config")
    public Response<Boolean> updateConfig(@RequestBody AiConfigDTO configDTO) {
        // 读取现有配置
        QueryWrapper<PicSystem> wrapper = new QueryWrapper<PicSystem>()
                .eq("syskey", SysConstants.AI_CONFIG_KEY);
        PicSystem record = picSystemMapper.selectOne(wrapper);

        AiConfigDTO config;
        if (record == null || record.getSysvalue() == null) {
            config = new AiConfigDTO();
            config.setTaggingEnabled(true);
            config.setEditingEnabled(true);
            config.setGenerationEnabled(true);
            config.setRecommendationEnabled(true);
            record = new PicSystem();
            record.setSyskey(SysConstants.AI_CONFIG_KEY);
        } else {
            config = JSONUtil.toBean(record.getSysvalue(), AiConfigDTO.class);
        }

        // 只更新传了的字段
        if (configDTO.getTaggingEnabled() != null) config.setTaggingEnabled(configDTO.getTaggingEnabled());
        if (configDTO.getEditingEnabled() != null) config.setEditingEnabled(configDTO.getEditingEnabled());
        if (configDTO.getGenerationEnabled() != null) config.setGenerationEnabled(configDTO.getGenerationEnabled());
        if (configDTO.getRecommendationEnabled() != null) config.setRecommendationEnabled(configDTO.getRecommendationEnabled());

        record.setSysvalue(JSONUtil.toJsonStr(config));
        picSystemMapper.insertOrUpdate(record);

        return ResUtils.success(true);
    }
}
