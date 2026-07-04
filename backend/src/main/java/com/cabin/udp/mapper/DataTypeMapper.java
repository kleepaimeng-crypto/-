package com.cabin.udp.mapper;

import com.cabin.udp.entity.DataTypeConfig;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DataTypeMapper {
    @Select("""
            SELECT code,
                   name,
                   message_type,
                   udp_port,
                   source_system_code,
                   source_device_code,
                   parser_code
            FROM data_type
            WHERE enabled = true
              AND udp_port IS NOT NULL
            ORDER BY udp_port
            """)
    List<DataTypeConfig> findEnabledUdpTypes();
}

