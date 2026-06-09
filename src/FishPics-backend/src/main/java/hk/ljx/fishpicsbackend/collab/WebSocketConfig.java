package hk.ljx.fishpicsbackend.collab;

import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 端点配置
 * 注册 /ws/collab 端点，允许所有来源（开发阶段，生产环境应限制）
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Resource
    private CollabWebSocketHandler collabWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(collabWebSocketHandler, "/ws/collab")
                .setAllowedOrigins("*");
    }
}
