# 乘客实时动态数据契约

## 1. Schema 决策

本期不新增表、字段、索引或 Flyway 迁移，继续保持根 `schema.md` 固定的 15 张表。

页面只读取：

- `ife_633_behavior`：633 IFE 乘客行为。
- `ife_cockrell_behavior`：科克瑞尔 IFE 乘客行为。
- `smart_window_status`：智慧舷窗状态。
- `data_record`：过滤软删除来源及跳转只读原始报文。

`traffic_record`、`session_summary`、`simulation_task` 不参与本页面统计。

## 2. 影音排行查询口径

1. 两张 IFE 表均连接 `data_record`，排除 `deleted_at IS NOT NULL` 的来源记录。
2. 仅取 `MOVIE_PLAY` 和 `MUSIC_PLAY`，合并两套 IFE 来源。
3. 以 `passenger_id + media_kind` 分组，按 `event_at DESC, created_at DESC, id DESC` 取最新一条。
4. 视频读取 `behavior_detail->>'contentType'`，音乐读取 `behavior_detail->>'musicType'`。
5. 按 `/` 拆分，trim、移除空值并在单条状态内去重；每个保留类型贡献一次计数。
6. 排行按 `count DESC, type ASC`；total 是对应媒体种类拥有有效最新状态的不同乘客数。

该口径与 `simulator/receiver_server.py` 的 `user_media`、`videoRanking` 和 `musicRanking` 行为一致，不统计 Mbps 或历史累计流量。

## 3. 座位与乘客当前状态

- 固定布局为 32 排×10 座，字母 `A B C D E F G H J K`，不是新增数据库配置。
- 每个座位从两张 IFE 表合并结果中取最近行为；相同时间按 `created_at`、`id` 决定稳定顺序。
- `MOVIE_PLAY` 映射 `VIDEO`，`MUSIC_PLAY` 映射 `MUSIC`，其他行为映射 `OTHER`。
- 电影标题取 `contentName`，音乐标题取 `musicName`，动作分别取 `playAction`；缺失字段返回 `null`，不得猜测。

## 4. 舷窗快照

- 按 `record_id` 分组，只选择未软删除且具有 200 条、200 个唯一 `window_id` 的最新来源记录。
- 每四个连续 ID 中位置 1、2 为 `LEFT`，3、4 为 `RIGHT`；同侧序号为 1–100。
- 平均亮度保留两位小数；断连、故障和测试数由该完整快照直接计算。
