package com.cabin.log.service;

import com.cabin.common.exception.BusinessException;
import com.cabin.common.response.PageResponse;
import com.cabin.common.response.ResponseCode;
import com.cabin.log.dto.AuditLogQuery;
import com.cabin.log.dto.AuditLogResponse;
import com.cabin.log.entity.AuditLogRow;
import com.cabin.log.mapper.AuditLogQueryMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class AuditLogQueryService {
    private final ObjectProvider<AuditLogQueryMapper> mapperProvider;
    private final ObjectMapper objectMapper;

    public AuditLogQueryService(
            ObjectProvider<AuditLogQueryMapper> mapperProvider,
            ObjectMapper objectMapper
    ) {
        this.mapperProvider = mapperProvider;
        this.objectMapper = objectMapper;
    }

    public PageResponse<AuditLogResponse> listAuditLogs(AuditLogQuery query) {
        AuditLogQueryMapper mapper = mapper();
        long total = mapper.countAuditLogs(query);
        List<AuditLogResponse> items = total == 0
                ? List.of()
                : mapper.findAuditLogPage(query).stream().map(this::toResponse).toList();
        return PageResponse.of(items, query.getPage(), query.getPageSize(), total);
    }

    private AuditLogResponse toResponse(AuditLogRow row) {
        return new AuditLogResponse(
                row.getId(),
                row.getAction(),
                row.getTargetType(),
                row.getTargetId(),
                row.getOperatorId(),
                row.getRequestIp(),
                jsonOrNull(row.getBeforeValue()),
                jsonOrNull(row.getAfterValue()),
                row.getResult(),
                row.getTraceId(),
                row.getCreatedAt()
        );
    }

    private JsonNode jsonOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ResponseCode.INTERNAL_ERROR, "审计日志读取失败");
        }
    }

    private AuditLogQueryMapper mapper() {
        AuditLogQueryMapper mapper = mapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BusinessException(ResponseCode.DATABASE_UNAVAILABLE, "数据库暂不可用");
        }
        return mapper;
    }
}
