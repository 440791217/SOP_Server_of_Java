package org.jsut.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jsut.inference.OnnxInferenceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// 收到 cameraId 后执行检测，推回结果
@Slf4j
@Component
public class DetectionWebSocketHandler extends TextWebSocketHandler {

    @Autowired
    private OnnxInferenceService inferenceService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
        log.info("[WS] 连接建立: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            var request = objectMapper.readValue(message.getPayload(), DetectionRequest.class);
            String cameraId = request.cameraId();

            OnnxInferenceService.DetectionResult result = inferenceService.detect(cameraId);
            String json = objectMapper.writeValueAsString(result);

            session.sendMessage(new TextMessage(json));
            log.info("[WS] 推送检测结果: cameraId={}, detections={}", cameraId,
                    result.detections().size());
        } catch (Exception e) {
            log.error("[WS] 处理消息异常", e);
            try {
                session.sendMessage(new TextMessage("{\"error\":\"" + e.getMessage() + "\"}"));
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) {
        sessions.remove(session.getId());
        log.info("[WS] 连接关闭: {}, status: {}", session.getId(), status);
    }

    public record DetectionRequest(String cameraId) {}
}
