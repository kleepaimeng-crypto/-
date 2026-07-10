package com.cabin.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserUpdateRequest(
        @Size(min = 3, max = 64, message = "用户名长度必须为 3–64 个字符")
        @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "用户名只能包含字母、数字、点、下划线和连字符")
        String username,

        @Email(message = "请输入有效邮箱")
        @Size(max = 254, message = "邮箱长度不能超过 254 个字符")
        String email,

        String roleCode,

        String status,

        @NotNull(message = "缺少用户版本")
        @Min(value = 1, message = "用户版本必须大于 0")
        Integer expectedVersion
) {
}
