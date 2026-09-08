package org.jsut.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 全局配置。
 *
 * 目前主要负责两件事：
 *
 * 1. 注册 ApiInterceptor；
 * 2. 配置 Vue 前端跨域访问。
 */
@Configuration
public class WebConfig
        implements WebMvcConfigurer {

    /**
     * Spring 自动注入 ApiInterceptor。
     */
    private final ApiInterceptor apiInterceptor;

    /**
     * 使用构造函数注入。
     */
    public WebConfig(
            ApiInterceptor apiInterceptor
    ) {
        this.apiInterceptor = apiInterceptor;
    }

    /**
     * ==========================================================
     * 注册接口拦截器
     * ==========================================================
     */
    @Override
    public void addInterceptors(
            InterceptorRegistry registry
    ) {

        registry
                /*
                 * 注册自己写的 ApiInterceptor。
                 */
                .addInterceptor(apiInterceptor)

                /*
                 * 所有 /api/** 地址都进行拦截。
                 *
                 * 例如：
                 *
                 * /api/v1/test
                 * /api/v1/detect
                 * /api/v1/station
                 */
                .addPathPatterns("/api/**")

                /*
                 * 以下接口不经过 ApiInterceptor。
                 *
                 * health 一般用于健康检查，
                 * login 一般登录前还没有 Token，
                 * 所以需要放行。
                 */
                .excludePathPatterns(
                        "/api/v1/system/health",
                        "/api/v1/login"
                );
    }

    /**
     * ==========================================================
     * Vue 跨域配置
     * ==========================================================
     *
     * Vue 开发环境一般是：
     *
     * http://localhost:5173
     *
     * Spring Boot 默认：
     *
     * http://localhost:8080
     *
     * 两个端口不同，
     * 浏览器认为属于不同 Origin，
     * 所以开发阶段需要允许跨域。
     */
    @Override
    public void addCorsMappings(
            CorsRegistry registry
    ) {

        registry
                /*
                 * 所有 /api/** 接口允许跨域。
                 */
                .addMapping("/api/**")

                /*
                 * 当前只允许 Vue 开发服务器访问。
                 *
                 * 正式发布以后如果 Vue 和后台同域，
                 * 可以调整。
                 */
                .allowedOrigins(
                        "http://localhost:5173"
                )

                /*
                 * 允许的 HTTP 方法。
                 */
                .allowedMethods(
                        "GET",
                        "POST",
                        "PUT",
                        "DELETE",
                        "OPTIONS"
                )

                /*
                 * 允许客户端携带任意请求 Header。
                 *
                 * 例如：
                 *
                 * Authorization
                 * X-Station-Id
                 * X-Request-Id
                 */
                .allowedHeaders("*")

                /*
                 * 默认情况下浏览器不能读取所有响应 Header。
                 *
                 * 这里明确允许 Vue 获取：
                 *
                 * X-Request-Id
                 * X-Server
                 */
                .exposedHeaders(
                        "X-Request-Id",
                        "X-Server"
                )

                /*
                 * 是否允许携带 Cookie / Credentials。
                 */
                .allowCredentials(true)

                /*
                 * 浏览器预检请求缓存时间，单位秒。
                 */
                .maxAge(3600);
    }
}