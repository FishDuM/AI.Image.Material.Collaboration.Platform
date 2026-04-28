package hk.ljx.fishpicsbackend.controller;

import hk.ljx.fishpicsbackend.common.response.ResUtils;
import hk.ljx.fishpicsbackend.common.response.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/system")
public class SystemController {

    @GetMapping("/list")
    public Response<List<String>> list() {
        return ResUtils.success(List.of("推荐", "穿搭", "美食", "旅行", "宠物", "运动"));
    }
}
