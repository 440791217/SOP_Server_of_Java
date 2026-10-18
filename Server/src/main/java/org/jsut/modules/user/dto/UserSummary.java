package org.jsut.modules.user.dto;

/** API 返回模型与数据库实体分离；ID 返回字符串以避免前端精度丢失。 */
public record UserSummary(String id, String username, String real_name, String card_number,
                          String team_id, String phone, String avatar_url, Integer status,
                          Boolean is_superuser, String created_at, String updated_at,
                          Integer version) {
}
