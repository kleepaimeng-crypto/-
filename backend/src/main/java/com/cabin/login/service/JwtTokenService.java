package com.cabin.login.service;

import com.cabin.common.exception.BusinessException;
import com.cabin.common.response.ResponseCode;
import com.cabin.config.JwtProperties;
import com.cabin.login.entity.AppUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {
    private final JwtProperties properties;

    public JwtTokenService(JwtProperties properties) {
        this.properties = properties;
    }

    public String issueToken(AppUser user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(expiresInSeconds());
        return Jwts.builder()
                .issuer(issuer())
                .subject(user.getId().toString())
                .claim("username", user.getUsername())
                .claim("roleCode", user.getRoleCode())
                .issuedAt(java.util.Date.from(now))
                .expiration(java.util.Date.from(expiresAt))
                .signWith(signingKey(), Jwts.SIG.HS256)
                .compact();
    }

    public JwtClaims parse(String token) {
        if (token == null || token.isBlank()) {
            throw unauthorized();
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey())
                    .requireIssuer(issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new JwtClaims(
                    UUID.fromString(claims.getSubject()),
                    claims.get("username", String.class),
                    claims.get("roleCode", String.class),
                    claims.getExpiration().toInstant()
            );
        } catch (IllegalArgumentException | JwtException exception) {
            throw unauthorized();
        }
    }

    public long expiresInSeconds() {
        return properties.expiresInSeconds();
    }

    private SecretKey signingKey() {
        String secret = properties.secret();
        if (secret == null || secret.isBlank()) {
            throw new BusinessException(ResponseCode.INTERNAL_ERROR, "JWT 密钥未配置");
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new BusinessException(ResponseCode.INTERNAL_ERROR, "JWT 密钥长度不足");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private String issuer() {
        String issuer = properties.issuer();
        return issuer == null || issuer.isBlank() ? "cabin-data-platform" : issuer;
    }

    private BusinessException unauthorized() {
        return new BusinessException(ResponseCode.UNAUTHORIZED, "缺少、过期或无效 JWT");
    }
}
