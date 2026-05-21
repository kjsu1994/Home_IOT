package com.homeiot.camera.websocket;

import com.homeiot.camera.service.CameraService;
import com.homeiot.device.entity.Device;
import com.homeiot.device.repository.DeviceRepository;
import com.homeiot.device.service.DeviceTokenService;
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
import java.util.regex.Pattern;

@Component
public class CameraWebSocketHandler extends BinaryWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(CameraWebSocketHandler.class);
    private static final String DEVICE_API_KEY_HEADER = "X-Device-Api-Key";
    private static final Pattern DEVICE_PATH = Pattern.compile("^/api/devices/([^/]+)/camera/ws$");

    private final CameraService cameraService;
    private final DeviceRepository deviceRepository;
    private final DeviceTokenService deviceTokenService;
    private final String bootstrapDeviceApiKey;

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

        ByteBuffer payload = message.getPayload();
        if (payload.remaining() < 5) {
            return;
        }

        int width = Short.toUnsignedInt(payload.getShort());
        int height = Short.toUnsignedInt(payload.getShort());
        byte[] jpegBytes = new byte[payload.remaining()];
        payload.get(jpegBytes);
        cameraService.acceptFrame(deviceId, jpegBytes, width, height);
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
