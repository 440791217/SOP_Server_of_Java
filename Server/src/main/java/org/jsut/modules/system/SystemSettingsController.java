package org.jsut.modules.system;

import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import org.jsut.common.ApiResponse;
import org.jsut.modules.user.service.AuthService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/system/settings")
public class SystemSettingsController {
    private final SystemSettingsService service;

    public SystemSettingsController(SystemSettingsService service) {
        this.service = service;
    }

    public record Write(Map<String, Object> values, Map<String, Integer> versions) {}

    @GetMapping("/appearance")
    public ApiResponse<Map<String, Object>> appearance(HttpServletRequest request) {
        return ApiResponse.success(service.read(token(request)));
    }

    @PutMapping("/appearance")
    public ApiResponse<Map<String, Object>> appearance(HttpServletRequest request, @RequestBody Write body) {
        return ApiResponse.success(service.save(token(request), body.values(), body.versions()));
    }

    private String token(HttpServletRequest request) {
        return AuthService.tokenFrom(request.getHeader("Authorization"));
    }
}
