# 原始 JSON 报文导出数据边界

## 1. 既有表复用

```text
已勾选 data_record.id
      ↓
按 data_type_code 分组
      ↓
data_record.raw_payload (jsonb)
      ↓
file_job (EXPORT)
      ↓
受控服务器文件
```

本阶段复用：

- `data_record.raw_payload`：有效 JSON 报文的来源。
- `data_record.raw_text`：仅用于非 JSON 或无法按 JSON 保存的原始文本，本期不导出。
- `file_job`：保存导出任务状态、筛选快照、结果文件名、文件路径、行数和错误信息。

## 2. JSON 保真边界

`raw_payload` 的列类型是 PostgreSQL `jsonb`。因此：

- 保证导出 JSON 的字段和值与已保存的报文一致。
- 不保证输入 UDP 文本的空格、换行、缩进和对象字段顺序。
- 不提供逐字节还原或签名校验能力。

该边界已经得到确认，满足“字段和值完全相同的 JSON 原始报文”导出要求。

## 3. 不新增数据库结构

- 不新增 Flyway 迁移、表、字段、索引或约束。
- 不回改既有 `V1` 至当前已发布迁移。
- 不复制 QAR、633 IFE、科可瑞尔 IFE、舷窗或流量业务表的数据。
- `file_job.filter_snapshot` 保存任务创建时的记录 ID 和数据类型快照；不保存原始报文副本。

## 4. 数据安全

- 导出文件可能包含敏感业务数据，应按创建用户和现有数据管理权限限制下载。
- `storage_path` 仅由服务端写入和解析，不能暴露为 API 字段或接受用户传入。
- 删除、过期清理和长期归档不属于本阶段；不得在实现导出时顺带清理既有业务数据。
