package com.cabin.flighthistory.mapper;

import com.cabin.flighthistory.entity.FlightHistoryPointRow;
import com.cabin.flighthistory.entity.FlightHistorySessionRow;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FlightHistoryQueryMapper {
    @Select("""
            <script>
            SELECT count(*) FROM flight_history.flight_session_archive
            WHERE ended_at &gt;= #{endedFrom} AND ended_at &lt;= #{endedTo}
            <if test="flightNo != null">AND flight_no ILIKE CONCAT(#{flightNo}, '%')</if>
            <if test="origin != null">AND origin = #{origin}</if>
            <if test="destination != null">AND destination = #{destination}</if>
            <if test="aircraftRegistrationNo != null">AND aircraft_registration_no ILIKE CONCAT(#{aircraftRegistrationNo}, '%')</if>
            <if test="finishReason != null">AND finish_reason = #{finishReason}</if>
            </script>
            """)
    long countSessions(
            @Param("endedFrom") OffsetDateTime endedFrom, @Param("endedTo") OffsetDateTime endedTo,
            @Param("flightNo") String flightNo, @Param("origin") String origin,
            @Param("destination") String destination, @Param("aircraftRegistrationNo") String aircraftRegistrationNo,
            @Param("finishReason") String finishReason
    );

    @Select("""
            <script>
            SELECT id, source_flight_session_id, source_system_code, source_device_code, host(source_host) AS source_host,
                   flight_no, origin, destination, aircraft_registration_no, aircraft_model, airline_code,
                   started_at, ended_at, point_count, finish_reason, archived_at
            FROM flight_history.flight_session_archive
            WHERE ended_at &gt;= #{endedFrom} AND ended_at &lt;= #{endedTo}
            <if test="flightNo != null">AND flight_no ILIKE CONCAT(#{flightNo}, '%')</if>
            <if test="origin != null">AND origin = #{origin}</if>
            <if test="destination != null">AND destination = #{destination}</if>
            <if test="aircraftRegistrationNo != null">AND aircraft_registration_no ILIKE CONCAT(#{aircraftRegistrationNo}, '%')</if>
            <if test="finishReason != null">AND finish_reason = #{finishReason}</if>
            ORDER BY
            <choose>
              <when test="sortBy == 'startedAt'">started_at</when>
              <when test="sortBy == 'flightNo'">flight_no</when>
              <when test="sortBy == 'pointCount'">point_count</when>
              <otherwise>ended_at</otherwise>
            </choose>
            <choose><when test="sortDirection == 'asc'">ASC</when><otherwise>DESC</otherwise></choose>, id DESC
            LIMIT #{pageSize} OFFSET #{offset}
            </script>
            """)
    List<FlightHistorySessionRow> findSessions(
            @Param("endedFrom") OffsetDateTime endedFrom, @Param("endedTo") OffsetDateTime endedTo,
            @Param("flightNo") String flightNo, @Param("origin") String origin,
            @Param("destination") String destination, @Param("aircraftRegistrationNo") String aircraftRegistrationNo,
            @Param("finishReason") String finishReason, @Param("sortBy") String sortBy,
            @Param("sortDirection") String sortDirection, @Param("offset") int offset, @Param("pageSize") int pageSize
    );

    @Select("""
            SELECT id, source_flight_session_id, source_system_code, source_device_code, host(source_host) AS source_host,
                   flight_no, origin, destination, aircraft_registration_no, aircraft_model, airline_code,
                   started_at, ended_at, point_count, finish_reason, archived_at
            FROM flight_history.flight_session_archive WHERE id = CAST(#{sessionId} AS uuid)
            """)
    FlightHistorySessionRow findSession(@Param("sessionId") UUID sessionId);

    @Select("""
            SELECT source_qar_sample_id, sample_at, source_time_text, frame_count, air_ground_status,
                   latitude, longitude, altitude_ft, ground_speed_kt, computed_air_speed_kt, track_angle_deg,
                   heading_deg, pitch_deg, roll_deg, distance_to_go_nm, destination_eta_text
            FROM flight_history.qar_point_archive
            WHERE session_id = CAST(#{sessionId} AS uuid) AND sample_at >= #{from} AND sample_at <= #{to}
            ORDER BY sample_at, frame_count, source_qar_sample_id
            """)
    List<FlightHistoryPointRow> findTrack(
            @Param("sessionId") UUID sessionId, @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to
    );
}
