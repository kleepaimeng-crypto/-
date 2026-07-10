package com.cabin.login.service;

import com.cabin.common.exception.BusinessException;
import com.cabin.common.response.ResponseCode;
import com.cabin.common.security.CurrentUser;
import com.cabin.login.dto.LoginRequest;
import com.cabin.login.dto.LoginResponse;
import com.cabin.login.dto.UserInfoResponse;
import com.cabin.login.entity.AppUser;
import com.cabin.login.mapper.AppUserMapper;
import com.cabin.log.service.AuditLogService;
import java.util.Locale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final ObjectProvider<AppUserMapper> appUserMapperProvider;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final AuditLogService auditLogService;

    public AuthService(
            ObjectProvider<AppUserMapper> appUserMapperProvider,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService,
            AuditLogService auditLogService
    ) {
        this.appUserMapperProvider = appUserMapperProvider;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public LoginResponse login(LoginRequest request, String requestIp) {
        String username = normalizeUsername(request.username());
        AppUser user = appUserMapper().findByUsername(username);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            auditLogService.recordLoginFailure(username, "bad_credentials", requestIp);
            throw new BusinessException(ResponseCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            auditLogService.recordLoginFailure(username, "account_disabled", requestIp);
            throw new BusinessException(ResponseCode.ACCOUNT_DISABLED, "账号当前不可用");
        }

        appUserMapper().updateLastLoginAt(user.getId());
        auditLogService.recordLoginSuccess(user, requestIp);
        String token = jwtTokenService.issueToken(user);
        return new LoginResponse(
                token,
                "Bearer",
                jwtTokenService.expiresInSeconds(),
                UserInfoResponse.from(user)
        );
    }

    public UserInfoResponse currentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUser currentUser)) {
            throw new BusinessException(ResponseCode.UNAUTHORIZED, "缺少、过期或无效 JWT");
        }
        return UserInfoResponse.from(currentUser);
    }

    private AppUserMapper appUserMapper() {
        AppUserMapper mapper = appUserMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BusinessException(ResponseCode.DATABASE_UNAVAILABLE, "数据库暂不可用");
        }
        return mapper;
    }

    private String normalizeUsername(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
