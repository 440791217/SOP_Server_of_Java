# 后台文档导航

启动与配置见 [工程说明](../Server/README.md)。

| 模块 | 当前说明 | 变更记录 |
|---|---|---|
| 用户 | [用户接口与规则](user/README.md)、[登录与初始化](user/login.md) | [变更记录](user/changelog.md) |
| 项目 | [项目与子项目接口](project/README.md) | [变更记录](project/changelog.md) |
| SOP | [图文草稿、版本与项目关联接口](sop/README.md) | [变更记录](sop/changelog.md) |
| 公共开发 | [Swagger、OpenAPI与接口约定](development/api_guide.md) | — |
| 现场资源与任务 | [工位、工具设备与子项目任务](resource/README.md) | [变更记录](resource/changelog.md) |

后续业务按 docs/<模块>/ 归档，README.md 说明当前行为，复杂模块可拆 api.md、configuration.md。changelog.md 简要记录日期、变化及兼容影响；不复制Git逐行差异，也不把过期说明追加到当前说明末尾。修改功能时同步更新对应文档；根目录只保留导航和工程启动说明。

此仓库只维护后台职责。跨仓库链接按三个工程位于同一父目录组织；单独克隆时这些链接可能不可用，当前仓库内的文档与代码仍完整。
