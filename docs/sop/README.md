# SOP 后台：图文草稿、版本与项目关联

已实现新建SOP、步骤维护和排序、图片上传及授权读取、草稿保存、发布、复制新草稿、版本预览、停用、负责人交接，以及项目/子项目固定版本关联。页面见[前端文档](../../../KedaAdmin/docs/sop/README.md)，字段和约束见[数据库设计](../../../SOP_MySQL/docs/sop/README.md)。本批不包含现场调度、执行记录、视频/PDF；步骤图片标注与制作配置已接入。

## 启动与数据库

需要用户模块、项目模块及项目001/002迁移和[SOP七表](../../../SOP_MySQL/sop-module-v1/README.md)。**七表已经建好就无需重跑schema.sql；制作配置需额外执行下文001_authoring_visual.sql。当前编号由前端生成，无需流水表迁移。** 应用不自动执行DDL，本次未连接实际开发库或写入业务数据。更新后重启Java和前端，Swagger入口为 http://localhost:8080/swagger-ui/index.html，OpenAPI为/v3/api-docs，标签“SOP草稿与版本”“项目SOP关联”。

root和主项目负责人按项目范围使用功能。普通成员可重复执行[permissions.sql](../../../SOP_MySQL/sop-module-v1/permissions.sql)更新角色功能码（本批补充sop:bind），再由root分配角色及项目成员身份；脚本不自动给用户授权。SOP编写/负责人角色包含关联能力，新增/切换仍要求源SOP访问权和目标编辑身份。

图片默认保存到**启动工作目录下data/sop-files**，可在现有配置文件中单独添加：

```yaml
sop:
  storage:
    directory: D:/projects/sop-data/files
```

首次上传创建目录，部署账号需要读写权限。保持路径稳定，数据库和文件目录一起备份，此目录不能映射成公共静态资源。仅支持实际可解码PNG/JPEG，每张≤5MiB，每步最多20张，最大边长8192、总像素1600万。传输层上限文件6MiB/请求7MiB，业务仍限制5MiB；超限413。随机文件名、原始名称清洗、SHA256与长度校验，禁止覆盖对象。

上传在同一请求完成文件保存、元数据与草稿引用入库；数据库回滚回调删除新对象。复制版本复用同一SOP的不可变文件，生成新步骤与引用ID。移除图片、放弃草稿和解绑都不删除文件。本批没有自动清理任务，进程中断遗留对象需核对数据库引用后处理，不能仅按文件时间删除。

## 接口契约

Authorization: Bearer <token>；JSON统一code/message/data，ID字符串、UTC ISO8601时间。400参数/发布条件，401会话，403权限，404归属/不存在，409重复/版本冲突/只读，413图片过大。图片成功响应为二进制，不套JSON，设置no-store和nosniff，每次读取复核权限与具体版本引用。

以下后缀统一以 **/sop** 为前缀：

| 方法 | 后缀 | 请求/返回 |
|---|---|---|
| GET | /list | page、limit≤100、keyword、project_id；items、total |
| GET | /project/options | keyword；最多50个可创建SOP的项目 |
| GET | /project/{pid}/owner/options | 管理项目正常有效编辑成员 |
| POST | 空 | code、name、managing_project_id、owner_user_id；code由前端生成并提交；返回文档与draft |
| GET/PUT | /{id} | 查看资料；编辑提交name、文档version |
| PUT | /{id}/owner | owner_user_id、文档version；root/管理项目负责人可交接 |
| DELETE | /{id} | 文档version；仅从未发布且无任何关联的SOP |
| GET | /{id}/version/list | 版本历史，不含已放弃草稿 |
| GET | /{id}/version/{rid} | 版本头、steps与附件元数据，隐藏私有路径及摘要 |
| POST | /{id}/draft | source_revision_id、文档version；复制历史发布/停用版 |
| PUT | /{id}/version/{rid} | title、applicable_scope、purpose、precautions、revision_note、草稿version |
| DELETE | /{id}/version/{rid} | 草稿version；放弃草稿 |
| POST | /{id}/version/{rid}/step | title、instruction、precautions、check_requirements、expected_duration_seconds、草稿version |
| PUT | /{id}/version/{rid}/step/{sid} | 步骤内容、步骤version、revision_version |
| DELETE | /{id}/version/{rid}/step/{sid} | 步骤version、revision_version |
| PUT | /{id}/version/{rid}/step/sort | 全部步骤ids、草稿version |
| POST | /{id}/version/{rid}/step/{sid}/image | multipart：file、草稿version；返回最新完整版本 |
| DELETE | /{id}/version/{rid}/step/{sid}/image/{aid} | JSON草稿version；仅移除引用 |
| GET | /{id}/version/{rid}/image/{fid} | 授权读取该版本引用图片 |
| PUT | /{id}/version/{rid}/publish | 草稿version；发布前校验步骤和图片完整性 |
| PUT | /{id}/version/{rid}/retire | 版本version；仍有未完成目标使用时拒绝 |

关联接口以 **/project/{pid}/sop** 或 **/project/{pid}/subproject/{sid}/sop** 为前缀：

| 方法 | 后缀 | 请求/返回 |
|---|---|---|
| GET | /list | items（含解绑行）、target_version、can_manage |
| GET | /options | keyword；最多50份本人可访问且有发布版的SOP |
| POST | 空 | sop_id、revision_id、target_version；新关联 |
| PUT | /{bid} | 同上及关联version；切换/恢复原行，sop_id不可变 |
| DELETE | /{bid} | 关联version、target_version；解绑并保留行 |
| PUT | /sort | 全部有效关联ids、target_version，不含解绑行 |
| GET | /{bid}/preview | 按目标项目权限查看关联固定版本 |
| GET | /{bid}/image/{fid} | 按目标关联授权读取固定版本图片 |

文档version、草稿version、步骤version是不同计数；草稿变化也递增文档version，写入后使用服务器返回值。关联target_version取主项目或子项目当前version；写入递增父项目version，子项目关联另递增子项目version。关联version防止旧选择覆盖新选择。409时刷新，不自动覆盖。

## 权限与历史规则

- 管理项目拥有源SOP；普通成员查看需有效成员身份和project:read、sop:read，列表另需sop:list。编写需编辑成员及对应功能码。root/主项目负责人按管理身份处理，子项目负责人不自动获得整个主项目管理权限。
- 发布/停用/删除限root、管理项目负责人或有对应功能码的SOP负责人；普通编辑者不能发布他人负责的SOP。负责人须为正常有效编辑成员。
- 同一SOP只保留一个有效草稿；发布至少一个步骤，V2起修订说明必填。发布后图文不可改，修改必须复制新草稿。目录改名不改变历史标题。
- 新版发布不自动切换已有关联、不自动停用旧版。关联只接受发布且未停用版本，同一目标每份SOP只一行，恢复复用解绑行。
- 目标编辑成员需sop:bind、project:read；root/目标主项目负责人按管理身份处理。新增/切换/恢复另需源SOP读取权，解绑不要求源访问。目标有效查看成员可通过关联预览固定版本与图片，不因此获得源草稿、其他版本或文件读取权。
- 主项目与子项目关联互不继承。完成/归档目标关联只读，仍可查历史。停用若被未完成目标使用则拒绝；历史冻结关联可预览停用版，恢复目标后提示切换。
- 项目/子项目有历史关联即不能删除，管理未删除SOP的项目也不能删除。负责SOP的成员移除/降级前先交接。交接记录SOP_OWNER_CHANGED，前端显示中文动作。
- 每次请求重新认证用户及成员身份，撤权后阻止后续预览和图片请求。已下载到客户端的内容无法远程收回。

## 并发与验证

写入先按用户ID升序锁用户，再按项目ID升序锁源/目标项目，随后锁SOP文档；锁后复核会话、权限、负责人和状态。关联与停用共用文档锁；目标父项目锁协调归档、成员变更与排序。

SopIntegrationTest通过独立H2和真实HTTP覆盖草稿/步骤、并发、版本冻结、成员保护，并新增真实PNG multipart、错误内容/大小、授权读取、复制后移除图片不影响旧版、固定关联、切换、解绑/恢复、撤权和子项目归属/关闭检查。前端另有挂载组件和请求契约测试。本次未连接实际MySQL 8.4联调，也未把组件测试当作真实浏览器验收。

## 手动验收

1. root登录，创建可编辑项目；SOP管理新建SOP，选择管理项目和负责人。
2. 保存草稿资料，添加步骤和PNG/JPEG，调整顺序、预览并发布V1。
3. 项目/子项目详情 → 关联SOP → 选择SOP及V1 → 查看标准。
4. 复制V1为新草稿，修改步骤/图片、填写修订说明并发布V2；原关联仍是V1，提示有新版。
5. 手动切换V2，旧V1仍可在源版本历史查看；使用方只能查看其当前关联版。
6. 使用只读成员和项目外账号检查范围，归档目标后关联只读、历史可查。

## 制作配置与标注接口

启用统一制作界面前执行一次[001_authoring_visual.sql](../../../SOP_MySQL/sop-module-v1/migrations/001_authoring_visual.sql)。新增sop_step_visual及步骤的detection_wait_seconds、voice_enabled、voice_text；后台开始查询可视配置，因此应先升级再重启，不重复执行原七表脚本。

PUT /sop/{id}/version/{rid}/step/{sid}/authoring：

- step：现有步骤字段、步骤version、revision_version，以及可选detection_wait_seconds（0–3600，一位小数）、voice_enabled和voice_text（启用时必填，≤1000）。
- visuals：最多20个当前步骤图片引用配置；attachment_id、schema_version=1、image_width/image_height、guide_items和detection_regions。未提交的图片配置保留；提交空数组可以清空对应图片的标注。图片实际尺寸与引用归属由后台校验。
- revision：可选版本头字段与version，和步骤/标注同一事务保存。三个部分有任一失败都回滚；成功时整个草稿version及文档version各递增一次，返回最新完整版本。

标注项必须有唯一id、kind、name、#RRGGBB颜色和比例坐标。矩形/文字使用x/y/width/height，箭头使用x/y/x2/y2。guide_items支持rect/arrow/text，文字项需text；检测区域仅rect，需enabled、detect_type、confidence（0–100）。object/count/status需class_name，count需正整数expected_count，status需expected_state，ocr需expected_text。各数组最多100项，坐标有限数且不能越界，禁止未知属性/重复项ID。

完整版本的附件增加visual（无配置为null），含原图尺寸、schema_version和两组标注。源与项目绑定预览共用该输出，文件鉴权不变。复制版本时生成新附件引用及visual记录，图像文件仍不可变复用；删除草稿图片引用级联删除自己的visual，不删除其他版本或文件。发布前检查标注结构及步骤设置，发布后authoring写入拒绝。检测参数只是配置，不返回模型检测成功结果。

RTSP仅为前端禁用占位，不开放摄像头连接接口，不恢复legacy采集/推理代码。现有project/subproject绑定接口继续用于制作视图的“关联项目”，不增加另一种标注关联。

SopIntegrationTest新增原子读写、版本递增、失效保存、引用归属、尺寸/坐标/重复项校验、非法语音回滚、发布只读、复制隔离、级联清理和使用方固定版本含标注预览。H2的visual JSON列采用文本fixture验证序列化与应用逻辑，不能代替MySQL JSON/DDL验证。


## 工位与子项目任务

任务属于子项目，直接绑定工位并独立固定发布版本；项目不单独维护工位分配。步骤设备及环境要求随版本保存，旧关联入口保留用于标准查阅。当前行为、权限与启用顺序见 [资源与任务模块](../resource/README.md)。

2026-10-17更新：项目和子项目的删除改为root专用级联逻辑删除，可保留旧SOP及关联历史而移除业务入口；有未结束执行时拒绝删除。历史内容不物理清除，执行记录继续通过其固定快照及图片接口查阅。普通账号无项目、子项目、任务删除权限。
