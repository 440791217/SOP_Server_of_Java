package org.jsut.config;

import org.jsut.websocket.DetectionWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

// 注册 WebSocket 端点 /ws/detect
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final DetectionWebSocketHandler detectionHandler;

    public WebSocketConfig(DetectionWebSocketHandler detectionHandler) {
        this.detectionHandler = detectionHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(detectionHandler, "/ws/detect").setAllowedOrigins("*");
    }
}
