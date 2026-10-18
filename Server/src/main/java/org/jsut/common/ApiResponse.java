package org.jsut.common;

import io.swagger.v3.oas.annotations.media.Schema;

/** 成功 code=200；错误 code 与 HTTP 状态一致。 */
public record ApiResponse<T>(
        @Schema(description = "业务结果码，成功200，错误与HTTP状态一致", example = "200") int code,
        @Schema(description = "结果说明", example = "成功") String message,
        @Schema(description = "接口数据；失败时为空") T data) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "成功", data);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(200, message, data);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
