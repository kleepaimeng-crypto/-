# 乘客实时动态 REST API 契约

## 1. 公共约定

- 基础路径 `/api/v1/passenger-realtime`，全部为管理员 JWT 保护的只读 `GET`。
- 沿用根 `API.md` 的统一响应、traceId、错误结构和 ISO 8601 带时区时间。
- 不定义驾驶舱监控接口，不返回 SVG 内容，不改变 UDP 报文字段集合。
- 633 与科克瑞尔表保持原样，通过 `UNION ALL` 临时合并；只读取未软删除来源。
- 前端复用集中 API 客户端；5 秒轮询不得并发，旧响应不得覆盖新状态。

## 2. 页面快照

### `GET /api/v1/passenger-realtime/snapshot`

返回影音排行和全部 237 个座位的当前状态：

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
  "passengerActivities": {
    "total": 237,
    "items": [{
      "passengerId":"PAX-00001",
      "seatNo":"A11",
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
    }]
  }
}
```

- 当前航班取两路 IFE 中业务时间最新的航班，所有排行和活动均限定在该航班。
- `activityKind` 为 `VIDEO|MUSIC|BROWSING|OTHER|IDLE`。
- 视频读取 `contentName/contentType/playAction`，音乐读取 `musicName/musicType/playAction`，浏览读取 `dstDomain/url/trafficBytes`。
- `bandwidthMbps/windowBytes` 取同航班、同座位最新 `traffic_record`；无匹配记录返回 `null`。
- 237 个固定座位全部返回；没有 IFE 行为的座位返回 `IDLE`，业务字段为 `null`。
- 无任何 IFE 数据时返回 `hasData=false`、空排行和 237 条 `IDLE` 座位状态。

## 3. 智慧舷窗

### `GET /api/v1/passenger-realtime/smart-windows`

- 返回同一未软删除 `record_id` 下最新的 116 个唯一舷窗完整快照。
- 历史 200 窗和不完整快照不参与展示；无完整快照时返回 `hasData=false`、空 `windows` 和零值汇总。
- `windowId` 1–58 为左侧、59–116 为右侧；每侧区域数量为 17/20/21。

## 4. 前后端交互

- 页面初始化并行请求 `snapshot` 和 `smart-windows`；任一失败只影响对应区域。
- 点击座位或右侧详情只操作已返回的内存列表，不请求分页或定位接口。
- 自动刷新默认 5 秒；刷新成功后按 `seatNo` 原位更新，保持选中座位和滚动位置。
- 数据库不可用统一返回 503 `DATABASE_UNAVAILABLE`，不得返回 SQL 或堆栈。
