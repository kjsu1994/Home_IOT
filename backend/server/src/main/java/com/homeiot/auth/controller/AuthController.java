package com.homeiot.auth.controller;

import com.homeiot.auth.dto.AdminSessionResponse;
import com.homeiot.common.dto.ApiResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @GetMapping("/me")
    public ApiResponse<AdminSessionResponse> me(Authentication authentication) {
        return ApiResponse.ok(new AdminSessionResponse(authentication.getName()));
    }
}
