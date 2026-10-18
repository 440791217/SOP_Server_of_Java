package org.jsut.common;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/** 存活检查；不表示数据库连接或用户功能已经就绪。 */
@RestController
@RequestMapping("/system")
@Tag(name = "系统", description = "系统存活检查")
public class SystemController {
    @GetMapping("/health")
    @Operation(summary = "服务存活检查", description = "无需登录；仅表示HTTP服务运行，不代表数据库连接已验证。")
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.success(Map.of("status", "UP", "time", Instant.now().toString()));
    }
}
