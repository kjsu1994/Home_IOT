package com.homeiot.member.controller;

import com.homeiot.auth.service.AuthService;
import com.homeiot.common.dto.ApiResponse;
import com.homeiot.member.dto.MemberDtos;
import com.homeiot.member.entity.Member;
import com.homeiot.member.service.MemberService;
import com.homeiot.member.service.SignupVerificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members")
public class MemberController {
    private final MemberService memberService;
    private final SignupVerificationService signupVerificationService;
    private final AuthService authService;

    public MemberController(MemberService memberService,
                            SignupVerificationService signupVerificationService,
                            AuthService authService) {
        this.memberService = memberService;
        this.signupVerificationService = signupVerificationService;
        this.authService = authService;
    }

    @GetMapping("/check-email")
    public ApiResponse<MemberDtos.EmailCheckResponse> checkEmail(@RequestParam String email) {
        boolean available = memberService.isEmailAvailable(email);
        return ApiResponse.ok(new MemberDtos.EmailCheckResponse(email, available, !available));
    }

    @PostMapping("/email-verification/send")
    public ApiResponse<MemberDtos.EmailVerificationSendResponse> sendVerification(@Valid @RequestBody MemberDtos.EmailVerificationSendRequest request,
                                                                                  HttpServletRequest servletRequest) {
        return ApiResponse.ok(signupVerificationService.sendVerificationCode(request.email(), clientIp(servletRequest)));
    }

    @PostMapping("/email-verification/confirm")
    public ApiResponse<MemberDtos.EmailVerificationConfirmResponse> confirmVerification(@Valid @RequestBody MemberDtos.EmailVerificationConfirmRequest request) {
        return ApiResponse.ok(signupVerificationService.confirmVerificationCode(request.email(), request.code()));
    }

    @PostMapping
    public ApiResponse<MemberDtos.MemberResponse> signup(@Valid @RequestBody MemberDtos.SignupRequest request) {
        return ApiResponse.ok(memberService.signup(request), "member created");
    }

    @GetMapping("/me")
    public ApiResponse<MemberDtos.MemberResponse> me() {
        Member member = authService.currentMember();
        return ApiResponse.ok(memberService.toResponse(member));
    }
    @PatchMapping("/me")
    public ApiResponse<MemberDtos.MemberResponse> updateMe(@Valid @RequestBody MemberDtos.AccountUpdateRequest request) {
        Member member = authService.currentMember();
        return ApiResponse.ok(memberService.updateAccount(member, request), "account updated");
    }

    @PatchMapping("/me/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody MemberDtos.PasswordChangeRequest request) {
        Member member = authService.currentMember();
        memberService.changePassword(member, request);
        return ApiResponse.ok(null, "password changed");
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}