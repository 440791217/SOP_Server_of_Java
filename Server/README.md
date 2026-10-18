# SOP Server

单 Maven 工程，Java 17+、Spring Boot 4.1.1、MyBatis Starter 4.0.0、MySQL 8.4。
保留现有 Spring Boot 版本；持久化使用原生 MyBatis Mapper 接口 + XML，不使用 JPA 或 MyBatis-Plus。
MyBatis Starter 4.0 系列与 Spring Boot 4.0+ 的兼容关系见：
https://mybatis.org/spring-boot-starter/mybatis-spring-boot-autoconfigure/

## 目录

```text
src/main/java/org/jsut/
├── ServerApplication.java
├── common/                    # 公共返回、业务异常、存活检查
├── config/                    # 跨域、请求 ID、异常和响应处理
└── modules/
    └── user/                  # 用户、角色、班组统一在一个模块
        ├── controller/        # 登录、登出、当前用户和改密接口
        ├── service/           # 具体业务类，不强制 Service/Impl
        ├── mapper/            # @Mapper 接口
        ├── entity/            # 数据库内部模型
        └── dto/               # 对外数据结构
src/main/resources/
├── application.yml
└── mapper/user/UserMapper.xml
src/test/                      # 隔离的 H2 测试及 HTTP 验证
legacy/camera/                 # 原摄像头源码备份，不参与编译
```

新增项目、SOP 时在 modules 下并列增加 project、sop，不继续嵌套子模块。
角色、班组以后直接在 user 对应技术分层中增加类，不建 user/role 或 user/team。
删除了旧演示接口、全放行占位拦截器和四个空推理类。

## 当前完成程度

这是工程结构整理和 MyBatis 基础接入。已有内部 UserMapper → UserService 查询链路，
按主键查询未删除用户，转换为不含密码字段的 DTO，ID 输出字符串、时间输出 UTC。

已实现 POST /user/auth/login、POST /user/auth/logout、GET /user/me 和 PUT /user/me/password，
以及可选的一次性 root 密码初始化。会话有效期8小时，密码使用 Argon2id，数据库仅存 token 摘要。
root 自动拥有全部功能权限，普通用户读取启用角色关联的权限；无角色也允许登录。
完整初始化、请求示例和错误规则见 [登录与初始化](../docs/user/login.md)。
在线接口文档和开发约定见 [接口开发约定](../docs/development/api_guide.md)，
启动后访问 http://localhost:8080/swagger-ui/index.html，规范JSON为 /v3/api-docs。
GET /system/health 是存活检查，不表示数据库已就绪。
用户管理分页、增删改、状态、密码重置、角色分配及权限检查已实现并接入前端，
详见 [用户模块](../docs/user/README.md)。现有用户表结构足够，无需重新建表或初始化。
项目和子项目接口也已实现，见 [项目模块](../docs/project/README.md)。全部模块说明及变更记录由 [文档导航](../docs/README.md) 统一索引。

文字SOP基本资料、草稿、步骤及版本发布/停用已实现，见 [SOP模块](../docs/sop/README.md)。运行本批后端前需安装SOP七表，已有项目和成员服务新增了SOP引用保护；附件上传、使用关联及前端尚未接入。

统一 JSON 响应为 {code:200,message:"成功",data:...}，
错误响应使用真实 HTTP 状态，并返回同值 code。字符串及文件不自动包装。

## 本地配置和启动

先在 MySQL 中建立 sop_dev，执行 SOP_MySQL/user-module-v1 下的建表与种子脚本。
本工程设置 spring.sql.init.mode=never，不会自动建表、删表或重置 root。

VS Code：用“打开文件夹”打开本目录 Server，安装 Extension Pack for Java，
等待 Maven 项目导入完成。在“运行和调试”选择“启动 SOP 后端（sop_dev）”后按 F5，
依次输入 MySQL 用户名和密码。配置在 .vscode/launch.json，密码输入隐藏且不写入文件。
该配置按打开 Server 目录设计；如果打开的是父目录，请先切换到 Server 目录。

其他启动方式或自定义连接时，提供以下环境变量：
- DB_USERNAME：连接 MySQL 的数据库用户名（不是平台 root）
- DB_PASSWORD：该数据库用户的密码
- DB_URL：可选，默认连接 localhost:3306/sop_dev，使用 UTC 会话
- SERVER_PORT：可选，默认 8080
- CORS_ALLOWED_ORIGINS：可选，逗号分隔；默认 localhost/127.0.0.1 的 9527 端口
- SOP_ROOT_INIT_ENABLED：可选，默认 false；首次初始化平台 root 时开启
- SOP_ROOT_PASSWORD：初始化密码，8–128字符；初始化后移除，亦可在文件中配置，见 [登录与初始化](../docs/user/login.md)

不要将真实密码提交进 Git；不依赖 .env 自动加载。跨域按当前前端默认端口 9527 配置，
前端开发代理已指向 localhost:8080；运行前端 npm run dev 即可使用真实认证接口。

在 Server 目录使用 JDK 17+（建议 21 或 25）和 Maven 3.6.3+：
```text
mvn clean verify
mvn spring-boot:run
```

或 IDEA 导入本目录 pom.xml，重新加载 Maven 后运行 ServerApplication。
检查 http://localhost:8080/system/health。

测试通过属性覆盖使用 H2 内存数据库，不读写实际开发库，不依赖 DB_USERNAME、
DB_PASSWORD；验证 Spring 装配、Mapper XML、软删除过滤、ID/时间转换、HTTP 响应、
root 初始化、登录、会话、角色读取和密码修改。测试不会开启启动时 root 初始化。
这不能代替 MySQL 8.4 全量建表或生产环境并发验收。

RTSP 摄像头取图和连续 JPEG 预览已接入，需要配置环境变量 SOP_CAMERA_FFMPEG 指向 FFmpeg 可执行文件；详见 [摄像头部署说明](../../KedaAdmin/docs/resource/rtsp-camera.md)。
