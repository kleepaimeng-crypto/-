package com.cabin.log.mapper;

import com.cabin.log.entity.AuditLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditLogMapper {
    @Insert("""
            INSERT INTO audit_log (
                action,
                target_type,
                target_id,
                operator_id,
                request_ip,
                before_value,
                after_value,
                result,
                trace_id
            )
            VALUES (
                #{action},
                #{targetType},
                #{targetId},
                CAST(#{operatorId,jdbcType=OTHER} AS uuid),
                CAST(#{requestIp,jdbcType=VARCHAR} AS inet),
                CAST(#{beforeValue,jdbcType=VARCHAR} AS jsonb),
                CAST(#{afterValue,jdbcType=VARCHAR} AS jsonb),
                #{result},
                #{traceId}
            )
            """)
    int insert(AuditLog entry);
}
