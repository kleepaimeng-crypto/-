package com.cabin.passenger.mapper;

import com.cabin.passenger.entity.PassengerActivityRow;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PassengerRealtimeMapper {
    @Select("""
            SELECT id
            FROM flight_session
            WHERE status = 'ACTIVE'
            ORDER BY last_received_at DESC, last_sample_at DESC, id DESC
            LIMIT 1
            """)
    UUID findLatestActiveFlightSessionId();

    @Select("""
            <script>
            WITH requested_seat(seat_no) AS (
                VALUES
                <foreach collection="seatNos" item="seatNo" separator=",">
                    (#{seatNo})
                </foreach>
            )
            SELECT
                activity.passenger_id,
                activity.seat_no,
                activity.cabin_class,
                activity.behavior_type,
                activity.media_code,
                activity.title,
                activity.types_text,
                activity.action,
                activity.domain,
                activity.url,
                activity.traffic_bytes,
                activity.bandwidth_mbps,
                activity.window_bytes,
                activity.event_at,
                activity.bandwidth_updated_at,
                activity.source_record_id
            FROM requested_seat requested
            CROSS JOIN LATERAL (
                SELECT
                    b.passenger_id,
                    b.seat_no,
                    b.cabin_class,
                    b.behavior_type,
                    CASE b.behavior_type
                        WHEN 'MOVIE_PLAY' THEN b.behavior_detail ->> 'contentId'
                        WHEN 'MUSIC_PLAY' THEN b.behavior_detail ->> 'musicId'
                        ELSE NULL
                    END AS media_code,
                    CASE b.behavior_type
                        WHEN 'MOVIE_PLAY' THEN b.behavior_detail ->> 'contentName'
                        WHEN 'MUSIC_PLAY' THEN b.behavior_detail ->> 'musicName'
                        WHEN 'WAP_BROWSING' THEN b.behavior_detail ->> 'dstDomain'
                        ELSE NULL
                    END AS title,
                    CASE b.behavior_type
                        WHEN 'MOVIE_PLAY' THEN b.behavior_detail ->> 'contentType'
                        WHEN 'MUSIC_PLAY' THEN b.behavior_detail ->> 'musicType'
                        ELSE NULL
                    END AS types_text,
                    CASE b.behavior_type
                        WHEN 'MOVIE_PLAY' THEN b.behavior_detail ->> 'playAction'
                        WHEN 'MUSIC_PLAY' THEN b.behavior_detail ->> 'playAction'
                        ELSE NULL
                    END AS action,
                    b.behavior_detail ->> 'dstDomain' AS domain,
                    b.behavior_detail ->> 'url' AS url,
                    CASE
                        WHEN (b.behavior_detail ->> 'trafficBytes') ~ '^[0-9]+$'
                        THEN (b.behavior_detail ->> 'trafficBytes')::bigint
                        ELSE NULL
                    END AS traffic_bytes,
                    NULL::numeric AS bandwidth_mbps,
                    NULL::bigint AS window_bytes,
                    b.event_at,
                    r.received_at AS bandwidth_updated_at,
                    b.record_id AS source_record_id
                FROM ife_cockrell_behavior b
                JOIN data_record r ON r.id = b.record_id
                WHERE b.flight_session_id = CAST(#{flightSessionId} AS uuid)
                  AND b.seat_no = requested.seat_no
                  AND r.is_deleted = false
                ORDER BY b.event_at DESC, b.created_at DESC, b.id DESC
                LIMIT 1
            ) activity
            ORDER BY requested.seat_no
            </script>
            """)
    List<PassengerActivityRow> findLatestActivities(
            @Param("flightSessionId") UUID flightSessionId,
            @Param("seatNos") List<String> seatNos
    );
}
