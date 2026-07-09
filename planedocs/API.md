# 单机飞机实时轨迹 API 文档

> 本文档定义当前项目新增“飞机轨迹实时系统”所需接口。接口基于现有 QAR 入库表 `qar_sample`，只服务单架飞机实时展示，不兼容旧项目多机 `flightmap` API。

## 1. 通用约定

### 1.1 Base URL

前端通过当前项目已有 HTTP 客户端访问后端。开发环境默认由 Vite 代理或环境变量统一处理 `/api` 前缀。

本文接口路径按后端 Controller 路径描述：

```text
/api/flight-track
```

### 1.2 响应格式

沿用当前项目统一响应对象：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | ---: | --- |
| `code` | `string` | 是 | 当前项目成功值为 `OK`，失败值为 `ResponseCode` 枚举名 |
| `message` | `string` | 是 | 当前项目成功默认为 `success`，失败为错误说明 |
| `data` | `object` / `array` / `null` | 否 | 业务数据 |
| `details` | `array/null` | 否 | 参数校验等详细错误 |
| `traceId` | `string/null` | 否 | 请求追踪 ID |

开发时应直接使用当前 `com.cabin.common.response.Response`，不要为了本模块单独改回旧项目的成功码和 `info` 字段。

成功且有数据：

```json
{
  "code": "OK",
  "message": "success",
  "data": {},
  "traceId": "trace-id"
}
```

成功但当前无活跃轨迹：

```json
{
  "code": "OK",
  "message": "success",
  "data": null,
  "traceId": "trace-id"
}
```

### 1.3 时间与单位

| 字段 | 约定 |
| --- | --- |
| `sampleAt`、`startAt`、`endAt` | ISO 8601 字符串，直接来自 `OffsetDateTime` 序列化 |
| `sampleTimeText` | 前端展示用本地时间文本，格式建议 `HH:mm:ss` |
| `latitude`、`longitude` | degrees |
| `altitudeFt` | ft，来自 QAR `BARO COR ALT NO. 1` |
| `groundSpeedKt` | kt，来自 QAR `GROUNDSPEED` |
| `computedAirSpeedKt` | kt，来自 QAR `COMPUTED AIRSPEED` |
| `trackAngleDeg`、`headingDeg`、`pitchDeg`、`rollDeg` | degrees 或 QAR 原始角度量 |
| `distanceToGoNm` | NM |

### 1.4 空值规则

- 当前无活跃 QAR：接口成功，`data: null`。
- 单个指标缺失：字段返回 `null`，前端图表跳过该点或断线。
- 最新点缺少经纬度：不视为可展示点，后端应继续寻找最近有效点；找不到则 `data: null`。

## 2. DTO

### 2.1 `FlightTrackPoint`

轨迹点对象，来源于 `qar_sample`。

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | ---: | --- |
| `sampleAt` | `string` | 是 | QAR 采样时间 |
| `sampleTimeText` | `string` | 是 | 图表横轴展示文本 |
| `frameCount` | `number` | 是 | QAR 帧号 |
| `latitude` | `number` | 是 | 纬度 |
| `longitude` | `number` | 是 | 经度 |
| `altitudeFt` | `number/null` | 否 | 高度，ft |
| `groundSpeedKt` | `number/null` | 否 | 地速，kt |
| `computedAirSpeedKt` | `number/null` | 否 | 空速，kt |
| `trackAngleDeg` | `number/null` | 否 | 真航迹角 |
| `headingDeg` | `number/null` | 否 | 显示航向 |
| `pitchDeg` | `number/null` | 否 | 俯仰量 |
| `rollDeg` | `number/null` | 否 | 横滚量 |
| `distanceToGoNm` | `number/null` | 否 | 剩余航程 |
| `destinationEtaText` | `string/null` | 否 | 到达剩余时间文本 |

示例：

```json
{
  "sampleAt": "2026-07-08T10:02:30+08:00",
  "sampleTimeText": "10:02:30",
  "frameCount": 1517,
  "latitude": 36.411113024,
  "longitude": 120.09225375,
  "altitudeFt": 35000,
  "groundSpeedKt": 456,
  "computedAirSpeedKt": 286,
  "trackAngleDeg": 71.718,
  "headingDeg": 72.1,
  "pitchDeg": 1.2,
  "rollDeg": 0.4,
  "distanceToGoNm": 620,
  "destinationEtaText": "0:26.2"
}
```

### 2.2 `FlightTrackInfo`

当前航班和飞机信息。

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | ---: | --- |
| `aircraftRegistrationNo` | `string/null` | 否 | 飞机注册号，来自 `data_record` 或配置 |
| `aircraftModel` | `string/null` | 否 | 机型 |
| `airlineCode` | `string/null` | 否 | 航司二字码 |
| `airlineName` | `string/null` | 否 | 航司名称 |
| `flightNo` | `string` | 是 | 当前航班号 |
| `originAirportCode` | `string` | 是 | 起飞机场 ICAO |
| `originAirportName` | `string` | 是 | 起飞机场展示名 |
| `destinationAirportCode` | `string` | 是 | 目的机场 ICAO |
| `destinationAirportName` | `string` | 是 | 目的机场展示名 |
| `statusText` | `string` | 是 | 例如 `飞行中`、`未进入飞行状态` |
| `lastUpdatedAt` | `string` | 是 | 最新有效点时间 |

### 2.3 `FlightTrackCurrentResponse`

当前页面一次渲染所需的聚合数据。

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | ---: | --- |
| `flight` | `FlightTrackInfo` | 是 | 当前航班信息 |
| `latestPoint` | `FlightTrackPoint` | 是 | 最新有效轨迹点 |
| `startAt` | `string` | 是 | 当前轨迹窗口第一点时间 |
| `endAt` | `string` | 是 | 当前轨迹窗口最后一点时间 |
| `pollIntervalSeconds` | `number` | 是 | 建议前端轮询间隔，默认 `5` |
| `freshnessSeconds` | `number` | 是 | 活跃新鲜度窗口，默认 `300` |
| `track` | `FlightTrackPoint[]` | 是 | 按 `sampleAt` 升序排列 |

示例：

```json
{
  "flight": {
    "aircraftRegistrationNo": "B-1012",
    "aircraftModel": "Airbus A330-200",
    "airlineCode": "CA",
    "airlineName": "中国国际航空",
    "flightNo": "CA4732",
    "originAirportCode": "ZBAA",
    "originAirportName": "北京首都国际机场",
    "destinationAirportCode": "ZSHC",
    "destinationAirportName": "杭州萧山国际机场",
    "statusText": "飞行中",
    "lastUpdatedAt": "2026-07-08T10:02:30+08:00"
  },
  "latestPoint": {
    "sampleAt": "2026-07-08T10:02:30+08:00",
    "sampleTimeText": "10:02:30",
    "frameCount": 1517,
    "latitude": 36.411113024,
    "longitude": 120.09225375,
    "altitudeFt": 35000,
    "groundSpeedKt": 456,
    "computedAirSpeedKt": 286,
    "trackAngleDeg": 71.718,
    "headingDeg": 72.1,
    "pitchDeg": 1.2,
    "rollDeg": 0.4,
    "distanceToGoNm": 620,
    "destinationEtaText": "0:26.2"
  },
  "startAt": "2026-07-08T09:54:30+08:00",
  "endAt": "2026-07-08T10:02:30+08:00",
  "pollIntervalSeconds": 5,
  "freshnessSeconds": 300,
  "track": []
}
```

## 3. 必需接口

### 3.1 查询当前单机实时轨迹

| 项 | 内容 |
| --- | --- |
| Method | `GET` |
| Path | `/api/flight-track/current` |
| Query | 无 |
| Auth | 沿用当前项目登录态要求 |
| Success data | `FlightTrackCurrentResponse` 或 `null` |

业务规则：

- 从 `qar_sample` 中查询最近 `5` 分钟内最新一条有经纬度的 QAR。
- 如果最新有效 QAR 的 `ground_speed_kt <= 100`，返回 `data: null` 或 `statusText = 未进入飞行状态`。推荐返回 `null`，前端展示空态。
- 当前航班号取最新有效 QAR 的 `flight_no`。
- 轨迹查询范围为同一 `flight_no` 最近 `8` 小时内有经纬度的点。
- `track` 按 `sample_at` 升序。
- `latestPoint` 必须等于 `track` 最后一条。
- 航司、机场展示名由后端映射补齐，映射缺失时返回原始代码。

响应示例：

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "flight": {
      "aircraftRegistrationNo": "B-1012",
      "aircraftModel": "Airbus A330-200",
      "airlineCode": "CA",
      "airlineName": "中国国际航空",
      "flightNo": "CA4732",
      "originAirportCode": "ZBAA",
      "originAirportName": "北京首都国际机场",
      "destinationAirportCode": "ZSHC",
      "destinationAirportName": "杭州萧山国际机场",
      "statusText": "飞行中",
      "lastUpdatedAt": "2026-07-08T10:02:30+08:00"
    },
    "latestPoint": {
      "sampleAt": "2026-07-08T10:02:30+08:00",
      "sampleTimeText": "10:02:30",
      "frameCount": 1517,
      "latitude": 36.411113024,
      "longitude": 120.09225375,
      "altitudeFt": 35000,
      "groundSpeedKt": 456,
      "computedAirSpeedKt": 286,
      "trackAngleDeg": 71.718,
      "headingDeg": 72.1,
      "pitchDeg": 1.2,
      "rollDeg": 0.4,
      "distanceToGoNm": 620,
      "destinationEtaText": "0:26.2"
    },
    "startAt": "2026-07-08T09:54:30+08:00",
    "endAt": "2026-07-08T10:02:30+08:00",
    "pollIntervalSeconds": 5,
    "freshnessSeconds": 300,
    "track": []
  },
  "traceId": "trace-id"
}
```

空数据示例：

```json
{
  "code": "OK",
  "message": "success",
  "data": null,
  "traceId": "trace-id"
}
```

## 4. 可选接口

以下接口不是首期必须项。只有当前端需要减少 payload 或排查数据时再实现。

### 4.1 查询当前单机快照

| 项 | 内容 |
| --- | --- |
| Method | `GET` |
| Path | `/api/flight-track/current/snapshot` |
| Success data | `{ flight, latestPoint, pollIntervalSeconds, freshnessSeconds }` 或 `null` |

用途：

- 地图只需要最新飞机位置时使用。
- 页面可先加载快照，再异步加载完整轨迹。

### 4.2 查询当前单机轨迹点

| 项 | 内容 |
| --- | --- |
| Method | `GET` |
| Path | `/api/flight-track/current/points` |
| Query | `limit` 可选，默认后端控制 |
| Success data | `FlightTrackPoint[]` |

规则：

- 仍以当前最新有效航班为准。
- `limit` 只允许正整数，并应设置后端上限。
- 返回按 `sampleAt` 升序排列。

## 5. 错误场景

| 场景 | 建议响应 | 处理 |
| --- | --- | --- |
| 没有 QAR 数据 | `code=OK, data=null` | 前端展示空态 |
| 最新 QAR 超过新鲜度窗口 | `code=OK, data=null` | 前端提示等待模拟器数据 |
| 最新 QAR 无经纬度 | `code=OK, data=null` | 后端可继续寻找最近有效点 |
| 数据库不可用 | 当前项目数据库错误码 | 前端保留上一次成功数据 |
| 参数非法 | 当前项目参数错误码 | 仅可选接口涉及 |

## 6. 前端消费建议

- 页面加载后立即请求 `/api/flight-track/current`。
- 成功后用 `pollIntervalSeconds` 设置轮询间隔，默认不低于 `5` 秒。
- `track` 为空或 `data: null` 时展示“等待 QAR 数据”。
- 地图轨迹只画后端返回的真实 QAR 点，不做预测、不插值。
- 地图飞机朝向优先由最后两个真实轨迹点计算，只有单点时再取 `trackAngleDeg`，为空时取 `headingDeg`。
- 图表横轴使用 `sampleTimeText`。
- 图表不要自行换算单位；高度展示为 ft，地速展示为 kt。

## 7. 接口验收

- 模拟器发送至少两帧有效 `qar.frame` 后，接口返回非空 `data`。
- `track` 按时间升序，最后一点与 `latestPoint` 一致。
- 返回字段能直接支撑参考图中的飞行状态、地图飞机、轨迹线和五组曲线。
- 停止模拟器超过 `5` 分钟后，接口返回 `data: null`。
- 接口不暴露数据库字段名，例如 `sample_at`、`ground_speed_kt` 不直接出现在 JSON 中。
