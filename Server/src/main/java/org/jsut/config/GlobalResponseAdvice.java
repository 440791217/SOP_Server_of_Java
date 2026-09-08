package org.jsut.config;

import org.jsut.common.ApiResponse;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Controller 正常响应统一包装。
 *
 * 例如 Controller：
 *
 * @GetMapping("/status")
 * public Map<String, Object> status() {
 *     return Map.of("running", true);
 * }
 *
 * Controller 原始返回：
 *
 * {
 *     "running": true
 * }
 *
 * 经过本类以后变成：
 *
 * {
 *     "code": 0,
 *     "message": "success",
 *     "data": {
 *         "running": true
 *     }
 * }
 *
 * HTTP Status 不在这里设置。
 *
 * Controller 正常执行时，Spring 默认 HTTP Status 为 200。
 * 如果出现异常，则由 GlobalExceptionHandler 决定
 * HTTP 400、401、403、500 等状态。
 */
@RestControllerAdvice
public class GlobalResponseAdvice
        implements ResponseBodyAdvice<Object> {

    /**
     * 是否需要执行 beforeBodyWrite。
     *
     * true：
     * 所有 REST Controller 返回结果都参与统一包装。
     */
    @Override
    public boolean supports(
            MethodParameter returnType,
            Class<? extends HttpMessageConverter<?>> converterType
    ) {

        return true;
    }

    /**
     * Controller 数据真正写入 HTTP Response 前执行。
     */
    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response
    ) {

        /*
         * 如果已经是 ApiResponse，
         * 就说明已经被手动包装过。
         *
         * 不再重复：
         *
         * ApiResponse<ApiResponse<Object>>
         */
        if (body instanceof ApiResponse<?>) {
            return body;
        }

        /*
         * 正常 Controller 返回值统一包装。
         *
         * HTTP 状态仍然保持 Controller 原本的状态，
         * 一般情况下就是 HTTP 200。
         */
        return ApiResponse.success(body);
    }
}