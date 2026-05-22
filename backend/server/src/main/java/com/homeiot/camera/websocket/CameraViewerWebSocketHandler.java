package com.homeiot.camera.websocket;

import com.homeiot.camera.service.CameraService;
import com.homeiot.camera.service.CameraService.CameraFramePayload;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

@Component
public class CameraViewerWebSocketHandler extends BinaryWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(CameraViewerWebSocketHandler.class);
    private static final Pattern CAMERA_PATH = Pattern.compile("^/api/cameras/([^/]+)/ws$");
    private static final int CAMERA_MESSAGE_SIZE_LIMIT_BYTES = 256 * 1024;
    private static final Duration VIEWER_METRICS_LOG_INTERVAL = Duration.ofSeconds(10);

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
        session.setBinaryMessageSizeLimit(CAMERA_MESSAGE_SIZE_LIMIT_BYTES);

        ViewerSession viewerSession = new ViewerSession(deviceId, session);
        AutoCloseable viewerRegistration = cameraService.registerViewer(deviceId);
        AutoCloseable subscription = cameraService.addFrameListener(deviceId, viewerSession::offer);
        session.getAttributes().put("viewerRegistration", viewerRegistration);
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
        Object viewerRegistration = session.getAttributes().get("viewerRegistration");
        if (viewerRegistration instanceof AutoCloseable closeable) {
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
        private final String deviceId;
        private final WebSocketSession session;
        private final AtomicReference<CameraFramePayload> pendingFrame = new AtomicReference<>();
        private final AtomicBoolean sending = new AtomicBoolean(false);
        private final AtomicLong metricSentFrames = new AtomicLong();
        private final AtomicLong metricBytes = new AtomicLong();
        private final AtomicLong metricSendMicros = new AtomicLong();
        private final AtomicLong metricOverwrittenFrames = new AtomicLong();
        private final AtomicLong metricMaxSendMicros = new AtomicLong();
        private volatile long lastMetricsLogAt = System.currentTimeMillis();
        private volatile boolean closed;

        private ViewerSession(String deviceId, WebSocketSession session) {
            this.deviceId = deviceId;
            this.session = session;
        }

        private void offer(CameraFramePayload frame) {
            if (closed || !session.isOpen()) {
                return;
            }
            if (pendingFrame.getAndSet(frame) != null) {
                metricOverwrittenFrames.incrementAndGet();
            }
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
                            long sendStartedAt = System.nanoTime();
                            session.sendMessage(new BinaryMessage(frame.jpegBytes()));
                            long sendMicros = Math.max(0, (System.nanoTime() - sendStartedAt) / 1000);
                            recordSend(frame.jpegBytes().length, sendMicros);
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

        private void recordSend(int bytes, long sendMicros) {
            metricSentFrames.incrementAndGet();
            metricBytes.addAndGet(bytes);
            metricSendMicros.addAndGet(sendMicros);
            metricMaxSendMicros.updateAndGet(current -> Math.max(current, sendMicros));
            logMetricsIfDue();
        }

        private void logMetricsIfDue() {
            long now = System.currentTimeMillis();
            if (now - lastMetricsLogAt < VIEWER_METRICS_LOG_INTERVAL.toMillis()) {
                return;
            }

            synchronized (this) {
                now = System.currentTimeMillis();
                if (now - lastMetricsLogAt < VIEWER_METRICS_LOG_INTERVAL.toMillis()) {
                    return;
                }
                lastMetricsLogAt = now;
                long sentFrames = drain(metricSentFrames);
                long overwrittenFrames = drain(metricOverwrittenFrames);
                long bytes = drain(metricBytes);
                long sendMicros = drain(metricSendMicros);
                long maxSendMicros = drain(metricMaxSendMicros);
                if (sentFrames == 0 && overwrittenFrames == 0) {
                    return;
                }
                log.info("camera viewer websocket deviceId={} sentFrames={} overwrittenFrames={} bytes={} avgSendMs={} maxSendMs={} pending={}",
                        deviceId,
                        sentFrames,
                        overwrittenFrames,
                        bytes,
                        sentFrames == 0 ? "-" : String.format(Locale.ROOT, "%.2f", (sendMicros / 1000.0) / sentFrames),
                        String.format(Locale.ROOT, "%.2f", maxSendMicros / 1000.0),
                        pendingFrame.get() != null);
            }
        }

        private long drain(AtomicLong value) {
            return value.getAndSet(0);
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
