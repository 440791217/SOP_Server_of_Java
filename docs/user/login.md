# 用户登录接口

用户、角色、班组仍归在 modules/user，认证接口前缀为 /user/auth。
前端已接入登录、登出、当前用户和改密。数据库沿用已创建的7张用户表，不需要重复建表或重跑 seed.sql。

## 首次初始化平台 root

MySQL 的连接账号密码与平台 root 是两套独立账号。
先确认 seed.sql 已执行、sys_user 中存在 root。没有种子用户时初始化会失败，不自动创建或覆盖。

在 application.yml 已有的 sop 节点下配置（不要再添加一个同名 sop 根节点）：

```yaml
sop:
  bootstrap:
    root:
      enabled: true
      password: "填写你自己设置的8至128字符密码"
```

保留原有 sop.cors 配置。也可以使用 SOP_ROOT_INIT_ENABLED=true 和 SOP_ROOT_PASSWORD 环境变量。
启动后日志显示 root 初始化完成即成功：后端保存 Argon2id 哈希、启用 root，初始密码不写日志。
随后关闭 enabled，并删除 password 配置。已有密码时再次启动只跳过，不重置密码或重新启用用户。
不提供公开的 root 初始化 HTTP 接口。不要把真实密码提交进 Git。

## 登录

POST http://localhost:8080/user/auth/login

Content-Type: application/json

```json
{"account":"root","password":"你设置的平台root密码"}
```

密码必须放 JSON 请求体，不使用 URL 参数。账号会去首尾空白并转小写，密码不做 trim。
登录不校验原前端的固定验证码，也不实现 login_type 和 refresh_token。

成功 HTTP 200：

```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "access_token": "每次登录生成的随机token",
    "token_type": "Bearer",
    "expires_in": 28800,
    "must_change_password": false,
    "user": {
      "id": "1",
      "username": "root",
      "real_name": "超级管理员",
      "avatar_url": null,
      "is_superuser": true,
      "must_change_password": false,
      "roles": ["superuser"],
      "permissions": ["*"]
    }
  }
}
```

root 的 ["*"] 表示拥有所有功能权限；它不依赖角色关联表。
普通用户仅返回启用角色关联的角色与权限；无角色用户仍可登录。
不存在、错误密码、禁用、离职、软删除账号均统一返回 HTTP 401、code=401、账号或密码错误。
空参数/非法 JSON 返回400；单实例每个直连 IP 每分钟最多20次有效格式的登录尝试，超限429。
经反向代理部署时应另行配置可信代理和网关限流，当前不信任 X-Forwarded-For。

## token 验证、登出和本人改密

下列请求统一携带 Authorization: Bearer <access_token>。

| 方法 | 路径 | 功能 |
|---|---|---|
| GET | /user/me | 验证 token，返回当前用户及实时角色权限 |
| POST | /user/auth/logout | 撤销当前 token；重复撤销同一格式有效的 token 仍成功 |
| PUT | /user/me/password | JSON {"old_password":"原密码","new_password":"8至128字符的新密码"} |

密码修改后撤销该用户全部会话，必须重新登录；新密码不能与旧密码相同。
must_change_password=true 的账号只允许查看本人资料、修改本人密码和登出。
用户接口由 UserAuthInterceptor 校验会话；未来管理接口还必须增加各自的权限检查，
不能仅凭登录就开放用户列表、编辑或删除。

token 为32字节安全随机数的 URL-safe Base64 编码，8小时到期，数据库仅保存 SHA-256 摘要。
每次认证实时检查用户状态、软删除及会话撤销/到期状态。登录更新 last_login_at。
密码保存使用 Spring Security Crypto 的 Argon2id（内存19MiB、迭代2、并行度1）。
参考：[Spring 密码编码](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html)、
[OWASP 参数建议](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)。

## 测试和前端

mvn clean verify：使用独立 H2 内存库验证接口和 Mapper，不操作 sop_dev；
该测试不能替代 MySQL 8.4 实际运行验收。真实运行时使用你已配置好的 MySQL 连接。

前端已统一为新路径、JSON密码请求体、Bearer token和当前响应结构，并处理必须改密状态。
前端启动方式和范围见 [用户模块联调](../../../KedaAdmin/docs/user/README.md)。在线接口文档为 /swagger-ui/index.html，
OpenAPI JSON为 /v3/api-docs；可在Swagger UI或Apifox中验证。
