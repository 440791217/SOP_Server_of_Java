package org.jsut;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot 项目启动入口。
 *
 * @SpringBootApplication 是一个组合注解，主要包含：
 * 1. @Configuration：表示当前类是配置类；
 * 2. @EnableAutoConfiguration：开启 Spring Boot 自动配置；
 * 3. @ComponentScan：自动扫描当前包及其子包中的 Bean。
 *
 * 因此建议启动类放在 org.jsut 根包下，
 * controller、service、config 等都放在 org.jsut.xxx 子包中。
 */
@SpringBootApplication
public class ServerApplication {

    /**
     * Java 程序主入口。
     *
     * SpringApplication.run() 会完成：
     * 1. 创建 Spring 容器；
     * 2. 加载配置；
     * 3. 扫描 Controller、Service、Component 等组件；
     * 4. 启动内置 Web Server。
     */
    public static void main(String[] args) {
        SpringApplication.run(ServerApplication.class, args);
    }
}