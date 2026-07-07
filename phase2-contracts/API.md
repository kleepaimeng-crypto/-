# 乘客实时动态 REST API 契约

## 1. 公共约定

- 基础路径 `/api/v1/passenger-realtime`，全部为管理员 JWT 保护的只读 `GET`。
- 沿用根 `API.md` 的统一响应、traceId、错误结构和 ISO 8601 带时区时间。
- 不定义驾驶舱监控接口，不返回 SVG 内容，不改变任何 UDP 报文格式。

## 2. 页面快照

### `GET /api/v1/passenger-realtime/snapshot`

返回影音排行、座位当前状态和最新完整舷窗快照：

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
  }],
  "smartWindows": {
    "hasData":true,
    "sourceRecordId":"9f4f9f0c-0000-4000-8000-000000000001",
    "updatedAt":"2026-07-07T10:00:00+08:00",
    "summary":{"averageBrightness":5.4,"disconnectedCount":3,"faultCount":2,"testCount":4},
    "windows":[{"windowId":1,"zoneId":1,"brightnessLevel":7,"connected":true,"status":"NORMAL","updatedAt":"2026-07-07T10:00:00+08:00","sourceRecordId":"9f4f9f0c-0000-4000-8000-000000000001"}]
  }
}
```

- `seats` 只返回现有业务字段，不返回 SVG ID；前端拥有固定 237 座布局。
- 完整舷窗快照必须包含同一 `record_id` 下 116 个唯一 `windowId`，否则 `smartWindows.hasData=false`。

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
