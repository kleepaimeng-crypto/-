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
- 每名乘客只取当前航班最新总体行为；只有当前为 `MOVIE_PLAY` 或 `MUSIC_PLAY` 才进入对应排行。
- `videoTotalCount`、`musicTotalCount` 分别表示当前视频观看和音乐收听的去重乘客人数，不是类型次数之和。
- 排行项 `count` 表示当前乘客的类型命中人数；一条行为可含多个类型，因此排行合计允许超过标题人数。
- `activityKind` 为 `VIDEO|MUSIC|BROWSING|OTHER|IDLE`。
- 视频读取 `contentName/contentType/playAction`，音乐读取 `musicName/musicType/playAction`，浏览读取 `dstDomain/url/trafficBytes`。
- `bandwidthMbps/windowBytes` 取同航班、同座位最新 `traffic_record`；无匹配记录返回 `null`。
- 237 个固定座位全部返回；没有 IFE 行为的座位返回 `IDLE`，业务字段为 `null`。
- 无任何 IFE 数据时返回 `hasData=false`、空排行和 237 条 `IDLE` 座位状态。

## 3. 智慧舷窗

### `GET /api/v1/passenger-realtime/smart-windows`

- 返回最新未软删除智慧舷窗 `data_record` 中实际存在的 1～116 号舷窗；不使用旧快照补齐缺口。
- 响应增加 `complete`、`expectedCount=116`、`actualCount` 和 `missingWindowIds`；`windows` 只包含本批实际存在的数据。
- `actualCount>0` 时 `hasData=true`；只有 116 个编号全部存在时 `complete=true`。汇总只按实际数据计算。
- `actualCount=0` 时返回 `hasData=false`、全部 116 个缺失编号和零值汇总；历史 200 窗记录通过 `payload_count>116` 排除。
- `windowId` 1–58 为左侧、59–116 为右侧；每侧区域数量为 17/20/21。

## 4. 前后端交互

- 页面初始化并行请求 `snapshot` 和 `smart-windows`；任一失败只影响对应区域。
- 点击座位或右侧详情只操作已返回的内存列表，不请求分页或定位接口。
- 自动刷新默认 5 秒；刷新成功后按 `seatNo` 原位更新，保持选中座位和滚动位置。
- 部分快照中已有舷窗正常显示，缺失编号在对应 SVG 节点原位标红并显示“舷窗数据缺失”。
- 数据库不可用统一返回 503 `DATABASE_UNAVAILABLE`，不得返回 SQL 或堆栈。
