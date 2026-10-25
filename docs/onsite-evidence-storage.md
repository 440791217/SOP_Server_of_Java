# 现场照片与工程文件存储

## 配置

`Server/src/main/resources/application.yml`：

```yaml
sop:
  storage:
    data-root: ${SOP_DATA_ROOT:D:/projects/sop-data}
    directory: ${SOP_LEGACY_IMAGE_DIRECTORY:D:/projects/data/sop-files}
```

`data-root` 是系统数据根目录；`directory` 仅用于兼容旧的平铺参考图。数据库存相对路径，禁止将目录映射为公共静态资源。业务目录由 `StoragePaths` 生成，`projects` 是根目录下的一个业务分支，可与将来的 exports/temp 并列。运行日志仍放 `D:/projects/logs`。

```text
<data-root>/projects/project-3/subproject-3/task-5/
  annotations/v002/step-22_<uuid>.jpg
  executions/2026-10-23/TEST-001_exec-1001/step-001_<uuid>.jpg
```

独立 SOP 无任务归属时使用 `projects/project-3/sop-8/annotations/v002/`。日期取该次作业数据库中的开工日期，跨午夜不换目录。产品号只保留安全 ASCII 字符并限制目录片段长度，原始产品号仍存数据库；exec-ID 保证目录唯一。文件名加 UUID，避免失败重试和进程崩溃遗留对象导致覆盖。

## 现场留存

工作页把用于本次成功推理的同一张 JPEG 和检测结果一起发到 `POST /execution/{id}/step/{position}/evidence`。后端校验操作者、执行版本、步骤顺序、绑定摄像头、实际图像尺寸，以及快照中所有启用区域的类别/数量/置信度条件；当前自动检测支持 object/count，不把不支持的 OCR/status 判为通过。

保存图片、留存元数据、确认步骤处于同一数据库事务；数据库失败时回收本次新图片。使用 request_key 和每作业步骤唯一约束防重复，重试不会覆盖已留存照片。有相机的人工步骤在确认时抓拍，无相机步骤允许人工确认但明确无现场照片。带摄像头的步骤不能通过旧的无照片确认接口绕过留存。

现场照片与参考图分离，后续修改参考图不会替换现场照片。执行记录默认显示留存图、拍摄时间和检测框，提供参考图入口。旧作业没有现场照片时显示“未留存现场照片”，不能补造过去画面。

## 数据库与维护工具

先编译 Java 服务，然后在 `Server/` 运行 PowerShell 工具。日志自动写入 `D:/projects/logs`；工具只允许连接本地 sop_dev，默认只读校验，不输出凭证。

```powershell
./tools/storage-maintenance.ps1 -Mode apply-schema
./tools/storage-maintenance.ps1 -Mode verify
```

`apply-schema` 执行 `SOP_MySQL/execution-module-v1/migrations/001_evidence_storage.sql`，增加现场照片表并扩展参考图相对路径长度，不改现有照片和作业快照。全新数据库的 schema.sql 也包含新结构。

旧参考图默认继续原位读取。需要迁移时，先备份数据库、旧图目录和新数据根目录，再暂停图片上传，运行：

```powershell
./tools/storage-maintenance.ps1 -Mode migrate-files
./tools/storage-maintenance.ps1 -Mode verify
```

迁移前校验长度和 SHA-256；复制到新目录后更新文件元数据的相对路径，文件 ID 不变，历史快照仍能通过原 ID 读取。一个图片对象可能被多个版本复用，物理位置按首个可归属版本归档，不复制成多份；未被步骤引用的旧对象保持原位。迁移保留旧原件，不自动清理历史图片或日志。

切换数据根目录时迁移整个 projects 树并保留相对结构，修改 SOP_DATA_ROOT 后重启；旧平铺图片尚未全部迁移时也要保留/配置 SOP_LEGACY_IMAGE_DIRECTORY。数据库和两个仍在使用的图片目录需配套备份。

## 本地测试

重启 Java 后端并刷新前端。新拍一张参考图，确认落在 task 的 annotations 目录；扫码执行，让该步骤所有条件满足，确认照片保存后进入下一步。在执行记录中回看现场照片，再切到参考图。旧作业仍应显示无现场照片，不应误用参考图。

当前同一任务产品码唯一的业务约束保留，复测请使用新的产品码。
