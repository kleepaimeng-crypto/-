package com.cabin.common.security;

import com.cabin.common.exception.BusinessException;
import com.cabin.common.response.ResponseCode;
import com.cabin.login.entity.AppUser;
import com.cabin.login.mapper.AppUserMapper;
import com.cabin.login.service.JwtClaims;
import com.cabin.login.service.JwtTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService jwtTokenService;
    private final ObjectProvider<AppUserMapper> appUserMapperProvider;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(
            JwtTokenService jwtTokenService,
            ObjectProvider<AppUserMapper> appUserMapperProvider,
            ObjectMapper objectMapper
    ) {
        this.jwtTokenService = jwtTokenService;
        this.appUserMapperProvider = appUserMapperProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            JwtClaims claims = jwtTokenService.parse(authorization.substring(BEARER_PREFIX.length()));
            AppUserMapper appUserMapper = appUserMapperProvider.getIfAvailable();
            AppUser user = appUserMapper == null ? null : appUserMapper.findById(claims.userId());
            if (user == null) {
                SecurityResponseWriter.write(response, objectMapper, ResponseCode.UNAUTHORIZED, "缺少、过期或无效 JWT");
                return;
            }
            if (!"ACTIVE".equals(user.getStatus())) {
                SecurityResponseWriter.write(response, objectMapper, ResponseCode.ACCOUNT_DISABLED, "管理员账号已禁用");
                return;
            }

            CurrentUser principal = new CurrentUser(
                    user.getId(),
                    user.getUsername(),
                    user.getEmail(),
                    user.getRoleCode()
            );
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.getRoleCode()))
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (BusinessException exception) {
            SecurityResponseWriter.write(response, objectMapper, exception.responseCode(), exception.getMessage());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
