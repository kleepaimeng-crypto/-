# Phase 2 REST API 契约

## 1. 公共规则

- 基础路径 `/api/v1`，均为管理员 JWT 保护的只读 `GET` 接口。
- 沿用根 `API.md` 的统一响应、错误、traceId、分页和 ISO 8601 带时区时间格式。
- `applications` 为逗号分隔的应用名称；`from`/`to` 必须同时提供且满足 `from < to`。流量窗口需满足 `windowStart >= from` 且 `windowEnd <= to`，会话快照使用 `[from,to)`。
- 非法参数返回 400；任务不存在或终端详情在筛选范围内无记录时返回 404；列表和概览无命中数据时返回成功响应中的空集合或零指标。

## 2. 流量统计

### `GET /api/v1/traffic-statistics/options`

响应 `data`：

```json
{"tasks":[{"taskId":"T-001","flightNo":"CA0001","status":"running","snapshotAt":"2026-07-06T10:00:00+08:00"}],"applications":["高清视频","音乐","网页浏览"],"defaultTaskId":"T-001","latestDataAt":"2026-07-06T10:00:00+08:00"}
```

任务按最新业务数据倒序；默认任务为最新有流量记录的任务。

### `GET /api/v1/traffic-statistics/overview`

参数：必填 `taskId`；可选 `applications`、`from`、`to`。省略时间时使用该任务全部可用流量范围。

响应 `data` 包含：

```json
{"task":{"taskId":"T-001","flightNo":"CA0001","scenario":"demo","status":"running"},"range":{"from":"2026-07-06T09:00:00+08:00","to":"2026-07-06T10:00:00+08:00"},"latestWindow":{"start":"2026-07-06T09:59:55+08:00","end":"2026-07-06T10:00:00+08:00","seconds":5},"metrics":{"currentThroughputMbps":320.5,"peakThroughputMbps":351.2,"cumulativeBytes":123456789,"activeTerminalCount":320,"activeSessionCount":280},"applicationRanking":[{"application":"高清视频","throughputMbps":200.1,"bytes":80000000,"terminalCount":120}],"updatedAt":"2026-07-06T10:00:00+08:00"}
```

无命中窗口时 `latestWindow=null`、指标为零、排行为空。

### `GET /api/v1/traffic-statistics/terminals`

参数：同 overview，另有 `page`（默认 1）、`pageSize`（20，可选 20/50/100）、`sortBy`（`currentThroughputMbps|peakThroughputMbps|cumulativeBytes|updatedAt`）、`sortDirection`（`asc|desc`，默认 desc）。

响应使用公共分页结构，`items` 元素包含 `terminalId`、`displayTerminalId`、`seatLabel`、`currentThroughputMbps`、`peakThroughputMbps`、`cumulativeBytes`、`activeSessionCount`、`updatedAt`。

### `GET /api/v1/traffic-statistics/terminals/{terminalId}`

参数：必填 `taskId`，以及可选 `applications`、`from`、`to`、`page`、`pageSize`。路径中的终端 ID 使用 URL 编码。

响应 `data` 包含 `summary`、分页 `trafficRecords` 和 `latestSessions`。流量记录包含 `recordId`、窗口、应用、协议、方向、字节数、包数、吞吐、峰值和状态；会话包含会话 ID、应用、协议、开始时间、持续时间、上下行字节、平均/峰值吞吐、状态和快照时间。响应不含丢包率。

## 3. 智慧舷窗

### `GET /api/v1/smart-windows/snapshot`

无查询参数。响应 `data`：

```json
{"hasData":true,"sourceRecordId":"9f4f9f0c-0000-4000-8000-000000000001","updatedAt":"2026-07-06T10:00:00+08:00","summary":{"averageBrightness":5.40,"disconnectedCount":3,"faultCount":2,"testCount":4},"layout":{"seatRowCount":32,"seatsPerRow":10,"seatLetters":["A","B","C","D","E","F","G","H","J","K"],"windowCount":200,"leftWindowCount":100,"rightWindowCount":100},"windows":[{"windowId":1,"zoneId":1,"side":"LEFT","sideSequence":1,"brightnessLevel":7,"connected":true,"status":"NORMAL","updatedAt":"2026-07-06T10:00:00+08:00","sourceRecordId":"9f4f9f0c-0000-4000-8000-000000000001"}]}
```

`averageBrightness` 对完整快照计算并保留两位小数；`sideSequence` 是同侧从前向后的 1 基序号。无完整 200 窗快照时返回 `hasData=false`、`sourceRecordId=null`、`updatedAt=null`、零汇总和空 `windows`，`layout` 仍返回固定布局参数。
