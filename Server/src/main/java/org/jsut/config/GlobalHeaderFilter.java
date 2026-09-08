package org.jsut.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 全局 HTTP Filter。
 *
 * Filter 位于整个 Web 请求链路的最前面，
 * 很适合处理：
 *
 * 1. 统一请求头；
 * 2. 统一响应头；
 * 3. Request ID；
 * 4. 日志追踪；
 * 5. Token 前置处理；
 * 6. CORS 等。
 *
 * OncePerRequestFilter 可以保证一个请求只执行一次。
 */
@Component
public class GlobalHeaderFilter
        extends OncePerRequestFilter {

    /**
     * 每一个 HTTP 请求都会经过这里。
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        /*
         * =====================================================
         * 1. 获取客户端传过来的 Request ID
         * =====================================================
         *
         * 如果客户端自己已经生成了 X-Request-Id，
         * 就继续沿用；
         * 如果没有，就由服务器生成。
         */
        String requestId =
                request.getHeader("X-Request-Id");

        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        /*
         * =====================================================
         * 2. 把 requestId 放到当前 Request 中
         * =====================================================
         *
         * 后面的 Interceptor、Controller、Service
         * 都可以通过 request.getAttribute("requestId") 获取。
         */
        request.setAttribute(
                "requestId",
                requestId
        );

        /*
         * =====================================================
         * 3. 统一设置响应 Header
         * =====================================================
         *
         * 前端拿到响应后，可以知道：
         * 当前请求对应哪个 Request ID。
         */
        response.setHeader(
                "X-Request-Id",
                requestId
        );

        /*
         * 标识当前服务器名称。
         * 后期可以根据需要修改或者删除。
         */
        response.setHeader(
                "X-Server",
                "JSUT-SOP-Server"
        );

        /*
         * =====================================================
         * 4. 继续执行后面的过滤器、Interceptor、Controller
         * =====================================================
         *
         * 如果不调用 filterChain.doFilter()，
         * 请求会在这里直接结束。
         */
        filterChain.doFilter(
                request,
                response
        );
    }
}