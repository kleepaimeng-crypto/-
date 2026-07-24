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
            SELECT r.id
            FROM data_record r
            WHERE r.is_deleted = false
              AND r.data_type_code = 'SMART_WINDOW_STATUS'
              AND r.payload_count <= 118
            ORDER BY r.sent_at DESC, r.received_at DESC, r.id DESC
            LIMIT 1
            """)
    UUID findLatestSnapshotRecordId();

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
              AND window_id BETWEEN 1 AND 118
            ORDER BY window_id
            """)
    List<SmartWindowRow> findSnapshotWindows(@Param("recordId") UUID recordId);
}
