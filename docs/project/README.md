# 项目与子项目：后台接口

已实现：项目和子项目增删改查、状态、归档恢复、排序，以及成员管理、主项目/子项目负责人转移和操作记录。SOP关联、现场调度属于后续批次。

## 启动与访问

已建项目表的库先执行一次 [归档恢复迁移](../../../SOP_MySQL/project-module-v1/migrations/001_archive_restore.sql)，不重跑schema.sql。新库先建表再迁移。保持现有数据库配置，重启 Java 后端（默认8080）与前端（默认9527）。前端通过 /dev-api 代理后台；项目真实接口不走 Mock。

本批还需执行 [成员与记录迁移002](../../../SOP_MySQL/project-module-v1/migrations/002_members_and_activity.sql)。已有库按001→002顺序，只执行尚未执行的脚本；DDL不是整体事务，出错先核对，不要盲目重跑。应用不会自动迁移数据库。

新增文字SOP后台后，项目删除/成员查询与降级移除已检查SOP引用，运行最新版还需按 [SOP模块说明](../sop/README.md) 安装七张SOP表，再重启后端。SOP负责人先通过SOP owner接口交接，才能移除或降级该成员；成员响应新增owned_sops。项目曾有SOP关联时保留并归档，不能靠解绑后删除绕过历史保护。

登录root后进入项目详情，使用“项目成员”维护成员、“操作记录”查交接历史、“编辑→转移负责人”办理交接。主项目负责人普通编辑仍只读；子项目资料编辑也不再更换负责人。新建项目自动加入创建人及负责人；增加其他有效编辑成员后可选择为子项目负责人。

root 无需新增权限数据即可使用。普通用户要创建项目，可在正确数据库手动执行可重复的 SOP_MySQL/project-module-v1/permissions.sql，再由 root 在用户管理中分配“项目编辑人员”角色。脚本不会自动为任何人授权，应用启动不会运行此脚本。已有 user_admin 不会因此获得项目权限。

Swagger：http://localhost:8080/swagger-ui/index.html；OpenAPI：http://localhost:8080/v3/api-docs。标签为“项目与子项目”，已纳入springdoc路径范围。Apifox可用OpenAPI地址重新导入。除OPTIONS预检外，业务接口要求 Authorization: Bearer <token>；未改初始密码的账号不能访问。

## 接口

统一响应为 `{code:200,message:"success",data:...}`（message以公共响应实现为准）。错误使用实际HTTP状态：400参数错误、401未认证、403无权限、404不存在、409编号重复/版本冲突/状态冲突。ID输出字符串，时间UTC ISO8601；version为非负整数。

| 方法 | 路径 | 参数/用途 |
|---|---|---|
| GET | /project/list | page默认1、limit默认20且最多100、keyword编号/名称包含匹配、status可选0–3；返回items、total、can_create |
| GET | /project/owner/options | keyword可选；正常用户的id、username、real_name，最多50条；需要创建权限 |
| GET | /project/{id} | 项目详情，含负责人姓名、子项目数、版本及可操作标志 |
| POST | /project | code、name、owner_user_id必填；customer_name、description可空 |
| PUT | /project/{id} | name、version必填；customer_name、description可空；编号和负责人不改 |
| PUT | /project/{id}/status | status、version |
| DELETE | /project/{id} | JSON体version；仅未开始且没有有效子项目 |
| PUT | /project/{id}/restore | JSON体version；root或主项目负责人恢复归档前状态，联动恢复随其归档的子项目 |
| GET | /project/{projectId}/subproject/list | 全部有效子项目，按sort_order、id排序 |
| GET | /project/{projectId}/subproject/owner/options | 本项目正常编辑成员，keyword可选，最多50条 |
| GET | /project/{projectId}/subproject/{id} | 子项目详情，校验父子归属 |
| POST | /project/{projectId}/subproject | code、name、owner_user_id必填；description可空 |
| PUT | /project/{projectId}/subproject/{id} | name、owner_user_id、version必填；description可空；编号不改 |
| PUT | /project/{projectId}/subproject/{id}/status | status、version |
| DELETE | /project/{projectId}/subproject/{id} | JSON体version；仅未开始 |
| PUT | /project/{projectId}/subproject/{id}/restore | JSON体version；root、主项目负责人，或有subproject:restore的本子项目负责人 |
| PUT | /project/{projectId}/subproject/sort | 父项目version、全部有效子项目ids数组，不得重复/遗漏/跨项目 |

分页列表与详情返回can_edit、can_manage、can_create_subproject、can_sort、can_restore、can_manage_members、can_transfer_owner；子项目返回can_edit、can_status、can_delete、can_restore、can_transfer_owner。前端按标志展示入口；后端仍独立校验权限，不能信任客户端标志。

## 成员、交接与记录接口

下表路径统一以/project/{projectId}开头。写接口仅root或当前主项目负责人可用，完成/归档项目也可交接，但已删除项目不可用。

| 方法 | 后缀 | 请求及返回 |
|---|---|---|
| GET | /member/list | page/limit、keyword、status(1有效/0移除，可空)；items、total、project_version、can_manage |
| GET | /member/options | keyword、purpose(member/project_owner/subproject_owner)，最多50条正常用户 |
| POST | /member | user_id、access_level(0查看/1编辑)、version(父项目版本)；移除成员重新加入时复用关联 |
| PUT | /member/{userId} | access_level、version(成员版本) |
| DELETE | /member/{userId} | JSON体version(成员版本)，保留成员关联和历史引用 |
| PUT | /owner | owner_user_id、version(项目版本)，新负责人自动加入/恢复/提升为编辑成员 |
| PUT | /subproject/{subprojectId}/owner | owner_user_id、version(子项目版本)，新负责人必须已是项目正常有效编辑成员 |
| GET | /activity/list | page/limit；items、total，项目查看权限可读，按ID倒序 |

成员列表包括账号状态、成员状态、是否项目负责人、负责的未删除子项目数、can_change/can_remove及missing_permissions。missing_permissions是当前查看/编辑等级常用功能码的缺项提示，不代表已具备SOP等未来模块权限；负责人及root按管理能力豁免。权限不足时由用户管理员配置角色；可重复执行更新后的permissions.sql取得project_viewer和project_contributor角色，二者不含创建主项目权限。添加成员不自动分配系统角色。

主项目负责人不可直接移除或降级；负责未删除子项目（含归档）的成员先交接。仅有已删除子项目引用时允许移除，历史外键不变。移除后仍可登录平台，但项目列表、详情、编辑、候选人、成员和日志查询都重新校验有效成员关系。重新加入递增原成员version，不产生新关联主键。

转移主项目负责人后旧负责人保留编辑成员身份，系统功能权限按其当前角色重新判断。子项目普通编辑接口只兼容传原负责人ID或不传，不接受负责人变化；这也是对旧客户端的越权防护。交接更新项目/成员/子项目version，操作者会话和权限在用户锁及父项目锁后复核；并发版本冲突409，整个事务回滚。

操作记录追加保存成员加入/恢复/移除、权限变更和负责人转移；包含操作者、目标用户姓名快照和负责人姓名账号的变更前后值。只记录启用功能后通过对应接口产生的变化，不补造历史；不提供修改或删除记录接口。

## 业务规则

- 主项目编号全局唯一、子项目编号项目内唯一，删除后也不复用；新编号由前端生成PRJ/SUB-12位时间戳-6位随机码，旧编号保留。名称1–128字符，说明最多5000字符。
- 状态0未开始、1进行中、2已完成、3已归档。支持0→1→2、2→1、任意未归档→3。归档通过专用restore接口恢复，不允许用status接口直接绕过。完成需先完成/归档有效子项目。父项目完成或归档时业务资料只读，但允许有管理权的人维护成员、转移负责人；其他修改需先重新开启或恢复。
- 恢复保留归档前状态；主项目恢复只恢复本次联动归档的子项目，独立归档的保持归档。旧档无可靠历史，恢复为未开始。恢复要求version，冲突409；父项目归档或完成时不能单独恢复子项目，恢复为进行中的子项目还要求父项目进行中。成员无权恢复主项目；普通子项目负责人另需subproject:restore。全部变更在锁父项目的同一事务内执行，软删除记录不会恢复。
- 子项目开始前主项目必须进行中。归档主项目时同事务归档未归档有效子项目。归档内容只读；父项目排序仅调整展示位置，不编辑步骤或其他业务内容。
- 项目负责人管理自身项目，root管理全部项目。普通成员需要相应功能权限及项目关系：列表需project:list，详情需project:read；编辑成员才可编辑基本信息、创建/编辑/排序子项目。主项目状态/删除仅root或项目负责人；子项目状态/删除也可由持有对应权限的子项目负责人执行。
- 每次写操作按ID锁用户、锁父项目、复核会话和权限。子项目增删改、状态、排序都会递增父项目version；使用旧版本返回409。排序修改所有提交子项目的顺序及版本。客户端发生409后关闭过期表单并刷新，避免用旧version再次提交。
- 当前库只有项目相关三表；没有SOP或任务的绑定入口，因此删除检查仅覆盖当前子项目关系。后续增加SOP/任务前，必须补上引用阻止删除、未结束任务阻止归档的检查，不能直接沿用当前删除条件。

## 相关文档

- [前端页面与联调](../../../KedaAdmin/docs/project/README.md)
- [数据库设计与脚本入口](../../../SOP_MySQL/docs/project/README.md)
- [后台模块变更记录](changelog.md)

## 验证范围

ProjectIntegrationTest 使用独立H2数据库，通过真实HTTP测试授权范围、创建成员、唯一编号、修改版本、跨项目保护、排序、状态/归档、软删除、强制改密和并发修改。它验证MyBatis映射及接口行为，不等于在真实MySQL上进行写入测试。

前端单元测试覆盖版本参数、字符串ID、编辑字段边界、状态动作和409处理；另外运行现有用户/登录回归测试与生产构建。本批不会在用户现有开发库中创建测试项目或更改已有数据。

当前前端生成编号，后台校验并保存，不需要流水表迁移；规则见[公共数据说明](../../../SOP_MySQL/docs/common/README.md)。


## 工位与子项目任务

任务属于子项目，直接绑定工位并独立固定发布版本；项目不单独维护工位分配。步骤设备及环境要求随版本保存，旧关联入口保留用于标准查阅。当前行为、权限与启用顺序见 [资源与任务模块](../resource/README.md)。

## root专用删除

项目、子项目和SOP任务提供逻辑删除，仅is_superuser账号可用。项目列表／详情和子项目操作菜单使用can_delete，任务列表响应另含can_delete；普通负责人和任务管理者不能删除，旧的空项目删除权限也统一收紧为超级用户。归档／已完成记录也允许root删除。

删除项目会同步逻辑删除其有效子项目和任务；删除子项目会同步删除其任务。删除前确认影响范围，后台校验version并持有父项目锁；范围内有进行中或暂停的执行记录则409，必须先结束作业。任务删除接口为DELETE /project/{pid}/subproject/{sid}/task/{id}，JSON {version}；项目和子项目沿用既有DELETE接口。

数据库保留名称、编号、内容、版本、附件、执行历史和删除审计，业务列表及现场入口隐藏已删除记录。逻辑删除不提供恢复入口，归档恢复仍是独立功能。执行记录沿用快照和历史图片授权，不因删除业务对象失去追溯。编号不回收。

无需新SQL或新增权限定义，但必须已部署上一批execution-module-v1/schema.sql的执行记录三表，供删除拦截查询。更新前后端并重启后台，刷新页面。
