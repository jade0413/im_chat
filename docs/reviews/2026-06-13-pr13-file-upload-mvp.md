# PR-13 审查报告：feat: add file upload mvp (3696362)

审查人：Claude｜日期：2026-06-13｜关联任务：T28

## 结论：**打回，需整改** ❌

一项严重缺陷（S1）：`file_meta` 表无 Flyway 迁移脚本。其余为建议级。

---

## 严重项（S，阻塞合并）

### S1 — `file_meta` 表缺 Flyway 迁移脚本

**位置**：`im-server/im-bootstrap/src/main/resources/db/migration/`

当前目录下只有 `V1__init.sql` 和 `V2__user_conv_event.sql`，**没有 `V3__file_meta.sql`**。

`im-file-service`（`FileMetaEntity` / `FileMetaMapper`）和 `im-message-service`（`MessageFileMetaEntity` / `MessageFileMetaMapper`）均映射到 `file_meta` 表，且 `im-file-service` 已被 `im-bootstrap` 依赖：

```xml
<!-- im-bootstrap/pom.xml -->
<artifactId>im-file-service</artifactId>
```

无 DDL 时服务启动后任何 file_meta 操作均报 `Table 'file_meta' doesn't exist`，presign / confirm 接口完全不可用。

**修复**：在 `im-server/im-bootstrap/src/main/resources/db/migration/` 下新增 `V3__file_meta.sql`，至少包含：

```sql
CREATE TABLE file_meta (
  id          BIGINT NOT NULL,
  tenant_id   BIGINT NOT NULL,
  uploader_id BIGINT NOT NULL,
  object_key  VARCHAR(255) NOT NULL,
  mime        VARCHAR(64)  NOT NULL,
  size        BIGINT NOT NULL,
  duration_ms INT,
  status      INT NOT NULL DEFAULT 0,
  created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_object_key (tenant_id, object_key),
  KEY idx_tenant_uploader (tenant_id, uploader_id)
) ENGINE=InnoDB COMMENT='文件元数据';
```

---

## 建议项（B，不阻塞）

### B1 — `FileService` 公有构造器 `@Autowired` 冗余

同 PR-12 B3（已在 75a375a 修复 `MessageRevokeService`），`FileService` 的公有构造器仍保留了 `@Autowired`。单构造器 Spring Bean 无需显式标注，可顺手删除。

### B2 — `FILE_STATUS_CONFIRMED = 1` 魔数跨模块隐式约定

`MessageFileReferenceValidator` 中：
```java
private static final int FILE_STATUS_CONFIRMED = 1;
```

与 `im-file-service` 中 `FileMetaStatus.CONFIRMED` 的整数值相同，但两个模块无法互相导入（模块隔离铁律），所以只能各自维护。如果 `FileMetaStatus` 枚举顺序调整，`im-message-service` 侧不会有编译报错，会静默失效。

建议在 `im-common` 中定义一个常量类 `FileMetaConstants.STATUS_CONFIRMED = 1`，两侧均引用，避免隐式约定。

### B3 — `presign()` 内 MinIO 调用包在 `@Transactional` 中

`FileService.presign()` 和 `confirm()` 均标 `@Transactional`。`presign()` 中 `storageClient.presignPut()` 是外部 IO 调用，放在事务内会使 DB 连接持有时间等于 MinIO 响应时间，在 MinIO 慢或超时时会增加连接池压力。

建议将 `presign()` 拆为：①事务内仅做 DB insert；②事务提交后再调 MinIO presign；confirm() 同理。MVP 可接受，记录供后续优化。

---

## 通过项（✅）

| 项 | 验证结果 |
|---|---|
| 模块依赖铁律 | ✅ `im-file-service` pom 仅依赖 `im-common` + `im-proto-java`，enforcer 已绑定 |
| 跨模块 `file_meta` 访问 | ✅ `im-message-service` 用独立 `MessageFileMetaMapper` 读 `file_meta`，未跨模块导入 `im-file-service` 类，符合模块隔离约定 |
| object_key 租户前缀校验 | ✅ `validateObjectKey` 两处（FileService + MessageFileReferenceValidator）均验证 `startsWith(tenantId + "/")` 且拒绝 `..` 路径穿越 |
| `confirm` 幂等性 | ✅ `updateStatus(PENDING → CONFIRMED)` 乐观锁；`updated=0` 时 re-select 判断是否已是 CONFIRMED，是则返回成功，否则抛 INTERNAL_ERROR |
| presign 流程 | ✅ 生成 tenant 路径 `{tenantId}/{yyyyMM}/{uuid}{ext}`，落 PENDING 状态；URL、expiresAt、requiredHeaders 正确返回 |
| confirm 校验链 | ✅ 文件不存在 / uploader 不匹配 / 非 PENDING 状态 / 客户端 size/mime 与 DB 不符 / MinIO stat 不存在或 size/mime 不符 → 各自返回对应错误码 |
| `MessageSendService` 集成 | ✅ `fileReferenceValidator.ensureReferencesConfirmed()` 在 `persistService.persist()` 之前调用，FILE/IMAGE/VOICE 内容均在落库前校验 |
| 消息类型扩展 | ✅ `validateContent` 解除"只允许 TEXT"限制，新增 IMAGE/VOICE/FILE case；`newMessage` 替代 `newTextMessage`，`msgType` / `abstractText` 按 contentCase 分支正确 |
| ext.msg_type 透传 | ✅ `toPush()` 新增 `putExt(EXT_MSG_TYPE, ...)` 字段，客户端可据此渲染不同消息类型 |
| 扩展名净化 | ✅ `sanitizeExtension` 限制 ≤16 字符、仅 `[a-z0-9]+`，防止路径注入或过长扩展名 |
| MIME 白名单 | ✅ `FileProperties.DEFAULT_ALLOWED_MIMES` 明确列出 14 种，`isMimeAllowed` 全小写规范化后比对 |
| 大小限制 | ✅ `maxBytesFor` 按 image/audio/其他 三类区分，默认 10 MB / 20 MB / 50 MB；`validateSize` 先于 presign 执行 |
| 租户隔离 | ✅ `FileMapper.selectByObjectKey` / `updateStatus` 均显式 `tenant_id`；`FileService` 每个公共方法首行 `TenantContext.requiredTenantId()` |
| `ObjectStorageClient` 接口抽象 | ✅ `MinioObjectStorageClient` 实现 `ObjectStorageClient` 接口，测试使用 mock；`isNotFound` 判断 `NoSuchKey` / `NoSuchObject` / `NoSuchBucket` 三种错误码 |
| 测试覆盖 | ✅ `FileServiceTest`：presign 正常/MIME 拒绝/大小超限/confirm 正常/幂等/uploader 不匹配/stat 不存在；`MessageFileReferenceValidatorTest`：image 通过/未 confirm 拒绝/跨租户 key 拒绝/TEXT 跳过；`FileControllerTest` / `MessageSendServiceTest` 文件路径集成 |

---

## 整改清单

1. **S1（必须）** 新增 `V3__file_meta.sql`，包含 `file_meta` 表 DDL（`id` PK + `uk_tenant_object_key` 唯一键 + `idx_tenant_uploader` 索引）。

整改后重新提交，S1 验证通过即可合并。B1~B3 可带上或下一 PR 跟踪。

---

## 附：整体质量评价

文件上传 MVP 设计与 D10 一致（预签名直传，服务器只颁凭证和落元数据），两步式 presign→confirm 流程稳健，`MessageFileReferenceValidator` 的跨模块安全隔离方案干净（独立 mapper + 租户前缀校验），消息发送侧集成点正确。唯一严重问题是遗漏了 Flyway 迁移脚本，属于低级遗漏，补上即可合并。
