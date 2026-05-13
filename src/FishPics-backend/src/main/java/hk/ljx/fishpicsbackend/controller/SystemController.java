package hk.ljx.fishpicsbackend.controller;

import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.response.ResUtils;
import hk.ljx.fishpicsbackend.common.response.Response;
import hk.ljx.fishpicsbackend.dto.system.AddSysMarquee;
import hk.ljx.fishpicsbackend.dto.system.AddSysPicType;
import hk.ljx.fishpicsbackend.service.PicSystemService;
import hk.ljx.fishpicsbackend.service.PictureService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/system")
public class SystemController {

    @Resource
    private PicSystemService picSystemService;

    @GetMapping("/list")
    public Response<List<String>> list() {
        List<String> list = picSystemService.getTypeList();
//        return ResUtils.success(List.of("推荐", "穿搭", "美食", "旅行", "宠物", "运动"));
        return ResUtils.success(list);
    }

    @PostMapping("/addList")
    public Response<Boolean> addList(@RequestBody AddSysPicType addSysPicType) {
        ExcUtils.throwIfTrue(addSysPicType == null, ExceptionCode.PARAMETER_ERROR, "type不能为空");
        picSystemService.addTypeList(addSysPicType);
        return ResUtils.success(true);
    }

    @PostMapping("/deleteType")
    public Response<Boolean> deleteType(@RequestBody Map<String, String> body) {
        String type = body.get("value");
        ExcUtils.throwIfTrue(type == null || type.isEmpty(), ExceptionCode.PARAMETER_ERROR, "type不能为空");
        picSystemService.deleteType(type);
        return ResUtils.success(true);
    }

    @GetMapping("/marquee")
    public Response<List<String>> marquee() {
        List<String> urls = picSystemService.getMarquess();
        return ResUtils.success(urls);
    }

    @PostMapping("/addMarquee")
    public Response<Boolean> addMarquee(@RequestBody AddSysMarquee addSysMarquee) {
        ExcUtils.throwIfTrue(addSysMarquee == null, ExceptionCode.PARAMETER_ERROR, "url不能为空");
        picSystemService.addMarquee(addSysMarquee);
        return ResUtils.success(true);
    }

    @PostMapping("/deleteMarquee")
    public Response<Boolean> deleteMarquee(@RequestBody Map<String, String> body) {
        String url = body.get("url");
        ExcUtils.throwIfTrue(url == null || url.isEmpty(), ExceptionCode.PARAMETER_ERROR, "url不能为空");
        picSystemService.deleteMarquee(url);
        return ResUtils.success(true);
    }
}
