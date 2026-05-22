package com.homeiot.camera.websocket;

import com.homeiot.camera.service.CameraService;
import com.homeiot.camera.service.CameraService.CameraAcceptMetrics;
import com.homeiot.device.entity.Device;
import com.homeiot.device.repository.DeviceRepository;
import com.homeiot.device.service.DeviceTokenService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

@Component
public class CameraWebSocketHandler extends BinaryWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(CameraWebSocketHandler.class);
    private static final String DEVICE_API_KEY_HEADER = "X-Device-Api-Key";
    private static final Pattern DEVICE_PATH = Pattern.compile("^/api/devices/([^/]+)/camera/ws$");
    private static final int CAMERA_MESSAGE_SIZE_LIMIT_BYTES = 256 * 1024;
    private static final Duration INGEST_METRICS_LOG_INTERVAL = Duration.ofSeconds(10);

    private final CameraService cameraService;
    private final DeviceRepository deviceRepository;
    private final DeviceTokenService deviceTokenService;
    private final String bootstrapDeviceApiKey;
    private final ExecutorService ingestExecutor = Executors.newSingleThreadExecutor();
    private final AtomicLong metricFrames = new AtomicLong();
    private final AtomicLong metricBytes = new AtomicLong();
    private final AtomicLong metricCopyMicros = new AtomicLong();
    private final AtomicLong metricQueueMicros = new AtomicLong();
    private final AtomicLong metricAcceptMicros = new AtomicLong();
    private final AtomicLong metricUpdateMicros = new AtomicLong();
    private final AtomicLong metricNotifyMicros = new AtomicLong();
    private final AtomicLong metricRecordingMicros = new AtomicLong();
    private volatile long lastMetricsLogAt = System.currentTimeMillis();

    public CameraWebSocketHandler(CameraService cameraService,
                                  DeviceRepository deviceRepository,
                                  DeviceTokenService deviceTokenService,
                                  @Value("${app.device-api-key:}") String bootstrapDeviceApiKey) {
        this.cameraService = cameraService;
        this.deviceRepository = deviceRepository;
        this.deviceTokenService = deviceTokenService;
        this.bootstrapDeviceApiKey = bootstrapDeviceApiKey == null ? "" : bootstrapDeviceApiKey.trim();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String deviceId = deviceId(session);
        if (deviceId == null || !hasValidDeviceKey(deviceId, presentedKey(session))) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("invalid device api key"));
            return;
        }
        session.setBinaryMessageSizeLimit(CAMERA_MESSAGE_SIZE_LIMIT_BYTES);
        session.getAttributes().put("deviceId", deviceId);
        log.info("camera device websocket connected deviceId={} remote={}", deviceId, session.getRemoteAddress());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Object deviceId = session.getAttributes().get("deviceId");
        if (deviceId != null) {
            log.info("camera device websocket closed deviceId={} code={} reason={}",
                    deviceId,
                    status.getCode(),
                    status.getReason());
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        String deviceId = (String) session.getAttributes().get("deviceId");
        if (deviceId == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("missing device id"));
            return;
        }

        long receivedAt = System.nanoTime();
        ByteBuffer payload = message.getPayload();
        if (payload.remaining() < 5) {
            return;
        }

        int width = Short.toUnsignedInt(payload.getShort());
        int height = Short.toUnsignedInt(payload.getShort());
        byte[] jpegBytes = new byte[payload.remaining()];
        payload.get(jpegBytes);
        long copiedAt = System.nanoTime();
        try {
            ingestExecutor.execute(() -> acceptFrame(deviceId, jpegBytes, width, height, receivedAt, copiedAt));
        } catch (RejectedExecutionException exception) {
            log.warn("camera ingest rejected deviceId={} bytes={}", deviceId, jpegBytes.length);
        }
    }

    @PreDestroy
    public void shutdown() {
        ingestExecutor.shutdownNow();
    }

    private void acceptFrame(String deviceId,
                             byte[] jpegBytes,
                             Integer width,
                             Integer height,
                             long receivedAt,
                             long copiedAt) {
        long acceptStartedAt = System.nanoTime();
        CameraAcceptMetrics metrics = cameraService.acceptFrameWithMetrics(deviceId, jpegBytes, width, height);
        long finishedAt = System.nanoTime();
        recordMetrics(
                deviceId,
                jpegBytes.length,
                microsBetween(receivedAt, copiedAt),
                microsBetween(copiedAt, acceptStartedAt),
                microsBetween(acceptStartedAt, finishedAt),
                metrics
        );
    }

    private void recordMetrics(String deviceId,
                               long bytes,
                               long copyMicros,
                               long queueMicros,
                               long acceptMicros,
                               CameraAcceptMetrics metrics) {
        metricFrames.incrementAndGet();
        metricBytes.addAndGet(bytes);
        metricCopyMicros.addAndGet(copyMicros);
        metricQueueMicros.addAndGet(queueMicros);
        metricAcceptMicros.addAndGet(acceptMicros);
        metricUpdateMicros.addAndGet(metrics.updateMicros());
        metricNotifyMicros.addAndGet(metrics.notifyMicros());
        metricRecordingMicros.addAndGet(metrics.recordingMicros());

        long now = System.currentTimeMillis();
        if (now - lastMetricsLogAt < INGEST_METRICS_LOG_INTERVAL.toMillis()) {
            return;
        }

        synchronized (this) {
            now = System.currentTimeMillis();
            if (now - lastMetricsLogAt < INGEST_METRICS_LOG_INTERVAL.toMillis()) {
                return;
            }
            lastMetricsLogAt = now;
            long frames = drain(metricFrames);
            if (frames == 0) {
                return;
            }
            log.info("camera websocket ingest deviceId={} frames={} bytes={} avgCopyUs={} avgQueueUs={} avgAcceptUs={} avgUpdateUs={} avgNotifyUs={} avgRecordUs={} listeners={} recording={}",
                    deviceId,
                    frames,
                    drain(metricBytes),
                    drain(metricCopyMicros) / frames,
                    drain(metricQueueMicros) / frames,
                    drain(metricAcceptMicros) / frames,
                    drain(metricUpdateMicros) / frames,
                    drain(metricNotifyMicros) / frames,
                    drain(metricRecordingMicros) / frames,
                    metrics.listenerCount(),
                    metrics.recordingActive());
        }
    }

    private long drain(AtomicLong value) {
        return value.getAndSet(0);
    }

    private long microsBetween(long startedAt, long finishedAt) {
        return Math.max(0, (finishedAt - startedAt) / 1000);
    }

    private String deviceId(WebSocketSession session) {
        if (session.getUri() == null) {
            return null;
        }
        var matcher = DEVICE_PATH.matcher(session.getUri().getPath());
        return matcher.matches() ? matcher.group(1) : null;
    }

    private String presentedKey(WebSocketSession session) {
        String headerKey = session.getHandshakeHeaders().getFirst(DEVICE_API_KEY_HEADER);
        if (headerKey != null && !headerKey.isBlank()) {
            return headerKey;
        }
        if (session.getUri() == null) {
            return "";
        }
        return UriComponentsBuilder.fromUri(session.getUri()).build().getQueryParams().getFirst("api_key");
    }

    private boolean hasValidDeviceKey(String deviceId, String presentedKey) {
        if (bootstrapDeviceApiKey.isBlank()) {
            return true;
        }
        return deviceRepository.findByDeviceId(deviceId)
                .map(device -> hasValidDeviceToken(device, presentedKey))
                .orElse(false);
    }

    private boolean hasValidDeviceToken(Device device, String presentedKey) {
        if (deviceTokenService.matches(device, presentedKey)) {
            return true;
        }
        return device.getDeviceApiKeyHash() == null && bootstrapDeviceApiKey.equals(presentedKey);
    }
}
