package hk.ljx.fishpicsbackend.controller;

import cn.hutool.core.util.ObjectUtil;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.response.ResUtils;
import hk.ljx.fishpicsbackend.common.response.Response;
import hk.ljx.fishpicsbackend.dto.post.UploadPostRequest;
import hk.ljx.fishpicsbackend.service.PostService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/post")
@Slf4j
public class PostController {

    @Resource
    private PostService postService;

    @PostMapping("/post")
    public Response<Boolean> uploadPost(@RequestBody UploadPostRequest uploadPostRequest, HttpServletRequest request) {
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(uploadPostRequest), "发送失败，内容不能为空");
        postService.uploadPost(uploadPostRequest, request);
        return ResUtils.success(true);
    }
}
