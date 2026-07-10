package com.cabin.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cabin.common.exception.BusinessException;
import com.cabin.common.response.ResponseCode;
import com.cabin.common.security.CurrentUser;
import com.cabin.log.service.AuditLogService;
import com.cabin.user.dto.UserCreateRequest;
import com.cabin.user.dto.UserDeleteRequest;
import com.cabin.user.dto.UserUpdateRequest;
import com.cabin.user.entity.UserRow;
import com.cabin.user.mapper.UserMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserServiceTests {
    private final UserMapper mapper = mock(UserMapper.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final UserService service = new UserService(provider(mapper), passwordEncoder, auditLogService);

    @Test
    void listUsersUsesServerPagination() {
        UserRow row = user(UUID.randomUUID(), "operator", "USER", "ACTIVE", 1);
        when(mapper.countUsers()).thenReturn(1L);
        when(mapper.findUsers(0, 20, "createdAt", "desc")).thenReturn(List.of(row));

        var response = service.listUsers(1, 20, "createdAt", "desc");

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.items()).extracting("username").containsExactly("operator");
    }

    @Test
    void createUserHashesPasswordAndAuditsSafeSnapshot() {
        UUID operatorId = UUID.randomUUID();
        when(passwordEncoder.encode("abc123")).thenReturn("bcrypt-hash");
        when(mapper.insertUser(any())).thenReturn(1);
        when(mapper.findById(any())).thenAnswer(invocation ->
                user(invocation.getArgument(0), "operator", "USER", "PENDING", 1));

        var response = service.createUser(
                new UserCreateRequest(
                        " Operator ",
                        "Operator@example.com",
                        "abc123",
                        "USER",
                        "PENDING"
                ),
                new CurrentUser(operatorId, "root", "root@example.com", "SUPER_ADMIN"),
                "127.0.0.1"
        );

        ArgumentCaptor<UserRow> inserted = ArgumentCaptor.forClass(UserRow.class);
        verify(mapper).insertUser(inserted.capture());
        assertThat(inserted.getValue().getUsername()).isEqualTo("operator");
        assertThat(inserted.getValue().getEmail()).isEqualTo("operator@example.com");
        assertThat(inserted.getValue().getPasswordHash()).isEqualTo("bcrypt-hash");
        verify(auditLogService).recordSuccess(
                eq("CREATE_USER"),
                eq("APP_USER"),
                eq(response.id().toString()),
                eq(operatorId),
                eq("127.0.0.1"),
                eq(null),
                any()
        );
    }

    @Test
    void updateRejectsRemovingLastActiveSuperAdmin() {
        UUID userId = UUID.randomUUID();
        UserRow before = user(userId, "root", "SUPER_ADMIN", "ACTIVE", 2);
        when(mapper.findById(userId)).thenReturn(before);
        when(mapper.lockActiveSuperAdminIds()).thenReturn(List.of(userId));

        assertThatThrownBy(() -> service.updateUser(
                userId,
                new UserUpdateRequest(null, null, "ADMIN", null, 2),
                new CurrentUser(UUID.randomUUID(), "other-root", null, "SUPER_ADMIN"),
                "127.0.0.1"
        )).isInstanceOf(BusinessException.class)
                .extracting("responseCode")
                .isEqualTo(ResponseCode.RESOURCE_CONFLICT);
    }

    @Test
    void deleteRejectsCurrentUser() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> service.deleteUser(
                userId,
                new UserDeleteRequest("离职", 1),
                new CurrentUser(userId, "root", null, "SUPER_ADMIN"),
                "127.0.0.1"
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前账号");
    }

    @Test
    void updateAllowsChangingOneOfTwoActiveSuperAdmins() {
        UUID userId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        UserRow before = user(userId, "root-a", "SUPER_ADMIN", "ACTIVE", 2);
        UserRow after = user(userId, "root-a", "ADMIN", "ACTIVE", 3);
        when(mapper.findById(userId)).thenReturn(before, after);
        when(mapper.lockActiveSuperAdminIds()).thenReturn(List.of(userId, otherId));
        when(mapper.updateUser(userId, null, null, "ADMIN", null, 2)).thenReturn(1);

        var response = service.updateUser(
                userId,
                new UserUpdateRequest(null, null, "ADMIN", null, 2),
                new CurrentUser(otherId, "root-b", null, "SUPER_ADMIN"),
                "127.0.0.1"
        );

        assertThat(response.roleCode()).isEqualTo("ADMIN");
    }

    private UserRow user(UUID id, String username, String roleCode, String status, int version) {
        UserRow row = new UserRow();
        row.setId(id);
        row.setUsername(username);
        row.setEmail(username + "@example.com");
        row.setRoleCode(roleCode);
        row.setStatus(status);
        row.setVersion(version);
        row.setCreatedAt(OffsetDateTime.parse("2026-07-09T10:00:00+08:00"));
        row.setUpdatedAt(OffsetDateTime.parse("2026-07-09T10:00:00+08:00"));
        return row;
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<UserMapper> provider(UserMapper mapper) {
        ObjectProvider<UserMapper> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mapper);
        return provider;
    }
}
