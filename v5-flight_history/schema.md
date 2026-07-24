# 飞机轨迹历史回放数据库方案

## 1. Schema 决策

历史轨迹不新建 PostgreSQL 服务或数据库，直接在现有 `cabin_data_platform` 内创建独立 Schema：

```sql
CREATE SCHEMA flight_history;
```

实时表继续位于 `public` Schema：

```text
public.flight_session 1 ---- N public.qar_sample
```

历史归档表位于 `flight_history` Schema：

```text
flight_history.flight_session_archive 1 ---- N flight_history.qar_point_archive
                                      1 ---- 1 flight_history.archive_job
```

历史表只保存回放所需的会话元数据和 QAR 点快照，不依赖实时表外键。这样即使实时库按既有策略清理原始数据，已归档历史轨迹仍可回放。

新 Flyway 迁移建议为：

```text
backend/src/main/resources/db/migration/V11__add_flight_history.sql
```

禁止修改既有 `V1` 至 `V10` 迁移。

## 2. `flight_history.flight_session_archive`

| 字段 | 类型 | 约束/说明 |
| --- | --- | --- |
| `id` | uuid | 历史会话主键 |
| `source_flight_session_id` | uuid | 非空、唯一；来源 `public.flight_session.id`，只作逻辑关联，不建跨生命周期外键 |
| `source_system_code` | varchar(64) | 非空 |
| `source_device_code` | varchar(64) | 非空 |
| `source_host` | inet | 非空 |
| `flight_no` | varchar(20) | 非空 |
| `origin`、`destination` | varchar(4) | 非空，ICAO 代码 |
| `aircraft_registration_no` | varchar(32) | 非空 |
| `aircraft_model` | varchar(128) | 可空 |
| `airline_code` | varchar(16) | 可空 |
| `started_at`、`ended_at` | timestamptz | 非空，结束不早于开始 |
| `point_count` | integer | 非空，大于等于 0 |
| `finish_reason` | varchar(32) | `LANDED`、`TIMEOUT`、`NEW_FLIGHT`、`FRAME_RESET` |
| `archived_at` | timestamptz | 非空，默认 `now()` |

索引：

```sql
CREATE UNIQUE INDEX uk_history_session_source
    ON flight_history.flight_session_archive (source_flight_session_id);

CREATE INDEX idx_history_session_ended
    ON flight_history.flight_session_archive (ended_at DESC, id);

CREATE INDEX idx_history_session_route_ended
    ON flight_history.flight_session_archive (flight_no, origin, destination, ended_at DESC);
```

## 3. `flight_history.qar_point_archive`

每行是一个归档 QAR 点。保留回放、状态卡和曲线使用的字段：

- `id bigint generated always as identity`：历史点主键。
- `session_id uuid`：非空，外键指向历史会话，`ON DELETE RESTRICT`。
- `source_qar_sample_id bigint`：非空、唯一，来源实时 QAR 点 ID。
- `sample_at timestamptz`、`source_time_text varchar(16)`、`frame_count bigint`。
- `air_ground_status varchar(16)`。
- `latitude`、`longitude`、`altitude_ft`、`ground_speed_kt`、`computed_air_speed_kt`。
- `track_angle_deg`、`heading_deg`、`pitch_deg`、`roll_deg`。
- `distance_to_go_nm`、`destination_eta_text`。
- `archived_at timestamptz not null default now()`。

不保存 `raw_payload`、`raw_text`、`record_id`、请求 IP 或任何凭据。

索引：

```sql
CREATE UNIQUE INDEX uk_history_qar_source
    ON flight_history.qar_point_archive (source_qar_sample_id);

CREATE INDEX idx_history_qar_session_sample
    ON flight_history.qar_point_archive (session_id, sample_at, frame_count, id);
```

## 4. `flight_history.archive_job`

该表是归档的可恢复任务记录，不向前端直接暴露。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | uuid | 主键 |
| `source_flight_session_id` | uuid | 非空、唯一 |
| `finish_reason` | varchar(32) | 会话结束原因 |
| `status` | varchar(16) | `PENDING`、`RUNNING`、`SUCCEEDED`、`FAILED` |
| `attempt_count` | integer | 非空，默认 0 |
| `next_retry_at` | timestamptz | 下次重试时间，可空 |
| `last_error` | varchar(1000) | 脱敏错误摘要，可空 |
| `created_at`、`started_at`、`completed_at`、`updated_at` | timestamptz | 任务追溯时间 |

索引：

```sql
CREATE UNIQUE INDEX uk_history_archive_job_source
    ON flight_history.archive_job (source_flight_session_id);

CREATE INDEX idx_history_archive_job_schedule
    ON flight_history.archive_job (status, next_retry_at, created_at);
```

## 5. 收尾与归档事务

### 5.1 收尾

收尾任务先锁定来源流和目标 `ACTIVE` 会话，确认最新 QAR 仍满足规则后更新 `public.flight_session.status='FINISHED'`。更新成功后，以来源会话 ID 插入 `archive_job`；唯一索引保证重复调度安全。

### 5.2 归档

归档工作线程锁定一条可执行任务，在同一事务中：

1. 验证来源会话为 `FINISHED`。
2. 插入历史会话；来源会话重复时视为幂等成功。
3. 按 `sample_at, frame_count, id` 升序复制该会话全部 QAR 点。
4. 更新历史会话 `point_count`。
5. 标记任务 `SUCCEEDED` 并写入完成时间。

任何一步失败均回滚历史会话和点，任务记录为 `FAILED` 并由受控退避重试。不得在复制成功前清理实时 QAR 数据。

## 6. 保留与备份

- 不为 `flight_history` 建立自动软删除、物理删除、TTL 或 7+7 天清理任务。
- 现有实时数据清理 SQL 必须显式限定 `public` Schema，不得通配删除历史表。
- 历史库纳入 PostgreSQL 常规备份和恢复演练；恢复后必须验证历史会话与点数量、唯一约束和查询索引。
- 历史数据量随时间持续增长，实施后监控表大小、索引大小、备份时长和查询耗时；没有实测瓶颈前不引入分区或额外存储系统。

## 7. 迁移验证

1. 从空库依次执行 V1 至 V11 成功。
2. 既有 `public` 表、数据和迁移校验不受影响。
3. `flight_history` Schema、三张表、约束和索引创建正确。
4. 同一来源会话重复归档不产生重复会话或点。
5. 删除/清理 `public` 数据后，历史回放查询仍能读取已归档点。
6. 历史表不被任何自动删除任务选中。
