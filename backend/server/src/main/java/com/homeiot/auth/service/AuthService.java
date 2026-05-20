package com.homeiot.auth.service;

import com.homeiot.auth.dto.AuthDtos;
import com.homeiot.auth.jwt.JwtTokenService;
import com.homeiot.common.error.UnauthorizedException;
import com.homeiot.device.service.DeviceShareInviteService;
import com.homeiot.member.entity.Member;
import com.homeiot.member.service.MemberService;
import io.jsonwebtoken.Claims;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class AuthService {
    private static final String REFRESH_KEY_PREFIX = "auth:refresh:";
    private static final String BLACKLIST_KEY_PREFIX = "auth:blacklist:";

    private final MemberService memberService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final StringRedisTemplate redisTemplate;
    private final DeviceShareInviteService deviceShareInviteService;

    public AuthService(MemberService memberService,
                       PasswordEncoder passwordEncoder,
                       JwtTokenService jwtTokenService,
                       StringRedisTemplate redisTemplate,
                       DeviceShareInviteService deviceShareInviteService) {
        this.memberService = memberService;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.redisTemplate = redisTemplate;
        this.deviceShareInviteService = deviceShareInviteService;
    }

    public AuthDtos.TokenResponse login(AuthDtos.LoginRequest request) {
        Member member = memberService.findActiveByEmail(request.email());
        if (member.getPasswordHash() == null || !passwordEncoder.matches(request.password(), member.getPasswordHash())) {
            throw new UnauthorizedException("invalid credentials");
        }
        return issueTokens(member);
    }

    public AuthDtos.TokenResponse issueTokens(Member member) {
        deviceShareInviteService.acceptPendingInvites(member);
        String accessToken = jwtTokenService.generateAccessToken(member.getEmail(), Map.of(
                "username", member.getEmail(),
                "roles", member.getRoles(),
                "member_id", member.getId()
        ));
        String refreshToken = jwtTokenService.generateRefreshToken(member.getEmail());
        redisTemplate.opsForValue().set(refreshKey(member.getEmail()), refreshToken, jwtTokenService.getRefreshTokenExpirationMinutes(), TimeUnit.MINUTES);
        return new AuthDtos.TokenResponse(accessToken, refreshToken, "Bearer");
    }

    public AuthDtos.TokenResponse refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new UnauthorizedException("refresh token required");
        }
        Claims claims = jwtTokenService.parse(refreshToken).getBody();
        String email = claims.getSubject();
        String stored = redisTemplate.opsForValue().get(refreshKey(email));
        if (!refreshToken.equals(stored)) {
            throw new UnauthorizedException("invalid refresh token");
        }
        return issueTokens(memberService.findActiveByEmail(email));
    }

    public void logout(String accessToken, String email) {
        redisTemplate.delete(refreshKey(email));
        if (accessToken != null && !accessToken.isBlank()) {
            Date expiration = jwtTokenService.parse(accessToken).getBody().getExpiration();
            long seconds = Math.max(1, Duration.between(Instant.now(), expiration.toInstant()).toSeconds());
            redisTemplate.opsForValue().set(BLACKLIST_KEY_PREFIX + accessToken, "1", seconds, TimeUnit.SECONDS);
        }
    }

    public boolean isBlacklisted(String accessToken) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_KEY_PREFIX + accessToken));
    }

    public AuthDtos.SessionResponse me() {
        Member member = currentMember();
        return new AuthDtos.SessionResponse(member.getId(), member.getEmail(), member.getName(), member.getRoles());
    }

    public Member currentMember() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("login required");
        }
        return memberService.findActiveByEmail(authentication.getName());
    }

    public boolean currentUserHasRole(String role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals(role));
    }

    public String currentEmailOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? null : authentication.getName();
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(Claims claims) {
        Object roles = claims.get("roles");
        if (!(roles instanceof List<?> rawRoles)) {
            return List.of();
        }
        return rawRoles.stream().filter(String.class::isInstance).map(String.class::cast).toList();
    }

    private String refreshKey(String email) {
        return REFRESH_KEY_PREFIX + email;
    }
}
