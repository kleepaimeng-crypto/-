package com.cabin.data.mapper;

import com.cabin.data.entity.TagRow;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TagMapper {
    List<TagRow> findTags(@Param("includeDisabled") boolean includeDisabled);

    TagRow findById(@Param("tagId") UUID tagId);

    int insertTag(TagRow tag);

    int updateTag(
            @Param("tagId") UUID tagId,
            @Param("name") String name,
            @Param("color") String color,
            @Param("enabled") Boolean enabled,
            @Param("expectedVersion") int expectedVersion
    );

    int disableTag(@Param("tagId") UUID tagId);

    int countEnabledTags(@Param("tagIds") List<UUID> tagIds);

    int countTags(@Param("tagIds") List<UUID> tagIds);

    int countActiveRecords(@Param("recordIds") List<UUID> recordIds);

    int insertRecordTags(
            @Param("recordIds") List<UUID> recordIds,
            @Param("tagIds") List<UUID> tagIds,
            @Param("createdBy") UUID createdBy
    );

    int deleteRecordTags(
            @Param("recordIds") List<UUID> recordIds,
            @Param("tagIds") List<UUID> tagIds
    );
}
