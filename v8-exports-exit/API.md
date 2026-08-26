# 原始 JSON 报文导出 API 契约

所有接口均位于 `/api/v1` 下，均要求现有平台 JWT。

## 1. 创建导出任务

```text
POST /api/v1/exports
Content-Type: application/json
Authorization: Bearer <platform JWT>
```

请求体：

```json
{
  "groups": [
    { "dataTypeCode": "QAR", "recordIds": ["id-1", "id-2"] },
    { "dataTypeCode": "IFE_633_BEHAVIOR", "recordIds": ["id-3"] }
  ]
}
```

规则：

- `groups` 至少包含一组；每组包含一个数据类型和该类型下已勾选的记录 ID。
- 同一请求中的数据类型不得重复。
- 后端必须校验每条记录未删除且确实属于该组数据类型。
- 每个分组创建一个独立 CSV 任务；创建成功只表示任务已创建，不表示文件已生成。

响应：

```json
{
  "code": "OK",
  "message": "success",
  "data": [{
    "id": "0c6b5c16-2e2b-4f26-89fd-a25a1df64f4c",
    "jobType": "EXPORT",
    "dataTypeCode": "QAR",
    "format": "CSV",
    "status": "PENDING",
    "totalRows": 0,
    "successRows": 0,
    "failedRows": 0,
    "resultFileName": null,
    "createdAt": "2026-08-25T10:00:00+08:00"
  }],
  "traceId": "..."
}
```

## 2. 查询导出历史和任务

```text
GET /api/v1/exports?page=1&pageSize=20
GET /api/v1/exports/{jobId}
```

仅返回当前用户创建的导出任务。任务状态为：`PENDING`、`RUNNING`、`SUCCEEDED`、`PARTIAL`、`FAILED`。

`PARTIAL` 表示至少一条记录无 `raw_payload` 而被跳过，其余 JSON 已成功生成。

## 3. 下载导出文件

```text
GET /api/v1/exports/{jobId}/file
```

- 仅 `SUCCEEDED` 或 `PARTIAL` 状态可下载。
- 返回 `text/csv; charset=UTF-8` 并使用附件下载文件名。
- 不接受文件路径、格式或筛选条件等额外下载参数。

## 4. 文件内容契约

### 4.1 CSV

```csv
raw_json
"{\"time\":\"12:00:00\",\"FLIGHT NUMBER\":\"CA8533\"}"
```

文件可以被 CSV 阅读器读取；第一列的值是完整 JSON 字符串。超长 Base64 JSON 可能超过 Excel 可显示的单元格上限，但不得在服务端截断。
