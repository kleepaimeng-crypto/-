# 飞机实时轨迹展示 API 文档

> 本文档基于 `docs/SREC.md` 和 `docs/schema.md`，只描述飞机实时轨迹展示可复用部分的接口契约。接口路径均为相对后端服务 baseURL 的路径；若部署层配置了 `/api` 前缀，应由网关或前端环境变量统一处理。

## 1. 通用约定

### 1.1 响应格式

所有接口返回统一响应对象：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `code` | `string` | 是 | 状态码，`0000` 表示成功，`0001` 表示失败 |
| `info` | `string` | 是 | 响应说明 |
| `data` | `object` / `array` / `null` | 否 | 业务数据 |

成功示例：

```json
{
  "code": "0000",
  "info": "调用成功",
  "data": {}
}
```

失败示例：

```json
{
  "code": "0001",
  "info": "服务器内部错误: 数据库连接失败",
  "data": null
}
```

### 1.2 时间与单位

| 字段 | 约定 |
|---|---|
| `timeStamp` / `startTime` / `endTime` | Unix 秒 |
| `startTimeReadable` / `endTimeReadable` | `yyyy-MM-dd HH:mm:ss`，后端本地时区 |
| `latitude` / `longitude` | degrees |
| `altitude` | `system_position` 来源为 meters |
| `groundSpeed` | Knots |
| `trueHeading` / `pitchAngle` / `rollAngle` | degrees |
| `timeToGo` | 分钟，字符串形式返回，允许为空 |
| `distanceToGo` | miles，字符串形式返回，允许为空 |

### 1.3 命名约定

- 数据库字段使用下划线命名，例如 `air_id`、`time_stamp`。
- API JSON 字段使用驼峰命名，例如 `airId`、`timeStamp`。
- `airId` 表示飞机注册号/机尾号。
- `flightNum` 表示航班号。

## 2. DTO 结构

### 2.1 FlightPoint

轨迹点对象，来源于 `system_position`。

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `timeStamp` | `number` | 是 | 轨迹点时间戳，Unix 秒 |
| `latitude` | `number` | 是 | 纬度 |
| `longitude` | `number` | 是 | 经度 |
| `altitude` | `number` | 是 | 高度，meters |
| `trueHeading` | `number` | 是 | 真航向，用于飞机图标旋转 |
| `pitchAngle` | `number` | 是 | 俯仰角 |
| `rollAngle` | `number` | 是 | 横滚角 |
| `bodyPitchRate` | `number` | 是 | 俯仰率 |
| `bodyRollRate` | `number` | 是 | 横滚率 |
| `headingAngularRate` | `number` | 是 | 航向角速率 |
| `groundSpeed` | `number` | 是 | 地速，Knots |

示例：

```json
{
  "timeStamp": 1783476030,
  "latitude": 30.6,
  "longitude": 114.34,
  "altitude": 3720,
  "trueHeading": 146.5,
  "pitchAngle": 2,
  "rollAngle": 0.2,
  "bodyPitchRate": 0.01,
  "bodyRollRate": 0.01,
  "headingAngularRate": 0.02,
  "groundSpeed": 432
}
```

### 2.2 FlightSnapshot

活跃飞机快照对象，每架活跃飞机返回一条。

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `airId` | `string` | 是 | 飞机注册号/机尾号 |
| `flightNum` | `string` | 否 | 航班号 |
| `airlineName` | `string` | 否 | 航空公司名称 |
| `orAirportCode` | `string` | 否 | 起飞机场代码 |
| `orAirportIcao` | `string` | 否 | 起飞机场 ICAO，当前项目可能由代码拼接得到 |
| `orAirportName` | `string` | 否 | 起飞机场名称 |
| `desAirportCode` | `string` | 否 | 目的机场代码 |
| `desAirportIcao` | `string` | 否 | 目的机场 ICAO，当前项目可能由代码拼接得到 |
| `desAirportName` | `string` | 否 | 目的机场名称 |
| `timeToGo` | `string` | 否 | 预计到达剩余时间 |
| `distanceToGo` | `string` | 否 | 预计到达剩余距离 |
| `latestPoint` | `FlightPoint` | 是 | 最新位置点 |

### 2.3 FlightTrack

完整航段轨迹对象。

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `airId` | `string` | 是 | 飞机注册号/机尾号 |
| `flightNum` | `string` | 否 | 航班号 |
| `airlineName` | `string` | 否 | 航空公司名称 |
| `orAirportCode` | `string` | 否 | 起飞机场代码 |
| `orAirportIcao` | `string` | 否 | 起飞机场 ICAO |
| `orAirportName` | `string` | 否 | 起飞机场名称 |
| `desAirportCode` | `string` | 否 | 目的机场代码 |
| `desAirportIcao` | `string` | 否 | 目的机场 ICAO |
| `desAirportName` | `string` | 否 | 目的机场名称 |
| `timeToGo` | `string` | 否 | 预计到达剩余时间 |
| `distanceToGo` | `string` | 否 | 预计到达剩余距离 |
| `startTime` | `number` | 是 | 航段开始 Unix 秒 |
| `endTime` | `number` | 是 | 航段结束 Unix 秒 |
| `startTimeReadable` | `string` | 是 | 航段开始时间文本 |
| `endTimeReadable` | `string` | 是 | 航段结束时间文本 |
| `track` | `FlightPoint[]` | 是 | 按 `timeStamp` 升序排列的轨迹点 |

### 2.4 FlightSegment

历史回放可选接口使用的航段摘要对象。

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `airId` | `string` | 是 | 飞机注册号/机尾号 |
| `flightNum` | `string` | 否 | 航班号 |
| `airlineName` | `string` | 否 | 航空公司名称 |
| `orAirportName` | `string` | 否 | 起飞机场名称 |
| `desAirportName` | `string` | 否 | 目的机场名称 |
| `startTime` | `number` | 是 | 航段开始 Unix 秒 |
| `endTime` | `number` | 是 | 航段结束 Unix 秒 |
| `startTimeReadable` | `string` | 是 | 航段开始时间文本 |
| `endTimeReadable` | `string` | 是 | 航段结束时间文本 |

## 3. 必需接口

### 3.1 查询活跃飞机快照

获取所有当前活跃飞机的最新位置快照。前端实时地图约每 5 秒轮询一次。

| 项 | 内容 |
|---|---|
| Method | `GET` |
| Path | `/flightmap/active-tracks` |
| Query | 无 |
| Auth | 由宿主项目决定；本复用模块不强制定义 |
| Success data | `FlightSnapshot[]` |

业务规则：

- 后端优先返回内存缓存中的快照。
- 缓存无可用数据时，查询 `system_position` 最近 10 分钟内每架飞机的最新位置。
- 只返回 `groundSpeed > 100` 且未过期的飞机。
- 无活跃飞机时，`data` 返回空数组。

响应示例：

```json
{
  "code": "0000",
  "info": "查询成功 (来自缓存)",
  "data": [
    {
      "airId": "B-5688",
      "flightNum": "MU6666",
      "airlineName": "中国东方航空",
      "orAirportCode": "PEK",
      "orAirportIcao": "ZPEK",
      "orAirportName": "北京首都国际机场",
      "desAirportCode": "CAN",
      "desAirportIcao": "ZCAN",
      "desAirportName": "广州白云国际机场",
      "timeToGo": "85",
      "distanceToGo": "620",
      "latestPoint": {
        "timeStamp": 1783476030,
        "latitude": 30.6,
        "longitude": 114.34,
        "altitude": 3720,
        "trueHeading": 146.5,
        "pitchAngle": 2,
        "rollAngle": 0.2,
        "bodyPitchRate": 0.01,
        "bodyRollRate": 0.01,
        "headingAngularRate": 0.02,
        "groundSpeed": 432
      }
    }
  ]
}
```

空数据示例：

```json
{
  "code": "0000",
  "info": "查询成功",
  "data": []
}
```

### 3.2 查询单机最新活跃航段完整轨迹

用户点击地图上的飞机后，获取该飞机最新活跃航段的完整轨迹点。

| 项 | 内容 |
|---|---|
| Method | `GET` |
| Path | `/flightmap/active-tracks/{airId}/latest` |
| Path 参数 | `airId`，飞机注册号/机尾号 |
| Auth | 由宿主项目决定；本复用模块不强制定义 |
| Success data | `FlightTrack` 或 `null` |

业务规则：

- 查询该飞机过去 8 小时内的 `system_position`。
- 轨迹点按 `time_stamp` 升序排序。
- 从后向前寻找最近一个 `groundSpeed < 100` 的中断点，中断点之后为最新活跃航段。
- 如果最后一个点已经低于阈值，或没有任何位置点，返回 `data: null`。
- 航班补充信息来自 `flight_status` 最新匹配记录。

响应示例：

```json
{
  "code": "0000",
  "info": "调用成功",
  "data": {
    "airId": "B-5688",
    "flightNum": "MU6666",
    "airlineName": "中国东方航空",
    "orAirportCode": "PEK",
    "orAirportIcao": "ZPEK",
    "orAirportName": "北京首都国际机场",
    "desAirportCode": "CAN",
    "desAirportIcao": "ZCAN",
    "desAirportName": "广州白云国际机场",
    "timeToGo": "85",
    "distanceToGo": "620",
    "startTime": 1783476010,
    "endTime": 1783476030,
    "startTimeReadable": "2026-07-08 10:00:10",
    "endTimeReadable": "2026-07-08 10:00:30",
    "track": [
      {
        "timeStamp": 1783476010,
        "latitude": 30.58,
        "longitude": 114.3,
        "altitude": 3658,
        "trueHeading": 145,
        "pitchAngle": 2.1,
        "rollAngle": 0.4,
        "bodyPitchRate": 0.01,
        "bodyRollRate": 0.02,
        "headingAngularRate": 0.03,
        "groundSpeed": 430
      },
      {
        "timeStamp": 1783476030,
        "latitude": 30.6,
        "longitude": 114.34,
        "altitude": 3720,
        "trueHeading": 146.5,
        "pitchAngle": 2,
        "rollAngle": 0.2,
        "bodyPitchRate": 0.01,
        "bodyRollRate": 0.01,
        "headingAngularRate": 0.02,
        "groundSpeed": 432
      }
    ]
  }
}
```

未找到活跃航段示例：

```json
{
  "code": "0000",
  "info": "未找到指定飞机的活跃航段信息",
  "data": null
}
```

## 4. 可选扩展接口：历史回放

以下接口属于本项目 `flightmap` 模块，但不是实时轨迹展示的最小闭环。若复用项目也需要历史回放，可按本节实现。

### 4.1 查询历史航段列表

| 项 | 内容 |
|---|---|
| Method | `GET` |
| Path | `/flightmap/history-tracks` |
| Query | `airId`、`startTime`、`endTime` |
| Success data | `FlightSegment[]` |

Query 参数：

| 参数 | 类型 | 必填 | 格式 | 说明 |
|---|---|---:|---|---|
| `airId` | `string` | 是 | - | 飞机注册号/机尾号 |
| `startTime` | `string` | 是 | `yyyy-MM-dd HH:mm:ss` | 查询开始时间 |
| `endTime` | `string` | 是 | `yyyy-MM-dd HH:mm:ss` | 查询结束时间 |

说明：

- 后端将时间字符串按 `Asia/Shanghai` 转为 Unix 秒。
- 当前实现兼容单/双位小时、分钟、秒。
- 返回数据不包含 `track` 轨迹点，仅用于让用户选择航段。

### 4.2 查询历史航段详情

| 项 | 内容 |
|---|---|
| Method | `GET` |
| Path | `/flightmap/history-tracks/detail` |
| Query | `airId`、`startTime`、`endTime` |
| Success data | `FlightTrack` 或 `null` |

Query 参数：

| 参数 | 类型 | 必填 | 格式 | 说明 |
|---|---|---:|---|---|
| `airId` | `string` | 是 | - | 飞机注册号/机尾号 |
| `startTime` | `number` | 是 | Unix 秒 | 航段开始时间，来自历史航段列表 |
| `endTime` | `number` | 是 | Unix 秒 | 航段结束时间，来自历史航段列表 |

说明：

- 后端精确查询 `[startTime, endTime]` 范围内的位置点。
- 返回 `FlightTrack.track`，按 `timeStamp` 升序排列。
- 未找到航段时返回 `data: null`。

## 5. 错误场景

| 场景 | code | data | 处理建议 |
|---|---|---|---|
| 无活跃飞机 | `0000` | `[]` | 前端展示空地图或空态 |
| 指定飞机没有活跃航段 | `0000` | `null` | 前端提示暂无活跃轨迹 |
| 历史航段不存在 | `0000` | `null` | 前端提示未找到航段 |
| 参数格式错误 | `0001` | `null` | 返回明确错误说明 |
| 数据库异常 | `0001` | `null` | 记录日志并返回服务端错误 |
| 缓存异常但数据库兜底成功 | `0000` | 正常数据 | 不影响前端展示 |

## 6. 接口验收

- `GET /flightmap/active-tracks` 在无数据时返回 `code=0000` 和空数组。
- 写入 `schema.md` 中的最小样例数据，并将 `time_stamp` 调整到当前时间附近后，活跃快照能返回 `B-5688`。
- `GET /flightmap/active-tracks/B-5688/latest` 能返回按时间升序排列的 `track`。
- `track` 中每个点都包含经纬度、航向、高度、速度和姿态角字段。
- 任何接口失败时都返回统一响应对象，不直接返回裸字符串或 HTML 错误页。
