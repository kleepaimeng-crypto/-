package com.cabin.data.mapper;

import com.cabin.data.dto.DataRecordQuery;
import com.cabin.data.dto.MetadataUpdateRequest;
import com.cabin.data.entity.AnnotationRow;
import com.cabin.data.entity.DataRecordDetailRow;
import com.cabin.data.entity.DataRecordListRow;
import com.cabin.data.entity.ExportRawPayloadRow;
import com.cabin.data.entity.OptionRow;
import com.cabin.data.entity.TagAssignmentRow;
import com.cabin.data.entity.TagRow;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DataRecordMapper {
    List<OptionRow> findDataTypeOptions();
    List<OptionRow> findAirlineOptions();
    List<String> findAircraftModels();
    List<String> findAircraftRegistrations();
    List<String> findSourceDeviceCodes();
    List<String> findAirports();
    List<TagRow> findEnabledTags();
    long countDataRecords(@Param("query") DataRecordQuery query);
    List<DataRecordListRow> findDataRecordPage(@Param("query") DataRecordQuery query);
    List<ExportRawPayloadRow> findExportRawPayloadsByIds(@Param("recordIds") List<UUID> recordIds);
    List<TagAssignmentRow> findTagsForRecords(@Param("recordIds") List<UUID> recordIds);

    DataRecordDetailRow findDetail(
            @Param("recordId") UUID recordId,
            @Param("includeDeleted") boolean includeDeleted
    );

    List<TagRow> findTagsForRecord(@Param("recordId") UUID recordId);
    List<AnnotationRow> findAnnotationsForRecord(@Param("recordId") UUID recordId);

    int updateMetadata(
            @Param("recordId") UUID recordId,
            @Param("payload") MetadataUpdateRequest payload,
            @Param("expectedVersion") int expectedVersion
    );

    @SuppressWarnings("MybatisXMapperMethodInspection")
    Map<String, Object> findQarSummary(@Param("recordId") UUID recordId);

    @SuppressWarnings("MybatisXMapperMethodInspection")
    Map<String, Object> findTaskSummary(@Param("recordId") UUID recordId);

    @SuppressWarnings("MybatisXMapperMethodInspection")
    Map<String, Object> findTrafficSummary(@Param("recordId") UUID recordId);

    @SuppressWarnings("MybatisXMapperMethodInspection")
    Map<String, Object> findSessionSummary(@Param("recordId") UUID recordId);

    @SuppressWarnings("MybatisXMapperMethodInspection")
    Map<String, Object> findSmartWindowSummary(@Param("recordId") UUID recordId);

    @SuppressWarnings("MybatisXMapperMethodInspection")
    Map<String, Object> findIfe633Summary(@Param("recordId") UUID recordId);

    @SuppressWarnings("MybatisXMapperMethodInspection")
    Map<String, Object> findIfeCockrellSummary(@Param("recordId") UUID recordId);
}
