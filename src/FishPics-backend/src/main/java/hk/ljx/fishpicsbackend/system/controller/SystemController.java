package hk.ljx.fishpicsbackend.system.controller;
import hk.ljx.fishpicsbackend.system.service.PicSystemService;

import hk.ljx.fishpicsbackend.common.response.Response;
import hk.ljx.fishpicsbackend.common.annotation.AuditLog;
import hk.ljx.fishpicsbackend.common.annotation.RequireAdmin;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.system.dto.AddSysMarqueeRequest;
import hk.ljx.fishpicsbackend.system.dto.AddSysPicTypeRequest;
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
    public Response<List<String>> listPicTypes() {
        List<String> list = picSystemService.getTypeList();
        return Response.ok(list);
    }

    @AuditLog(module = "系统配置", operation = "添加图片标签")
    @RequireAdmin
    @PostMapping("/addList")
    public Response<Boolean> addList(@Valid @RequestBody AddSysPicTypeRequest addSysPicType) {
        picSystemService.addTypeList(addSysPicType);
        return Response.ok(true);
    }

    @AuditLog(module = "系统配置", operation = "删除图片标签")
    @RequireAdmin
    @PostMapping("/deleteType")
    public Response<Boolean> deleteType(@Valid @RequestBody DeleteTypeRequest request) {
        ExcUtils.throwIfTrue(request.getValue() == null || request.getValue().isEmpty(), ExceptionCode.PARAMETER_ERROR, "type不能为空");
        picSystemService.deleteType(request.getValue());
        return Response.ok(true);
    }

    @GetMapping("/marquee")
    public Response<List<String>> listMarquees() {
        List<String> urls = picSystemService.getMarquees();
        return Response.ok(urls);
    }

    @AuditLog(module = "系统配置", operation = "添加跑马灯")
    @RequireAdmin
    @PostMapping("/addMarquee")
    public Response<Boolean> addMarquee(@Valid @RequestBody AddSysMarqueeRequest addSysMarquee) {
        picSystemService.addMarquee(addSysMarquee);
        return Response.ok(true);
    }

    @AuditLog(module = "系统配置", operation = "删除跑马灯")
    @RequireAdmin
    @PostMapping("/deleteMarquee")
    public Response<Boolean> deleteMarquee(@Valid @RequestBody DeleteMarqueeRequest request) {
        ExcUtils.throwIfTrue(request.getUrl() == null || request.getUrl().isEmpty(), ExceptionCode.PARAMETER_ERROR, "url不能为空");
        picSystemService.deleteMarquee(request.getUrl());
        return Response.ok(true);
    }
}
