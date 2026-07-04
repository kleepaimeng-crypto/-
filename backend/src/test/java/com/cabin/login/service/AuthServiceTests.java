package com.cabin.login.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cabin.common.exception.BusinessException;
import com.cabin.common.response.ResponseCode;
import com.cabin.common.security.CurrentUser;
import com.cabin.config.JwtProperties;
import com.cabin.login.dto.LoginRequest;
import com.cabin.login.dto.LoginResponse;
import com.cabin.login.entity.AppUser;
import com.cabin.login.mapper.AppUserMapper;
import com.cabin.log.service.AuditLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTests {
    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final AppUserMapper mapper = mock(AppUserMapper.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final AuthService service = new AuthService(
            provider(mapper),
            passwordEncoder,
            new JwtTokenService(new JwtProperties("test-issuer", SECRET, 7200)),
            auditLogService
    );

    @Test
    void loginReturnsBearerTokenAndUser() {
        AppUser user = activeUser();
        when(mapper.findByUsername("admin")).thenReturn(user);

        LoginResponse response = service.login(new LoginRequest("Admin", "secret"), "127.0.0.1");

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.expiresInSeconds()).isEqualTo(7200);
        assertThat(response.user().username()).isEqualTo("admin");
        verify(mapper).updateLastLoginAt(user.getId());
        verify(auditLogService).recordLoginSuccess(user, "127.0.0.1");
    }

    @Test
    void loginRejectsBadPasswordAndAuditsFailure() {
        when(mapper.findByUsername("admin")).thenReturn(activeUser());

        assertThatThrownBy(() -> service.login(new LoginRequest("admin", "bad"), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .extracting("responseCode")
                .isEqualTo(ResponseCode.UNAUTHORIZED);
        verify(auditLogService).recordLoginFailure("admin", "bad_credentials", "127.0.0.1");
    }

    @Test
    void loginRejectsDisabledUser() {
        AppUser user = activeUser();
        user.setStatus("DISABLED");
        when(mapper.findByUsername("admin")).thenReturn(user);

        assertThatThrownBy(() -> service.login(new LoginRequest("admin", "secret"), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .extracting("responseCode")
                .isEqualTo(ResponseCode.ACCOUNT_DISABLED);
        verify(auditLogService).recordLoginFailure("admin", "account_disabled", "127.0.0.1");
    }

    @Test
    void currentUserReadsSecurityPrincipal() {
        CurrentUser currentUser = new CurrentUser(UUID.randomUUID(), "admin", "admin@example.invalid", "ADMIN");
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(currentUser, null);

        assertThat(service.currentUser(authentication).username()).isEqualTo("admin");
    }

    private AppUser activeUser() {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setUsername("admin");
        user.setPasswordHash(passwordEncoder.encode("secret"));
        user.setEmail("admin@example.invalid");
        user.setRoleCode("ADMIN");
        user.setStatus("ACTIVE");
        return user;
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<AppUserMapper> provider(AppUserMapper mapper) {
        ObjectProvider<AppUserMapper> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mapper);
        return provider;
    }
}
