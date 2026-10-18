package org.jsut.modules.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

public record ChangePasswordRequest(
        @Schema(description = "原密码", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank(message = "请输入原密码") @Size(max = 128) String old_password,
        @Schema(description = "新密码，8至128字符，不能与原密码相同", format = "password",
                accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank(message = "请输入新密码")
        @Size(min = 8, max = 128, message = "新密码长度为8至128个字符") String new_password) {
    @Override
    public String toString() {
        return "ChangePasswordRequest[redacted]";
    }
}
