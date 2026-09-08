package org.jsut.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 系统相关接口。
 *
 * 后面可以继续增加：
 *
 * 服务器状态
 * GPU 状态
 * ONNX 模型状态
 * 版本信息
 * 工位连接数量
 */
@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    /**
     * 系统健康检查接口。
     *
     * 请求：
     *
     * GET /api/v1/system/health
     *
     * 这个接口在 WebConfig 中设置为不经过 ApiInterceptor。
     *
     * 后期 Tauri 客户端可以用它判断服务器是否已经启动完成。
     */
    @GetMapping("/health")
    public Map<String, Object> health() {

        Map<String, Object> result =
                new HashMap<>();

        result.put(
                "status",
                "UP"
        );

        result.put(
                "time",
                LocalDateTime.now()
        );

        return result;
    }
}