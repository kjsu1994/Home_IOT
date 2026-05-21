package com.homeiot.camera.websocket;

import com.homeiot.camera.service.CameraService;
import com.homeiot.camera.service.CameraService.CameraFramePayload;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

@Component
public class CameraViewerWebSocketHandler extends BinaryWebSocketHandler {
    private static final Pattern CAMERA_PATH = Pattern.compile("^/api/cameras/([^/]+)/ws$");

    private final CameraService cameraService;
    private final ExecutorService senderExecutor = Executors.newCachedThreadPool();

    public CameraViewerWebSocketHandler(CameraService cameraService) {
        this.cameraService = cameraService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String deviceId = deviceId(session);
        String token = token(session);
        if (deviceId == null || !cameraService.isValidStreamToken(deviceId, token)) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("invalid stream token"));
            return;
        }

        ViewerSession viewerSession = new ViewerSession(session);
        AutoCloseable subscription = cameraService.addFrameListener(deviceId, viewerSession::offer);
        session.getAttributes().put("subscription", subscription);
        session.getAttributes().put("viewerSession", viewerSession);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Object viewerSession = session.getAttributes().get("viewerSession");
        if (viewerSession instanceof ViewerSession viewer) {
            viewer.close();
        }
        Object subscription = session.getAttributes().get("subscription");
        if (subscription instanceof AutoCloseable closeable) {
            closeable.close();
        }
    }

    @PreDestroy
    public void shutdown() {
        senderExecutor.shutdownNow();
    }

    private String deviceId(WebSocketSession session) {
        if (session.getUri() == null) {
            return null;
        }
        var matcher = CAMERA_PATH.matcher(session.getUri().getPath());
        return matcher.matches() ? matcher.group(1) : null;
    }

    private String token(WebSocketSession session) {
        if (session.getUri() == null) {
            return "";
        }
        return UriComponentsBuilder.fromUri(session.getUri()).build().getQueryParams().getFirst("token");
    }

    private class ViewerSession {
        private final WebSocketSession session;
        private final AtomicReference<CameraFramePayload> pendingFrame = new AtomicReference<>();
        private final AtomicBoolean sending = new AtomicBoolean(false);
        private volatile boolean closed;

        private ViewerSession(WebSocketSession session) {
            this.session = session;
        }

        private void offer(CameraFramePayload frame) {
            if (closed || !session.isOpen()) {
                return;
            }
            pendingFrame.set(frame);
            if (sending.compareAndSet(false, true)) {
                senderExecutor.execute(this::drainLatestFrames);
            }
        }

        private void drainLatestFrames() {
            try {
                while (!closed && session.isOpen()) {
                    CameraFramePayload frame = pendingFrame.getAndSet(null);
                    if (frame == null) {
                        return;
                    }
                    synchronized (session) {
                        if (session.isOpen()) {
                            session.sendMessage(new BinaryMessage(frame.jpegBytes()));
                        }
                    }
                }
            } catch (IOException exception) {
                close();
            } finally {
                sending.set(false);
                if (pendingFrame.get() != null && sending.compareAndSet(false, true)) {
                    senderExecutor.execute(this::drainLatestFrames);
                }
            }
        }

        private void close() {
            closed = true;
            pendingFrame.set(null);
            if (session.isOpen()) {
                try {
                    session.close(CloseStatus.SERVER_ERROR);
                } catch (IOException ignored) {
                    // Session is already closing.
                }
            }
        }
    }
}
