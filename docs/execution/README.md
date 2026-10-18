# 现场执行模块

业务链：工位 → 子项目SOP任务 → 产品扫码开工 → 顺序确认步骤 → 完成／异常暂停／中止。SOP任务定义要求，执行记录表示一次实际作业。

## 启用

在 sop_dev 手动执行 SOP_MySQL/execution-module-v1/schema.sql 全文，新建 sop_execution、sop_execution_step、sop_execution_event 三表，不重跑旧模块建表脚本。新建空库也先建立用户、项目、SOP、资源表。启动新后端前确认三张表全部成功创建；CREATE TABLE不是整体事务，若中断先核对错误。

普通操作人员可执行可重复的 permissions.sql，分配 execution_operator 角色，并加入目标项目（查看成员即可）。root无需分配角色。已有 resource_manager/task_editor 角色含 resource:use，也仍需项目可见范围。更新前端后通过“现场作业 → 现场工作台／执行记录”访问。

## 规则

- 开工要求 resource:read、resource:use 和目标项目查看权限（项目负责人/root例外）；工位、项目／子项目、任务、发布内容和硬件须可用。客户端提交看到的task_version与revision_id，配置变化返回409，不自动替换版本。
- 一个工位同时只能有一笔进行中或暂停的作业。active_station_id唯一约束及工位锁防止并发双开；结束才释放。产品码长度1～128，去首尾空白，拒绝控制字符；同一任务下大小写敏感且唯一，包含已中止记录，暂不支持自动返工。
- operator_id来自登录会话，时间来自数据库。开工request_key按操作人唯一，同一请求重试返回原记录；更换产品／任务必须更换key。网络丢包不能再次创建重复记录。
- 开工保存发布版本ID及完整图文标注JSON快照，名称、工位、人员也保存快照。发布新版本、任务换工位／取消，不会重写已有作业。已开工作业可按原快照继续、暂停或中止，不会自动跟随新任务；设备临时故障应由操作人暂停。步骤必须按位置顺序确认，暂停时禁止确认，全部确认后才可完成。
- running=0、paused=1、completed=2、aborted=3；后两种终态不可修改。暂停／中止必填原因，恢复保留先前确认。每次修改锁记录并校验version，冲突返回409，客户端刷新后核对，禁止自动重复确认。每个状态变化、步骤确认均写事件，与主记录在同一事务提交。
- 只有原操作人且仍持有资源使用和项目查看权限才能确认、暂停、恢复、完成；root可以填写原因中止其他人的作业并留下实际处理人，不能代签步骤。记录列表“查看记录”为只读，“处理作业”按后台返回权限显示操作。
- 记录查询继续按当前项目负责人／成员权限过滤，不因知晓ID或工位泄露其他项目数据；逻辑删除项目的历史仍对root等有权账号可查。执行记录无删除接口。历史图像按记录保存的revision_id与快照附件授权，不读取当前任务新版本，图片仍以Bearer请求Blob。

## 接口

| 方法和路径 | 用途 |
|---|---|
| GET /execution | 分页记录：page/limit，station_id/project_id/operator_id，project_name/station_name/operator_name，product_code，status，active，from/to（含首尾日期）；返回items、total、can_start |
| POST /execution | 开工，JSON：project_id、subproject_id、task_id、station_id、revision_id、task_version、product_code、request_key |
| GET /execution/{id} | 主记录、content快照、steps确认、events历史、can_operate、can_abort |
| PUT /execution/{id}/step/{position}/confirm | JSON {version}，position从1开始 |
| PUT /execution/{id}/pause、resume、complete、abort | JSON {version,reason}，暂停／中止原因必填 |
| GET /execution/{id}/image/{fileId} | 验证记录权限及固定快照附件后返回私有图片 |

Swagger中分组为“现场SOP执行”。数据持久化使用MyBatis，mapper与service位于 modules/execution 和 resources/mapper/execution。

## 验证与范围

集成测试使用隔离H2数据库，不连接用户sop_dev；覆盖幂等开工、并发工位占用、重复产品、顺序确认、暂停恢复、终态、权限、版本快照、原版本图片和筛选。MySQL8.4脚本尚需在实际环境手动执行验证。

扫码枪以USB键盘输入加回车方式接入，也可手工输入。暂不识别实际扫码枪身份、不接摄像头识别、不自动判定质量、不包含返工审批或人员交接。完成代表操作人确认，不代表算法检测合格。
