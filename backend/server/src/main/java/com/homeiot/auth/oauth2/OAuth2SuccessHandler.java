package com.homeiot.auth.oauth2;

import com.homeiot.auth.dto.AuthDtos;
import com.homeiot.auth.service.AuthService;
import com.homeiot.member.entity.Member;
import com.homeiot.member.service.MemberService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {
    private final MemberService memberService;
    private final AuthService authService;
    private final String successRedirectPath;

    public OAuth2SuccessHandler(MemberService memberService,
                                AuthService authService,
                                @Value("${app.oauth2-success-redirect-path:/iot/}") String successRedirectPath) {
        this.memberService = memberService;
        this.authService = authService;
        this.successRedirectPath = successRedirectPath == null || successRedirectPath.isBlank() ? "/iot/" : successRedirectPath;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        OAuth2User user = (OAuth2User) authentication.getPrincipal();
        String email = extractEmail(user);
        Member member = memberService.findActiveByEmail(email);
        AuthDtos.TokenResponse tokens = authService.issueTokens(member);
        String redirect = UriComponentsBuilder.fromPath(successRedirectPath)
                .queryParam("access_token", tokens.accessToken())
                .queryParam("refresh_token", tokens.refreshToken())
                .build()
                .toUriString();
        getRedirectStrategy().sendRedirect(request, response, redirect);
    }

    private String extractEmail(OAuth2User user) {
        Object email = user.getAttributes().get("email");
        if (email != null) {
            return String.valueOf(email);
        }
        Object response = user.getAttributes().get("response");
        if (response instanceof java.util.Map<?, ?> map && map.get("email") != null) {
            return String.valueOf(map.get("email"));
        }
        throw new IllegalArgumentException("oauth email not found");
    }
}