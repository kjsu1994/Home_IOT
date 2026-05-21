package com.homeiot.camera.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class CameraWebSocketConfig implements WebSocketConfigurer {
    private final CameraWebSocketHandler cameraWebSocketHandler;
    private final CameraViewerWebSocketHandler cameraViewerWebSocketHandler;

    public CameraWebSocketConfig(CameraWebSocketHandler cameraWebSocketHandler,
                                 CameraViewerWebSocketHandler cameraViewerWebSocketHandler) {
        this.cameraWebSocketHandler = cameraWebSocketHandler;
        this.cameraViewerWebSocketHandler = cameraViewerWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(cameraWebSocketHandler, "/api/devices/*/camera/ws")
                .setAllowedOrigins("*");
        registry.addHandler(cameraViewerWebSocketHandler, "/api/cameras/*/ws")
                .setAllowedOrigins("*");
    }
}
