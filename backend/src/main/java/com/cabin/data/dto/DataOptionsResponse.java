package com.cabin.data.dto;

import java.util.List;

public record DataOptionsResponse(
        List<CodeNameOption> dataTypes,
        List<CodeNameOption> airlines,
        List<String> aircraftModels,
        List<String> aircraftRegistrations,
        List<CodeNameOption> devices,
        List<String> airports,
        List<TagResponse> tags
) {
}
