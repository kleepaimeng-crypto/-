package com.cabin.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserCreateRequest(
        @NotBlank(message = "请输入用户名")
        @Size(min = 3, max = 64, message = "用户名长度必须为 3–64 个字符")
        @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "用户名只能包含字母、数字、点、下划线和连字符")
        String username,

        @NotBlank(message = "请输入邮箱")
        @Email(message = "请输入有效邮箱")
        @Size(max = 254, message = "邮箱长度不能超过 254 个字符")
        String email,

        @NotNull(message = "请输入初始密码")
        @Size(min = 6, max = 72, message = "初始密码长度必须为 6–72 个字符")
        String password,

        @NotBlank(message = "请选择权限")
        String roleCode,

        @NotBlank(message = "请选择状态")
        String status
) {
}
