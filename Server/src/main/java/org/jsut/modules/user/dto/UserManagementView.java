package org.jsut.modules.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
public record UserManagementView(String id, String username, String real_name, String card_number,
        String team_id, @Schema(description = "班组显示名称，写入使用team_id") String shift_group,
        String phone, String avatar_url, Integer status, boolean is_superuser,
        String created_at, String updated_at, Integer version,
        List<UserOption> roles, @Schema(description = "有角色分配权限时返回ID列表，否则为null") List<String> role_ids) { }
