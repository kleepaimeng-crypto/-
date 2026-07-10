package com.cabin.data.service;

import com.cabin.common.exception.BusinessException;
import com.cabin.common.response.ResponseCode;
import com.cabin.data.dto.CodeNameOption;
import com.cabin.data.dto.DataOptionsResponse;
import com.cabin.data.dto.TagResponse;
import com.cabin.data.entity.OptionRow;
import com.cabin.data.entity.TagRow;
import com.cabin.data.mapper.DataRecordMapper;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class DataOptionsService {
    private static final Map<String, String> DEVICE_NAMES = Map.of(
            "SIM-QAR", "QAR 模拟设备",
            "SIM-GROUND", "地面模拟设备",
            "SIM-WINDOW", "智能舷窗模拟设备",
            "SIM-IFE-633", "633 IFE 模拟设备",
            "SIM-IFE-COCKRELL", "科克瑞尔 IFE 模拟设备"
    );

    private final ObjectProvider<DataRecordMapper> mapperProvider;

    public DataOptionsService(ObjectProvider<DataRecordMapper> mapperProvider) {
        this.mapperProvider = mapperProvider;
    }

    public DataOptionsResponse getOptions() {
        DataRecordMapper mapper = mapper();
        return new DataOptionsResponse(
                mapper.findDataTypeOptions().stream().map(this::toOption).toList(),
                mapper.findAirlineOptions().stream().map(this::toOption).toList(),
                mapper.findAircraftModels(),
                mapper.findAircraftRegistrations(),
                mapper.findSourceDeviceCodes().stream().map(this::toDeviceOption).toList(),
                mapper.findAirports(),
                mapper.findEnabledTags().stream().map(this::toTag).toList()
        );
    }

    private CodeNameOption toOption(OptionRow row) {
        return new CodeNameOption(row.getCode(), row.getName());
    }

    private CodeNameOption toDeviceOption(String code) {
        return new CodeNameOption(code, DEVICE_NAMES.getOrDefault(code, code));
    }

    private TagResponse toTag(TagRow row) {
        return new TagResponse(row.getId(), row.getName(), row.getColor());
    }

    private DataRecordMapper mapper() {
        DataRecordMapper mapper = mapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BusinessException(ResponseCode.DATABASE_UNAVAILABLE, "数据库暂不可用");
        }
        return mapper;
    }
}
