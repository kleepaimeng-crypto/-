package com.cabin.log.mapper;

import com.cabin.log.dto.AuditLogQuery;
import com.cabin.log.entity.AuditLogRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuditLogQueryMapper {
    long countAuditLogs(@Param("query") AuditLogQuery query);

    List<AuditLogRow> findAuditLogPage(@Param("query") AuditLogQuery query);
}
