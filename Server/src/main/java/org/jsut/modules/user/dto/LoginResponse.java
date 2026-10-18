package org.jsut.modules.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record LoginResponse(
        @Schema(description = "随机会话token，后续请求放入Authorization: Bearer <token>") String access_token,
        @Schema(description = "认证类型", example = "Bearer") String token_type,
        @Schema(description = "有效期，单位秒", example = "28800") long expires_in,
        @Schema(description = "是否需要先修改初始密码") boolean must_change_password,
        @Schema(description = "当前用户资料及角色权限") UserInfo user) {
    @Override
    public String toString() {
        return "LoginResponse[redacted]";
    }
}
