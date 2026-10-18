# 现场资源与子项目 SOP 任务接口

启用前按顺序执行尚未执行的 [资源 schema.sql](../../../SOP_MySQL/resource-module-v1/schema.sql) 和 [位置／工位 IP 迁移](../../../SOP_MySQL/resource-module-v1/migrations/001_station_location_ip.sql)，已有 SOP 制作增量迁移为前置。普通用户角色定义见同目录 permissions.sql；不直接运行旧库初始化脚本。所有接口使用 Bearer，会话、初始密码、数据范围及权限在后端校验。

## 资源

`kind` 只允许 location、station、model、device，不接受任意表名。

| 接口 | 功能 |
|---|---|
| GET /resource/{kind} | page、limit（1–100）、keyword、status；返回 items、total、can_manage |
| POST /resource/{kind} | 新建，前端生成 code |
| GET /resource/{kind}/{id} | 详情；工位包含 devices，设备包含 connection 和未验证状态 |
| PUT /resource/{kind}/{id} | 修改，version 必填；code 不可修改 |
| DELETE /resource/{kind}/{id} | body: {version}，引用检查与逻辑删除 |
| GET /resource/owner/options | keyword 查询正常负责人人选，最多 50 条 |
| PUT /resource/device/{id}/connection | 保存当前连接，device_version 必填；递增设备版本 |
| GET /resource/device/{id}/preview | 摄像头适配状态，当前 available=false |
| POST /resource/device/{id}/snapshot | 截图适配状态，当前 available=false，不生成图片 |

资源保存公共字段：code（创建）、name、status、description、version（编辑）。位置使用公共字段；工位额外 location_id、ip_address、owner_user_id（location 文本为兼容字段，写入时由位置清单生成）；规格额外 type_code、model、specification、unit；设备额外 model_id、station_id、owner_user_id、serial_number、可选 connection；serial_number 为厂家序列号，系统 code 仍由前端自动生成。设备的空工位与负责人传 null；工位必须选择可用位置并填写合法 IPv4／IPv6，规范化后检查有效工位 IP 唯一。旧工位缺字段仍可查阅，编辑时须补齐。位置名称最长255字，其余资源名称128字。删除被工位引用的位置返回409；停用位置不允许新选用。可选类型代码匹配小写字母、数字与下划线，不固定为数据库枚举。

连接 body：transport（network/usb/serial/none）、protocol、host、port、path、host_key、device_key、enabled、device_version。network 当前记录 rtsp/tcp/http/vendor 配置；usb 记录 hid_keyboard/serial/uvc/vendor 配置；serial 记录 serial/vendor 配置；none 对应 none。记录某协议不代表安装了通信适配器。禁止把密码 URL 或查询参数写入 path。当前未保存凭据，不访问设备网络地址。

查询需要 resource:read，写需要 resource:manage，适配状态入口需要 resource:use。resource_manager 可选角色包含上述三项；root 自动放行。

## 子项目任务

基路径 `/project/{pid}/subproject/{sid}/task`。不提供项目根级任务创建，也不建立项目工位分配表。

| 方法及路径 | 功能 |
|---|---|
| GET 基路径 | 返回 items 与 can_manage；每行 issues 是当前配置缺项 |
| GET 基路径/owner/options | keyword 查询本项目正常编辑成员，供负责人选择 |
| POST 基路径 | 新建待配置或已配置任务 |
| PUT 基路径/{id} | version 并发保护，配置工位、负责人、顺序、状态及显式切换固定版本 |
| GET 基路径/{id}/preview | 项目权限内读取固定版本完整标准 |
| GET 基路径/{id}/image/{fid} | 验证图片属于该任务固定版本，私有图片按 no-store 返回 |

任务 body：code（创建）、name、station_id（可空）、owner_user_id、sort_order（0–1000000）、status（0待配置/1已配置/2已取消）、description、sop_id 与 revision_id（同时为空或同时指定）、version（修改）。取消通过 PUT status=2，不能同时切换标准，取消后只读；不提供物理删除。选择版本的历史保存在 sop_task_standard，active 当前唯一。

task:manage + project:read + 本项目编辑成员可以配置；root／项目负责人可管理本项目任务。资源读取、SOP 创建与源标准读取仍使用对应模块原权限。目标项目普通查看者只需项目读取权限即可通过任务查看固定版本，不继承源项目管理权限。

## 步骤资源要求

现有 `PUT /sop/{id}/version/{rid}/step/{sid}/authoring` 可增加：

```json
{
  "resources": {
    "workplace_requirements": "稳定台面",
    "items": [{"model_id": "1", "quantity": 1, "description": "拍照参考"}]
  }
}
```

上例是增量字段示意，完整请求仍须包含 step、visuals 及可选 revision。一次请求原子保存，省略 resources 保持旧要求；items=[] 清空。最多 100 种规格，同规格不重复，数量 1–9999。版本详情和任务／项目预览返回步骤 workplace_requirements 与 resource_requirements（冻结快照）。

数据规则和迁移细节见 [数据库模块](../../../SOP_MySQL/docs/resource/README.md)。OpenAPI 通过运行中的 `/v3/api-docs` 提供，Swagger 入口 `/swagger-ui/index.html`。当前只完成资源与任务配置、键盘扫码输入和摄像头待接入边界，不提供执行调度或真实摄像头媒体功能。


## 设备资料与通信原子保存

POST /resource/device 与 PUT /resource/device/{id} 支持嵌套 connection，字段为 transport、protocol、host、port、path、host_key、device_key、enabled，规则与独立连接接口一致，嵌套对象不需要 device_version，编辑使用外层 version。一次请求只递增一次设备版本。省略 connection 兼容旧客户端并保留已有配置；传 transport=none、protocol=none 可显式设为无需连接。

设备资料与连接在同一事务内写入：任一连接校验或保存失败，资料修改、编号插入、版本递增与旧连接替换全部回滚。独立连接接口仍使用 device_version，并复用相同校验与保存逻辑。

列表增加 transport、protocol、connection_host、connection_port、host_key、device_key、connection_enabled；没有配置的旧设备不被 JOIN 排除。keyword 同时匹配设备编号、名称与 connection.host。网络数字 IP 规范化并校验，主机名按原字符规则录入，不发起 DNS 或设备探测；切换 USB／串口／无需连接时清除不再适用的网络字段，避免列表残留旧 IP。

数据库使用现有连接表，无结构升级。此功能不改变设备在线状态，不启动 RTSP 或 USB 采集。


## 2026-10-17 工位任务入口

工位详情增加 SOP 任务清单与新建入口。选择项目、子项目后复用子项目任务表单，当前工位自动带入；两个入口维护同一条任务。任务必须属于一个子项目并指定一个工位，SOP 可以暂不选择，以待配置保存。后台校验所选项目的任务权限，资源管理权限不等于项目权限；工位任务清单仅展示有项目查阅权限的数据。

新增 GET /resource/station/{id}/task，返回项目、子项目、固定标准和配置缺项。创建与编辑继续使用 /project/{pid}/subproject/{sid}/task。未取消任务保存时必须指定工位；历史空工位任务保留并要求补齐，允许直接取消。数据库继续使用现有 sop_task.station_id，不修改旧数据或增加非空约束，本次不需要执行 SQL。

SOP 制作继续上传 PNG/JPEG 作为步骤底图并保存标注，不提供模拟视频或假 RTSP。任务入口带工位上下文；发布后提示返回子项目任务，显式选择发布版本。摄像头仍为未接入，上传图片不代表完成设备联调。


### 新建绑定与备用工位

工位入口新建时立即将当前工位带入任务表单并加载硬件，保存后写入 station_id；已有任务编辑允许切换工位。切换会刷新可用硬件，保存时确认；已配置任务缺少规定规格或数量时拒绝保存，保留原关联，可由用户明确改为待配置后保存。保持原SOP版本，不自动替换、修改图片标注或移动设备。切换成功后，原工位任务列表移除该任务，新工位可查询到同一任务。

从任务制作 SOP 时，工具设备要求仅从该工位可用硬件的有效规格中选取，按规格去重；无工位上下文的通用 SOP 制作仍可选全局规格。要求随标准版本保存，不绑定具体设备 ID。备用工位需符合规格与数量要求，拍摄角度、检测区域仍需人工复核。


## 发布并用于任务

PUT /project/{pid}/subproject/{sid}/task/{id}/publish，JSON字段：sop_id、revision_id、revision_version、task_version。版本号取进入制作时的快照，不自动用最新任务版本覆盖并发修改。校验目标子项目任务管理权限和源SOP发布权限；发布、任务固定版本选择历史、任务配置状态更新在同一事务提交。工位不可用、硬件数量不足、任务已改动、取消／归档、发布校验失败时全部回滚，不会留下已发布但未选用的半成结果。成功返回 task 与 revision。原独立发布接口保持原行为。复用既有表，无需SQL。


## 任务直接维护内容

新增 sop_task_content（task_id 主键、sop_id 唯一、外键及创建人），任务固定拥有一份内容文档，已有 sop_task_standard 保留发布快照及旧关联历史，不删除旧数据。部署先执行数据库 resource-module-v1/migrations/002_task_content.sql 再启动新代码。

POST /project/{pid}/subproject/{sid}/task/{id}/content，JSON {code,version}。code为内部自动编号，仅第一次创建使用，继续已有内容可省略。校验任务编辑权限、负责人状态、任务版本及子项目归属；首次自动创建内容，旧任务从自己的发布快照复制步骤、附件和视觉标注。以后直接返回已有草稿，已发布则自动建立下一草稿；需要时同步任务名称与负责人。建立归属或新草稿时增加任务version，返回 {sop_id,revision_id,task}。

发布接口复用同一事务，已有归属时只能发布当前任务自己的内容。内容与任务版本、工位硬件同时校验，失败保留原草稿及已发布内容。归属内容不能通过旧删除接口删除。原SOP创建／编辑／发布权限仍校验，未扩大权限。

## 任务内容写入限制

任务归属内容的原SOP写入接口也检查任务、子项目及项目状态与任务管理权限。取消任务、完成／归档项目或子项目后，can_edit/can_publish 返回 false，直接修改内容返回403，防止旧编辑页面绕过任务状态。任务页统一查看／编辑不新增表或接口。

## 现场指导（第一阶段）

GET /resource/station/{id}/task 增加 project_status、subproject_status、onsite_available。后者仅在任务status=1、项目和子项目status<2且任务issues为空时为true；仍按资源查询和项目成员权限过滤。现场页面打开前重新查询，发布内容及图片沿用现有task preview/image接口。本阶段不写执行记录，不新增表；原任务查看接口继续支持管理端历史查阅。

2026-10-17：工位任务增加 onsite_unavailable_reasons，现场页面优先展示后台具体原因；缺少 onsite_available 时明确提示后端接口版本不匹配，不再误报任务待配置。需更新并重启后台，无SQL变更。

## root删除SOP任务

GET /project/{pid}/subproject/{sid}/task 新增can_delete，仅超级用户为true。DELETE /project/{pid}/subproject/{sid}/task/{id} 接收{version}，仅root可调用；父项目锁内检查归属、版本与未结束作业，软删除任务、保留内容和执行历史，写项目操作审计。项目／子项目的root删除会级联软删除下属任务。普通任务管理权限不含删除。归档状态也可由root删除；不提供删除恢复入口。无需新SQL，但需已有执行记录三表。

## 发布即就绪

SOP任务取消“待配置／已配置”手动选择，界面以未发布／已发布／已取消表示。保存任务不再要求status，0/1兼容旧客户端但由发布引用自动计算，2仍用于显式取消。已有发布内容的旧status=0任务无需SQL即可在现场使用；现场仍校验工位、硬件及项目关闭状态，缺项显示具体原因，不要求人工切换配置状态。更新前后端并重启后台。
