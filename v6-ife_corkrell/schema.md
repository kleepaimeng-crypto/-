# 科可瑞尔 IFE 单事件数据库方案

## 1. Schema 决策

本阶段不新增当前状态快照表。实时状态缓存仅保存在后端内存，历史数据继续复用现有两张表：

```text
data_record 1 ---- 1 ife_cockrell_behavior
```

每个客户单事件均追加一条原始数据记录和一条行为历史记录。现有 `ife_cockrell_behavior` 不新增 `(flight_no, seat_no)` 唯一约束，避免覆盖数据管理页面所需的事件历史。

本阶段不执行数据库清理、字段删除或新增迁移。旧科可瑞尔历史数据和 `item_no` 均保留，禁止修改已发布的 `V1` 至 `V12` 迁移。

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

历史表的 `(record_id, item_no)` 唯一约束继续适用：KKRE 单事件写入时固定 `item_no = 1`。需要改变的是 UDP 解析映射，不能再假定一条报文含 `items` 数组。

## 3. 事件写入与内存缓存

每条可解析的 UDP 事件处于同一个数据库事务：

1. 新增 `data_record`，保存原始 JSON，`payload_count=1`。
2. 新增 `ife_cockrell_behavior`。

事务提交成功后，只有事件的 `flightId` 与当前 QAR 航班会话匹配时，后端才以 `flight_session_id + seat_no` 更新内存中的最新状态缓存。缓存不写入数据库、不覆盖历史记录。

缓存更新规则：

- 仅同一 `flight_session_id + seat_no` 的新事件可以替换该座位状态；其他座位保持不变。
- 以 `event_at` 较新者为准；事件时间相同，以 `received_at` 较晚者为准。
- 旧事件和重复事件仍进入历史表，但不得覆盖缓存中的较新状态。
- 282 个不同座位的事件可并发更新不同缓存键；同一键的比较与替换必须原子化。

缓存属于运行时数据。服务重启或缓存清空后不从历史表恢复，页面等待新的匹配 IFE 事件；未收到事件的座位仍没有行为数据。

## 4. 查询规则

- 当前航班采用现有 QAR `flight_session.id`，其权威来源为 QAR；不得以最新科可瑞尔事件替代该判断。
- 科可瑞尔事件仅读取当前飞行上下文，用于补齐 `data_record` 的 `origin`、`destination` 和 `airline_code` 等管理字段；其自身 `flight_no` 始终来自客户 `flightId`。
- 当 IFE `flight_no` 与当前 QAR 航班会话不一致，或 QAR 会话暂不可用时，事件仅保存到历史表；不得更新缓存、切换全局飞行上下文或进入当前实时页面。
- 乘客实时接口读取当前 QAR 航班会话的内存缓存。服务层再以固定 C929-700 座位清单补齐未收到事件的座位；其行为字段为空，前端展示为空闲。
- 不直接向乘客实时页面返回 `pnr`、源 IP、完整 URL、完整订单、封面 Base64 或原始报文。
- 数据管理页继续按历史表查询，不从内存缓存反推事件历史。

## 5. 缓存验证

1. 单事件写入一条 `data_record` 和一条 `ife_cockrell_behavior`，且 `item_no = 1`。
2. 旧科可瑞尔历史和既有表结构不受影响。
4. 同一座位的旧事件不能覆盖内存中的较新状态；相同事件时间按 `received_at` 处理。
5. 282 个不同座位的突发写入均可入历史表并更新各自缓存键。
6. 清空缓存或重启后，当前实时页回到无事件状态，直至收到匹配当前 QAR 会话的新事件；数据管理历史查询仍正确。
