package org.jsut.modules.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

public record LoginRequest(
        @Schema(description = "登录账号，自动去首尾空白并转小写", example = "root")
        @NotBlank(message = "请输入账号") @Size(max = 64, message = "账号最长64个字符") String account,
        @Schema(description = "平台用户密码，保留原始空格；不是MySQL连接密码", format = "password",
                accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank(message = "请输入密码") @Size(max = 128, message = "密码最长128个字符") String password) {
    @Override
    public String toString() {
        return "LoginRequest[redacted]";
    }
}
