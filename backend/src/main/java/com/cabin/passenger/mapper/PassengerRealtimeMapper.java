package com.cabin.passenger.mapper;

import com.cabin.passenger.entity.PassengerActivityRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PassengerRealtimeMapper {
    String ALL_IFE = """
            SELECT b.id, b.record_id, b.event_at, b.created_at, b.flight_no,
                   b.passenger_id, b.seat_no, b.cabin_class, b.behavior_type,
                   b.behavior_detail, 1 AS source_priority
            FROM ife_633_behavior b
            JOIN data_record r ON r.id = b.record_id
            WHERE r.is_deleted = false
            UNION ALL
            SELECT b.id, b.record_id, b.event_at, b.created_at, b.flight_no,
                   b.passenger_id, b.seat_no, b.cabin_class, b.behavior_type,
                   b.behavior_detail, 2 AS source_priority
            FROM ife_cockrell_behavior b
            JOIN data_record r ON r.id = b.record_id
            WHERE r.is_deleted = false
            """;

    @Select("""
            WITH all_ife AS (
            """ + ALL_IFE + """
            )
            SELECT flight_no
            FROM all_ife
            ORDER BY event_at DESC, created_at DESC, source_priority DESC, id DESC
            LIMIT 1
            """)
    String findCurrentFlightNo();

    @Select("""
            WITH all_ife AS (
            """ + ALL_IFE + """
            ), ranked AS (
                SELECT *, row_number() OVER (
                    PARTITION BY passenger_id
                    ORDER BY event_at DESC, created_at DESC, source_priority DESC, id DESC
                ) AS row_no
                FROM all_ife
                WHERE flight_no = #{flightNo}
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
            FROM ranked a
            LEFT JOIN LATERAL (
                SELECT t.throughput_mbps, t.bytes_count, t.window_end
                FROM traffic_record t
                JOIN data_record r ON r.id = t.record_id
                WHERE r.is_deleted = false
                  AND r.flight_no = #{flightNo}
                  AND t.seat_label = a.seat_no
                ORDER BY t.window_end DESC, t.created_at DESC, t.id DESC
                LIMIT 1
            ) traffic ON true
            WHERE a.row_no = 1
            """)
    List<PassengerActivityRow> findLatestActivities(@Param("flightNo") String flightNo);

    @Select("""
            WITH all_ife AS (
            """ + ALL_IFE + """
            ), ranked AS (
                SELECT passenger_id,
                       CASE
                           WHEN #{behaviorType} = 'MOVIE_PLAY' THEN behavior_detail ->> 'contentType'
                           ELSE behavior_detail ->> 'musicType'
                       END AS types_text,
                       row_number() OVER (
                           PARTITION BY passenger_id
                           ORDER BY event_at DESC, created_at DESC, source_priority DESC, id DESC
                       ) AS row_no
                FROM all_ife
                WHERE flight_no = #{flightNo}
                  AND behavior_type = #{behaviorType}
            )
            SELECT types_text
            FROM ranked
            WHERE row_no = 1
              AND types_text IS NOT NULL
              AND btrim(types_text) <> ''
            """)
    List<String> findLatestMediaTypes(
            @Param("flightNo") String flightNo,
            @Param("behaviorType") String behaviorType
    );
}
