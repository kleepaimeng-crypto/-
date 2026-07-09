package com.cabin.flighttrack.mapper;

import com.cabin.flighttrack.entity.FlightTrackPointRow;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FlightTrackMapper {
    @Select("""
            SELECT
                s.record_id,
                s.sample_at,
                s.source_time_text,
                s.flight_no,
                s.origin,
                s.destination,
                s.aircraft_registration_no,
                s.aircraft_model,
                s.airline_code,
                s.altitude_ft,
                s.computed_air_speed_kt,
                s.ground_speed_kt,
                s.latitude,
                s.longitude,
                s.track_angle_deg,
                s.heading_deg,
                s.pitch_deg,
                s.roll_deg,
                s.distance_to_go_nm,
                s.destination_eta_text,
                s.frame_count
            FROM flight_track_current_state s
            JOIN data_record r ON r.id = s.record_id
            WHERE s.state_key = 'CURRENT'
              AND r.is_deleted = false
            LIMIT 1
            """)
    FlightTrackPointRow findCurrentState();

    @Select("""
            SELECT
                q.record_id,
                q.sample_at,
                q.source_time_text,
                q.flight_no,
                q.origin,
                q.destination,
                r.aircraft_registration_no,
                r.aircraft_model,
                r.airline_code,
                q.altitude_ft,
                q.computed_air_speed_kt,
                q.ground_speed_kt,
                q.latitude,
                q.longitude,
                q.track_angle_deg,
                q.heading_deg,
                q.pitch_deg,
                q.roll_deg,
                q.distance_to_go_nm,
                q.destination_eta_text,
                q.frame_count
            FROM qar_sample q
            JOIN data_record r ON r.id = q.record_id
            WHERE r.is_deleted = false
              AND q.sample_at >= #{cutoff}
              AND q.latitude IS NOT NULL
              AND q.longitude IS NOT NULL
            ORDER BY q.sample_at DESC, q.frame_count DESC, q.id DESC
            LIMIT 1
            """)
    FlightTrackPointRow findLatestQarState(@Param("cutoff") OffsetDateTime cutoff);

    @Select("""
            WITH ordered AS (
                SELECT
                    q.sample_at,
                    q.frame_count,
                    LAG(q.sample_at) OVER (
                        ORDER BY q.sample_at ASC, q.frame_count ASC, q.id ASC
                    ) AS previous_sample_at,
                    LAG(q.frame_count) OVER (
                        ORDER BY q.sample_at ASC, q.frame_count ASC, q.id ASC
                    ) AS previous_frame_count
                FROM qar_sample q
                JOIN data_record r ON r.id = q.record_id
                WHERE r.is_deleted = false
                  AND q.flight_no = #{flightNo}
                  AND q.sample_at >= #{windowStart}
                  AND q.sample_at <= #{windowEnd}
                  AND q.latitude IS NOT NULL
                  AND q.longitude IS NOT NULL
            )
            SELECT sample_at
            FROM ordered
            WHERE previous_sample_at IS NULL
               OR frame_count <= previous_frame_count
               OR sample_at - previous_sample_at > INTERVAL '5 minutes'
            ORDER BY sample_at DESC
            LIMIT 1
            """)
    OffsetDateTime findCurrentSegmentStart(
            @Param("flightNo") String flightNo,
            @Param("windowStart") OffsetDateTime windowStart,
            @Param("windowEnd") OffsetDateTime windowEnd
    );

    @Select("""
            SELECT
                q.record_id,
                q.sample_at,
                q.source_time_text,
                q.flight_no,
                q.origin,
                q.destination,
                r.aircraft_registration_no,
                r.aircraft_model,
                r.airline_code,
                q.altitude_ft,
                q.computed_air_speed_kt,
                q.ground_speed_kt,
                q.latitude,
                q.longitude,
                q.track_angle_deg,
                q.heading_deg,
                q.pitch_deg,
                q.roll_deg,
                q.distance_to_go_nm,
                q.destination_eta_text,
                q.frame_count
            FROM qar_sample q
            JOIN data_record r ON r.id = q.record_id
            WHERE r.is_deleted = false
              AND q.flight_no = #{flightNo}
              AND q.sample_at >= #{windowStart}
              AND q.sample_at <= #{windowEnd}
              AND q.latitude IS NOT NULL
              AND q.longitude IS NOT NULL
            ORDER BY q.sample_at ASC, q.frame_count ASC, q.id ASC
            """)
    List<FlightTrackPointRow> findTrack(
            @Param("flightNo") String flightNo,
            @Param("windowStart") OffsetDateTime windowStart,
            @Param("windowEnd") OffsetDateTime windowEnd
    );
}
