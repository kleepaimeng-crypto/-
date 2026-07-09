# 单机飞机实时轨迹数据库方案

## 1. 数据模型

实时轨迹使用两级模型：

```text
flight_session 1 ---- N qar_sample
```

- `flight_session`：一次模拟器飞行会话，同时保存该会话的最新状态指针。
- `qar_sample`：QAR 轨迹点事实表，每个 UDP 数据包仍对应独立的 `data_record`。
- `data_record`：保存来源设备、飞机注册号、机型和原始报文。

`record_id` 只标识一条接收记录，不能作为一次飞行的边界。不同飞行即使航班号相同，也必须使用不同的 `flight_session_id`。

## 2. `flight_session`

核心字段：

| 字段 | 说明 |
| --- | --- |
| `id` | 飞行会话 UUID |
| `source_system_code`、`source_device_code`、`source_host` | 数据流来源 |
| `flight_no`、`origin`、`destination` | 航班和航线 |
| `status` | `ACTIVE` 或 `FINISHED` |
| `started_at` | 本次飞行首个 QAR 时间 |
| `last_sample_at`、`last_received_at` | 最新采样和接收时间 |
| `last_frame_count` | 最新帧号 |
| `latest_qar_sample_id` | 当前飞机位置对应的 QAR 点 |

同一来源设备最多只能存在一个 `ACTIVE` 会话。

## 3. 会话边界

新 QAR 点满足以下条件时沿用当前会话：

- 航班号、起点和终点不变。
- 接收时间及采样时间与上一点间隔均不超过 5 分钟。
- 帧号没有从较大值重新回到 `0–5`。

以下任一情况关闭旧会话并创建新会话：

- 模拟器重启导致 `frame_count` 重新从小值开始。
- 数据中断超过 5 分钟。
- 航班号、起点或终点发生变化。
- 当前来源设备没有活动会话。

会话识别、QAR 入库和最新点更新必须处于同一个数据库事务。来源数据流通过 PostgreSQL 事务级 advisory lock 串行化，不依赖 Redis。

## 4. 轨迹查询

当前飞机：

```sql
SELECT q.*
FROM flight_session fs
JOIN qar_sample q ON q.id = fs.latest_qar_sample_id
WHERE fs.status = 'ACTIVE'
ORDER BY fs.last_received_at DESC
LIMIT 1;
```

当前轨迹：

```sql
SELECT q.*
FROM qar_sample q
WHERE q.flight_session_id = #{flightSessionId}
  AND q.sample_at >= #{windowStart}
  AND q.sample_at <= #{latestSampleAt}
  AND q.latitude IS NOT NULL
  AND q.longitude IS NOT NULL
ORDER BY q.sample_at, q.frame_count, q.id;
```

后端最多返回 720 个等距抽样点，并保留首点和最新点。飞机位置始终等于轨迹最后一点。

## 5. 索引

主要索引：

```sql
CREATE UNIQUE INDEX uk_flight_session_active_source
    ON flight_session (source_system_code, source_device_code, source_host)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_flight_session_status_received
    ON flight_session (status, last_received_at DESC);

CREATE INDEX idx_qar_flight_session_sample
    ON qar_sample (flight_session_id, sample_at, frame_count, id);
```

## 6. 迁移

`V9__add_flight_sessions.sql` 会：

1. 创建 `flight_session`。
2. 给 `qar_sample` 增加非空 `flight_session_id`。
3. 按来源、航班、航线、帧号重置和 5 分钟断流回填历史会话。
4. 将每个来源最新会话标记为 `ACTIVE`。
5. 删除已被会话表替代的 `flight_track_current_state`。

迁移不删除任何 `qar_sample` 或 `data_record` 历史数据。

## 7. Redis

当前单机、5 秒轮询场景不使用 Redis。PostgreSQL 是飞行会话和轨迹的唯一事实来源。后续只有在多机、高频推送或数据库出现实测瓶颈时，才考虑缓存当前快照；Redis 不参与会话边界判断。
