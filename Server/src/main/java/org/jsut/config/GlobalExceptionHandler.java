package org.jsut.config;

import org.jsut.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器。
 *
 * 目的：
 *
 * Controller 和 Service 中不需要到处写 try/catch。
 *
 * Service 只需要正常抛异常，
 * 最后统一在这里转换成前端可以理解的 JSON。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 参数错误。
     *
     * 例如：
     *
     * throw new IllegalArgumentException("message不能为空");
     */
    @ExceptionHandler(
            IllegalArgumentException.class
    )
    public ApiResponse<Void> handleIllegalArgument(
            IllegalArgumentException e
    ) {

        return ApiResponse.error(
                400,
                e.getMessage()
        );
    }

    /**
     * 捕获其他没有单独处理的异常。
     *
     * 这是最后一道异常兜底。
     */
    @ExceptionHandler(
            Exception.class
    )
    public ApiResponse<Void> handleException(
            Exception e
    ) {

        /*
         * 当前开发阶段打印详细异常。
         *
         * 正式项目建议使用 Logger。
         */
        e.printStackTrace();

        return ApiResponse.error(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "服务器内部错误"
        );
    }
}