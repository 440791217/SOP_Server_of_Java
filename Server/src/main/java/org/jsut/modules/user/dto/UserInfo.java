package org.jsut.modules.user.dto;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

public record UserInfo(
        @Schema(description = "用户ID，字符串避免前端整数精度丢失") String id,
        @Schema(description = "登录账号") String username,
        @Schema(description = "显示姓名") String real_name,
        @Schema(description = "头像地址，可为空") String avatar_url,
        @Schema(description = "是否为超级用户") boolean is_superuser,
        @Schema(description = "是否必须先改密") boolean must_change_password,
        @Schema(description = "启用角色编码；root为superuser；可为空数组") List<String> roles,
        @Schema(description = "有效权限编码；root返回包含*的数组") List<String> permissions) {
}
