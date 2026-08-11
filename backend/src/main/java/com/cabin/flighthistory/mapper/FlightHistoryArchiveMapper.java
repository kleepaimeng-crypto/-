package com.cabin.flighthistory.mapper;

import com.cabin.flighthistory.entity.ArchiveJobRow;
import com.cabin.flighthistory.entity.FlightHistoryPointRow;
import com.cabin.flighthistory.entity.FlightHistorySessionRow;
import com.cabin.flighttrack.entity.FlightSessionRow;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface FlightHistoryArchiveMapper {
    @Insert("""
            INSERT INTO flight_history.archive_job (id, source_flight_session_id, finish_reason)
            VALUES (CAST(#{jobId} AS uuid), CAST(#{sessionId} AS uuid), #{finishReason})
            ON CONFLICT (source_flight_session_id) DO NOTHING
            """)
    int insertArchiveJob(
            @Param("jobId") UUID jobId,
            @Param("sessionId") UUID sessionId,
            @Param("finishReason") String finishReason
    );

    @Select("""
            SELECT id, source_flight_session_id, finish_reason, attempt_count
            FROM flight_history.archive_job
            WHERE status IN ('PENDING', 'FAILED')
              AND (next_retry_at IS NULL OR next_retry_at <= #{now})
            ORDER BY created_at, id
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """)
    ArchiveJobRow lockNextJob(@Param("now") OffsetDateTime now);

    @Update("""
            UPDATE flight_history.archive_job
            SET status = 'RUNNING', started_at = #{now}, updated_at = #{now}
            WHERE id = CAST(#{jobId} AS uuid)
            """)
    int markRunning(@Param("jobId") UUID jobId, @Param("now") OffsetDateTime now);

    @Update("""
            UPDATE flight_history.archive_job
            SET status = 'SUCCEEDED', completed_at = #{now}, next_retry_at = NULL,
                last_error = NULL, updated_at = #{now}
            WHERE id = CAST(#{jobId} AS uuid)
            """)
    int markSucceeded(@Param("jobId") UUID jobId, @Param("now") OffsetDateTime now);

    @Update("""
            UPDATE flight_history.archive_job
            SET status = 'FAILED', attempt_count = attempt_count + 1, next_retry_at = #{nextRetryAt},
                last_error = #{lastError}, updated_at = #{now}
            WHERE id = CAST(#{jobId} AS uuid)
            """)
    int markFailed(
            @Param("jobId") UUID jobId,
            @Param("nextRetryAt") OffsetDateTime nextRetryAt,
            @Param("lastError") String lastError,
            @Param("now") OffsetDateTime now
    );

    @Select("""
            SELECT id, source_system_code, source_device_code, host(source_host) AS source_host,
                   flight_no, origin, destination, aircraft_registration_no, aircraft_model, airline_code,
                   status, started_at, last_sample_at, last_received_at, ended_at, last_frame_count,
                   latest_qar_sample_id
            FROM public.flight_session
            WHERE id = CAST(#{sessionId} AS uuid) AND status = 'FINISHED'
            """)
    FlightSessionRow findFinishedSourceSession(@Param("sessionId") UUID sessionId);

    @Select("""
            SELECT id, source_system_code, source_device_code, host(source_host) AS source_host,
                   flight_no, origin, destination, aircraft_registration_no, aircraft_model, airline_code,
                   status, started_at, last_sample_at, last_received_at, ended_at, last_frame_count,
                   latest_qar_sample_id
            FROM public.flight_session
            WHERE status = 'ACTIVE'
            ORDER BY last_received_at
            """)
    List<FlightSessionRow> findActiveSessions();

    @Select("""
            SELECT q.id AS source_qar_sample_id, q.sample_at, q.source_time_text, q.frame_count,
                   q.air_ground_status, q.latitude, q.longitude, q.altitude_ft, q.ground_speed_kt,
                   q.computed_air_speed_kt, q.track_angle_deg, q.heading_deg, q.pitch_deg, q.roll_deg,
                   q.distance_to_go_nm, q.destination_eta_text
            FROM public.qar_sample q
            WHERE q.flight_session_id = CAST(#{sessionId} AS uuid)
              AND q.sample_at >= #{from}
            ORDER BY q.sample_at, q.frame_count, q.id
            """)
    List<FlightHistoryPointRow> findSourcePointsSince(
            @Param("sessionId") UUID sessionId,
            @Param("from") OffsetDateTime from
    );

    @Select("""
            SELECT id FROM flight_history.flight_session_archive
            WHERE source_flight_session_id = CAST(#{sourceSessionId} AS uuid)
            """)
    UUID findArchiveId(@Param("sourceSessionId") UUID sourceSessionId);

    @Insert("""
            INSERT INTO flight_history.flight_session_archive (
                id, source_flight_session_id, source_system_code, source_device_code, source_host,
                flight_no, origin, destination, aircraft_registration_no, aircraft_model, airline_code,
                started_at, ended_at, finish_reason
            ) VALUES (
                CAST(#{id} AS uuid), CAST(#{sourceFlightSessionId} AS uuid), #{sourceSystemCode},
                #{sourceDeviceCode}, CAST(#{sourceHost} AS inet), #{flightNo}, #{origin}, #{destination},
                #{aircraftRegistrationNo}, #{aircraftModel,jdbcType=VARCHAR}, #{airlineCode,jdbcType=VARCHAR},
                #{startedAt}, #{endedAt}, #{finishReason}
            ) ON CONFLICT (source_flight_session_id) DO NOTHING
            """)
    int insertArchiveSession(FlightHistorySessionRow row);

    @Insert("""
            INSERT INTO flight_history.qar_point_archive (
                session_id, source_qar_sample_id, sample_at, source_time_text, frame_count, air_ground_status,
                latitude, longitude, altitude_ft, ground_speed_kt, computed_air_speed_kt, track_angle_deg,
                heading_deg, pitch_deg, roll_deg, distance_to_go_nm, destination_eta_text
            )
            SELECT CAST(#{archiveId} AS uuid), q.id, q.sample_at, q.source_time_text, q.frame_count,
                   q.air_ground_status, q.latitude, q.longitude, q.altitude_ft, q.ground_speed_kt,
                   q.computed_air_speed_kt, q.track_angle_deg, q.heading_deg, q.pitch_deg, q.roll_deg,
                   q.distance_to_go_nm, q.destination_eta_text
            FROM public.qar_sample q
            WHERE q.flight_session_id = CAST(#{sourceSessionId} AS uuid)
            ORDER BY q.sample_at, q.frame_count, q.id
            ON CONFLICT (source_qar_sample_id) DO NOTHING
            """)
    int copySourcePoints(@Param("archiveId") UUID archiveId, @Param("sourceSessionId") UUID sourceSessionId);

    @Update("""
            UPDATE flight_history.flight_session_archive
            SET point_count = (
                SELECT count(*) FROM flight_history.qar_point_archive WHERE session_id = CAST(#{archiveId} AS uuid)
            )
            WHERE id = CAST(#{archiveId} AS uuid)
            """)
    int updatePointCount(@Param("archiveId") UUID archiveId);
}
