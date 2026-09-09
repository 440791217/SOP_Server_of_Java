package org.jsut.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.opencv_core.Mat;
import org.jsut.inference.OnnxInferenceService;
import org.jsut.task.camera.GlobalFrameCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// 收到 cameraId 后执行检测，将画面帧 JPEG 压缩 + 检测结果推送回前端
@Slf4j
@Component
public class DetectionWebSocketHandler extends TextWebSocketHandler {

    @Autowired
    private OnnxInferenceService inferenceService;

    @Autowired
    private GlobalFrameCache frameCache;

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

            Mat frame = frameCache.getLatestFrame(cameraId);
            String frameBase64 = null;
            int frameWidth = 0;
            int frameHeight = 0;
            if (frame != null && !frame.empty()) {
                frameWidth = frame.cols();
                frameHeight = frame.rows();
                frameBase64 = encodeFrameToJpegBase64(frame);
                frame.release();
            }

            Map<String, Object> response = new HashMap<>();
            response.put("cameraId", result.cameraId());
            response.put("timestamp", result.timestamp());
            response.put("detections", result.detections());
            response.put("inferTimeMs", result.inferTimeMs());
            response.put("frame", frameBase64);
            response.put("width", frameWidth);
            response.put("height", frameHeight);

            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        } catch (Exception e) {
            log.error("[WS] 处理消息异常", e);
            try {
                session.sendMessage(new TextMessage("{\"error\":\"" + e.getMessage() + "\"}"));
            } catch (IOException ignored) {
            }
        }
    }

    // 将 Mat 编码为 JPEG 压缩格式（quality=75），再转 Base64 用于文本传输
    private String encodeFrameToJpegBase64(Mat mat) {
        BytePointer buf = new BytePointer();
        IntPointer params = new IntPointer(opencv_imgcodecs.IMWRITE_JPEG_QUALITY, 75);
        boolean ok = opencv_imgcodecs.imencode(".jpg", mat, buf, params);
        params.close();
        if (!ok) return null;
        int size = (int) buf.limit();
        byte[] bytes = new byte[size];
        buf.position(0);
        buf.get(bytes);
        buf.close();
        return Base64.getEncoder().encodeToString(bytes);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) {
        sessions.remove(session.getId());
        log.info("[WS] 连接关闭: {}, status: {}", session.getId(), status);
    }

    public record DetectionRequest(String cameraId) {}
}
