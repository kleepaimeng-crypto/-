package com.cabin.log.service;

import com.cabin.common.exception.BusinessException;
import com.cabin.common.response.ResponseCode;
import com.cabin.common.trace.TraceContext;
import com.cabin.login.entity.AppUser;
import com.cabin.log.entity.AuditLog;
import com.cabin.log.mapper.AuditLogMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {
    private final ObjectProvider<AuditLogMapper> auditLogMapperProvider;
    private final ObjectMapper objectMapper;

    public AuditLogService(
            ObjectProvider<AuditLogMapper> auditLogMapperProvider,
            ObjectMapper objectMapper
    ) {
        this.auditLogMapperProvider = auditLogMapperProvider;
        this.objectMapper = objectMapper;
    }

    public void recordLoginSuccess(AppUser user, String requestIp) {
        Map<String, Object> afterValue = new LinkedHashMap<>();
        afterValue.put("username", user.getUsername());
        afterValue.put("userId", user.getId());
        afterValue.put("roleCode", user.getRoleCode());

        AuditLog entry = baseLoginEntry(user.getUsername(), requestIp, "SUCCESS");
        entry.setOperatorId(user.getId());
        entry.setAfterValue(toJson(afterValue));
        auditLogMapper().insert(entry);
    }

    public void recordLoginFailure(String username, String reason, String requestIp) {
        Map<String, Object> afterValue = new LinkedHashMap<>();
        afterValue.put("username", username);
        afterValue.put("reason", reason);

        AuditLog entry = baseLoginEntry(username, requestIp, "FAILURE");
        entry.setAfterValue(toJson(afterValue));
        auditLogMapper().insert(entry);
    }

    private AuditLog baseLoginEntry(String username, String requestIp, String result) {
        AuditLog entry = new AuditLog();
        entry.setAction("LOGIN");
        entry.setTargetType("AUTH");
        entry.setTargetId(username);
        entry.setRequestIp(blankToNull(requestIp));
        entry.setResult(result);
        entry.setTraceId(TraceContext.currentTraceId());
        return entry;
    }

    private AuditLogMapper auditLogMapper() {
        AuditLogMapper mapper = auditLogMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BusinessException(ResponseCode.DATABASE_UNAVAILABLE, "数据库暂不可用");
        }
        return mapper;
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Audit JSON serialization failed", exception);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
