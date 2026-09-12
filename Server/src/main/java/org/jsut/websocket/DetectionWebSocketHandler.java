package org.jsut.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jsut.inference.OnnxInferenceService;
import org.jsut.inference.DetectionPostprocessor;
import org.jsut.task.camera.GlobalFrameCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// 收到 cameraId 后直接读缓存返回（零处理），推理异步进行不阻塞请求路径
@Slf4j
@Component
public class DetectionWebSocketHandler extends TextWebSocketHandler {

    @Autowired
    private OnnxInferenceService inferenceService;

    @Autowired
    private GlobalFrameCache frameCache;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    // 推理异步线程池：单线程串行推理，避免并发 GPU 冲突
    private final ExecutorService inferExecutor = Executors.newSingleThreadExecutor();
    // 记录每路相机是否正在推理，防止重复提交
    private final Set<String> inferringCameras = ConcurrentHashMap.newKeySet();

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
            boolean doInfer = request.inference() == null || request.inference();

            // 异步触发推理
            if (doInfer && !inferringCameras.contains(cameraId)) {
                inferringCameras.add(cameraId);
                inferExecutor.submit(() -> {
                    try {
                        OnnxInferenceService.DetectionResult result = inferenceService.detect(cameraId);
                        frameCache.putDetections(cameraId, result.detections(), result.inferTimeMs());
                    } catch (Exception e) {
                        log.error("[WS] 异步推理异常", e);
                    } finally {
                        inferringCameras.remove(cameraId);
                    }
                });
            }

            // 直接读缓存返回，请求路径上零处理
            byte[] jpeg = frameCache.getJpegBytes(cameraId);
            List<DetectionPostprocessor.Detection> detections = frameCache.getDetections(cameraId);
            long inferTimeMs = frameCache.getInferTimeMs(cameraId);

            Map<String, Object> response = new HashMap<>();
            response.put("cameraId", cameraId);
            response.put("timestamp", System.currentTimeMillis());
            response.put("detections", detections != null ? detections : List.of());
            response.put("inferTimeMs", inferTimeMs);
            response.put("frame", jpeg != null ? Base64.getEncoder().encodeToString(jpeg) : null);
            response.put("width", frameCache.getJpegWidth(cameraId));
            response.put("height", frameCache.getJpegHeight(cameraId));
            response.put("origWidth", frameCache.getOrigWidth(cameraId));
            response.put("origHeight", frameCache.getOrigHeight(cameraId));

            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        } catch (Exception e) {
            log.error("[WS] 处理消息异常", e);
            try {
                session.sendMessage(new TextMessage("{\"error\":\"" + e.getMessage() + "\"}"));
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        log.info("[WS] 连接关闭: {}, status: {}", session.getId(), status);
    }

    public record DetectionRequest(String cameraId, Boolean inference) {}
}
