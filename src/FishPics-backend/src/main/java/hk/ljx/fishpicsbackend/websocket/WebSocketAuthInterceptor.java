package hk.ljx.fishpicsbackend.websocket;

import cn.hutool.core.util.StrUtil;
import hk.ljx.fishpicsbackend.common.constants.RedisConstants;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return false;
        }

        String token = servletRequest.getServletRequest().getParameter("token");
        if (StrUtil.isBlank(token)) {
            return false;
        }

        // 基于 Redis 验证 token 并获取用户 ID
        String tokenKey = RedisConstants.getUserIdKey(token);
        String userIdStr = stringRedisTemplate.opsForValue().get(tokenKey);
        if (StrUtil.isBlank(userIdStr)) {
            return false;
        }

        attributes.put("userId", Long.parseLong(userIdStr));
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}
