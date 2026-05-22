package com.homeiot.camera.controller;

import com.homeiot.camera.dto.CameraDeviceResponse;
import com.homeiot.camera.dto.CameraControlResponse;
import com.homeiot.camera.dto.CameraRecordingResponse;
import com.homeiot.camera.dto.CameraStatusRequest;
import com.homeiot.camera.dto.CameraStreamTokenResponse;
import com.homeiot.camera.service.CameraService;
import com.homeiot.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;

@RestController
@RequestMapping("/api")
public class CameraController {
    private final CameraService cameraService;

    public CameraController(CameraService cameraService) {
        this.cameraService = cameraService;
    }

    @GetMapping("/cameras")
    public ApiResponse<List<CameraDeviceResponse>> getCameras() {
        return ApiResponse.ok(cameraService.getCameras());
    }

    @PostMapping("/cameras/{deviceId}/stream-token")
    public ApiResponse<CameraStreamTokenResponse> issueStreamToken(@PathVariable String deviceId) {
        return ApiResponse.ok(cameraService.issueStreamToken(deviceId));
    }

    @GetMapping("/cameras/{deviceId}/mjpeg")
    public ResponseEntity<StreamingResponseBody> streamMjpeg(@PathVariable String deviceId,
                                                             @RequestParam String token) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .contentType(MediaType.parseMediaType(cameraService.mjpegContentType()))
                .body(cameraService.streamMjpeg(deviceId, token));
    }

    @GetMapping("/cameras/{deviceId}/snapshot")
    public ResponseEntity<byte[]> snapshot(@PathVariable String deviceId,
                                           @RequestParam String token) {
        var frame = cameraService.latestSnapshot(deviceId, token);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + deviceId + "-" + frame.sequence() + ".jpg\"")
                .header("X-Camera-Width", frame.width() == null ? "" : frame.width().toString())
                .header("X-Camera-Height", frame.height() == null ? "" : frame.height().toString())
                .header("X-Camera-Sequence", Long.toString(frame.sequence()))
                .contentType(MediaType.IMAGE_JPEG)
                .body(frame.jpegBytes());
    }

    @PostMapping("/cameras/{deviceId}/recordings/start")
    public ApiResponse<CameraRecordingResponse> startRecording(@PathVariable String deviceId) {
        return ApiResponse.ok(cameraService.startRecording(deviceId));
    }

    @PostMapping("/cameras/{deviceId}/recordings/stop")
    public ApiResponse<CameraRecordingResponse> stopRecording(@PathVariable String deviceId) {
        return ApiResponse.ok(cameraService.stopRecording(deviceId));
    }

    @GetMapping("/cameras/{deviceId}/recordings")
    public ApiResponse<List<CameraRecordingResponse>> getRecordings(@PathVariable String deviceId) {
        return ApiResponse.ok(cameraService.getRecordings(deviceId));
    }

    @PostMapping("/devices/{deviceId}/camera/frame")
    public ApiResponse<Void> acceptFrame(@PathVariable String deviceId,
                                         @RequestHeader(name = "X-Camera-Width", required = false) Integer width,
                                         @RequestHeader(name = "X-Camera-Height", required = false) Integer height,
                                         @RequestBody byte[] jpegBytes) {
        cameraService.acceptFrame(deviceId, jpegBytes, width, height);
        return ApiResponse.ok(null);
    }

    @PostMapping("/devices/{deviceId}/camera/status")
    public ApiResponse<Void> updateStatus(@PathVariable String deviceId,
                                          @Valid @RequestBody CameraStatusRequest request) {
        cameraService.updateStatus(deviceId, request);
        return ApiResponse.ok(null);
    }

    @GetMapping("/devices/{deviceId}/camera/control")
    public ApiResponse<CameraControlResponse> control(@PathVariable String deviceId) {
        return ApiResponse.ok(cameraService.control(deviceId));
    }
}
