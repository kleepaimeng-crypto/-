package com.cabin.log.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cabin.login.entity.AppUser;
import com.cabin.log.entity.AuditLog;
import com.cabin.log.mapper.AuditLogMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class AuditLogServiceTests {
    private final AuditLogMapper mapper = mock(AuditLogMapper.class);
    private final AuditLogService service = new AuditLogService(provider(mapper), new ObjectMapper());

    @Test
    void loginFailureAuditDoesNotIncludePasswordOrToken() {
        when(mapper.insert(any())).thenReturn(1);

        service.recordLoginFailure("admin", "bad_credentials", "127.0.0.1");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(mapper).insert(captor.capture());
        AuditLog entry = captor.getValue();
        assertThat(entry.getAction()).isEqualTo("LOGIN");
        assertThat(entry.getResult()).isEqualTo("FAILURE");
        assertThat(entry.getAfterValue()).contains("bad_credentials");
        assertThat(entry.getAfterValue()).doesNotContain("password").doesNotContain("token");
    }

    @Test
    void loginSuccessAuditUsesUserIdAsOperator() {
        when(mapper.insert(any())).thenReturn(1);
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setUsername("admin");
        user.setRoleCode("ADMIN");

        service.recordLoginSuccess(user, "127.0.0.1");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getOperatorId()).isEqualTo(user.getId());
        assertThat(captor.getValue().getAfterValue()).contains("admin").doesNotContain("token");
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<AuditLogMapper> provider(AuditLogMapper mapper) {
        ObjectProvider<AuditLogMapper> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mapper);
        return provider;
    }
}
