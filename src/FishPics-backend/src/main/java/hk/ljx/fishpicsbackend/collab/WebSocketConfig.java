package hk.ljx.fishpicsbackend.collab;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.Arrays;

@Slf4j
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Resource
    private CollabWebSocketHandler collabWebSocketHandler;

    @Value("${collab.websocket.allowed-origins:*}")
    private String allowedOriginsConfig;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String[] origins = Arrays.stream(allowedOriginsConfig.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);

        var registration = registry.addHandler(collabWebSocketHandler, "/ws/collab");
        if (origins.length == 0) {
            log.warn("[WebSocketConfig] collab.websocket.allowed-origins is empty, deny cross-origin WebSocket");
            registration.setAllowedOrigins();
            return;
        }

        registration.setAllowedOriginPatterns(origins);
    }
}
