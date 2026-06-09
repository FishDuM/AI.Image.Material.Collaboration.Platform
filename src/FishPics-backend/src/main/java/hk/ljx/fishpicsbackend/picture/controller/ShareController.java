package hk.ljx.fishpicsbackend.picture.controller;

import hk.ljx.fishpicsbackend.common.annotation.AuditLog;
import hk.ljx.fishpicsbackend.common.context.LoginContext;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.response.ResUtils;
import hk.ljx.fishpicsbackend.common.response.Response;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import hk.ljx.fishpicsbackend.picture.dto.ShareCancelRequest;
import hk.ljx.fishpicsbackend.picture.dto.ShareCreateRequest;
import hk.ljx.fishpicsbackend.picture.service.ShareService;
import hk.ljx.fishpicsbackend.picture.vo.ShareFileVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/share")
public class ShareController {

    @Resource
    private ShareService shareService;

    @AuditLog(module = "图片分享", operation = "创建分享")
    @PostMapping("/create")
    public Response<String> createShare(@Valid @RequestBody ShareCreateRequest request) {
        LoginContext ctx = UserHolder.getLoginContext();
        ExcUtils.throwIfTrue(ctx == null || ctx.getUserId() == null, ExceptionCode.NOT_LOGIN);
        String shareToken = shareService.createShare(
                request.getPictureId(),
                ctx.getUserId(),
                request.getExpireDays(),
                request.getAllowDownload()
        );
        return ResUtils.success(shareToken);
    }

    @GetMapping("/info/{token}")
    public Response<Map<String, Object>> getShareInfo(@PathVariable String token) {
        return ResUtils.success(shareService.getShareInfo(token));
    }

    @GetMapping("/preview/{token}")
    public void preview(@PathVariable String token, HttpServletResponse response) throws Exception {
        try (ShareFileVO file = shareService.getPreviewFile(token)) {
            response.setContentType(resolveContentType(file.getContentType()));
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            if (file.getContentLength() != null && file.getContentLength() >= 0) {
                response.setContentLengthLong(file.getContentLength());
            }
            StreamUtils.copy(file.getInputStream(), response.getOutputStream());
        }
    }

    @GetMapping("/download/{token}")
    public void download(@PathVariable String token, HttpServletResponse response) throws Exception {
        try (ShareFileVO file = shareService.getDownloadFile(token)) {
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            if (file.getContentLength() != null && file.getContentLength() >= 0) {
                response.setContentLengthLong(file.getContentLength());
            }
            response.setHeader(
                    HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename*=UTF-8''" + URLEncoder.encode(defaultFileName(file.getPictureName()), StandardCharsets.UTF_8)
            );
            StreamUtils.copy(file.getInputStream(), response.getOutputStream());
        }
    }

    @AuditLog(module = "图片分享", operation = "取消分享")
    @PostMapping("/cancel")
    public Response<?> cancelShare(@Valid @RequestBody ShareCancelRequest request) {
        LoginContext ctx = UserHolder.getLoginContext();
        ExcUtils.throwIfTrue(ctx == null || ctx.getUserId() == null, ExceptionCode.NOT_LOGIN);
        shareService.cancelShare(request.getShareId(), ctx.getUserId());
        return ResUtils.successOfMessage("已取消分享");
    }

    private String resolveContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        // 安全限制：仅允许图片类型，防止 XSS（如 text/html 被浏览器渲染执行）
        String lower = contentType.toLowerCase().trim();
        if (lower.startsWith("image/")) {
            return contentType;
        }
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    private String defaultFileName(String pictureName) {
        if (pictureName == null || pictureName.isBlank()) {
            return "image";
        }
        return pictureName;
    }
}
