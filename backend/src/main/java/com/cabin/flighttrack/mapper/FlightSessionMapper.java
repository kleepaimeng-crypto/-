package com.cabin.flighttrack.mapper;

import com.cabin.flighttrack.entity.FlightSessionRow;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface FlightSessionMapper {
    @Select("""
            SELECT hashtextextended(#{streamKey}, 0)
            FROM (
                SELECT pg_advisory_xact_lock(hashtextextended(#{streamKey}, 0))
            ) locked
            """)
    long lockStream(@Param("streamKey") String streamKey);

    @Select("""
            SELECT
                id,
                source_system_code,
                source_device_code,
                host(source_host) AS source_host,
                flight_no,
                origin,
                destination,
                aircraft_registration_no,
                aircraft_model,
                airline_code,
                status,
                started_at,
                last_sample_at,
                last_received_at,
                ended_at,
                last_frame_count,
                latest_qar_sample_id
            FROM flight_session
            WHERE source_system_code = #{sourceSystemCode}
              AND source_device_code = #{sourceDeviceCode}
              AND source_host = CAST(#{sourceHost} AS inet)
              AND status = 'ACTIVE'
            FOR UPDATE
            """)
    FlightSessionRow findActiveForUpdate(
            @Param("sourceSystemCode") String sourceSystemCode,
            @Param("sourceDeviceCode") String sourceDeviceCode,
            @Param("sourceHost") String sourceHost
    );

    @Insert("""
            INSERT INTO flight_session (
                id,
                source_system_code,
                source_device_code,
                source_host,
                flight_no,
                origin,
                destination,
                aircraft_registration_no,
                aircraft_model,
                airline_code,
                status,
                started_at,
                last_sample_at,
                last_received_at,
                last_frame_count
            )
            VALUES (
                CAST(#{id} AS uuid),
                #{sourceSystemCode},
                #{sourceDeviceCode},
                CAST(#{sourceHost} AS inet),
                #{flightNo},
                #{origin},
                #{destination},
                #{aircraftRegistrationNo},
                #{aircraftModel,jdbcType=VARCHAR},
                #{airlineCode,jdbcType=VARCHAR},
                'ACTIVE',
                #{startedAt},
                #{lastSampleAt},
                #{lastReceivedAt},
                #{lastFrameCount}
            )
            """)
    int insert(FlightSessionRow row);

    @Update("""
            UPDATE flight_session
            SET status = 'FINISHED',
                ended_at = last_sample_at,
                updated_at = now()
            WHERE id = #{sessionId}
              AND status = 'ACTIVE'
            """)
    int finish(@Param("sessionId") UUID sessionId);

    @Update("""
            UPDATE flight_session
            SET latest_qar_sample_id = (
                    SELECT id
                    FROM qar_sample
                    WHERE record_id = #{recordId}
                ),
                last_sample_at = #{sampleAt},
                last_received_at = #{receivedAt},
                last_frame_count = #{frameCount},
                updated_at = now()
            WHERE id = #{sessionId}
              AND (
                  last_sample_at < #{sampleAt}
                  OR (
                      last_sample_at = #{sampleAt}
                      AND last_frame_count <= #{frameCount}
                  )
              )
            """)
    int updateLatest(
            @Param("sessionId") UUID sessionId,
            @Param("recordId") UUID recordId,
            @Param("sampleAt") OffsetDateTime sampleAt,
            @Param("receivedAt") OffsetDateTime receivedAt,
            @Param("frameCount") long frameCount
    );
}
