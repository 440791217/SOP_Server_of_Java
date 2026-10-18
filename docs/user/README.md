# 用户管理接口

前端入口保留 /user_management/user，页面文件位于 KedaAdmin/src/views/user/management。
保留原有Element UI表格、筛选、弹窗和SCSS风格；JS文件使用小写加下划线，组件使用PascalCase。

现有7张表足够，无需改库或重跑seed。真实表结构检查记录位于
[数据库结构核对](../../../SOP_MySQL/docs/user/schema_review.md)。
重启Java后端与前端开发服务即可联调；Swagger UI /swagger-ui/index.html 会出现“用户管理”分组，
Apifox重新导入 /v3/api-docs 即可同步接口。

## 路径与权限

统一使用 Authorization: Bearer <token>；JSON请求/响应；成功code=200；错误与HTTP状态一致。
必须先完成初始改密。root无需逐条分配权限；普通用户按启用角色查询有效权限。

| 方法 | 路径 | 权限与用途 |
|---|---|---|
| GET | /user/list | user:list，分页筛选未删除用户 |
| GET | /user/{id} | user:list，读取详情及当前version |
| POST | /user | user:create，创建用户并保存角色 |
| PUT | /user/{id} | user:update，编辑资料；改变角色还需user:assign-role |
| PUT | /user/{id}/status | user:status，正常/禁用/离职 |
| DELETE | /user/{id} | user:delete，JSON请求体传version，软删除 |
| PUT | /user/{id}/password | user:reset-password，重置初始密码并撤销会话 |
| PUT | /user/{id}/roles | user:assign-role，替换角色集合 |
| GET | /user/team/options | user:list/create/update任一，查询班组选项 |
| GET | /user/role/options | user:create或user:assign-role，查询操作者可分配的启用角色 |

分页参数：page默认1，limit默认10，允许1–100；排序固定ID倒序。
筛选：username、real_name包含匹配；card_number精确匹配；team_id、status精确匹配。
status=0表示禁用，空筛选不传。返回data={items:[...],total:...}。
使用参数绑定，不拼接筛选SQL；包含匹配使用LOCATE，输入百分号不会变成通配符。

新建请求示例：

```json
{
  "username": "worker01",
  "password": "自定义至少8字符密码",
  "real_name": "人员姓名",
  "card_number": null,
  "team_id": null,
  "phone": null,
  "avatar_url": null,
  "status": 1
}
```

省略role_ids默认分配member。显式角色数组（包含空数组）需要分配角色权限，前端未选时采用默认角色。
密码8–128字符，不trim；其他文本去首尾空白，可空字段空字符串转NULL。
账号规范化为小写，3–64位字母、数字、点、下划线或连字符；创建后不能修改。
电话仅限制20字符，不限制某个国家手机号格式。头像只接受HTTP(S)或站内绝对路径。

编辑请求包含real_name、card_number、team_id、phone、avatar_url、version；
它是完整资料更新，可空字段省略/NULL表示清空，role_ids省略表示角色保持不变。
编辑不接受更改账号、状态、密码或超级用户标记；状态和密码使用专用接口。
更新角色和资料在同一事务完成，不能出现资料成功但角色失败的部分保存。

详情和列表返回字符串ID、team_id、shift_group（班组名称）、roles、version等，
有分配权限时还返回role_ids；不返回任何密码哈希或token摘要。
UTC时间返回ISO 8601字符串，前端按本地时区显示。

## 业务与并发规则

- root在列表可见并显示系统保护，管理API不能编辑、禁用、删除、分配角色或重置其密码。
- 本人不可自删、自改状态、自分配角色或使用管理员重置密码；本人改密使用 /user/me/password。
- 普通管理员只能新增授予本人持有的启用角色；已有角色可保持或移除。
- 普通管理员不能管理具有其未持有角色（默认member除外）或超出其权限的账号，防止通过改密接管高级账号。
- 新建和重置密码后必须改密；禁用、离职、删除、重置密码撤销目标全部会话。
- 账号即使软删除也不能复用；工卡只要求未删除用户中唯一，删除后可重新绑定。
- 班组选项包含禁用项以显示历史归属，新分配只允许启用项；班组为空可不选。
- 所有写操作按ID顺序锁定操作者/目标用户，锁后重新校验操作者会话和权限，使用READ COMMITTED。
- 更新、删除、状态、角色、重置密码必须带version；成功递增版本，旧版本返回409并要求刷新。
- 用户名/工卡唯一约束冲突返回409，不靠前端排重。数据库约束失败会回滚整个事务。

本次没有新增角色定义、权限定义或班组增删改接口，这些是后续独立功能。
前端“读卡”随机生成按钮已移除，当前可手工输入或由键盘式读卡设备输入工卡号。

## 验证

mvn verify 包括用户管理HTTP与MyBatis集成测试：新增、筛选、版本冲突、工卡唯一、
软删除、会话撤销、密码重置、权限限制、root保护和两个并发编辑请求只有一个成功。
测试使用H2内存库，不写实际sop_dev。已只读核对实际MySQL 8.4.11表与索引。
前端测试验证状态0、version、JSON请求白名单和ISO UTC时间解析；生产构建检查组件及引用。
