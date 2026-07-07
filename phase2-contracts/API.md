# 乘客实时动态 REST API 契约

## 1. 公共约定

- 基础路径 `/api/v1/passenger-realtime`，全部为管理员 JWT 保护的只读 `GET`。
- 沿用根 `API.md` 的统一响应、traceId、错误结构和 ISO 8601 带时区时间。
- 不定义驾驶舱监控接口，不返回 SVG 内容，不改变任何 UDP 报文格式。

### 1.1 后端边界

- Controller 只负责参数绑定、校验和统一响应；影音/乘客活动与智慧舷窗使用各自的只读查询服务。
- Service 在只读事务中完成 IFE 临时合并、最新行为选择、带宽关联、排行和舷窗快照装配。
- Repository/Mapper 分别查询 IFE、流量和舷窗，不创建视图、临时表或持久化汇总结果。
- 633 与科克瑞尔表保持原样，使用 `UNION ALL` 临时合并；相同乘客的当前行为按 `event_at DESC, created_at DESC, sourcePriority DESC` 取最新记录，完全同时时科克瑞尔优先。
- 查询只读取未软删除 `data_record` 关联的数据；数据库异常统一转换为 503，不返回 SQL 或堆栈。

### 1.2 前端 API 客户端

- 在集中 API 模块中定义 `PassengerRealtimeSnapshotDto`、`PassengerSmartWindowSnapshotDto`、`PassengerActivityDto`、`PassengerActivityPageDto` 和 `SeatLocationDto`，禁止使用 `any`。
- 暴露 `getPassengerRealtimeSnapshot()`、`getPassengerSmartWindows()`、`getPassengerActivities(page)` 和 `locatePassengerActivity(seatNo)`。
- 所有请求复用现有 JWT、traceId 和统一错误处理；页面组件不得直接调用 `fetch`。
- 初次进入时并行请求快照和第 1 页详情；自动刷新时并行刷新快照与当前详情页，不自动调用定位接口。
- 上一轮刷新未结束时跳过本轮；响应到达后仍需核对请求序号，旧响应不得覆盖新状态。

## 2. 页面快照

### `GET /api/v1/passenger-realtime/snapshot`

返回影音排行和座位当前状态；智慧舷窗由独立端点返回：

```json
{
  "hasData": true,
  "updatedAt": "2026-07-07T10:00:00+08:00",
  "mediaStatistics": {
    "videoTotalCount": 148,
    "videoRanking": [{"type":"奇幻","count":57}],
    "musicTotalCount": 96,
    "musicRanking": [{"type":"民谣","count":48}]
  },
  "seats": [{
    "seatNo":"11A",
    "cabinClass":"BUSINESS",
    "passengerId":"PAX-00001",
    "behaviorType":"MOVIE_PLAY",
    "eventAt":"2026-07-07T09:59:58+08:00"
  }]
}
```

- `seats` 只返回现有业务字段，不返回 SVG ID；前端拥有固定 237 座布局。

### `GET /api/v1/passenger-realtime/smart-windows`

返回最新完整的 116 舷窗快照：

```json
{
  "hasData": true,
  "sourceRecordId": "9f4f9f0c-0000-4000-8000-000000000001",
  "updatedAt": "2026-07-07T10:00:00+08:00",
  "summary": {"averageBrightness":5.4,"disconnectedCount":3,"faultCount":2,"testCount":4},
  "windows": [{"windowId":1,"zoneId":1,"brightnessLevel":7,"connected":true,"status":"NORMAL","updatedAt":"2026-07-07T10:00:00+08:00","sourceRecordId":"9f4f9f0c-0000-4000-8000-000000000001"}]
}
```

- 完整快照必须在同一未软删除 `record_id` 下恰好包含 116 个唯一 `windowId`，范围为 1–116；历史 200 窗和不完整快照均跳过。
- 无完整快照时返回 `hasData=false`、空 `windows` 和零值汇总，不生成假数据。
- 前端依据 `windowId` 定位 `airplane_windows.svg` 中的 `Window-001`～`Window-116`；1–58 为左侧、59–116 为右侧，同侧序号 1–17 前舱、18–37 中舱、38–58 后舱。

## 3. 乘客观看与浏览列表

### `GET /api/v1/passenger-realtime/passenger-activities?page={page}&pageSize=4`

- `page` 从 1 开始；`pageSize` 固定为 4，其他值返回 400。
- 按飞机布局中的座位顺序稳定分页，返回公共分页结构。

列表项：

```json
{
  "passengerId":"PAX-00001",
  "seatNo":"11A",
  "cabinClass":"BUSINESS",
  "behaviorType":"MOVIE_PLAY",
  "activityKind":"VIDEO",
  "title":"星海远航",
  "types":["奇幻","科幻"],
  "action":"PLAY",
  "domain":null,
  "url":null,
  "trafficBytes":null,
  "bandwidthMbps":8.42,
  "windowBytes":5262500,
  "eventAt":"2026-07-07T09:59:58+08:00",
  "bandwidthUpdatedAt":"2026-07-07T10:00:00+08:00",
  "sourceRecordId":"9f4f9f0c-0000-4000-8000-000000000002"
}
```

- `activityKind` 为 `VIDEO|MUSIC|BROWSING|OTHER|IDLE`。
- 视频标题/类型取 `contentName/contentType`；音乐取 `musicName/musicType`；浏览取 `dstDomain/url/trafficBytes`。
- `bandwidthMbps/windowBytes` 取同座位最新 `traffic_record`；无匹配记录返回 `null`。
- 237 名乘客全部参与分页；没有观看或浏览行为的乘客返回 `IDLE` 或 `OTHER`，不从列表删除。

## 4. 座位定位

### `GET /api/v1/passenger-realtime/passenger-activities/locate?seatNo={seatNo}`

按固定每页 4 人计算该座位所在页：

```json
{"seatNo":"11A","page":1,"indexInPage":0}
```

- 非法或不存在的座位返回 404。
- 该接口只用于座位点击后定位右侧分页，不返回重复的乘客详情。

## 5. 前后端交互流程

### 页面初始化

1. 前端并行请求 `snapshot`、`smart-windows` 和 `passenger-activities?page=1&pageSize=4`。
2. 页面快照更新排行和座位状态；智慧舷窗响应只更新舷窗层；分页响应只更新右侧四条详情。
3. 任一请求失败时只影响对应区域；已有成功数据不得被清空。

### 点击座位

1. 前端立即以 `seatNo` 设置中部选中态。
2. 调用 `locate?seatNo=` 获取 `page/indexInPage`。
3. 请求目标详情页；成功后切换页码并高亮 `indexInPage`。
4. 定位或分页失败时保留座位选中态并显示可重试错误。

### 点击右侧详情

1. 直接读取列表项已有的 `seatNo`，不再请求后端。
2. 转换为 SVG 座位标识并滚动到可视区域中央。
3. 更新中部与右侧的共享选中态。

### 自动刷新

- 默认 5 秒；暂停后不创建新请求，手动刷新仍可用。
- 独立刷新智慧舷窗和当前详情页，不重置 `page`、`seatNo`、高亮项或舱位图 `scrollTop`；舷窗上一轮请求未完成时跳过本轮。
- 若当前乘客行为改变，只原位更新详情内容；若座位不存在，才清除选择并显示提示。

## 6. 校验与错误

| 场景 | HTTP/行为 |
| --- | --- |
| `page < 1` 或 `pageSize != 4` | 400 `VALIDATION_ERROR` |
| `seatNo` 格式非法 | 400 `VALIDATION_ERROR` |
| 合法但不属于当前 237 座布局 | 404 `RESOURCE_NOT_FOUND` |
| 当前页超过总页数 | 200，空 `items`，保留请求页码 |
| 暂无 IFE 或舷窗数据 | 200，对应列表为空或 `hasData=false` |
| 数据库不可用 | 503 `DATABASE_UNAVAILABLE` |
