# 乘客实时动态数据口径

## 1. Schema 决策

本期不新增表、字段、索引或 Flyway 迁移。页面只读取 `ife_633_behavior`、`ife_cockrell_behavior`、`traffic_record`、`smart_window_status` 和 `data_record`。

## 2. 最新乘客行为

- 两张 IFE 表连接未软删除 `data_record` 后以 `UNION ALL` 临时合并，633 的 `sourcePriority=1`，科克瑞尔为 2。
- 当前航班取 `event_at DESC, created_at DESC, sourcePriority DESC, id DESC` 的最新记录。
- 当前行为在该航班内按 `passenger_id` 使用同一排序取第一条。
- 座位号统一为 `A11`；237 名乘客按 A330-200 固定座位清单排序，不依赖数据库自然顺序。

## 3. 影音排行

- 影音排行直接使用第 2 节选出的最新总体行为，不再分别回查历史视频和历史音乐。
- 当前为 `MOVIE_PLAY` 才统计视频，当前为 `MUSIC_PLAY` 才统计音乐；其他行为均排除。
- 类型字符串按 `/` 拆分、trim、去空并在单条行为内去重；排序为 `count DESC, type ASC`。
- 视频和音乐总数分别为对应当前行为的去重乘客人数，不等于多标签类型计数之和。

## 4. 带宽关联

- 以 `seat_no = traffic_record.seat_label` 关联当前航班最新流量窗口。
- 按 `window_end DESC, created_at DESC, id DESC` 取一条；返回 `throughput_mbps`、`bytes_count` 和窗口时间。
- IFE `trafficBytes` 是浏览累计字节，不能替代当前带宽。

## 5. 舷窗快照

- 从 `data_record` 选择最新未软删除且 `payload_count<=116` 的智慧舷窗报文，排除历史 200 窗记录。
- 只返回该 `record_id` 中实际存在的 1～116 号明细；缺失集合由固定编号全集减去实际编号得到，不查询旧快照补齐。
- 平均亮度和状态计数只使用实际存在的数据，平均亮度除数为实际数量。
- ID 1–58 为左侧，59–116 为右侧；每侧区域数量为前舱 17、中舱 20、后舱 21。
