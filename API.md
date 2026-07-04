# 前中后舱网联数据显示平台 API 契约（首期）

## 1. 通用约定

| 项 | 约定 |
| --- | --- |
| 基础路径 | `/api/v1` |
| 数据格式 | `application/json; charset=utf-8` |
| 文件上传 | `multipart/form-data` |
| 文件下载 | `text/csv`、`application/pdf` 或 `application/octet-stream` |
| 认证 | `Authorization: Bearer <JWT>` |
| 时间 | ISO 8601 带时区，例如 `2026-07-03T15:30:00+08:00` |
| ID | 平台实体使用 UUID；审计流水为十进制整数 |
| 分页 | `page` 从 1 开始，默认 20；`pageSize` 允许 20、50、100 |
| 实现基线 | Vue 3 + TypeScript + Element Plus；Java 21 + Spring Boot；PostgreSQL 18 |

除登录接口外，所有接口均要求已登录且状态为 `ACTIVE` 的管理员。

### 1.1 成功响应

```json
{
  "code": "OK",
  "message": "success",
  "data": {},
  "traceId": "6a5d4129e4de4b25"
}
```

分页响应中的 `data`：

```json
{
  "items": [],
  "page": 1,
  "pageSize": 20,
  "total": 314,
  "totalPages": 16
}
```

删除等无返回体操作仍返回统一外层结构，`data` 为 `null`。

### 1.2 错误响应

```json
{
  "code": "VALIDATION_ERROR",
  "message": "receivedTo 必须晚于 receivedFrom",
  "details": [
    {"field": "receivedTo", "reason": "invalid_range"}
  ],
  "traceId": "6a5d4129e4de4b25"
}
```

| HTTP 状态 | 错误码 | 场景 |
| ---: | --- | --- |
| 400 | `VALIDATION_ERROR` | 参数、字段、时间范围或文件格式非法 |
| 400 | `UNSUPPORTED_DATA_TYPE` | 当前接口不支持该数据类型 |
| 401 | `UNAUTHORIZED` | 缺少、过期或无效 JWT |
| 403 | `ACCOUNT_DISABLED` | 管理员账号已禁用 |
| 404 | `RESOURCE_NOT_FOUND` | 记录、标签、批注、任务或文件不存在 |
| 409 | `RESOURCE_CONFLICT` | 标签重名、版本冲突或重复关系 |
| 409 | `RECORD_ALREADY_DELETED` | 记录已经软删除 |
| 409 | `RECORD_NOT_DELETED` | 对未删除记录执行恢复 |
| 410 | `FILE_EXPIRED` | 导入错误文件或导出文件已清理 |
| 413 | `FILE_TOO_LARGE` | 上传文件超过 50 MB |
| 422 | `IMPORT_SCHEMA_ERROR` | CSV 表头或数据类型与模板不一致 |
| 429 | `TOO_MANY_REQUESTS` | 登录或任务创建频率超限 |
| 500 | `INTERNAL_ERROR` | 未分类服务错误，不返回内部堆栈 |
| 503 | `DATABASE_UNAVAILABLE` | 数据库暂不可用 |

## 2. 枚举

| 名称 | 值 |
| --- | --- |
| `ParseStatus` | `RECEIVED`、`PARSED`、`PARTIAL`、`FAILED` |
| `ImportJobStatus` | `PENDING`、`RUNNING`、`SUCCEEDED`、`PARTIAL`、`FAILED` |
| `ExportJobStatus` | `PENDING`、`RUNNING`、`SUCCEEDED`、`FAILED` |
| `ExportFormat` | `CSV`、`PDF` |
| `UserStatus` | `ACTIVE`、`DISABLED` |
| `SortDirection` | `asc`、`desc` |
| `TagBatchMode` | `ADD`、`REMOVE` |
| `DataTypeCode` | `QAR`、`GROUND_TASK`、`GROUND_TRAFFIC_RECORD`、`GROUND_SESSION_SUMMARY`、`SMART_WINDOW_STATUS`、`IFE_633_BEHAVIOR`、`IFE_COCKRELL_BEHAVIOR` |

PHM、ACARS、视频流等可以作为停用的数据类型字典项存在，但首期接口不承诺其导入模板和解析详情。

## 3. 认证接口

### 3.1 登录

`POST /api/v1/auth/login`

权限：公开。连续失败应限流，日志不得记录密码。

请求：

```json
{
  "username": "admin",
  "password": "<由部署环境设置>"
}
```

成功响应 `data`：

```json
{
  "accessToken": "<jwt>",
  "tokenType": "Bearer",
  "expiresInSeconds": 7200,
  "user": {
    "id": "8c19c207-8053-4bee-a76e-6590eaaee846",
    "username": "admin",
    "email": "admin@example.invalid",
    "roleCode": "ADMIN"
  }
}
```

错误：400 `VALIDATION_ERROR`、401 `UNAUTHORIZED`、403 `ACCOUNT_DISABLED`、429 `TOO_MANY_REQUESTS`。

### 3.2 当前用户

`GET /api/v1/auth/me`

请求：无。成功响应 `data` 为登录响应中的 `user`。错误：401、403。

## 4. 查询选项

### 4.1 数据管理筛选项

`GET /api/v1/data-options`

请求：无。权限：管理员。

成功响应 `data`：

```json
{
  "dataTypes": [{"code": "QAR", "name": "QAR"}],
  "airlines": [{"code": "CA", "name": "CA"}],
  "aircraftModels": ["Boeing 777-300ER"],
  "aircraftRegistrations": ["B-TEST-001"],
  "devices": [{"code": "SIM-QAR", "name": "QAR 模拟设备"}],
  "airports": ["ZBAA", "ZSPD", "ZGGG", "ZUUU", "ZSHC"],
  "tags": [{"id": "<uuid>", "name": "巡航", "color": "#409EFF"}]
}
```

错误：401、403。

## 5. 数据记录接口

### 5.1 分页查询

`GET /api/v1/data-records`

权限：管理员。

查询参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `tagIds` | UUID[] | 否 | 逗号分隔；记录必须匹配全部所选标签 |
| `airlineCode` | string | 否 | 航司代码精确匹配 |
| `flightNo` | string | 否 | 航班号，不区分大小写精确匹配 |
| `sourceDeviceCode` | string | 否 | 来源设备编码 |
| `aircraftModel` | string | 否 | 机型精确匹配 |
| `origin` | string | 否 | 四字机场代码 |
| `destination` | string | 否 | 四字机场代码 |
| `dataTypeCode` | string | 否 | 数据类型代码 |
| `receivedFrom` | datetime | 否 | 接收时间下界，包含 |
| `receivedTo` | datetime | 否 | 接收时间上界，不包含 |
| `includeDeleted` | boolean | 否 | 默认 `false` |
| `page` | integer | 否 | 默认 1 |
| `pageSize` | integer | 否 | 默认 20 |
| `sortBy` | string | 否 | `dataType`、`sentAt`、`receivedAt`，默认 `receivedAt` |
| `sortDirection` | string | 否 | 默认 `desc` |

成功响应 `data.items` 示例：

```json
[
  {
    "id": "7f0c2100-5105-4cc7-9f18-92dde557207e",
    "aircraftRegistrationNo": "B-TEST-001",
    "aircraftModel": "Boeing 777-300ER",
    "airlineCode": "CA",
    "flightNo": "CA4732",
    "origin": "ZBAA",
    "destination": "ZSPD",
    "sourceDevice": {"code": "SIM-QAR", "name": "QAR 模拟设备"},
    "dataType": {"code": "QAR", "name": "QAR"},
    "sentAt": "2026-07-03T15:30:00+08:00",
    "receivedAt": "2026-07-03T15:30:00.132+08:00",
    "payloadCount": 1,
    "parseStatus": "PARSED",
    "tags": [{"id": "<uuid>", "name": "巡航", "color": "#409EFF"}],
    "deleted": false,
    "version": 1
  }
]
```

错误：400 `VALIDATION_ERROR`、401、403。

### 5.2 查询详情

`GET /api/v1/data-records/{recordId}`

请求：路径参数 UUID；查询参数 `includeDeleted=false`。

成功响应 `data`：

```json
{
  "id": "7f0c2100-5105-4cc7-9f18-92dde557207e",
  "metadata": {
    "aircraftRegistrationNo": "B-TEST-001",
    "flightNo": "CA4732",
    "dataTypeCode": "QAR",
    "sourceDeviceCode": "SIM-QAR",
    "sourceSystemCode": "SIMULATOR",
    "sentAt": "2026-07-03T15:30:00+08:00",
    "receivedAt": "2026-07-03T15:30:00.132+08:00",
    "parseStatus": "PARSED",
    "parseError": null,
    "version": 1
  },
  "rawPayload": {"FLIGHT NUMBER": "CA4732", "frameCount": 1},
  "rawText": null,
  "parsedSummary": {
    "sampleAt": "2026-07-03T15:30:00+08:00",
    "latitude": 36.411113024,
    "longitude": 120.09225375,
    "altitudeFt": 35000
  },
  "tags": [],
  "annotations": [],
  "deleted": false
}
```

`parsedSummary` 由原始 JSON 动态生成，不对应首期业务专表。`rawPayload` 和解析摘要只读。错误：404、401、403。

### 5.3 修改管理元数据

`PATCH /api/v1/data-records/{recordId}/metadata`

请求：

```json
{
  "aircraftRegistrationNo": "B-TEST-001",
  "aircraftModel": "Boeing 777-300ER",
  "airlineCode": "CA",
  "flightNo": "CA4732",
  "origin": "ZBAA",
  "destination": "ZSPD",
  "sourceDeviceCode": "SIM-QAR",
  "expectedVersion": 1
}
```

不接受 `rawPayload`、`rawText`、解析明细或接收时间。成功响应 `data` 返回更新后的 `metadata`。错误：400、404、409 `RESOURCE_CONFLICT`、401、403。

### 5.4 软删除

`DELETE /api/v1/data-records/{recordId}`

请求：

```json
{
  "reason": "测试数据清理",
  "expectedVersion": 1
}
```

成功：`data=null`。错误：404、409 `RECORD_ALREADY_DELETED`、409 `RESOURCE_CONFLICT`、401、403。

### 5.5 批量软删除

`POST /api/v1/data-records/batch-delete`

请求：

```json
{
  "recordIds": ["<uuid-1>", "<uuid-2>"],
  "reason": "批量清理测试数据"
}
```

单次最多 1,000 条。成功响应：

```json
{"requested": 2, "deleted": 2, "skipped": 0}
```

错误：400、404（全部不存在时）、401、403。

### 5.6 恢复记录

`POST /api/v1/data-records/{recordId}/restore`

请求：`{"reason":"误删恢复"}`。成功返回恢复后的列表项。错误：404、409 `RECORD_NOT_DELETED`、410（已物理清理）、401、403。

## 6. 标签接口

| Method | Path | 请求 | 成功响应 | 主要错误 |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/tags` | `includeDisabled=false` | 标签数组 | 401、403 |
| POST | `/api/v1/tags` | `name`、`color` | 新标签 | 400、409、401、403 |
| PATCH | `/api/v1/tags/{tagId}` | 可选 `name`、`color`、`enabled`、`expectedVersion` | 更新后标签 | 400、404、409 |
| DELETE | `/api/v1/tags/{tagId}` | `reason` | `null` | 404、409 |
| POST | `/api/v1/data-records/tags/batch` | `recordIds`、`tagIds`、`mode` | 请求数和实际变更数 | 400、404、409 |

创建示例：

```json
{"name": "巡航", "color": "#409EFF"}
```

批量关联示例：

```json
{
  "recordIds": ["<record-uuid>"],
  "tagIds": ["<tag-uuid>"],
  "mode": "ADD"
}
```

标签删除采用软删除/禁用语义；已有关联不会物理消失。单次批量最多 1,000 条记录、20 个标签。

## 7. 批注接口

### 7.1 查询记录批注

`GET /api/v1/data-records/{recordId}/annotations`

请求：`includeDeleted=false`。成功响应为按创建时间升序的批注数组。错误：404、401、403。

### 7.2 新增批注

`POST /api/v1/data-records/{recordId}/annotations`

请求：`{"content":"该记录用于巡航阶段验收"}`。内容去除首尾空白后长度为 1–2,000。成功响应为批注对象。错误：400、404、401、403。

### 7.3 批量新增批注

`POST /api/v1/data-records/annotations/batch`

请求：

```json
{
  "recordIds": ["<uuid-1>", "<uuid-2>"],
  "content": "批量验收标记"
}
```

单次最多 1,000 条。成功响应：`{"requested":2,"created":2,"skipped":0}`。错误：400、404（全部不存在时）、401、403。

### 7.4 修改批注

`PATCH /api/v1/annotations/{annotationId}`

请求：`{"content":"修订后的批注","expectedVersion":1}`。成功响应为更新后批注。错误：400、404、409、401、403。

### 7.5 删除批注

`DELETE /api/v1/annotations/{annotationId}`

请求：`{"reason":"批注填写错误","expectedVersion":1}`。使用软删除。错误：404、409、401、403。

## 8. CSV 导入接口

### 8.1 下载模板

`GET /api/v1/imports/templates/{dataTypeCode}`

支持：`QAR`、`GROUND_TASK`、`GROUND_TRAFFIC_RECORD`、`GROUND_SESSION_SUMMARY`。请求无正文，成功直接返回 UTF-8 BOM CSV 文件。错误：400 `UNSUPPORTED_DATA_TYPE`、401、403。

### 8.2 创建导入任务

`POST /api/v1/imports`

`multipart/form-data`：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `dataTypeCode` | string | 是 | 四种受支持类型之一 |
| `file` | CSV file | 是 | 最大 50 MB、100,000 行 |
| `sourceDeviceCode` | string | 是 | 导入数据的来源设备编码 |
| `aircraftRegistrationNo` | string | 是 | 默认飞机注册号 |
| `aircraftModel` | string | 否 | 默认机型 |
| `airlineCode` | string | 否 | 默认航司代码 |
| `flightNo` | string | 否 | 默认航班号 |
| `origin` | string | 否 | 默认起飞机场 |
| `destination` | string | 否 | 默认到达机场 |

成功返回 202：

```json
{
  "id": "<job-uuid>",
  "status": "PENDING",
  "fileName": "qar-import.csv",
  "dataTypeCode": "QAR",
  "createdAt": "2026-07-03T15:30:00+08:00"
}
```

错误：400、413、422、401、403。

### 8.3 导入历史

`GET /api/v1/imports`

参数：`status`、`dataTypeCode`、`createdFrom`、`createdTo`、分页参数。成功返回分页任务列表。错误：400、401、403。

### 8.4 导入任务详情

`GET /api/v1/imports/{jobId}`

成功响应包含状态、总行数、成功/失败数、错误文件是否可用、开始和结束时间。错误：404、401、403。

### 8.5 下载错误报告

`GET /api/v1/imports/{jobId}/error-file`

任务必须为 `PARTIAL` 或 `FAILED` 且存在错误报告。成功直接返回 CSV。错误：404、409、410、401、403。

## 9. 导出接口

### 9.1 创建导出任务

`POST /api/v1/exports`

请求：

```json
{
  "format": "CSV",
  "filters": {
    "dataTypeCode": "QAR",
    "tagIds": [],
    "airlineCode": "CA",
    "flightNo": null,
    "sourceDeviceCode": null,
    "aircraftModel": null,
    "origin": null,
    "destination": null,
    "receivedFrom": "2026-07-03T00:00:00+08:00",
    "receivedTo": "2026-07-04T00:00:00+08:00"
  },
  "sortBy": "receivedAt",
  "sortDirection": "desc"
}
```

后端必须保存完整筛选快照。成功返回 202：

```json
{"id":"<job-uuid>","status":"PENDING","format":"CSV","createdAt":"2026-07-03T15:30:00+08:00"}
```

错误：400、401、403、429。PDF 预估超过 5,000 行或 CSV 超过 100,000 行时返回 `VALIDATION_ERROR`。

### 9.2 导出历史

`GET /api/v1/exports`

参数：`status`、`format`、`dataTypeCode`、`createdFrom`、`createdTo`、分页参数。成功返回分页任务列表。错误：400、401、403。

### 9.3 导出任务详情

`GET /api/v1/exports/{jobId}`

成功响应包含格式、状态、筛选快照、总行数、错误原因、文件是否可用和起止时间。错误：404、401、403。

### 9.4 下载导出文件

`GET /api/v1/exports/{jobId}/file`

仅 `SUCCEEDED` 任务可下载。成功直接返回 CSV/PDF。错误：404、409、410、401、403。下载动作写审计日志。

## 10. 审计查询

`GET /api/v1/audit-logs`

参数：`action`、`targetType`、`targetId`、`operatorId`、`createdFrom`、`createdTo`、分页参数。成功返回分页审计摘要；详情中的 `beforeValue`、`afterValue` 不得包含密码或 JWT。错误：400、401、403。

首期界面可以不提供独立审计页面，但该接口用于删除、恢复和验收追溯。

## 11. 并发、幂等与文件规则

- 可编辑资源使用整数 `version` 做乐观锁；请求中的 `expectedVersion` 不一致返回 409。
- `POST /imports` 和 `POST /exports` 可以使用 `Idempotency-Key` 请求头；相同管理员在 10 分钟内使用相同键时返回原任务。
- CSV 使用 UTF-8 BOM、逗号分隔、双引号转义；时间字段必须包含日期与时区。
- 文件内容和生成文件存于部署配置的 `storage/`，数据库只保存元数据和相对路径。
- 日志、响应和审计快照禁止记录密码、JWT 或数据库连接密码。
