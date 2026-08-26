package com.cabin.data.mapper;

import com.cabin.data.entity.ExportJobRow;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ExportJobMapper {
    int insert(
            @Param("id") UUID id,
            @Param("dataTypeCode") String dataTypeCode,
            @Param("filterSnapshot") String filterSnapshot,
            @Param("requestedBy") UUID requestedBy
    );

    int markRunning(@Param("id") UUID id, @Param("requestedBy") UUID requestedBy);

    int complete(
            @Param("id") UUID id,
            @Param("requestedBy") UUID requestedBy,
            @Param("status") String status,
            @Param("fileName") String fileName,
            @Param("storagePath") String storagePath,
            @Param("fileSize") long fileSize,
            @Param("totalRows") int totalRows,
            @Param("successRows") int successRows,
            @Param("failedRows") int failedRows
    );

    int fail(@Param("id") UUID id, @Param("requestedBy") UUID requestedBy, @Param("errorMessage") String errorMessage);

    int deleteById(@Param("id") UUID id, @Param("requestedBy") UUID requestedBy);

    ExportJobRow findById(@Param("id") UUID id, @Param("requestedBy") UUID requestedBy);

    List<ExportJobRow> findPage(
            @Param("requestedBy") UUID requestedBy,
            @Param("pageSize") int pageSize,
            @Param("offset") int offset
    );

    long countByRequestedBy(@Param("requestedBy") UUID requestedBy);
}
