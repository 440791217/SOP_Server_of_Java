package org.jsut.service;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 测试业务 Service。
 */
@Service
public class TestService {

    /**
     * GET 测试。
     */
    public Map<String, Object> hello() {

        Map<String, Object> result =
                new HashMap<>();

        result.put(
                "message",
                "Hello Spring Boot"
        );

        result.put(
                "server",
                "SOP Server"
        );

        return result;
    }

    /**
     * POST 测试。
     */
    public Map<String, Object> echo(
            String message
    ) {

        /*
         * 参数为空时直接抛异常。
         *
         * 不需要在这里 try/catch。
         *
         * GlobalExceptionHandler
         * 会自动转换成：
         *
         * HTTP 400
         *
         * {
         *     "code": 10001,
         *     "message": "message不能为空",
         *     "data": null
         * }
         */
        if (message == null ||
                message.isBlank()) {

            throw new IllegalArgumentException(
                    "message不能为空"
            );
        }

        Map<String, Object> result =
                new HashMap<>();

        result.put(
                "message",
                message
        );

        result.put(
                "received",
                true
        );

        return result;
    }
}