package hk.ljx.fishpicsbackend.common.interceptor;

import hk.ljx.fishpicsbackend.common.utils.JwtUtils;
import hk.ljx.fishpicsbackend.mapper.UserMapper;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private JwtUtils jwtUtils;

    @Resource
    private UserMapper userMapper;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Token 刷新拦截器（order=0，最先执行）
        // JWT 解析 + 黑名单检查 + 自动续签 + 简化版权限上下文加载
        // 分享页路径需要排除，否则已登录但 JWT 失效的用户无法访问分享页
        registry.addInterceptor(new TokenRefreshInterceptor(stringRedisTemplate, jwtUtils, userMapper))
                .excludePathPatterns("/share/info/*", "/share/preview/*", "/share/download/*", "/ws/**")
                .order(0);

        // 登录拦截器（order=1，在 Token 刷新之后）
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(
                        "/user/login",
                        "/user/register",
                        "/user/checkCode/register",
                        "/user/checkCode/login",
                        "/picture/list",
                        "/picture/recommend",
                        "/system/list",
                        "/system/marquee",
                        "/doc.html",
                        "/webjars/**",
                        "/v3/api-docs/**",
                        "/favicon.ico",
                        "/share/info/*",
                        "/share/preview/*",
                        "/share/download/*",
                        "/ws/**")
                .order(1);
    }
}
