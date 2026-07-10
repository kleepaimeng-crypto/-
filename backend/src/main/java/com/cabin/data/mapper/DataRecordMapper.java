package com.cabin.data.mapper;

import com.cabin.data.dto.DataRecordQuery;
import com.cabin.data.dto.MetadataUpdateRequest;
import com.cabin.data.entity.AnnotationRow;
import com.cabin.data.entity.DataRecordDetailRow;
import com.cabin.data.entity.DataRecordListRow;
import com.cabin.data.entity.OptionRow;
import com.cabin.data.entity.TagAssignmentRow;
import com.cabin.data.entity.TagRow;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DataRecordMapper {
    @Select("""
            SELECT code, name
            FROM data_type
            WHERE enabled = true
            ORDER BY sort_order, code
            """)
    List<OptionRow> findDataTypeOptions();

    @Select("""
            SELECT DISTINCT airline_code AS code, airline_code AS name
            FROM data_record
            WHERE is_deleted = false
              AND airline_code IS NOT NULL
            ORDER BY airline_code
            """)
    List<OptionRow> findAirlineOptions();

    @Select("""
            SELECT DISTINCT aircraft_model
            FROM data_record
            WHERE is_deleted = false
              AND aircraft_model IS NOT NULL
            ORDER BY aircraft_model
            """)
    List<String> findAircraftModels();

    @Select("""
            SELECT DISTINCT aircraft_registration_no
            FROM data_record
            WHERE is_deleted = false
            ORDER BY aircraft_registration_no
            """)
    List<String> findAircraftRegistrations();

    @Select("""
            SELECT DISTINCT source_device_code
            FROM data_type
            WHERE enabled = true
              AND source_device_code IS NOT NULL
            ORDER BY source_device_code
            """)
    List<String> findSourceDeviceCodes();

    @Select("""
            SELECT airport
            FROM (
                SELECT DISTINCT origin AS airport
                FROM data_record
                WHERE is_deleted = false AND origin IS NOT NULL
                UNION
                SELECT DISTINCT destination AS airport
                FROM data_record
                WHERE is_deleted = false AND destination IS NOT NULL
            ) airports
            ORDER BY airport
            """)
    List<String> findAirports();

    @Select("""
            SELECT id, name, color
            FROM tag
            WHERE enabled = true
            ORDER BY name
            """)
    List<TagRow> findEnabledTags();

    @Select("""
            <script>
            SELECT count(*)
            FROM data_record r
            JOIN data_type dt ON dt.code = r.data_type_code
            <where>
                <if test="query.includeDeleted == false">
                    r.is_deleted = false
                </if>
                <if test="query.airlineCode != null">
                    AND r.airline_code = #{query.airlineCode}
                </if>
                <if test="query.flightNo != null">
                    AND upper(r.flight_no) = #{query.flightNo}
                </if>
                <if test="query.sourceDeviceCode != null">
                    AND r.source_device_code = #{query.sourceDeviceCode}
                </if>
                <if test="query.aircraftModel != null">
                    AND r.aircraft_model = #{query.aircraftModel}
                </if>
                <if test="query.origin != null">
                    AND r.origin = #{query.origin}
                </if>
                <if test="query.destination != null">
                    AND r.destination = #{query.destination}
                </if>
                <if test="query.dataTypeCode != null">
                    AND r.data_type_code = #{query.dataTypeCode}
                </if>
                <if test="query.receivedFrom != null">
                    AND r.received_at &gt;= #{query.receivedFrom}
                </if>
                <if test="query.receivedTo != null">
                    AND r.received_at &lt; #{query.receivedTo}
                </if>
                <if test="query.tagIds != null and query.tagIds.size > 0">
                    AND r.id IN (
                        SELECT rt.record_id
                        FROM data_record_tag rt
                        WHERE rt.tag_id IN
                        <foreach collection="query.tagIds" item="tagId" open="(" close=")" separator=",">
                            #{tagId}
                        </foreach>
                        GROUP BY rt.record_id
                        HAVING count(DISTINCT rt.tag_id) = #{query.tagCount}
                    )
                </if>
            </where>
            </script>
            """)
    long countDataRecords(@Param("query") DataRecordQuery query);

    @Select("""
            <script>
            SELECT
                r.id,
                r.aircraft_registration_no,
                r.aircraft_model,
                r.airline_code,
                r.flight_no,
                r.origin,
                r.destination,
                r.source_device_code,
                r.data_type_code,
                dt.name AS data_type_name,
                r.sent_at,
                r.received_at,
                r.payload_count,
                r.parse_status,
                r.is_deleted AS deleted,
                r.version
            FROM data_record r
            JOIN data_type dt ON dt.code = r.data_type_code
            <where>
                <if test="query.includeDeleted == false">
                    r.is_deleted = false
                </if>
                <if test="query.airlineCode != null">
                    AND r.airline_code = #{query.airlineCode}
                </if>
                <if test="query.flightNo != null">
                    AND upper(r.flight_no) = #{query.flightNo}
                </if>
                <if test="query.sourceDeviceCode != null">
                    AND r.source_device_code = #{query.sourceDeviceCode}
                </if>
                <if test="query.aircraftModel != null">
                    AND r.aircraft_model = #{query.aircraftModel}
                </if>
                <if test="query.origin != null">
                    AND r.origin = #{query.origin}
                </if>
                <if test="query.destination != null">
                    AND r.destination = #{query.destination}
                </if>
                <if test="query.dataTypeCode != null">
                    AND r.data_type_code = #{query.dataTypeCode}
                </if>
                <if test="query.receivedFrom != null">
                    AND r.received_at &gt;= #{query.receivedFrom}
                </if>
                <if test="query.receivedTo != null">
                    AND r.received_at &lt; #{query.receivedTo}
                </if>
                <if test="query.tagIds != null and query.tagIds.size > 0">
                    AND r.id IN (
                        SELECT rt.record_id
                        FROM data_record_tag rt
                        WHERE rt.tag_id IN
                        <foreach collection="query.tagIds" item="tagId" open="(" close=")" separator=",">
                            #{tagId}
                        </foreach>
                        GROUP BY rt.record_id
                        HAVING count(DISTINCT rt.tag_id) = #{query.tagCount}
                    )
                </if>
            </where>
            ORDER BY ${query.orderBySql} ${query.sortDirectionSql}, r.id DESC
            LIMIT #{query.pageSize}
            OFFSET #{query.offset}
            </script>
            """)
    List<DataRecordListRow> findDataRecordPage(@Param("query") DataRecordQuery query);

    @Select("""
            <script>
            SELECT
                rt.record_id,
                t.id AS tag_id,
                t.name,
                t.color
            FROM data_record_tag rt
            JOIN tag t ON t.id = rt.tag_id
            WHERE rt.record_id IN
            <foreach collection="recordIds" item="recordId" open="(" close=")" separator=",">
                #{recordId}
            </foreach>
            ORDER BY t.name
            </script>
            """)
    List<TagAssignmentRow> findTagsForRecords(@Param("recordIds") List<UUID> recordIds);

    @Select("""
            SELECT
                r.id,
                r.aircraft_registration_no,
                r.aircraft_model,
                r.airline_code,
                r.flight_no,
                r.origin,
                r.destination,
                r.data_type_code,
                r.source_device_code,
                r.source_system_code,
                r.sent_at,
                r.received_at,
                r.parse_status,
                r.parse_error,
                r.version,
                r.raw_payload::text AS raw_payload,
                r.raw_text,
                r.is_deleted AS deleted,
                r.deleted_at
            FROM data_record r
            WHERE r.id = #{recordId}
              AND (#{includeDeleted} = true OR r.is_deleted = false)
            """)
    DataRecordDetailRow findDetail(
            @Param("recordId") UUID recordId,
            @Param("includeDeleted") boolean includeDeleted
    );

    @Select("""
            SELECT t.id, t.name, t.color
            FROM data_record_tag rt
            JOIN tag t ON t.id = rt.tag_id
            WHERE rt.record_id = #{recordId}
            ORDER BY t.name
            """)
    List<TagRow> findTagsForRecord(@Param("recordId") UUID recordId);

    @Select("""
            SELECT id, content, created_at, updated_at, version
            FROM data_annotation
            WHERE record_id = #{recordId}
              AND is_deleted = false
            ORDER BY created_at
            """)
    List<AnnotationRow> findAnnotationsForRecord(@Param("recordId") UUID recordId);

    @Update("""
            UPDATE data_record
            SET aircraft_registration_no = #{payload.aircraftRegistrationNo},
                aircraft_model = #{payload.aircraftModel,jdbcType=VARCHAR},
                airline_code = #{payload.airlineCode,jdbcType=VARCHAR},
                flight_no = #{payload.flightNo,jdbcType=VARCHAR},
                origin = #{payload.origin,jdbcType=VARCHAR},
                destination = #{payload.destination,jdbcType=VARCHAR},
                source_device_code = #{payload.sourceDeviceCode},
                version = version + 1
            WHERE id = #{recordId}
              AND is_deleted = false
              AND version = #{expectedVersion}
            """)
    int updateMetadata(
            @Param("recordId") UUID recordId,
            @Param("payload") MetadataUpdateRequest payload,
            @Param("expectedVersion") int expectedVersion
    );

    @Select("""
            SELECT
                sample_at AS "sampleAt",
                latitude,
                longitude,
                altitude_ft AS "altitudeFt",
                ground_speed_kt AS "groundSpeedKt",
                computed_air_speed_kt AS "computedAirSpeedKt",
                frame_count AS "frameCount"
            FROM qar_sample
            WHERE record_id = #{recordId}
            """)
    Map<String, Object> findQarSummary(@Param("recordId") UUID recordId);

    @Select("""
            SELECT
                task_id AS "taskId",
                status,
                phase,
                terminal_count AS "terminalCount",
                started_at AS "startedAt",
                ended_at AS "endedAt",
                snapshot_at AS "snapshotAt"
            FROM simulation_task
            WHERE record_id = #{recordId}
            """)
    Map<String, Object> findTaskSummary(@Param("recordId") UUID recordId);

    @Select("""
            SELECT
                count(*) AS "itemCount",
                min(window_start) AS "windowStart",
                max(window_end) AS "windowEnd",
                sum(bytes_count) AS "bytesCount",
                max(peak_mbps) AS "peakMbps"
            FROM traffic_record
            WHERE record_id = #{recordId}
            """)
    Map<String, Object> findTrafficSummary(@Param("recordId") UUID recordId);

    @Select("""
            SELECT
                count(*) AS "itemCount",
                min(started_at) AS "firstStartedAt",
                max(snapshot_at) AS "snapshotAt",
                sum(uplink_bytes) AS "uplinkBytes",
                sum(downlink_bytes) AS "downlinkBytes",
                max(peak_throughput_mbps) AS "peakThroughputMbps"
            FROM session_summary
            WHERE record_id = #{recordId}
            """)
    Map<String, Object> findSessionSummary(@Param("recordId") UUID recordId);

    @Select("""
            SELECT
                count(*) AS "itemCount",
                count(*) FILTER (WHERE connect_status = true) AS "connectedCount",
                count(*) FILTER (WHERE status = 'FAULT') AS "faultCount",
                max(event_at) AS "eventAt"
            FROM smart_window_status
            WHERE record_id = #{recordId}
            """)
    Map<String, Object> findSmartWindowSummary(@Param("recordId") UUID recordId);

    @Select("""
            SELECT
                count(*) AS "itemCount",
                min(event_at) AS "firstEventAt",
                max(event_at) AS "lastEventAt",
                count(DISTINCT passenger_id) AS "passengerCount",
                count(DISTINCT behavior_type) AS "behaviorTypeCount"
            FROM ife_633_behavior
            WHERE record_id = #{recordId}
            """)
    Map<String, Object> findIfe633Summary(@Param("recordId") UUID recordId);

    @Select("""
            SELECT
                count(*) AS "itemCount",
                min(event_at) AS "firstEventAt",
                max(event_at) AS "lastEventAt",
                count(DISTINCT passenger_id) AS "passengerCount",
                count(DISTINCT behavior_type) AS "behaviorTypeCount"
            FROM ife_cockrell_behavior
            WHERE record_id = #{recordId}
            """)
    Map<String, Object> findIfeCockrellSummary(@Param("recordId") UUID recordId);
}
