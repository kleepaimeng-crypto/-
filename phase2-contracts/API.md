# 乘客实时动态 REST API 契约

## 1. 公共规则

- 基础路径 `/api/v1/passenger-realtime`；全部为管理员 JWT 保护的只读 `GET` 接口。
- 沿用根 `API.md` 的统一响应、traceId、错误码和 ISO 8601 带时区时间。
- 本目录不定义数据统计、流量、吞吐、带宽或丢包接口。

## 2. 页面快照

### `GET /api/v1/passenger-realtime/snapshot`

无查询参数。一次返回页面当前所需的影音排行、座位/乘客状态和智慧舷窗快照，避免三个区域使用不同刷新版本。

`data` 结构：

```json
{
  "hasData": true,
  "updatedAt": "2026-07-06T10:00:00+08:00",
  "mediaStatistics": {
    "videoTotalCount": 248,
    "videoRanking": [{"type":"奇幻","count":57}],
    "musicTotalCount": 139,
    "musicRanking": [{"type":"民谣","count":48}]
  },
  "cabin": {
    "seatRowCount": 32,
    "seatsPerRow": 10,
    "seatLetters": ["A","B","C","D","E","F","G","H","J","K"],
    "passengers": [{
      "seatNo":"1A",
      "passengerId":"USR-001",
      "deviceId":"DEV-001",
      "mediaKind":"VIDEO",
      "title":"示例标题",
      "types":["奇幻","科幻"],
      "action":"PLAY",
      "eventAt":"2026-07-06T09:59:58+08:00",
      "sourceRecordId":"9f4f9f0c-0000-4000-8000-000000000001"
    }]
  },
  "smartWindows": {
    "hasData": true,
    "sourceRecordId":"9f4f9f0c-0000-4000-8000-000000000002",
    "updatedAt":"2026-07-06T10:00:00+08:00",
    "summary":{"averageBrightness":5.40,"disconnectedCount":3,"faultCount":2,"testCount":4},
    "layout":{"windowCount":200,"leftWindowCount":100,"rightWindowCount":100},
    "windows":[{"windowId":1,"zoneId":1,"side":"LEFT","sideSequence":1,"brightnessLevel":7,"connected":true,"status":"NORMAL","updatedAt":"2026-07-06T10:00:00+08:00","sourceRecordId":"9f4f9f0c-0000-4000-8000-000000000002"}]
  }
}
```

约束：

- `videoRanking[].count`、`musicRanking[].count` 及两个 total 均为非负整数，不返回带宽单位。
- `mediaKind` 为 `VIDEO|MUSIC|OTHER|NONE`；无乘客行为的座位可省略于 `passengers`，前端按固定布局补为空闲座位。
- `types` 按 `/` 拆分、去空白并去重；`title/action/eventAt/sourceRecordId` 来自该乘客最新行为。
- 只有包含 200 个唯一舷窗 ID 的来源记录才构成完整舷窗快照；否则 `smartWindows.hasData=false`、汇总为零、`windows=[]`，布局计数仍返回。
- 全页无任何业务数据时 `hasData=false`，各列表为空；不返回 404。

## 3. 错误

- 401：JWT 缺失或失效。
- 403：管理员账号不可用。
- 503：数据库不可用；不得暴露 SQL、堆栈或原始报文。
