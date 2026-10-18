package org.jsut.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(title = "SOP 平台接口", version = "v1",
        description = "仅包含已实现接口。先调用登录，再在 Authorize 中粘贴 access_token（无需手写 Bearer）。"
                + "所有 JSON 接口使用 code/message/data，HTTP 状态与错误 code 一致。"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer",
        bearerFormat = "opaque token", description = "登录返回的 access_token，有效期8小时")
public class OpenApiConfig {
}
