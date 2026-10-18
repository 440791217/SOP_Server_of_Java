package org.jsut.modules.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.jsut.common.ApiResponse;
import org.jsut.modules.user.dto.ChangePasswordRequest;
import org.jsut.modules.user.dto.LoginRequest;
import org.jsut.modules.user.dto.LoginResponse;
import org.jsut.modules.user.dto.UserInfo;
import org.jsut.modules.user.service.AuthService;
import org.jsut.modules.user.service.LoginRateLimiter;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;

@RestController
@RequestMapping(value = "/user", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "用户认证", description = "账号密码登录、会话查询、登出和修改本人密码")
public class UserAuthController {
    private final AuthService service;
    private final LoginRateLimiter limiter;

    public UserAuthController(AuthService service, LoginRateLimiter limiter) {
        this.service = service;
        this.limiter = limiter;
    }

    @PostMapping(value = "/auth/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "账号密码登录", description = "JSON 请求体传账号和密码。返回8小时有效的 token。"
            + "账号不存在、密码错误、禁用、离职或删除均返回401；参数错误400；登录限流429。"
            + "must_change_password=true 时必须先修改密码。root 拥有全部功能权限。",
            responses = {
                @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "登录成功"),
                @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "参数错误", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ApiResponse.class))),
                @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "账号或密码错误", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ApiResponse.class))),
                @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "登录尝试过于频繁", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ApiResponse.class)))
            })
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest body,
                                            HttpServletRequest request) {
        limiter.check(request.getRemoteAddr());
        return ApiResponse.success(service.login(body));
    }

    @GetMapping("/me")
    @Operation(summary = "查询当前登录用户", description = "返回当前用户和实时角色权限；token过期、撤销或用户失效返回401。")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<UserInfo> me(@Parameter(hidden = true) @RequestAttribute("currentUser") UserInfo user) {
        return ApiResponse.success(user);
    }

    @PostMapping("/auth/logout")
    @Operation(summary = "退出登录", description = "撤销当前 token。重复退出同一格式有效的 token 仍成功；其他设备会话不受影响。")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<Void> logout(@Parameter(hidden = true)
                                    @RequestHeader(value = "Authorization", required = false) String header) {
        service.logout(AuthService.tokenFrom(header));
        return ApiResponse.success(null);
    }

    @PutMapping(value = "/me/password", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "修改本人密码", description = "校验原密码；新密码8至128字符且不能与原密码相同。"
            + "成功后撤销本人全部会话，必须重新登录；参数或原密码错误返回400。")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<Void> changePassword(
            @Parameter(hidden = true) @RequestHeader("Authorization") String header,
            @Valid @RequestBody ChangePasswordRequest body) {
        service.changePassword(AuthService.tokenFrom(header), body);
        return ApiResponse.success("密码已修改，请重新登录", null);
    }
}
