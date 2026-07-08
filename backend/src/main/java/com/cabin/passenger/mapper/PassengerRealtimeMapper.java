package com.cabin.passenger.mapper;

import com.cabin.passenger.entity.PassengerActivityRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PassengerRealtimeMapper {
    @Select("""
            SELECT flight_no
            FROM data_record
            WHERE is_deleted = false
              AND data_type_code IN ('IFE_633_BEHAVIOR', 'IFE_COCKRELL_BEHAVIOR')
              AND flight_no IS NOT NULL
              AND flight_no <> ''
            ORDER BY received_at DESC
            LIMIT 1
            """)
    String findCurrentFlightNo();

    @Select("""
            WITH latest_633_event AS (
                SELECT event_at
                FROM ife_633_behavior
                WHERE flight_no = #{flightNo}
                ORDER BY event_at DESC
                LIMIT 1
            ), previous_633_event AS (
                SELECT event_at
                FROM ife_633_behavior
                WHERE flight_no = #{flightNo}
                  AND event_at < (SELECT event_at FROM latest_633_event)
                ORDER BY event_at DESC
                LIMIT 1
            ), latest_633 AS (
                SELECT DISTINCT ON (b.passenger_id)
                       b.id, b.record_id, b.event_at, b.created_at, b.flight_no,
                       b.passenger_id, b.seat_no, b.cabin_class, b.behavior_type,
                       b.behavior_detail, 1 AS source_priority
                FROM ife_633_behavior b
                JOIN data_record r ON r.id = b.record_id
                WHERE r.is_deleted = false
                  AND b.flight_no = #{flightNo}
                  AND b.event_at >= COALESCE(
                      (SELECT event_at FROM previous_633_event),
                      (SELECT event_at FROM latest_633_event)
                  )
                ORDER BY b.passenger_id, b.event_at DESC, b.created_at DESC, b.id DESC
            ), latest_cockrell_event AS (
                SELECT event_at
                FROM ife_cockrell_behavior
                WHERE flight_no = #{flightNo}
                ORDER BY event_at DESC
                LIMIT 1
            ), previous_cockrell_event AS (
                SELECT event_at
                FROM ife_cockrell_behavior
                WHERE flight_no = #{flightNo}
                  AND event_at < (SELECT event_at FROM latest_cockrell_event)
                ORDER BY event_at DESC
                LIMIT 1
            ), latest_cockrell AS (
                SELECT DISTINCT ON (b.passenger_id)
                       b.id, b.record_id, b.event_at, b.created_at, b.flight_no,
                       b.passenger_id, b.seat_no, b.cabin_class, b.behavior_type,
                       b.behavior_detail, 2 AS source_priority
                FROM ife_cockrell_behavior b
                JOIN data_record r ON r.id = b.record_id
                WHERE r.is_deleted = false
                  AND b.flight_no = #{flightNo}
                  AND b.event_at >= COALESCE(
                      (SELECT event_at FROM previous_cockrell_event),
                      (SELECT event_at FROM latest_cockrell_event)
                  )
                ORDER BY b.passenger_id, b.event_at DESC, b.created_at DESC, b.id DESC
            ), latest_passengers AS (
                SELECT DISTINCT ON (passenger_id) *
                FROM (
                    SELECT * FROM latest_633
                    UNION ALL
                    SELECT * FROM latest_cockrell
                ) latest_by_source
                ORDER BY passenger_id, event_at DESC, created_at DESC, source_priority DESC, id DESC
            ), current_traffic_task AS (
                SELECT t.task_id
                FROM simulation_task t
                JOIN data_record r ON r.id = t.record_id
                WHERE r.is_deleted = false
                  AND t.flight_no = #{flightNo}
                ORDER BY t.snapshot_at DESC, t.created_at DESC, t.id DESC
                LIMIT 1
            )
            SELECT
                a.passenger_id,
                a.seat_no,
                a.cabin_class,
                a.behavior_type,
                CASE a.behavior_type
                    WHEN 'MOVIE_PLAY' THEN a.behavior_detail ->> 'contentName'
                    WHEN 'MUSIC_PLAY' THEN a.behavior_detail ->> 'musicName'
                    WHEN 'WAP_BROWSING' THEN a.behavior_detail ->> 'dstDomain'
                    ELSE NULL
                END AS title,
                CASE a.behavior_type
                    WHEN 'MOVIE_PLAY' THEN a.behavior_detail ->> 'contentType'
                    WHEN 'MUSIC_PLAY' THEN a.behavior_detail ->> 'musicType'
                    ELSE NULL
                END AS types_text,
                CASE a.behavior_type
                    WHEN 'MOVIE_PLAY' THEN a.behavior_detail ->> 'playAction'
                    WHEN 'MUSIC_PLAY' THEN a.behavior_detail ->> 'playAction'
                    WHEN 'CAST_SCREEN' THEN a.behavior_detail ->> 'castAction'
                    ELSE NULL
                END AS action,
                a.behavior_detail ->> 'dstDomain' AS domain,
                a.behavior_detail ->> 'url' AS url,
                CASE
                    WHEN (a.behavior_detail ->> 'trafficBytes') ~ '^[0-9]+$'
                    THEN (a.behavior_detail ->> 'trafficBytes')::bigint
                    ELSE NULL
                END AS traffic_bytes,
                traffic.throughput_mbps AS bandwidth_mbps,
                traffic.bytes_count AS window_bytes,
                a.event_at,
                traffic.window_end AS bandwidth_updated_at,
                a.record_id AS source_record_id
            FROM latest_passengers a
            LEFT JOIN LATERAL (
                SELECT t.throughput_mbps, t.bytes_count, t.window_end
                FROM traffic_record t
                JOIN data_record r ON r.id = t.record_id
                WHERE r.is_deleted = false
                  AND t.task_id = (SELECT task_id FROM current_traffic_task)
                  AND t.seat_label = a.seat_no
                ORDER BY t.window_end DESC, t.created_at DESC, t.id DESC
                LIMIT 1
            ) traffic ON true
            """)
    List<PassengerActivityRow> findLatestActivities(@Param("flightNo") String flightNo);
}
