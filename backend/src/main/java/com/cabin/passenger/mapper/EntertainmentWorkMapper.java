package com.cabin.passenger.mapper;

import com.cabin.passenger.entity.EntertainmentWorkRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface EntertainmentWorkMapper {
    @Select("""
            SELECT
                w.id,
                w.work_code,
                w.category,
                w.title,
                w.summary,
                w.creator_name,
                w.collection_name,
                w.duration_seconds,
                w.release_year,
                w.language,
                w.region,
                w.sort_order,
                string_agg(g.genre, '/' ORDER BY g.position) AS genres_text
            FROM entertainment_work w
            JOIN entertainment_work_genre g ON g.work_id = w.id
            WHERE w.enabled = true
            GROUP BY
                w.id,
                w.work_code,
                w.category,
                w.title,
                w.summary,
                w.creator_name,
                w.collection_name,
                w.duration_seconds,
                w.release_year,
                w.language,
                w.region,
                w.sort_order
            ORDER BY w.category, w.sort_order, w.work_code
            """)
    List<EntertainmentWorkRow> findEnabledWorks();
}
