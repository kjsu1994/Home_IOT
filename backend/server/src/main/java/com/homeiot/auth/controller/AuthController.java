package com.homeiot.auth.controller;

import com.homeiot.auth.dto.AuthDtos;
import com.homeiot.auth.service.AuthService;
import com.homeiot.auth.service.PasswordResetService;
import com.homeiot.common.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService authService, PasswordResetService passwordResetService) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/login")
    public ApiResponse<AuthDtos.TokenResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request,
                                                     HttpServletResponse response) {
        AuthDtos.TokenResponse tokens = authService.login(request);
        writeTokenHeaders(response, tokens);
        return ApiResponse.ok(tokens);
    }

    @PostMapping("/reissue")
    public ApiResponse<AuthDtos.TokenResponse> reissue(@RequestHeader(name = "Refresh", required = false) String refreshHeader,
                                                       @RequestBody(required = false) AuthDtos.RefreshRequest request,
                                                       HttpServletResponse response) {
        String refreshToken = refreshHeader != null && !refreshHeader.isBlank() ? refreshHeader : request == null ? null : request.refreshToken();
        AuthDtos.TokenResponse tokens = authService.refresh(refreshToken);
        writeTokenHeaders(response, tokens);
        return ApiResponse.ok(tokens);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader(name = "Authorization", required = false) String authorization) {
        String accessToken = authorization != null && authorization.startsWith("Bearer ") ? authorization.substring("Bearer ".length()).trim() : null;
        authService.logout(accessToken, authService.currentEmailOrNull());
        return ApiResponse.ok(null, "logged out");
    }

    @GetMapping("/me")
    public ApiResponse<AuthDtos.SessionResponse> me() {
        return ApiResponse.ok(authService.me());
    }

    @PostMapping("/password-reset/request")
    public ApiResponse<Void> requestPasswordReset(@Valid @RequestBody AuthDtos.PasswordResetRequest request) {
        passwordResetService.requestReset(request.email());
        return ApiResponse.ok(null, "password reset email sent if account exists");
    }

    @PostMapping("/password-reset/confirm")
    public ApiResponse<Void> confirmPasswordReset(@Valid @RequestBody AuthDtos.PasswordResetConfirmRequest request) {
        passwordResetService.confirmReset(request.email(), request.code(), request.newPassword());
        return ApiResponse.ok(null, "password reset complete");
    }

    private void writeTokenHeaders(HttpServletResponse response, AuthDtos.TokenResponse tokens) {
        response.setHeader("Authorization", "Bearer " + tokens.accessToken());
        response.setHeader("Refresh", tokens.refreshToken());
    }
}
