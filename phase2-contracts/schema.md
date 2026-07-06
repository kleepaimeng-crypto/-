# Phase 2 数据库契约

## 1. 结论

Phase 2 复用根 `schema.md` 定义的 15 张表，不新增表、字段、索引或 Flyway 迁移。原始报文仍不可更新。

## 2. 流量统计来源

- `simulation_task`：任务选项、任务状态和统计窗口秒数。
- `traffic_record`：任务、窗口、终端、座位、应用、字节数、包数、吞吐和记录状态。
- `session_summary`：会话最新快照、终端、座位、应用、流量及状态。

所有范围查询使用半开区间 `[from, to)`。相同任务、窗口和应用先按窗口聚合，再计算峰值，禁止把每个终端的 `peak_mbps` 直接相加作为任务峰值。会话必须先按 `session_id` 取 `snapshot_at` 最新一行，再判断 `status='active'`。

## 3. 智慧舷窗来源

- `smart_window_status`：`record_id`、`item_no`、`window_id`、`zone_id`、`brightness`、`connect_status`、`status`、`event_at`。
- `data_record`：确认父记录可见且未软删除，并提供只读原始报文追溯。

快照以一条最新、未删除、解析成功的父 `data_record` 为边界，只返回该 `record_id` 下的状态，不跨记录补齐。只有恰好包含 200 个不同 `window_id`（1–200）的记录才是完整快照；不存在完整快照时返回 empty，而不是伪造缺失舷窗。

显示侧别为派生值，不入库：`((window_id - 1) % 4) < 2` 为 LEFT，否则为 RIGHT；显示顺序按 `zone_id, window_id`。舱区使用已入库 `zone_id`：1 前舱、2 中舱、3 后舱。

## 4. 性能边界

查询必须参数绑定并复用现有索引。终端明细服务端分页；统计接口只返回聚合和当前页，不加载全量原始报文。若性能验收失败，先记录 `EXPLAIN (ANALYZE, BUFFERS)`，再单独评审迁移，不能在本期实现中临时加索引。
