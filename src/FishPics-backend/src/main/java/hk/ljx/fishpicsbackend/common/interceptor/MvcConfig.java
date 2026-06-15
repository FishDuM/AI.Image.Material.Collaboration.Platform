package hk.ljx.fishpicsbackend.common.interceptor;

import hk.ljx.fishpicsbackend.common.utils.JwtUtils;
import hk.ljx.fishpicsbackend.mapper.SpaceTeamMemberMapper;
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

    @Resource
    private SpaceTeamMemberMapper spaceTeamMemberMapper;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Token 刷新拦截器：JWT 解析 + 黑名单检查 + 自动续签 + 简化版权限上下文加载
        // 分享页和 ws 路径需要排除
        registry.addInterceptor(new TokenRefreshInterceptor(stringRedisTemplate, jwtUtils, userMapper, spaceTeamMemberMapper))
                .excludePathPatterns("/share/info/*", "/share/preview/*", "/share/download/*", "/ws/**")
                .order(0);
    }
}
