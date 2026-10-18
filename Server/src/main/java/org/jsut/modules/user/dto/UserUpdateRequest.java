package org.jsut.modules.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.util.List;

public record UserUpdateRequest(
        @NotBlank @Size(max = 64) String real_name,
        @Size(max = 64) String card_number, String team_id,
        @Size(max = 20) String phone, @Size(max = 255) String avatar_url,
        @Schema(description = "省略保持角色不变；空数组移除全部角色；改变角色需要额外权限")
        @Size(max = 100) List<@NotBlank String> role_ids,
        @Schema(description = "读取用户时得到的version，冲突返回409") @NotNull @Min(0) Integer version) {
}
