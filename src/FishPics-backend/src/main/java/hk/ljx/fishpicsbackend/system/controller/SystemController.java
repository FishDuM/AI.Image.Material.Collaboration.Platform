package hk.ljx.fishpicsbackend.system.controller;
import hk.ljx.fishpicsbackend.system.service.PicSystemService;

import hk.ljx.fishpicsbackend.common.annotation.AuditLog;
import hk.ljx.fishpicsbackend.common.annotation.RequireAdmin;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.response.ResUtils;
import hk.ljx.fishpicsbackend.common.response.Response;
import hk.ljx.fishpicsbackend.system.dto.AddSysMarquee;
import hk.ljx.fishpicsbackend.system.dto.AddSysPicType;
import hk.ljx.fishpicsbackend.system.dto.DeleteMarqueeRequest;
import hk.ljx.fishpicsbackend.system.dto.DeleteTypeRequest;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/system")
@Slf4j
public class SystemController {

    @Resource
    private PicSystemService picSystemService;

    @GetMapping("/list")
    public Response<List<String>> list() {
        List<String> list = picSystemService.getTypeList();
        return ResUtils.success(list);
    }

    @AuditLog(module = "系统配置", operation = "添加图片标签")
    @RequireAdmin
    @PostMapping("/addList")
    public Response<Boolean> addList(@Valid @RequestBody AddSysPicType addSysPicType) {
        ExcUtils.throwIfTrue(addSysPicType == null, ExceptionCode.PARAMETER_ERROR, "type不能为空");
        picSystemService.addTypeList(addSysPicType);
        return ResUtils.success(true);
    }

    @AuditLog(module = "系统配置", operation = "删除图片标签")
    @RequireAdmin
    @PostMapping("/deleteType")
    public Response<Boolean> deleteType(@Valid @RequestBody DeleteTypeRequest request) {
        ExcUtils.throwIfTrue(request.getValue() == null || request.getValue().isEmpty(), ExceptionCode.PARAMETER_ERROR, "type不能为空");
        picSystemService.deleteType(request.getValue());
        return ResUtils.success(true);
    }

    @GetMapping("/marquee")
    public Response<List<String>> marquee() {
        List<String> urls = picSystemService.getMarquess();
        return ResUtils.success(urls);
    }

    @AuditLog(module = "系统配置", operation = "添加跑马灯")
    @RequireAdmin
    @PostMapping("/addMarquee")
    public Response<Boolean> addMarquee(@Valid @RequestBody AddSysMarquee addSysMarquee) {
        ExcUtils.throwIfTrue(addSysMarquee == null, ExceptionCode.PARAMETER_ERROR, "url不能为空");
        picSystemService.addMarquee(addSysMarquee);
        return ResUtils.success(true);
    }

    @AuditLog(module = "系统配置", operation = "删除跑马灯")
    @RequireAdmin
    @PostMapping("/deleteMarquee")
    public Response<Boolean> deleteMarquee(@Valid @RequestBody DeleteMarqueeRequest request) {
        ExcUtils.throwIfTrue(request.getUrl() == null || request.getUrl().isEmpty(), ExceptionCode.PARAMETER_ERROR, "url不能为空");
        picSystemService.deleteMarquee(request.getUrl());
        return ResUtils.success(true);
    }
}
