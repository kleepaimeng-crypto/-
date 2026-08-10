# 科可瑞尔 IFE 单事件与实时状态契约

## 1. UDP 基本约定

| 项 | 约定 |
| --- | --- |
| 数据类型 | `ife_cockrell.behavior` |
| UDP 端口 | `8096` |
| 编码 | UTF-8 JSON |
| 报文粒度 | 一名乘客的一次行为事件 |
| 鉴别方式 | UDP 端口映射，不在 JSON 中新增 `messageType` |

科可瑞尔客户格式固定。接收端不得要求 `items`、`page`、`pageSize`、`total`、`sentAt` 或其他批量包装字段。

## 2. 固定事件对象

```json
{
  "sysInfo": {
    "timestamp": "2026-08-06 10:03:46.498",
    "flightId": "MU5101"
  },
  "paxInfo": {
    "pnr": "DEF456",
    "seatNo": "05F",
    "cabinClass": "ECONOMY",
    "deviceId": "ANDROID-1234",
    "userId": "PAX-00002"
  },
  "behaviorInfo": {
    "behaviorType": "MUSIC_PLAY"
  },
  "extInfo": {
    "errorCode": "0000",
    "errorDesc": ""
  }
}
```

### 2.1 `sysInfo`

| 字段 | 必填 | 说明 |
| --- | ---: | --- |
| `timestamp` | 是 | `yyyy-MM-dd HH:mm:ss.SSS`；按应用时区解析为事件时间。 |
| `flightId` | 是 | 航班标识，映射为 `flightNo`。 |

### 2.2 `paxInfo`

| 字段 | 必填 | 说明 |
| --- | ---: | --- |
| `pnr` | 是 | 订座编码；只用于数据管理和审计，不默认返回实时页面。 |
| `seatNo` | 是 | 客户座位号；用于当前状态键。与 C929 座位图的映射必须在实现前确认。 |
| `cabinClass` | 是 | `FIRST`、`BUSINESS` 或 `ECONOMY`。 |
| `deviceId` | 是 | IFE 终端标识。 |
| `userId` | 是 | 乘客标识。 |

### 2.3 `behaviorInfo`

`behaviorType` 必填，取值为：

| 值 | 说明 |
| --- | --- |
| `MOVIE_PLAY` | 电影播放，使用 `contentId`、`contentName`、`contentType`、`contentDuration`、`playAction`、`playPosition`、`resolution` 及可选封面字段。 |
| `MUSIC_PLAY` | 音乐播放，使用 `musicId`、`musicName`、`musicType`、`artist`、`album`、`playAction`、`playPosition`、`volume` 及可选封面字段。 |
| `WAP_BROWSING` | 浏览/看新闻，使用 `sessionId`、`srcIp`、`dstIp`、`dstDomain`、`protocol`、`port`、`trafficBytes`、`url`。 |
| `SHOPPING` | 购物，使用 `orderList` 及订单、商品明细。 |

`coverBase64`、`coverChecksum`、`coverMimeType` 均按客户格式接收。它们不作为乘客实时页面的必要字段；若单个 UDP 数据报超过协议承载上限，应由客户侧既有传输机制解决，本阶段不修改格式。

### 2.4 `extInfo`

| 客户字段 | 后端映射 | 说明 |
| --- | --- | --- |
| `errorCode` | `errorCode` | 可选错误码。 |
| `errorDesc` | `errorDescription` | 可选错误描述；后端字段名可不同，但必须兼容客户固定字段。 |

## 3. 接收与保存规则

- 一条 UDP 数据报生成一条 `data_record`，其 `payloadCount` 为 `1`，并唯一关联一条 `ife_cockrell_behavior`。
- 同一事务中插入一条 `ife_cockrell_behavior` 历史记录；事务提交成功后更新后端内存中的当前状态缓存。
- `sentAt` 使用 `sysInfo.timestamp`；`receivedAt` 使用服务器接收时间。
- 原始 JSON 保持在 `data_record.raw_payload`，解析后的 `behaviorInfo` 保存为 JSON 对象。
- 接收端复用现有 UDP 当前飞行上下文补齐 `data_record` 的航线与航司管理字段。
- QAR 是当前飞行上下文的权威来源。科可瑞尔事件的 `flightId` 与当前上下文不一致时，事件保留为历史数据，但不得切换当前飞行上下文。

## 4. 乘客实时快照

现有接口保持：

```text
GET /api/v1/passenger-realtime/snapshot
```

本阶段调整其科可瑞尔数据来源：按现有 UDP 当前 QAR 航班会话的 `flightSessionId` 读取后端内存中该会话的全部已知 KKRE 座位状态，再以固定 C929-700 座位清单补齐为 282 项。当前飞行上下文由 QAR 确定，不以最新 IFE 事件推断；本期不与 633 IFE 合并。

内存缓存按当前 QAR 航班会话隔离。服务重启或缓存清空后不从历史表重建，页面等待新的匹配 IFE 事件。

每项新增/明确如下语义：

| 字段 | 规则 |
| --- | --- |
| `seatNo` | 当前页面使用的固定座位标识。 |
| `activityKind` | 未收到事件时为 `null`；前端据此展示为“空闲”。 |
| `behaviorType` | 未收到事件时为 `null`；已收到时取客户事件值。 |
| `eventAt` | 已收到事件时为 `sysInfo.timestamp` 转换后的带时区时间；未收到时为 `null`。 |

当 `behaviorType`、`eventAt` 等行为字段为空时，前端自行显示“空闲”；后端不得以虚构的行为内容填充座位。

已收到事件的 `playAction`（客户样例已确认 `PLAY`、`PAUSE`）是乘客真实操作：前端保留其对应的影音行为和操作值，不得转换为空闲。`STOP` 未在客户文档中出现，未经客户确认不得由模拟器自行发送。

影音排行只统计当前航班已收到的 `MOVIE_PLAY`、`MUSIC_PLAY` 状态。空闲、浏览和购物不进入影音排行。

## 5. 错误场景

| 场景 | 处理 |
| --- | --- |
| 缺少固定根对象或必填字段 | 当前 UDP 数据记录为解析失败；不更新当前状态。 |
| `behaviorType` 不支持 | 当前 UDP 数据记录为解析失败；不更新当前状态。 |
| 客户座位号无法映射页面座位 | 保存历史记录并标记映射错误；不更新页面对应座位。 |
| 旧事件晚到 | 保存历史记录；不覆盖当前状态。 |
| 航班号重复的旧会话事件 | 保存历史记录；若事件时间早于当前 QAR 会话 `startedAt` 减 5 分钟，不更新当前缓存。 |
| QAR 会话结束或切换 | 立即清除旧会话的内存缓存。 |
| 内存缓存更新失败 | 记录稳定错误；不得静默伪造页面状态。 |
