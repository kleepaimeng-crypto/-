package com.cabin.user.service;

import com.cabin.common.exception.BusinessException;
import com.cabin.common.response.ErrorDetail;
import com.cabin.common.response.PageResponse;
import com.cabin.common.response.ResponseCode;
import com.cabin.common.security.CurrentUser;
import com.cabin.log.service.AuditLogService;
import com.cabin.user.dto.UserCreateRequest;
import com.cabin.user.dto.UserDeleteRequest;
import com.cabin.user.dto.UserSummaryResponse;
import com.cabin.user.dto.UserUpdateRequest;
import com.cabin.user.entity.UserRow;
import com.cabin.user.mapper.UserMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private static final Set<Integer> PAGE_SIZES = Set.of(20, 50, 100);
    private static final Set<String> SORT_FIELDS =
            Set.of("username", "roleCode", "email", "status", "createdAt");
    private static final Set<String> ROLE_CODES =
            Set.of("SUPER_ADMIN", "ADMIN", "USER");
    private static final Set<String> CREATE_STATUSES =
            Set.of("ACTIVE", "PENDING");
    private static final Set<String> UPDATE_STATUSES =
            Set.of("ACTIVE", "PENDING", "FROZEN");

    private final ObjectProvider<UserMapper> mapperProvider;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public UserService(
            ObjectProvider<UserMapper> mapperProvider,
            PasswordEncoder passwordEncoder,
            AuditLogService auditLogService
    ) {
        this.mapperProvider = mapperProvider;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    public PageResponse<UserSummaryResponse> listUsers(
            int page,
            int pageSize,
            String sortBy,
            String sortDirection
    ) {
        if (page < 1) {
            throw validation("page", "must_be_positive", "page 必须大于 0");
        }
        if (!PAGE_SIZES.contains(pageSize)) {
            throw validation("pageSize", "unsupported", "pageSize 只允许 20、50、100");
        }
        if (!SORT_FIELDS.contains(sortBy)) {
            throw validation("sortBy", "unsupported", "sortBy 不受支持");
        }
        String direction = sortDirection.toLowerCase(Locale.ROOT);
        if (!Set.of("asc", "desc").contains(direction)) {
            throw validation("sortDirection", "unsupported", "sortDirection 只允许 asc 或 desc");
        }
        UserMapper mapper = mapper();
        long total = mapper.countUsers();
        int offset = Math.multiplyExact(page - 1, pageSize);
        List<UserSummaryResponse> items = mapper.findUsers(offset, pageSize, sortBy, direction)
                .stream()
                .map(UserSummaryResponse::from)
                .toList();
        return PageResponse.of(items, page, pageSize, total);
    }

    @Transactional
    public UserSummaryResponse createUser(
            UserCreateRequest request,
            CurrentUser currentUser,
            String requestIp
    ) {
        UserMapper mapper = mapper();
        String username = normalizeUsername(request.username());
        String email = normalizeEmail(request.email());
        String roleCode = normalizeRole(request.roleCode());
        String status = normalizeStatus(request.status(), CREATE_STATUSES);
        ensureUnique(mapper, username, email, null);

        UserRow row = new UserRow();
        row.setId(UUID.randomUUID());
        row.setUsername(username);
        row.setEmail(email);
        row.setPasswordHash(passwordEncoder.encode(request.password()));
        row.setRoleCode(roleCode);
        row.setStatus(status);
        try {
            mapper.insertUser(row);
        } catch (DuplicateKeyException exception) {
            throw conflict("用户名或邮箱已存在");
        }
        UserRow created = requireUser(mapper, row.getId());
        auditLogService.recordSuccess(
                "CREATE_USER",
                "APP_USER",
                created.getId().toString(),
                currentUser.id(),
                requestIp,
                null,
                snapshot(created)
        );
        return UserSummaryResponse.from(created);
    }

    @Transactional
    public UserSummaryResponse updateUser(
            UUID userId,
            UserUpdateRequest request,
            CurrentUser currentUser,
            String requestIp
    ) {
        UserMapper mapper = mapper();
        UserRow before = requireUser(mapper, userId);
        if ("DELETED".equals(before.getStatus())) {
            throw conflict("已删除用户不能编辑");
        }

        String username = request.username() == null ? null : normalizeUsername(request.username());
        String email = request.email() == null ? null : normalizeEmail(request.email());
        String roleCode = request.roleCode() == null ? null : normalizeRole(request.roleCode());
        String status = request.status() == null ? null : normalizeStatus(request.status(), UPDATE_STATUSES);
        if (username == null && email == null && roleCode == null && status == null) {
            throw new BusinessException(ResponseCode.VALIDATION_ERROR, "至少提供一个要更新的字段");
        }
        if (currentUser.id().equals(userId)) {
            if (roleCode != null && !roleCode.equals(before.getRoleCode())) {
                throw conflict("不能修改当前账号的角色");
            }
            if (status != null && !"ACTIVE".equals(status)) {
                throw conflict("不能停用当前账号");
            }
        }

        String nextRole = roleCode == null ? before.getRoleCode() : roleCode;
        String nextStatus = status == null ? before.getStatus() : status;
        protectLastSuperAdmin(mapper, before, nextRole, nextStatus);
        ensureUnique(
                mapper,
                username == null ? before.getUsername() : username,
                email == null ? before.getEmail() : email,
                userId
        );
        try {
            int updated = mapper.updateUser(
                    userId,
                    username,
                    email,
                    roleCode,
                    status,
                    request.expectedVersion()
            );
            if (updated == 0) {
                throw conflict("用户数据已变化，请刷新后重试");
            }
        } catch (DuplicateKeyException exception) {
            throw conflict("用户名或邮箱已存在");
        }

        UserRow after = requireUser(mapper, userId);
        auditLogService.recordSuccess(
                "UPDATE_USER",
                "APP_USER",
                userId.toString(),
                currentUser.id(),
                requestIp,
                snapshot(before),
                snapshot(after)
        );
        return UserSummaryResponse.from(after);
    }

    @Transactional
    public void deleteUser(
            UUID userId,
            UserDeleteRequest request,
            CurrentUser currentUser,
            String requestIp
    ) {
        if (currentUser.id().equals(userId)) {
            throw conflict("不能删除当前账号");
        }
        UserMapper mapper = mapper();
        UserRow before = requireUser(mapper, userId);
        if ("DELETED".equals(before.getStatus())) {
            throw conflict("用户已删除");
        }
        protectLastSuperAdmin(mapper, before, before.getRoleCode(), "DELETED");
        String reason = normalizeReason(request.reason());
        int updated = mapper.softDeleteUser(
                userId,
                currentUser.id(),
                reason,
                request.expectedVersion()
        );
        if (updated == 0) {
            throw conflict("用户数据已变化，请刷新后重试");
        }
        UserRow after = requireUser(mapper, userId);
        Map<String, Object> afterValue = snapshot(after);
        afterValue.put("deleteReason", reason);
        auditLogService.recordSuccess(
                "DELETE_USER",
                "APP_USER",
                userId.toString(),
                currentUser.id(),
                requestIp,
                snapshot(before),
                afterValue
        );
    }

    private void protectLastSuperAdmin(
            UserMapper mapper,
            UserRow before,
            String nextRole,
            String nextStatus
    ) {
        if (!"SUPER_ADMIN".equals(before.getRoleCode())
                || !"ACTIVE".equals(before.getStatus())
                || ("SUPER_ADMIN".equals(nextRole) && "ACTIVE".equals(nextStatus))) {
            return;
        }
        List<UUID> activeSuperAdmins = mapper.lockActiveSuperAdminIds();
        if (activeSuperAdmins.size() <= 1) {
            throw conflict("系统必须保留至少一个激活的超级管理员");
        }
    }

    private void ensureUnique(UserMapper mapper, String username, String email, UUID excludeUserId) {
        if (mapper.countByUsername(username, excludeUserId) > 0) {
            throw validationConflict("username", "用户名已存在");
        }
        if (email != null && mapper.countByEmail(email, excludeUserId) > 0) {
            throw validationConflict("email", "邮箱已存在");
        }
    }

    private UserRow requireUser(UserMapper mapper, UUID userId) {
        UserRow row = mapper.findById(userId);
        if (row == null) {
            throw new BusinessException(ResponseCode.RESOURCE_NOT_FOUND, "用户不存在");
        }
        return row;
    }

    private String normalizeUsername(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("^[a-z0-9._-]{3,64}$")) {
            throw validation("username", "invalid_format", "用户名格式不正确");
        }
        return normalized;
    }

    private String normalizeEmail(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.length() > 254 || !normalized.contains("@")) {
            throw validation("email", "invalid_format", "邮箱格式不正确");
        }
        return normalized;
    }

    private String normalizeRole(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!ROLE_CODES.contains(normalized)) {
            throw validation("roleCode", "unsupported", "角色不受支持");
        }
        return normalized;
    }

    private String normalizeStatus(String value, Set<String> allowed) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw validation("status", "unsupported", "状态不受支持");
        }
        return normalized;
    }

    private String normalizeReason(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 500) {
            throw validation("reason", "invalid_length", "删除原因长度必须为 1-500");
        }
        return normalized;
    }

    private Map<String, Object> snapshot(UserRow row) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", row.getId());
        value.put("username", row.getUsername());
        value.put("email", row.getEmail());
        value.put("roleCode", row.getRoleCode());
        value.put("status", row.getStatus());
        value.put("version", row.getVersion());
        return value;
    }

    private BusinessException validation(String field, String reason, String message) {
        return new BusinessException(
                ResponseCode.VALIDATION_ERROR,
                message,
                List.of(new ErrorDetail(field, reason))
        );
    }

    private BusinessException validationConflict(String field, String message) {
        return new BusinessException(
                ResponseCode.RESOURCE_CONFLICT,
                message,
                List.of(new ErrorDetail(field, "duplicate"))
        );
    }

    private BusinessException conflict(String message) {
        return new BusinessException(ResponseCode.RESOURCE_CONFLICT, message);
    }

    private UserMapper mapper() {
        UserMapper mapper = mapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BusinessException(ResponseCode.DATABASE_UNAVAILABLE, "数据库暂不可用");
        }
        return mapper;
    }
}
