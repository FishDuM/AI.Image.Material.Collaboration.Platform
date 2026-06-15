package hk.ljx.fishpicsbackend.collab;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * WebSocket 端点配置
 *
 * 从 application.yml 读取允许列表，防止跨站 WebSocket 劫持
 */
@Slf4j
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Resource
    private CollabWebSocketHandler collabWebSocketHandler;

    /** 允许的 WebSocket 来源列表,逗号分隔;开发环境允许 * */
    @Value("${collab.websocket.allowed-origins:*}")
    private String allowedOriginsConfig;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        List<String> origins = Arrays.stream(allowedOriginsConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        if (origins.contains("*")) {
            // dev 模式显式配 * 时才允许所有来源
            registry.addHandler(collabWebSocketHandler, "/ws/collab")
                    .setAllowedOriginPatterns("*");
        } else if (origins.isEmpty()) {
            // prod 模式未配允许列表时拒绝所有跨域(防 CSWSH)
            log.warn("[WebSocketConfig] collab.websocket.allowed-origins 为空,拒绝所有跨域 WebSocket 连接");
            registry.addHandler(collabWebSocketHandler, "/ws/collab")
                    .setAllowedOrigins();
        } else {
            registry.addHandler(collabWebSocketHandler, "/ws/collab")
                    .setAllowedOrigins(origins.toArray(new String[0]));
        }
    }
}
