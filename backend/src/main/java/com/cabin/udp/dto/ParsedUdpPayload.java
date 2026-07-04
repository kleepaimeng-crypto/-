package com.cabin.udp.dto;

import com.cabin.udp.entity.DataRecord;
import java.util.List;
import java.util.Map;

public record ParsedUdpPayload(
        DataRecord record,
        List<Map<String, Object>> businessRows
) {
}
