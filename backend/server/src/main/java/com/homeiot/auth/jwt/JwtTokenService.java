package com.homeiot.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.io.Encoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

@Component
public class JwtTokenService {
    private final String secretKey;
    private final long accessTokenExpirationMinutes;
    private final long refreshTokenExpirationMinutes;

    public JwtTokenService(@Value("${jwt.secret-key}") String secretKey,
                           @Value("${jwt.access-token-expiration-minutes:60}") long accessTokenExpirationMinutes,
                           @Value("${jwt.refresh-token-expiration-minutes:10080}") long refreshTokenExpirationMinutes) {
        this.secretKey = secretKey;
        this.accessTokenExpirationMinutes = accessTokenExpirationMinutes;
        this.refreshTokenExpirationMinutes = refreshTokenExpirationMinutes;
    }

    public String generateAccessToken(String subject, Map<String, Object> claims) {
        return generate(subject, claims, accessTokenExpirationMinutes);
    }

    public String generateRefreshToken(String subject) {
        return generate(subject, Map.of(), refreshTokenExpirationMinutes);
    }

    public Jws<Claims> parse(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey())
                .build()
                .parseClaimsJws(token);
    }

    public long getAccessTokenExpirationMinutes() {
        return accessTokenExpirationMinutes;
    }

    public long getRefreshTokenExpirationMinutes() {
        return refreshTokenExpirationMinutes;
    }

    private String generate(String subject, Map<String, Object> claims, long expirationMinutes) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(expirationMinutes * 60)))
                .signWith(signingKey())
                .compact();
    }

    private Key signingKey() {
        String encoded = Encoders.BASE64.encode(secretKey.getBytes(StandardCharsets.UTF_8));
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(encoded));
    }
}
