package org.jsut.modules.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
public record UserPasswordResetRequest(
        @Schema(description = "新初始密码，目标用户下次登录必须修改", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank @Size(min = 8, max = 128) String new_password,
        @NotNull @Min(0) Integer version) {
    @Override public String toString() { return "UserPasswordResetRequest[redacted]"; }
}
