package hk.ljx.fishpicsbackend.websocket;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import hk.ljx.fishpicsbackend.common.constants.RedisConstants;
import hk.ljx.fishpicsbackend.user.entity.User;
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

        String token = extractTokenFromProtocols(request);
        if (StrUtil.isBlank(token)) {
            token = servletRequest.getServletRequest().getParameter("token");
        }
        if (StrUtil.isBlank(token)) {
            return false;
        }

        String tokenKey = RedisConstants.getUserIdKey(token);
        String userIdStr = stringRedisTemplate.opsForValue().get(tokenKey);
        if (StrUtil.isBlank(userIdStr)) {
            return false;
        }

        Long userId = Long.parseLong(userIdStr);

        String userKey = RedisConstants.getUserInfoKey(userId);
        String userJson = stringRedisTemplate.opsForValue().get(userKey);
        if (StrUtil.isNotBlank(userJson)) {
            User user = JSONUtil.toBean(userJson, User.class);
            if (user != null && user.getStatus() != null && user.getStatus() == 0) {
                return false;
            }
        }

        attributes.put("userId", userId);

        String protocols = request.getHeaders().getFirst("Sec-WebSocket-Protocol");
        if (StrUtil.isNotBlank(protocols)) {
            response.getHeaders().set("Sec-WebSocket-Protocol", "access_token");
        }

        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }

    /**
     * 从 Sec-WebSocket-Protocol 头提取 token
     * 协议格式：access_token,<actual-token>
     * @return token，未找到返回null
     */
    private String extractTokenFromProtocols(ServerHttpRequest request) {
        String protocols = request.getHeaders().getFirst("Sec-WebSocket-Protocol");
        if (StrUtil.isBlank(protocols)) {
            return null;
        }
        String[] parts = protocols.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.equalsIgnoreCase("access_token") && !trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return null;
    }
}
