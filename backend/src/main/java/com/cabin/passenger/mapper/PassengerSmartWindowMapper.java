package com.cabin.passenger.mapper;

import com.cabin.passenger.entity.SmartWindowRow;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PassengerSmartWindowMapper {
    @Select("""
            SELECT s.record_id
            FROM smart_window_status s
            JOIN data_record r ON r.id = s.record_id
            WHERE r.is_deleted = false
              AND r.data_type_code = 'SMART_WINDOW_STATUS'
            GROUP BY s.record_id, r.received_at
            HAVING count(*) = 116
               AND count(DISTINCT s.window_id) = 116
               AND min(s.window_id) = 1
               AND max(s.window_id) = 116
               AND bool_and(s.window_id BETWEEN 1 AND 116)
            ORDER BY max(s.event_at) DESC, r.received_at DESC, s.record_id DESC
            LIMIT 1
            """)
    UUID findLatestCompleteSnapshotRecordId();

    @Select("""
            SELECT
                window_id,
                zone_id,
                brightness_level,
                connect_status AS connected,
                status,
                event_at AS updated_at
            FROM smart_window_status
            WHERE record_id = #{recordId}
            ORDER BY window_id
            """)
    List<SmartWindowRow> findSnapshotWindows(@Param("recordId") UUID recordId);
}
