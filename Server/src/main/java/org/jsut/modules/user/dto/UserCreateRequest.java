package org.jsut.modules.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.util.List;

public record UserCreateRequest(
        @NotBlank @Size(min = 3, max = 64) String username,
        @Schema(description = "初始密码，8至128字符；首次登录必须修改", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank @Size(min = 8, max = 128) String password,
        @NotBlank @Size(max = 64) String real_name,
        @Size(max = 64) String card_number,
        @Schema(description = "班组ID字符串，可空") String team_id,
        @Size(max = 20) String phone,
        @Size(max = 255) String avatar_url,
        @NotNull @Min(0) @Max(2) Integer status,
        @Schema(description = "角色ID字符串列表，省略时默认member；显式指定需要分配角色权限")
        @Size(max = 100) List<@NotBlank String> role_ids) {
    @Override public String toString() { return "UserCreateRequest[redacted]"; }
}
