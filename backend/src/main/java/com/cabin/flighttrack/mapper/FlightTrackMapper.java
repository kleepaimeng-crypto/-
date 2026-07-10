package com.cabin.flighttrack.mapper;

import com.cabin.flighttrack.entity.FlightTrackPointRow;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FlightTrackMapper {
    @Select("""
            SELECT
                fs.id AS flight_session_id,
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
            FROM flight_session fs
            JOIN qar_sample q ON q.id = fs.latest_qar_sample_id
            JOIN data_record r ON r.id = q.record_id
            WHERE fs.status = 'ACTIVE'
              AND r.is_deleted = false
            ORDER BY fs.last_received_at DESC
            LIMIT 1
            """)
    FlightTrackPointRow findActiveSessionLatest();

    @Select("""
            SELECT
                q.flight_session_id,
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
              AND q.flight_session_id = #{flightSessionId}
              AND q.sample_at >= #{windowStart}
              AND q.sample_at <= #{windowEnd}
              AND q.latitude IS NOT NULL
              AND q.longitude IS NOT NULL
            ORDER BY q.sample_at ASC, q.frame_count ASC, q.id ASC
            """)
    List<FlightTrackPointRow> findTrack(
            @Param("flightSessionId") UUID flightSessionId,
            @Param("windowStart") OffsetDateTime windowStart,
            @Param("windowEnd") OffsetDateTime windowEnd
    );
}
