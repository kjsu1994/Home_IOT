package com.homeiot.common.config;

import com.homeiot.device.entity.Device;
import com.homeiot.device.repository.DeviceRepository;
import com.homeiot.device.service.DeviceTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class DeviceApiKeyFilter extends OncePerRequestFilter {
    private static final String HEADER_NAME = "X-Device-Api-Key";
    private static final Set<String> DEVICE_POST_SUFFIXES = Set.of(
            "/health",
            "/state/report",
            "/telemetry",
            "/ota/events",
            "/ota/status"
    );
    private static final Pattern PENDING_COMMAND_PATH = Pattern.compile("^/api/devices/[^/]+/commands/pending$");
    private static final Pattern COMMAND_STAGE_PATH = Pattern.compile("^/api/devices/[^/]+/commands/\\d+$");
    private static final Pattern DEVICE_ID_PATH = Pattern.compile("^/api/devices/([^/]+)/.*$");

    private final String deviceApiKey;
    private final DeviceRepository deviceRepository;
    private final DeviceTokenService deviceTokenService;

    public DeviceApiKeyFilter(@Value("${app.device-api-key:}") String deviceApiKey,
                              DeviceRepository deviceRepository,
                              DeviceTokenService deviceTokenService) {
        this.deviceApiKey = deviceApiKey == null ? "" : deviceApiKey.trim();
        this.deviceRepository = deviceRepository;
        this.deviceTokenService = deviceTokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (requiresDeviceKey(request) && !hasValidDeviceKey(request)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"data\":null,\"message\":\"invalid device api key\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean requiresDeviceKey(HttpServletRequest request) {
        if (deviceApiKey.isBlank()) {
            return false;
        }

        String method = request.getMethod();
        String path = request.getRequestURI();
        if ("POST".equals(method) && DEVICE_POST_SUFFIXES.stream().anyMatch(path::endsWith)) {
            return true;
        }
        if ("GET".equals(method) && PENDING_COMMAND_PATH.matcher(path).matches()) {
            return true;
        }
        return "PATCH".equals(method) && COMMAND_STAGE_PATH.matcher(path).matches();
    }

    private boolean hasValidDeviceKey(HttpServletRequest request) {
        String deviceId = extractDeviceId(request.getRequestURI());
        if (deviceId == null) {
            return false;
        }

        return deviceRepository.findByDeviceId(deviceId)
                .map(device -> hasValidDeviceToken(device, request.getHeader(HEADER_NAME)))
                .orElse(false);
    }

    private boolean hasValidDeviceToken(Device device, String presentedKey) {
        if (deviceTokenService.matches(device, presentedKey)) {
            return true;
        }
        return device.getDeviceApiKeyHash() == null && deviceApiKey.equals(presentedKey);
    }

    private String extractDeviceId(String path) {
        var matcher = DEVICE_ID_PATH.matcher(path);
        if (!matcher.matches()) {
            return null;
        }
        return matcher.group(1);
    }
}
