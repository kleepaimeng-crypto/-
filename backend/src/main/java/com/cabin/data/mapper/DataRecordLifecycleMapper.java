package com.cabin.data.mapper;

import com.cabin.data.entity.DataRecordLifecycleRow;
import com.cabin.data.entity.DataRecordListRow;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DataRecordLifecycleMapper {
    DataRecordLifecycleRow findLifecycle(@Param("recordId") UUID recordId);

    DataRecordListRow findListRowById(@Param("recordId") UUID recordId);

    int countExistingRecords(@Param("recordIds") List<UUID> recordIds);

    int countActiveRecords(@Param("recordIds") List<UUID> recordIds);

    int softDeleteRecord(
            @Param("recordId") UUID recordId,
            @Param("deletedBy") UUID deletedBy,
            @Param("reason") String reason,
            @Param("expectedVersion") int expectedVersion
    );

    int batchSoftDeleteRecords(
            @Param("recordIds") List<UUID> recordIds,
            @Param("deletedBy") UUID deletedBy,
            @Param("reason") String reason
    );

    int restoreRecord(@Param("recordId") UUID recordId);
}
