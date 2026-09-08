package org.jsut.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * API 拦截器。
 *
 * Interceptor 位于 Filter 之后、Controller 之前。
 *
 * 比较适合处理：
 *
 * 1. Token 校验；
 * 2. 用户权限；
 * 3. 工位 ID 校验；
 * 4. 客户端版本校验；
 * 5. API 日志；
 * 6. 接口访问控制。
 */
@Component
public class ApiInterceptor
        implements HandlerInterceptor {

    /**
     * Controller 执行之前调用。
     *
     * 返回 true：
     * 继续执行 Controller。
     *
     * 返回 false：
     * 直接终止当前请求。
     */
    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) {

        /*
         * 获取当前请求地址。
         */
        String uri =
                request.getRequestURI();

        /*
         * 获取 HTTP 方法。
         *
         * 例如：
         * GET、POST、PUT、DELETE。
         */
        String method =
                request.getMethod();

        /*
         * 获取客户端上传的工位编号。
         *
         * 后期多个工位时可以约定：
         *
         * X-Station-Id: station-01
         */
        String stationId =
                request.getHeader("X-Station-Id");

        /*
         * requestId 是 GlobalHeaderFilter
         * 前面已经生成并保存进去的。
         */
        String requestId =
                (String) request.getAttribute(
                        "requestId"
                );

        /*
         * 当前先简单输出日志。
         *
         * 后期建议换成 SLF4J Logger。
         */
        System.out.println(
                "[" + requestId + "] "
                        + method
                        + " "
                        + uri
                        + " station="
                        + stationId
        );

        /*
         * =====================================================
         * 后期可以在这里增加业务校验
         * =====================================================
         *
         * 例如：
         *
         * if (stationId == null) {
         *     response.setStatus(400);
         *     return false;
         * }
         *
         * 或者：
         *
         * 1. Token 校验
         * 2. Station ID 校验
         * 3. 权限判断
         * 4. 客户端版本判断
         */

        /*
         * 当前全部放行。
         */
        return true;
    }
}