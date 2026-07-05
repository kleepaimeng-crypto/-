package com.cabin.data.mapper;

import com.cabin.data.entity.AnnotationRow;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AnnotationMapper {
    List<AnnotationRow> findForRecord(
            @Param("recordId") UUID recordId,
            @Param("includeDeleted") boolean includeDeleted
    );

    AnnotationRow findById(
            @Param("annotationId") UUID annotationId,
            @Param("includeDeleted") boolean includeDeleted
    );

    int insertAnnotation(
            @Param("id") UUID id,
            @Param("recordId") UUID recordId,
            @Param("content") String content,
            @Param("createdBy") UUID createdBy
    );

    int insertBatch(
            @Param("recordIds") List<UUID> recordIds,
            @Param("content") String content,
            @Param("createdBy") UUID createdBy
    );

    int updateAnnotation(
            @Param("annotationId") UUID annotationId,
            @Param("content") String content,
            @Param("expectedVersion") int expectedVersion
    );

    int softDeleteAnnotation(
            @Param("annotationId") UUID annotationId,
            @Param("deletedBy") UUID deletedBy,
            @Param("expectedVersion") int expectedVersion
    );
}
