package org.jsut.common;

/**
 * 统一接口返回结构。
 *
 * HTTP 状态码负责描述 HTTP 请求结果，例如：
 *
 * 200：请求成功
 * 400：请求参数错误
 * 401：未登录
 * 403：无权限
 * 404：资源不存在
 * 500：服务器内部异常
 *
 * code 是系统内部业务码，不直接等同于 HTTP Status。
 *
 * 例如正常请求：
 *
 * HTTP 200
 *
 * {
 *     "code": 0,
 *     "message": "success",
 *     "data": {...}
 * }
 *
 * 参数错误：
 *
 * HTTP 400
 *
 * {
 *     "code": 10001,
 *     "message": "参数错误",
 *     "data": null
 * }
 *
 * @param code    业务状态码
 * @param message 提示信息
 * @param data    返回数据
 */
public record ApiResponse<T>(
        int code,
        String message,
        T data
) {

    /**
     * 正常成功响应。
     *
     * 业务 code 统一使用 0 表示成功。
     */
    public static <T> ApiResponse<T> success(T data) {

        return new ApiResponse<>(
                0,
                "success",
                data
        );
    }

    /**
     * 自定义成功消息。
     */
    public static <T> ApiResponse<T> success(
            String message,
            T data
    ) {

        return new ApiResponse<>(
                0,
                message,
                data
        );
    }

    /**
     * 错误响应。
     *
     * 注意：
     * 这里只负责创建 JSON Body，
     * HTTP Status 由 GlobalExceptionHandler 设置。
     */
    public static <T> ApiResponse<T> error(
            int code,
            String message
    ) {

        return new ApiResponse<>(
                code,
                message,
                null
        );
    }
}