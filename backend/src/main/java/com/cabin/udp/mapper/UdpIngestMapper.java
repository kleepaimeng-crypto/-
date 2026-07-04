package com.cabin.udp.mapper;

import com.cabin.udp.entity.DataRecord;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UdpIngestMapper {
    @Insert("""
            INSERT INTO data_record (
                id,
                data_type_code,
                ingest_method,
                source_system_code,
                source_device_code,
                source_host,
                source_port,
                aircraft_registration_no,
                aircraft_model,
                airline_code,
                flight_no,
                origin,
                destination,
                sent_at,
                received_at,
                payload_count,
                raw_payload,
                raw_text,
                parse_status,
                parse_error
            )
            VALUES (
                CAST(#{id} AS uuid),
                #{dataTypeCode},
                'UDP',
                #{sourceSystemCode},
                #{sourceDeviceCode},
                CAST(#{sourceHost,jdbcType=VARCHAR} AS inet),
                #{sourcePort,jdbcType=INTEGER},
                #{aircraftRegistrationNo},
                #{aircraftModel,jdbcType=VARCHAR},
                #{airlineCode,jdbcType=VARCHAR},
                #{flightNo,jdbcType=VARCHAR},
                #{origin,jdbcType=VARCHAR},
                #{destination,jdbcType=VARCHAR},
                #{sentAt},
                #{receivedAt},
                #{payloadCount},
                CAST(#{rawPayload,jdbcType=VARCHAR} AS jsonb),
                #{rawText,jdbcType=LONGVARCHAR},
                #{parseStatus},
                #{parseError,jdbcType=LONGVARCHAR}
            )
            """)
    int insertDataRecord(DataRecord record);

    @Insert("""
            INSERT INTO qar_sample (
                record_id,
                sample_at,
                source_time_text,
                flight_no,
                origin,
                destination,
                air_ground_status,
                altitude_ft,
                computed_air_speed_kt,
                ground_speed_kt,
                latitude,
                longitude,
                track_angle_deg,
                heading_deg,
                pitch_deg,
                roll_deg,
                left_fuel_qty,
                right_fuel_qty,
                center_fuel_qty,
                low_fuel_warning,
                distance_to_go_nm,
                destination_eta_text,
                frame_count
            )
            VALUES (
                CAST(#{row.recordId} AS uuid),
                #{row.sampleAt},
                #{row.sourceTimeText},
                #{row.flightNo},
                #{row.origin},
                #{row.destination},
                #{row.airGroundStatus},
                #{row.altitudeFt,jdbcType=NUMERIC},
                #{row.computedAirSpeedKt,jdbcType=NUMERIC},
                #{row.groundSpeedKt,jdbcType=NUMERIC},
                #{row.latitude,jdbcType=DOUBLE},
                #{row.longitude,jdbcType=DOUBLE},
                #{row.trackAngleDeg,jdbcType=NUMERIC},
                #{row.headingDeg,jdbcType=NUMERIC},
                #{row.pitchDeg,jdbcType=NUMERIC},
                #{row.rollDeg,jdbcType=NUMERIC},
                #{row.leftFuelQty,jdbcType=NUMERIC},
                #{row.rightFuelQty,jdbcType=NUMERIC},
                #{row.centerFuelQty,jdbcType=NUMERIC},
                #{row.lowFuelWarning,jdbcType=BOOLEAN},
                #{row.distanceToGoNm,jdbcType=NUMERIC},
                #{row.destinationEtaText,jdbcType=VARCHAR},
                #{row.frameCount}
            )
            """)
    int insertQarSample(@Param("row") Map<String, Object> row);

    @Insert("""
            INSERT INTO simulation_task (
                record_id,
                task_id,
                flight_no,
                scenario_name,
                status,
                phase,
                terminal_count,
                started_at,
                ended_at,
                downlink_target_mbps,
                statistics_window_seconds,
                total_bytes,
                failure_reason,
                rerun_source_task_id,
                archived,
                snapshot_at
            )
            VALUES (
                CAST(#{row.recordId} AS uuid),
                #{row.taskId},
                #{row.flightNo},
                #{row.scenarioName},
                #{row.status},
                #{row.phase,jdbcType=VARCHAR},
                #{row.terminalCount},
                #{row.startedAt},
                #{row.endedAt,jdbcType=TIMESTAMP},
                #{row.downlinkTargetMbps,jdbcType=NUMERIC},
                #{row.statisticsWindowSeconds},
                #{row.totalBytes},
                #{row.failureReason,jdbcType=VARCHAR},
                #{row.rerunSourceTaskId,jdbcType=VARCHAR},
                #{row.archived},
                #{row.snapshotAt}
            )
            """)
    int insertSimulationTask(@Param("row") Map<String, Object> row);

    @Insert("""
            INSERT INTO traffic_record (
                record_id,
                item_no,
                task_id,
                window_start,
                window_end,
                terminal_id,
                display_terminal_id,
                seat_label,
                application,
                protocol,
                direction,
                bytes_count,
                packet_count,
                throughput_mbps,
                peak_mbps,
                record_status
            )
            VALUES (
                CAST(#{row.recordId} AS uuid),
                #{row.itemNo},
                #{row.taskId},
                #{row.windowStart},
                #{row.windowEnd},
                #{row.terminalId},
                #{row.displayTerminalId,jdbcType=VARCHAR},
                #{row.seatLabel,jdbcType=VARCHAR},
                #{row.application},
                #{row.protocol},
                #{row.direction},
                #{row.bytesCount},
                #{row.packetCount},
                #{row.throughputMbps},
                #{row.peakMbps},
                #{row.recordStatus}
            )
            """)
    int insertTrafficRecord(@Param("row") Map<String, Object> row);

    @Insert("""
            INSERT INTO session_summary (
                record_id,
                item_no,
                session_id,
                task_id,
                terminal_id,
                display_terminal_id,
                seat_label,
                application,
                protocol,
                started_at,
                duration_seconds,
                uplink_bytes,
                downlink_bytes,
                average_throughput_mbps,
                peak_throughput_mbps,
                status,
                snapshot_at
            )
            VALUES (
                CAST(#{row.recordId} AS uuid),
                #{row.itemNo},
                #{row.sessionId},
                #{row.taskId},
                #{row.terminalId},
                #{row.displayTerminalId,jdbcType=VARCHAR},
                #{row.seatLabel,jdbcType=VARCHAR},
                #{row.application},
                #{row.protocol},
                #{row.startedAt},
                #{row.durationSeconds},
                #{row.uplinkBytes},
                #{row.downlinkBytes},
                #{row.averageThroughputMbps},
                #{row.peakThroughputMbps},
                #{row.status},
                #{row.snapshotAt}
            )
            """)
    int insertSessionSummary(@Param("row") Map<String, Object> row);

    @Insert("""
            INSERT INTO smart_window_status (
                record_id,
                item_no,
                window_id,
                zone_id,
                brightness_level,
                connect_status,
                status,
                event_at
            )
            VALUES (
                CAST(#{row.recordId} AS uuid),
                #{row.itemNo},
                #{row.windowId},
                #{row.zoneId},
                #{row.brightnessLevel},
                #{row.connectStatus},
                #{row.status},
                #{row.eventAt}
            )
            """)
    int insertSmartWindowStatus(@Param("row") Map<String, Object> row);

    @Insert("""
            INSERT INTO ife_633_behavior (
                record_id,
                item_no,
                event_at,
                flight_no,
                pnr,
                seat_no,
                cabin_class,
                device_id,
                passenger_id,
                behavior_type,
                behavior_detail,
                error_code,
                error_description
            )
            VALUES (
                CAST(#{row.recordId} AS uuid),
                #{row.itemNo},
                #{row.eventAt},
                #{row.flightNo},
                #{row.pnr},
                #{row.seatNo},
                #{row.cabinClass},
                #{row.deviceId},
                #{row.passengerId},
                #{row.behaviorType},
                CAST(#{row.behaviorDetail} AS jsonb),
                #{row.errorCode,jdbcType=VARCHAR},
                #{row.errorDescription,jdbcType=VARCHAR}
            )
            """)
    int insertIfe633Behavior(@Param("row") Map<String, Object> row);

    @Insert("""
            INSERT INTO ife_cockrell_behavior (
                record_id,
                item_no,
                event_at,
                flight_no,
                pnr,
                seat_no,
                cabin_class,
                device_id,
                passenger_id,
                behavior_type,
                behavior_detail,
                cover_mime_type,
                cover_checksum,
                error_code,
                error_description
            )
            VALUES (
                CAST(#{row.recordId} AS uuid),
                #{row.itemNo},
                #{row.eventAt},
                #{row.flightNo},
                #{row.pnr},
                #{row.seatNo},
                #{row.cabinClass},
                #{row.deviceId},
                #{row.passengerId},
                #{row.behaviorType},
                CAST(#{row.behaviorDetail} AS jsonb),
                #{row.coverMimeType,jdbcType=VARCHAR},
                #{row.coverChecksum,jdbcType=VARCHAR},
                #{row.errorCode,jdbcType=VARCHAR},
                #{row.errorDescription,jdbcType=VARCHAR}
            )
            """)
    int insertIfeCockrellBehavior(@Param("row") Map<String, Object> row);
}

