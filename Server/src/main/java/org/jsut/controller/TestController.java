package org.jsut.controller;

import org.jsut.dto.EchoRequest;
import org.jsut.service.TestService;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 测试 Controller。
 */
@RestController
@RequestMapping("/api/v1/test")
public class TestController {

    private final TestService testService;

    public TestController(
            TestService testService
    ) {
        this.testService = testService;
    }

    /**
     * GET 测试接口。
     */
    @GetMapping("/hello")
    public Map<String, Object> hello() {

        return testService.hello();
    }

    /**
     * POST 测试接口。
     */
    @PostMapping("/echo")
    public Map<String, Object> echo(
            @RequestBody EchoRequest request
    ) {

        return testService.echo(
                request.message()
        );
    }
}