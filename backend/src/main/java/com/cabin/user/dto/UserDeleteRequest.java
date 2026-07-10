package com.cabin.user.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UserDeleteRequest(
        @NotBlank(message = "请填写删除原因")
        @Size(max = 500, message = "删除原因不能超过 500 个字符")
        String reason,

        @NotNull(message = "缺少用户版本")
        @Min(value = 1, message = "用户版本必须大于 0")
        Integer expectedVersion
) {
}
