package hk.ljx.fishpicsbackend.websocket;

import cn.hutool.json.JSONUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.MessageListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class WebSocketHandler extends TextWebSocketHandler {

    private final ConcurrentHashMap<Long, WebSocketSession> userSessions = new ConcurrentHashMap<>();

    @Resource
    private RedissonClient redissonClient;

    @PostConstruct
    public void init() {
        RTopic topic = redissonClient.getTopic("websocket:notify");
        topic.addListener(WebSocketNotifyMessage.class, (channel, msg) -> {
            WebSocketSession session = userSessions.get(msg.getUserId());
            if (session != null && session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(msg.getPayload()));
                } catch (IOException e) {
                    log.error("websocket send error, userId={}", msg.getUserId(), e);
                }
            }
        });
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            userSessions.put(userId, session);
            log.info("websocket connected: userId={}, sessionId={}", userId, session.getId());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            userSessions.remove(userId);
            log.info("websocket disconnected: userId={}, sessionId={}", userId, session.getId());
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 目前不做客户端消息处理，预留
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            userSessions.remove(userId);
            log.error("websocket transport error: userId={}", userId, exception);
        }
    }

    public void sendToUser(Long userId, String type, String taskId, String bizType, String result, String errorMsg) {
        Map<String, Object> payload = Map.of(
                "type", type,
                "taskId", taskId != null ? taskId : "",
                "bizType", bizType != null ? bizType : "",
                "result", result != null ? result : "",
                "errorMsg", errorMsg != null ? errorMsg : ""
        );
        String json = JSONUtil.toJsonStr(payload);

        WebSocketSession session = userSessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(json));
                return;
            } catch (IOException e) {
                log.error("websocket send error, userId={}", userId, e);
            }
        }

        // 本地没有 session，通过 Redis Pub/Sub 广播给其他实例
        RTopic topic = redissonClient.getTopic("websocket:notify");
        topic.publish(new WebSocketNotifyMessage(userId, json));
    }
}
