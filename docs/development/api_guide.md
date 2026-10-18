# 接口文档与开发约定

## 在线文档

启动后端后访问：

- Swagger UI：http://localhost:8080/swagger-ui/index.html
- OpenAPI JSON：http://localhost:8080/v3/api-docs

文档由 springdoc-openapi 3.1.1 根据 Controller、DTO 和校验注解生成，
与当前 Spring Boot 4.1.1 工程配套。新增接口并重新启动后即可在页面看到，
无需手工维护另一份接口清单。仅扫描 common、modules 中的已实现接口。
官方入门：https://springdoc.org/getting-started.html

页面展示接口分组、用途、请求方法、路径、参数、JSON结构、字段解释和返回结构，支持 Try it out 实际发请求。
Swagger UI 的调试会访问真实配置的数据库，执行改密和登出也会产生真实效果。

1. 展开“用户认证 → 账号密码登录”，点击 Try it out。
2. 填写平台账号密码并 Execute，复制响应 data.access_token。
3. 点击 Authorize，粘贴 token，不手写 Bearer 前缀。
4. 展开“查询当前登录用户”并执行，验证授权；登出后旧 token 会失效。

同一份 OpenAPI JSON 可以导入 Apifox/Postman：使用链接导入 http://localhost:8080/v3/api-docs。
API_DOCS_ENABLED=false 可同时关闭 JSON 文档和 Swagger UI，重启生效。

## 本次已实现

| 方法 | 路径 | 用途 | 登录要求 |
|---|---|---|---|
| POST | /user/auth/login | 账号密码登录，返回8小时有效token | 无 |
| GET | /user/me | 当前用户、角色和权限 | Bearer token |
| POST | /user/auth/logout | 撤销当前token | 格式有效的Bearer token；重复撤销成功 |
| PUT | /user/me/password | 校验原密码、更新密码、撤销全部会话 | Bearer token |
| GET | /system/health | HTTP服务存活检查 | 无 |

用户管理的分页、详情、新建、编辑、状态、软删除、密码重置和分配角色也已实现，
另有 /user/team/options、/user/role/options，详见 [用户管理](../user/README.md)。
角色定义及班组增删改仍是后续功能。
人工说明 [登录与初始化](../user/login.md) 负责初始化步骤、业务规则和联调流程，在线文档负责参数和响应结构。
项目与子项目接口见 [项目模块](../project/README.md)。
SOP文字草稿、步骤、发布/停用及负责人交接见 [SOP模块](../sop/README.md)，附件与使用关联接口尚未开放。

## 统一格式

JSON 请求使用 Content-Type: application/json；密码不放 URL 参数。
认证请求使用 Authorization: Bearer <access_token>。

成功：HTTP 200，响应 {"code":200,"message":"成功","data":...}。
失败：HTTP状态和code一致，响应 {"code":401,"message":"账号或密码错误","data":null}。

| 状态 | 含义 | 前端处理 |
|---|---|---|
| 400 | 参数、原密码或JSON错误 | 展示后端message |
| 401 | 登录失败或会话失效 | 登录失败留在登录页；其他请求清理会话并回到登录页 |
| 403 | 权限不足或必须先改密 | 保留会话，提示或进入改密页面 |
| 404 | 资源/接口不存在 | 展示提示，不伪造成功 |
| 409 | 重复数据或并发冲突 | 提示刷新后重试 |
| 429 | 登录限流 | 稍后重试 |
| 500 | 服务端错误 | 展示通用提示，排查服务端日志 |

ID 使用字符串。时间字段输出 UTC ISO 8601。密码和 token 不记录到日志；不得给文档填写真实密码示例。
root 的 roles=["superuser"]、permissions=["*"]；普通用户无角色也允许登录。
must_change_password=true 时先进入改密页，成功后重新登录。

## 后续新增接口怎样形成文档

- Controller 使用 @Tag 标注业务模块，@Operation 描述接口作用、前提和副作用。
- 请求和返回 DTO 字段使用 @Schema，写清含义、单位、空值和示例；密码标为 writeOnly 和 password。
- 请求使用 @Valid 以及 @NotBlank、@Size 等真实校验，文档会同步读取约束。
- 受保护接口添加 @SecurityRequirement(name="bearerAuth")，但该注解仅影响文档，不会替代后端认证、权限检查。
- 不将内部实体、密码哈希、会话摘要或数据库异常作为返回模型。
- 为主要错误状态写明原因，继续使用 ApiResponse<T> 返回结构。
- 新增业务模块在 modules 下与 user 并列，更新 springdoc 扫描范围仅在包约定改变时需要。

当前用户认证接口和 DTO 就是可复用的注解示例；公共定义在 config/OpenApiConfig.java。
自动化测试验证 OpenAPI JSON、Swagger UI、认证方案和登录响应Schema。
