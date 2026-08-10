# 科可瑞尔 IFE 单事件数据库方案

## 1. Schema 决策

本阶段复用现有两张历史表：

```text
data_record 1 ---- 1 ife_cockrell_behavior
```

每个客户单事件均追加一条原始数据记录和一条行为历史记录。现有 `ife_cockrell_behavior` 不新增 `(flight_no, seat_no)` 唯一约束，避免覆盖数据管理页面所需的事件历史。

新增当前状态表：

```text
ife_cockrell_current_state
```

该表每个航班和座位最多一行，专门服务乘客实时快照：

```text
flight_no + seat_no 1 ---- 1 当前最新科可瑞尔行为
```

新 Flyway 迁移：

```text
backend/src/main/resources/db/migration/V13__add_ife_cockrell_current_state.sql
```

禁止修改已发布的 `V1` 至 `V12` 迁移。

## 2. 现有 `ife_cockrell_behavior` 兼容性

现有字段已经覆盖客户固定事件：

| 客户字段 | 现有列 |
| --- | --- |
| `sysInfo.timestamp` | `event_at` |
| `sysInfo.flightId` | `flight_no` |
| `paxInfo.pnr` | `pnr` |
| `paxInfo.seatNo` | `seat_no` |
| `paxInfo.cabinClass` | `cabin_class` |
| `paxInfo.deviceId` | `device_id` |
| `paxInfo.userId` | `passenger_id` |
| `behaviorInfo.behaviorType` | `behavior_type` |
| `behaviorInfo` | `behavior_detail`（JSONB） |
| 封面元数据 | `cover_mime_type`、`cover_checksum` |
| `extInfo.errorCode` | `error_code` |
| `extInfo.errorDesc` | `error_description` |

历史表的 `(record_id, item_no)` 唯一约束仍适用：单事件写入时 `item_no = 1`。需要改变的是 UDP 解析映射，不能再假定一条报文含 `items` 数组。

## 3. `ife_cockrell_current_state`

建议字段：

| 字段 | 类型 | 约束/说明 |
| --- | --- | --- |
| `flight_no` | varchar(20) | 非空，当前状态键的一部分。 |
| `seat_no` | varchar(8) | 非空，当前状态键的一部分。 |
| `passenger_id` | varchar(32) | 非空。 |
| `cabin_class` | varchar(16) | 非空。 |
| `device_id` | varchar(32) | 非空。 |
| `event_at` | timestamptz | 非空，客户业务时间。 |
| `received_at` | timestamptz | 非空，服务端接收时间，用于同时间并列。 |
| `behavior_type` | varchar(32) | 非空，使用与历史表相同的枚举。 |
| `behavior_detail` | jsonb | 非空，`behaviorInfo` 对象。 |
| `error_code` | varchar(8) | 可空。 |
| `error_description` | varchar(64) | 可空。 |
| `source_record_id` | uuid | 非空，来源 `data_record.id`。 |
| `created_at`、`updated_at` | timestamptz | 非空，追溯时间。 |

约束与索引：

```sql
PRIMARY KEY (flight_no, seat_no)

CHECK (cabin_class IN ('FIRST', 'BUSINESS', 'ECONOMY'))
CHECK (behavior_type IN ('MOVIE_PLAY', 'MUSIC_PLAY', 'WAP_BROWSING', 'SHOPPING'))
CHECK (jsonb_typeof(behavior_detail) = 'object')

CREATE INDEX idx_ife_cockrell_current_flight_event
    ON ife_cockrell_current_state (flight_no, event_at DESC, seat_no);
```

`source_record_id` 应指向存在的来源记录。清理策略必须同时考虑当前状态表，不能让历史清理破坏仍处于活动航班的最新状态。

## 4. 事件写入与 UPSERT

每条可解析的 UDP 事件处于同一个事务：

1. 新增 `data_record`，保存原始 JSON，`payload_count=1`。
2. 新增 `ife_cockrell_behavior`，`item_no=1`。
3. 条件 UPSERT `ife_cockrell_current_state`。

核心 UPSERT 语义：

```sql
INSERT INTO ife_cockrell_current_state (...)
VALUES (...)
ON CONFLICT (flight_no, seat_no) DO UPDATE
SET
    passenger_id = EXCLUDED.passenger_id,
    cabin_class = EXCLUDED.cabin_class,
    device_id = EXCLUDED.device_id,
    event_at = EXCLUDED.event_at,
    received_at = EXCLUDED.received_at,
    behavior_type = EXCLUDED.behavior_type,
    behavior_detail = EXCLUDED.behavior_detail,
    error_code = EXCLUDED.error_code,
    error_description = EXCLUDED.error_description,
    source_record_id = EXCLUDED.source_record_id,
    updated_at = now()
WHERE
    EXCLUDED.event_at > ife_cockrell_current_state.event_at
    OR (
        EXCLUDED.event_at = ife_cockrell_current_state.event_at
        AND EXCLUDED.received_at >= ife_cockrell_current_state.received_at
    );
```

不同座位的突发事件更新不同主键行，可并发执行；同一座位的竞争由 PostgreSQL 唯一键与条件更新保证顺序。

## 5. 查询规则

- 当前航班采用最近有效科可瑞尔事件的 `flight_no`；后续若接入可靠飞行上下文，必须同步修订本契约。
- 查询该航班的当前状态表后，由服务层以固定 C929-700 座位清单补齐未收到事件的座位；其行为字段为空，前端展示为空闲。
- 不直接向乘客实时页面返回 `pnr`、源 IP、完整 URL、完整订单、封面 Base64 或原始报文。
- 数据管理页继续按历史表查询，不从当前状态表反推事件历史。

## 6. 迁移验证

1. 从空库依次执行 V1 至 V13 成功。
2. V13 不改变既有 `ife_cockrell_behavior` 的数据、约束和索引。
3. 同航班同座位 UPSERT 后只保留一行当前状态。
4. 较旧事件不能覆盖较新状态；相同事件时间按 `received_at` 处理。
5. 282 个不同座位的突发写入均可写入历史表和当前状态表。
6. 数据管理历史查询与乘客实时查询分别正确。
