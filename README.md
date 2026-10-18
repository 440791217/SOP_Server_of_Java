# SOP_Server_of_Java

通用 SOP 平台 Java 后端，使用单 Maven 工程、Spring Boot 和 MyBatis。

工程位于 `Server/`，运行配置、目录约定和当前实现范围见 [Server/README.md](Server/README.md)。

模块说明与变更记录统一放在 [docs](docs/README.md)：[用户模块](docs/user/README.md)、[项目模块](docs/project/README.md)、[接口文档约定](docs/development/api_guide.md)。

[SOP后台](docs/sop/README.md)已提供文字草稿、步骤及版本接口；运行新版前需先完成SOP七表建表，具体顺序见模块说明。

业务模块统一位于 `org.jsut.modules`，当前用户、角色和班组归在 `modules/user`，不继续拆子模块。
