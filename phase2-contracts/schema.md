# 乘客实时动态数据口径

## 1. Schema 决策

本期不新增表、字段、索引或 Flyway 迁移。页面只读取：

- `ife_633_behavior`、`ife_cockrell_behavior`：影音和浏览行为。
- `traffic_record`：同座位最新带宽和窗口字节数。
- `smart_window_status`：116 个舷窗的透光度和状态。
- `data_record`：排除软删除来源并提供只读原始报文链接。

## 2. 最新乘客行为

- 合并两张 IFE 表，连接 `data_record` 并排除软删除记录。
- 临时合并查询为两张表附加 SQL 常量 `sourcePriority`：633 为 1、科克瑞尔为 2；不写回数据库。
- 每名乘客按 `event_at DESC, created_at DESC, sourcePriority DESC, id DESC` 取最新一条行为作为座位和右侧详情当前状态。
- 电影读取 `contentName/contentType/playAction`；音乐读取 `musicName/musicType/playAction`；浏览读取 `dstDomain/url/trafficBytes`。
- 237 名乘客按确认的 A330-200 座位清单排序；分页不依赖数据库自然顺序。

## 3. 影音排行

- 对视频和音乐分别按 `passenger_id` 取最新媒体行为，不受该乘客之后发生的另一媒体类型行为覆盖。
- 类型字符串按 `/` 拆分、trim、去空并在单条行为内去重，然后计数。
- 排序为 `count DESC, type ASC`，统计结果为整数次数。

## 4. 带宽关联

- 以乘客 `seat_no = traffic_record.seat_label` 关联最新流量窗口。
- 按 `window_end DESC, created_at DESC, id DESC` 取一条；返回 `throughput_mbps`、`bytes_count` 和窗口时间。
- `trafficBytes` 是 IFE 浏览累计字节，`throughput_mbps` 是当前带宽，两者不得互相替代。

## 5. 舷窗快照

- 只选择未软删除且同一 `record_id` 下具有 116 条、116 个唯一 `window_id` 的最新记录。
- ID 1–58 为左侧，59–116 为右侧；每侧区域数量为前舱 17、中舱 20、后舱 21。
- 平均亮度保留一位小数；断连、故障、测试数从该快照计算。
