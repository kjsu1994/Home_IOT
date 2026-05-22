package com.homeiot.camera.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.homeiot.auth.service.OwnershipService;
import com.homeiot.camera.dto.CameraControlResponse;
import com.homeiot.camera.dto.CameraDeviceResponse;
import com.homeiot.camera.dto.CameraRecordingResponse;
import com.homeiot.camera.dto.CameraStatusRequest;
import com.homeiot.camera.dto.CameraStreamTokenResponse;
import com.homeiot.common.error.NotFoundException;
import com.homeiot.common.error.UnauthorizedException;
import com.homeiot.device.entity.Device;
import com.homeiot.device.entity.DeviceShare;
import com.homeiot.device.repository.DeviceRepository;
import com.homeiot.device.repository.DeviceShareRepository;
import com.homeiot.member.entity.Member;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@Service
public class CameraService {
    private static final Logger log = LoggerFactory.getLogger(CameraService.class);
    private static final Duration STREAM_TOKEN_TTL = Duration.ofMinutes(10);
    private static final Duration FRAME_WAIT_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration INGEST_FPS_WINDOW = Duration.ofSeconds(10);
    private static final Duration INGEST_LOG_INTERVAL = Duration.ofSeconds(10);
    private static final Duration STATUS_LOG_INTERVAL = Duration.ofSeconds(10);
    private static final String MJPEG_BOUNDARY = "homeiotframe";
    private static final long LIVE_UPLOAD_INTERVAL_MS = 60;
    private static final long IDLE_UPLOAD_INTERVAL_MS = 3000;
    private static final int LIVE_JPEG_QUALITY = 15;
    private static final int IDLE_JPEG_QUALITY = 35;
    private static final String LIVE_FRAME_SIZE = "VGA";
    private static final String IDLE_FRAME_SIZE = "QVGA";

    private final DeviceRepository deviceRepository;
    private final DeviceShareRepository deviceShareRepository;
    private final OwnershipService ownershipService;
    private final ObjectMapper objectMapper;
    private final Path recordingRoot;
    private final Map<String, CameraRuntime> runtimes = new ConcurrentHashMap<>();
    private final Map<String, StreamToken> streamTokens = new ConcurrentHashMap<>();
    private final Map<String, CopyOnWriteArraySet<Consumer<CameraFramePayload>>> frameListeners = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> viewerCounts = new ConcurrentHashMap<>();

    public CameraService(DeviceRepository deviceRepository,
                         DeviceShareRepository deviceShareRepository,
                         OwnershipService ownershipService,
                         ObjectMapper objectMapper,
                         @Value("${app.camera.recording-root:camera-recordings}") String recordingRoot) {
        this.deviceRepository = deviceRepository;
        this.deviceShareRepository = deviceShareRepository;
        this.ownershipService = ownershipService;
        this.objectMapper = objectMapper;
        this.recordingRoot = Path.of(recordingRoot);
    }

    @Transactional(readOnly = true)
    public List<CameraDeviceResponse> getCameras() {
        List<Device> devices = visibleDevices();
        return devices.stream()
                .filter(this::isCameraDevice)
                .map(this::toCameraResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CameraStreamTokenResponse issueStreamToken(String deviceId) {
        Device device = findDevice(deviceId);
        ownershipService.requireDeviceAccess(device);
        if (!isCameraDevice(device)) {
            throw new NotFoundException("camera device not found: " + deviceId);
        }

        String token = UUID.randomUUID().toString();
        OffsetDateTime expiresAt = OffsetDateTime.now().plus(STREAM_TOKEN_TTL);
        streamTokens.put(token, new StreamToken(deviceId, expiresAt));
        return new CameraStreamTokenResponse(
                deviceId,
                token,
                expiresAt,
                "/api/cameras/" + deviceId + "/mjpeg?token=" + token
        );
    }

    public StreamingResponseBody streamMjpeg(String deviceId, String token) {
        validateStreamToken(deviceId, token);
        return outputStream -> {
            try (AutoCloseable ignored = registerViewer(deviceId)) {
                writeMjpegStream(deviceId, token, outputStream);
            } catch (IOException ignored) {
                // Browser navigations and stream fallback switches close the socket normally.
            } catch (Exception exception) {
                throw new IOException("failed to close camera viewer", exception);
            }
        };
    }

    public boolean isValidStreamToken(String deviceId, String token) {
        return isTokenValid(deviceId, token);
    }

    public AutoCloseable addFrameListener(String deviceId, Consumer<CameraFramePayload> listener) {
        CopyOnWriteArraySet<Consumer<CameraFramePayload>> listeners =
                frameListeners.computeIfAbsent(deviceId, ignored -> new CopyOnWriteArraySet<>());
        listeners.add(listener);

        CameraFrame latestFrame;
        CameraRuntime runtime = runtime(deviceId);
        synchronized (runtime) {
            latestFrame = runtime.latestFrame;
        }
        if (latestFrame != null) {
            listener.accept(toPayload(latestFrame));
        }

        return () -> {
            listeners.remove(listener);
            if (listeners.isEmpty()) {
                frameListeners.remove(deviceId, listeners);
            }
        };
    }

    public AutoCloseable registerViewer(String deviceId) {
        AtomicInteger counter = viewerCounts.computeIfAbsent(deviceId, ignored -> new AtomicInteger());
        int currentCount = counter.incrementAndGet();
        log.info("camera viewer registered deviceId={} viewers={}", deviceId, currentCount);
        return () -> {
            int nextCount = counter.updateAndGet(value -> Math.max(0, value - 1));
            log.info("camera viewer closed deviceId={} viewers={}", deviceId, nextCount);
            if (nextCount == 0) {
                viewerCounts.remove(deviceId, counter);
            }
        };
    }

    public CameraControlResponse control(String deviceId) {
        CameraRuntime runtime = runtime(deviceId);
        int viewerCount = viewerCount(deviceId);
        boolean recording;
        synchronized (runtime) {
            recording = runtime.activeRecording != null;
        }
        boolean live = viewerCount > 0 || recording;
        log.debug("camera control deviceId={} mode={} intervalMs={} viewers={} recording={}",
                deviceId,
                live ? "LIVE" : "IDLE",
                live ? LIVE_UPLOAD_INTERVAL_MS : IDLE_UPLOAD_INTERVAL_MS,
                viewerCount,
                recording);
        return new CameraControlResponse(
                live ? "LIVE" : "IDLE",
                live ? LIVE_UPLOAD_INTERVAL_MS : IDLE_UPLOAD_INTERVAL_MS,
                live ? LIVE_JPEG_QUALITY : IDLE_JPEG_QUALITY,
                live ? LIVE_FRAME_SIZE : IDLE_FRAME_SIZE,
                viewerCount,
                recording
        );
    }

    public void acceptFrame(String deviceId, byte[] jpegBytes, Integer width, Integer height) {
        acceptFrameWithMetrics(deviceId, jpegBytes, width, height);
    }

    public CameraAcceptMetrics acceptFrameWithMetrics(String deviceId, byte[] jpegBytes, Integer width, Integer height) {
        if (jpegBytes == null || jpegBytes.length == 0) {
            throw new IllegalArgumentException("empty camera frame");
        }

        long updateStartedAt = System.nanoTime();
        CameraRuntime runtime = runtime(deviceId);
        CameraFrame frame = runtime.updateFrame(new CameraFrame(jpegBytes, width, height, OffsetDateTime.now()));
        long updateFinishedAt = System.nanoTime();
        logIngestStatsIfDue(deviceId, runtime);
        int listenerCount = notifyFrameListeners(deviceId, frame);
        long notifyFinishedAt = System.nanoTime();
        boolean recordingActive = writeRecordingFrameIfNeeded(deviceId, runtime, frame);
        long recordingFinishedAt = System.nanoTime();
        return new CameraAcceptMetrics(
                microsBetween(updateStartedAt, updateFinishedAt),
                microsBetween(updateFinishedAt, notifyFinishedAt),
                microsBetween(notifyFinishedAt, recordingFinishedAt),
                listenerCount,
                recordingActive
        );
    }

    public void updateStatus(String deviceId, CameraStatusRequest request) {
        CameraRuntime runtime = runtime(deviceId);
        boolean shouldLogStatus;
        long now = System.currentTimeMillis();
        synchronized (runtime) {
            runtime.cameraReady = request.cameraReady() == null || request.cameraReady();
            runtime.width = request.width();
            runtime.height = request.height();
            runtime.statusMessage = request.message();
            runtime.captureMs = request.captureMs();
            runtime.uploadMs = request.uploadMs();
            runtime.totalMs = request.totalMs();
            runtime.firmwareFrameBytes = request.firmwareFrameBytes();
            runtime.appliedIntervalMs = request.appliedIntervalMs();
            runtime.captureFailures = request.captureFailures();
            runtime.uploadFailures = request.uploadFailures();
            runtime.cameraStreaming = request.cameraStreaming();
            runtime.cameraControlReceived = request.cameraControlReceived();
            runtime.sensorName = request.sensorName();
            runtime.sensorPid = request.sensorPid();
            shouldLogStatus = now - runtime.lastStatusLogAt >= STATUS_LOG_INTERVAL.toMillis();
            if (shouldLogStatus) {
                runtime.lastStatusLogAt = now;
            }
        }
        if (shouldLogStatus) {
            log.info("camera status deviceId={} ready={} sensor={}/{} size={}x{} captureMs={} uploadMs={} totalMs={} intervalMs={} frameBytes={} failures={}/{} streaming={} controlReceived={}",
                    deviceId,
                    request.cameraReady(),
                    request.sensorName(),
                    request.sensorPid() == null ? "-" : String.format(Locale.ROOT, "0x%04X", request.sensorPid()),
                    request.width(),
                    request.height(),
                    request.captureMs(),
                    request.uploadMs(),
                    request.totalMs(),
                    request.appliedIntervalMs(),
                    request.firmwareFrameBytes(),
                    request.captureFailures(),
                    request.uploadFailures(),
                    request.cameraStreaming(),
                    request.cameraControlReceived());
        }
    }

    @Transactional(readOnly = true)
    public CameraRecordingResponse startRecording(String deviceId) {
        Device device = findDevice(deviceId);
        ownershipService.requireDeviceControl(device);
        if (!isCameraDevice(device)) {
            throw new NotFoundException("camera device not found: " + deviceId);
        }

        CameraRuntime runtime = runtime(deviceId);
        synchronized (runtime) {
            if (runtime.activeRecording != null) {
                return runtime.activeRecording.toResponse();
            }

            CameraRecording recording = new CameraRecording(UUID.randomUUID().toString(), deviceId, OffsetDateTime.now());
            createRecordingDirectory(recording);
            runtime.activeRecording = recording;
            return recording.toResponse();
        }
    }

    @Transactional(readOnly = true)
    public CameraRecordingResponse stopRecording(String deviceId) {
        Device device = findDevice(deviceId);
        ownershipService.requireDeviceControl(device);
        CameraRuntime runtime = runtime(deviceId);
        synchronized (runtime) {
            if (runtime.activeRecording == null) {
                throw new NotFoundException("active recording not found: " + deviceId);
            }

            CameraRecording recording = runtime.activeRecording;
            recording.stop();
            writeRecordingManifest(recording);
            runtime.completedRecordings.add(recording);
            runtime.activeRecording = null;
            return recording.toResponse();
        }
    }

    @Transactional(readOnly = true)
    public List<CameraRecordingResponse> getRecordings(String deviceId) {
        Device device = findDevice(deviceId);
        ownershipService.requireDeviceAccess(device);

        List<CameraRecordingResponse> responses = new ArrayList<>();
        CameraRuntime runtime = runtime(deviceId);
        synchronized (runtime) {
            if (runtime.activeRecording != null) {
                responses.add(runtime.activeRecording.toResponse());
            }
            runtime.completedRecordings.stream()
                    .map(CameraRecording::toResponse)
                    .forEach(responses::add);
        }
        responses.addAll(readStoredRecordings(deviceId));
        return responses.stream()
                .distinct()
                .sorted(Comparator.comparing(CameraRecordingResponse::startedAt).reversed())
                .toList();
    }

    public String mjpegContentType() {
        return "multipart/x-mixed-replace; boundary=" + MJPEG_BOUNDARY;
    }

    private void writeMjpegStream(String deviceId, String token, OutputStream outputStream) throws IOException {
        long lastSequence = -1;
        while (isTokenValid(deviceId, token)) {
            CameraFrame frame = waitForNextFrame(deviceId, lastSequence);
            if (frame == null) {
                continue;
            }
            lastSequence = frame.sequence;
            outputStream.write(("--" + MJPEG_BOUNDARY + "\r\n").getBytes());
            outputStream.write("Content-Type: image/jpeg\r\n".getBytes());
            outputStream.write(("Content-Length: " + frame.jpegBytes.length + "\r\n\r\n").getBytes());
            outputStream.write(frame.jpegBytes);
            outputStream.write("\r\n".getBytes());
            outputStream.flush();
        }
    }

    private int notifyFrameListeners(String deviceId, CameraFrame frame) {
        CopyOnWriteArraySet<Consumer<CameraFramePayload>> listeners = frameListeners.get(deviceId);
        if (listeners == null || listeners.isEmpty()) {
            return 0;
        }

        CameraFramePayload payload = toPayload(frame);
        for (Consumer<CameraFramePayload> listener : listeners) {
            listener.accept(payload);
        }
        return listeners.size();
    }

    private CameraFramePayload toPayload(CameraFrame frame) {
        return new CameraFramePayload(frame.jpegBytes, frame.width, frame.height, frame.capturedAt, frame.sequence);
    }

    private void logIngestStatsIfDue(String deviceId, CameraRuntime runtime) {
        CameraStats stats;
        synchronized (runtime) {
            long now = System.currentTimeMillis();
            if (now - runtime.lastStatsLogAt < INGEST_LOG_INTERVAL.toMillis()) {
                return;
            }
            runtime.lastStatsLogAt = now;
            stats = runtime.stats(now);
        }
        log.info("camera ingest deviceId={} fps={} frameBytes={} ageMs={} sequence={}",
                deviceId,
                String.format(Locale.ROOT, "%.2f", stats.ingestFps()),
                stats.frameBytes(),
                stats.lastFrameAgeMs(),
                stats.sequence());
    }

    private CameraFrame waitForNextFrame(String deviceId, long lastSequence) {
        CameraRuntime runtime = runtime(deviceId);
        long deadline = System.currentTimeMillis() + FRAME_WAIT_TIMEOUT.toMillis();
        synchronized (runtime) {
            while ((runtime.latestFrame == null || runtime.latestFrame.sequence == lastSequence)
                    && System.currentTimeMillis() < deadline) {
                try {
                    runtime.wait(1000);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
            return runtime.latestFrame;
        }
    }

    private boolean writeRecordingFrameIfNeeded(String deviceId, CameraRuntime runtime, CameraFrame frame) {
        CameraRecording recording;
        synchronized (runtime) {
            recording = runtime.activeRecording;
        }
        if (recording == null) {
            return false;
        }

        synchronized (recording) {
            try {
                Path file = recordingDirectory(recording).resolve(String.format(Locale.ROOT, "%08d.jpg", recording.frameCount + 1));
                Files.write(file, frame.jpegBytes);
                recording.frameCount++;
                recording.totalBytes += frame.jpegBytes.length;
                return true;
            } catch (IOException exception) {
                runtime.activeRecording = null;
                throw new IllegalStateException("failed to write recording frame for " + deviceId, exception);
            }
        }
    }

    private long microsBetween(long startedAt, long finishedAt) {
        return Math.max(0, (finishedAt - startedAt) / 1000);
    }

    private void createRecordingDirectory(CameraRecording recording) {
        try {
            Files.createDirectories(recordingDirectory(recording));
        } catch (IOException exception) {
            throw new IllegalStateException("failed to create camera recording directory", exception);
        }
    }

    private void writeRecordingManifest(CameraRecording recording) {
        try {
            Files.writeString(recordingDirectory(recording).resolve("manifest.json"), objectMapper.writeValueAsString(recording.toResponse()));
        } catch (IOException exception) {
            throw new IllegalStateException("failed to write camera recording manifest", exception);
        }
    }

    private List<CameraRecordingResponse> readStoredRecordings(String deviceId) {
        Path deviceRoot = recordingRoot.resolve(deviceId);
        if (!Files.isDirectory(deviceRoot)) {
            return List.of();
        }

        try (var paths = Files.list(deviceRoot)) {
            return paths
                    .map(path -> path.resolve("manifest.json"))
                    .filter(Files::isRegularFile)
                    .map(this::readManifest)
                    .flatMap(Optional::stream)
                    .toList();
        } catch (IOException exception) {
            return List.of();
        }
    }

    private Optional<CameraRecordingResponse> readManifest(Path manifest) {
        try {
            return Optional.of(objectMapper.readValue(manifest.toFile(), CameraRecordingResponse.class));
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private Path recordingDirectory(CameraRecording recording) {
        return recordingRoot.resolve(recording.deviceId).resolve(recording.recordingId);
    }

    private CameraDeviceResponse toCameraResponse(Device device) {
        CameraRuntime runtime = runtime(device.getDeviceId());
        CameraFrame frame;
        CameraStats stats;
        boolean cameraReady;
        Integer width;
        Integer height;
        Long captureMs;
        Long uploadMs;
        Long totalMs;
        Long firmwareFrameBytes;
        Long appliedIntervalMs;
        Long captureFailures;
        Long uploadFailures;
        Boolean cameraStreaming;
        Boolean cameraControlReceived;
        String sensorName;
        Integer sensorPid;
        synchronized (runtime) {
            frame = runtime.latestFrame;
            stats = runtime.stats(System.currentTimeMillis());
            cameraReady = runtime.cameraReady;
            width = runtime.width;
            height = runtime.height;
            captureMs = runtime.captureMs;
            uploadMs = runtime.uploadMs;
            totalMs = runtime.totalMs;
            firmwareFrameBytes = runtime.firmwareFrameBytes;
            appliedIntervalMs = runtime.appliedIntervalMs;
            captureFailures = runtime.captureFailures;
            uploadFailures = runtime.uploadFailures;
            cameraStreaming = runtime.cameraStreaming;
            cameraControlReceived = runtime.cameraControlReceived;
            sensorName = runtime.sensorName;
            sensorPid = runtime.sensorPid;
        }
        return new CameraDeviceResponse(
                device.getDeviceId(),
                device.getEffectiveName(),
                device.getRoom(),
                device.getDeviceType(),
                device.getIp(),
                isOnline(device),
                cameraReady,
                frame == null ? null : frame.capturedAt,
                frame == null ? width : frame.width,
                frame == null ? height : frame.height,
                frame == null ? 0 : frame.jpegBytes.length,
                stats.ingestFps(),
                stats.lastFrameAgeMs(),
                captureMs,
                uploadMs,
                totalMs,
                firmwareFrameBytes,
                appliedIntervalMs,
                captureFailures,
                uploadFailures,
                cameraStreaming,
                cameraControlReceived,
                sensorName,
                sensorPid
        );
    }

    private List<Device> visibleDevices() {
        if (ownershipService.isMaster()) {
            return deviceRepository.findAllByOrderByCreatedAtAsc();
        }

        Member member = ownershipService.currentMember();
        List<Device> devices = new ArrayList<>(deviceRepository.findAllByOwner_IdOrderByCreatedAtAsc(member.getId()));
        deviceShareRepository.findAllByMember_IdOrderByCreatedAtAsc(member.getId()).stream()
                .map(DeviceShare::getDevice)
                .filter(device -> devices.stream().noneMatch(existing -> existing.getId().equals(device.getId())))
                .forEach(devices::add);
        return devices;
    }

    private Device findDevice(String deviceId) {
        return deviceRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new NotFoundException("device not found: " + deviceId));
    }

    private boolean isCameraDevice(Device device) {
        String type = device.getDeviceType() == null ? "" : device.getDeviceType().toUpperCase(Locale.ROOT);
        return type.contains("CAMERA") || type.contains("CAM");
    }

    private CameraRuntime runtime(String deviceId) {
        return runtimes.computeIfAbsent(deviceId, ignored -> new CameraRuntime());
    }

    private int viewerCount(String deviceId) {
        AtomicInteger counter = viewerCounts.get(deviceId);
        return counter == null ? 0 : Math.max(0, counter.get());
    }

    private void validateStreamToken(String deviceId, String token) {
        if (!isTokenValid(deviceId, token)) {
            throw new UnauthorizedException("invalid stream token");
        }
    }

    private boolean isTokenValid(String deviceId, String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        StreamToken streamToken = streamTokens.get(token);
        if (streamToken == null || !streamToken.deviceId.equals(deviceId)) {
            return false;
        }
        if (streamToken.expiresAt.isBefore(OffsetDateTime.now())) {
            streamTokens.remove(token);
            return false;
        }
        return true;
    }

    private boolean isOnline(Device device) {
        return device.isOnline()
                && device.getLastSeenAt() != null
                && device.getLastSeenAt().isAfter(OffsetDateTime.now().minusSeconds(90));
    }

    private static class CameraRuntime {
        private CameraFrame latestFrame;
        private boolean cameraReady;
        private Integer width;
        private Integer height;
        private String statusMessage;
        private Long captureMs;
        private Long uploadMs;
        private Long totalMs;
        private Long firmwareFrameBytes;
        private Long appliedIntervalMs;
        private Long captureFailures;
        private Long uploadFailures;
        private Boolean cameraStreaming;
        private Boolean cameraControlReceived;
        private String sensorName;
        private Integer sensorPid;
        private CameraRecording activeRecording;
        private final List<CameraRecording> completedRecordings = new ArrayList<>();
        private final Deque<Long> frameReceivedAt = new ArrayDeque<>();
        private long lastStatsLogAt;
        private long lastStatusLogAt;

        private synchronized CameraFrame updateFrame(CameraFrame frame) {
            long now = System.currentTimeMillis();
            long nextSequence = latestFrame == null ? 1 : latestFrame.sequence + 1;
            latestFrame = frame.withSequence(nextSequence);
            frameReceivedAt.addLast(now);
            trimFrameWindow(now);
            cameraReady = true;
            if (frame.width != null) {
                width = frame.width;
            }
            if (frame.height != null) {
                height = frame.height;
            }
            notifyAll();
            return latestFrame;
        }

        private CameraStats stats(long now) {
            trimFrameWindow(now);
            if (latestFrame == null) {
                return new CameraStats(0, 0, 0, 0);
            }
            long ageMs = Math.max(0, Duration.between(latestFrame.capturedAt, OffsetDateTime.now()).toMillis());
            return new CameraStats(ingestFps(), latestFrame.jpegBytes.length, ageMs, latestFrame.sequence);
        }

        private double ingestFps() {
            if (frameReceivedAt.size() < 2) {
                return 0;
            }
            long first = frameReceivedAt.peekFirst();
            long last = frameReceivedAt.peekLast();
            long elapsedMs = Math.max(1, last - first);
            return Math.round(((frameReceivedAt.size() - 1) * 1000.0 / elapsedMs) * 10.0) / 10.0;
        }

        private void trimFrameWindow(long now) {
            long cutoff = now - INGEST_FPS_WINDOW.toMillis();
            while (!frameReceivedAt.isEmpty() && frameReceivedAt.peekFirst() < cutoff) {
                frameReceivedAt.removeFirst();
            }
        }
    }

    public record CameraFramePayload(byte[] jpegBytes, Integer width, Integer height, OffsetDateTime capturedAt, long sequence) {
    }

    public record CameraAcceptMetrics(long updateMicros,
                                      long notifyMicros,
                                      long recordingMicros,
                                      int listenerCount,
                                      boolean recordingActive) {
        public long totalMicros() {
            return updateMicros + notifyMicros + recordingMicros;
        }
    }

    private record CameraStats(double ingestFps, long frameBytes, long lastFrameAgeMs, long sequence) {
    }

    private record CameraFrame(byte[] jpegBytes, Integer width, Integer height, OffsetDateTime capturedAt, long sequence) {
        private CameraFrame(byte[] jpegBytes, Integer width, Integer height, OffsetDateTime capturedAt) {
            this(jpegBytes, width, height, capturedAt, 0);
        }

        private CameraFrame withSequence(long sequence) {
            return new CameraFrame(jpegBytes, width, height, capturedAt, sequence);
        }
    }

    private record StreamToken(String deviceId, OffsetDateTime expiresAt) {
    }

    private static class CameraRecording {
        private final String recordingId;
        private final String deviceId;
        private final OffsetDateTime startedAt;
        private OffsetDateTime stoppedAt;
        private long frameCount;
        private long totalBytes;

        private CameraRecording(String recordingId, String deviceId, OffsetDateTime startedAt) {
            this.recordingId = recordingId;
            this.deviceId = deviceId;
            this.startedAt = startedAt;
        }

        private void stop() {
            this.stoppedAt = OffsetDateTime.now();
        }

        private CameraRecordingResponse toResponse() {
            return new CameraRecordingResponse(recordingId, deviceId, startedAt, stoppedAt, frameCount, totalBytes, stoppedAt == null);
        }
    }
}
