package com.cabin.login.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cabin.common.exception.BusinessException;
import com.cabin.common.response.ResponseCode;
import com.cabin.config.JwtProperties;
import com.cabin.login.entity.AppUser;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtTokenServiceTests {
    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void issuesAndParsesToken() {
        JwtTokenService service = new JwtTokenService(new JwtProperties("test-issuer", SECRET, 7200));
        AppUser user = user();

        String token = service.issueToken(user);
        JwtClaims claims = service.parse(token);

        assertThat(token).isNotBlank();
        assertThat(claims.userId()).isEqualTo(user.getId());
        assertThat(claims.username()).isEqualTo("admin");
        assertThat(claims.roleCode()).isEqualTo("ADMIN");
        assertThat(claims.expiresAt()).isAfter(java.time.Instant.now());
    }

    @Test
    void rejectsInvalidToken() {
        JwtTokenService service = new JwtTokenService(new JwtProperties("test-issuer", SECRET, 7200));

        assertThatThrownBy(() -> service.parse("bad-token"))
                .isInstanceOf(BusinessException.class)
                .extracting("responseCode")
                .isEqualTo(ResponseCode.UNAUTHORIZED);
    }

    @Test
    void rejectsShortSecret() {
        JwtTokenService service = new JwtTokenService(new JwtProperties("test-issuer", "short", 7200));

        assertThatThrownBy(() -> service.issueToken(user()))
                .isInstanceOf(BusinessException.class)
                .extracting("responseCode")
                .isEqualTo(ResponseCode.INTERNAL_ERROR);
    }

    private AppUser user() {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setUsername("admin");
        user.setRoleCode("ADMIN");
        user.setStatus("ACTIVE");
        return user;
    }
}
