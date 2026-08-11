# 飞机轨迹历史回放 REST API 契约

## 1. 公共约定

- 基础路径：`/api/v1/flight-history`。
- 认证：`Authorization: Bearer <JWT>`；允许任意 `ACTIVE` 角色访问。
- 响应、错误码、时间和 traceId 沿用 `../v1-docs/API.md`。
- 时间使用 ISO 8601 带时区字符串；数据库和服务层使用明确时区的时间类型。
- 所有接口只读，不暴露原始报文、数据库主库表或归档任务内部字段。

成功响应：

```json
{
  "code": "OK",
  "message": "success",
  "data": {},
  "traceId": "6a5d4129e4de4b25"
}
```

## 2. 枚举

### 2.1 `FlightFinishReason`

| 值 | 含义 |
| --- | --- |
| `LANDED` | 持续地面、低速、低高度，确认正常落地 |
| `TIMEOUT` | 超过 5 分钟未收到新 QAR |
| `NEW_FLIGHT` | 新 QAR 的航班号、起点或终点变化，或时间不连续 |
| `FRAME_RESET` | QAR 帧号重置 |

## 3. 历史航段对象

`FlightHistorySessionSummary`：

```json
{
  "id": "c21f57a9-d9e1-4ecc-9d3c-d9757a4b4753",
  "flightNo": "CA4732",
  "origin": "ZBAA",
  "destination": "ZSHC",
  "aircraftRegistrationNo": "B-1012",
  "aircraftModel": "Airbus A330-200",
  "airlineCode": "CA",
  "startedAt": "2026-07-24T08:00:00+08:00",
  "endedAt": "2026-07-24T10:18:00+08:00",
  "durationSeconds": 8280,
  "pointCount": 828,
  "finishReason": "LANDED",
  "archivedAt": "2026-07-24T10:19:04+08:00"
}
```

`FlightHistoryPoint`：

```json
{
  "sampleAt": "2026-07-24T09:20:00+08:00",
  "sampleTimeText": "09:20:00",
  "frameCount": 430,
  "airGroundStatus": "AIR",
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

## 4. 查询历史航段

`GET /api/v1/flight-history/sessions`

### 4.1 查询参数

| 参数 | 类型 | 必填 | 默认 | 规则 |
| --- | --- | ---: | --- | --- |
| `endedFrom` | ISO 8601 | 否 | — | 与 `endedTo` 至少提供一个；单独提供时查询至多覆盖其后的 31 天 |
| `endedTo` | ISO 8601 | 否 | — | 与 `endedFrom` 至少提供一个；单独提供时查询至多覆盖其前的 31 天 |
| `flightNo` | string | 否 | — | trim 后转大写，最长 20 |
| `origin` | string | 否 | — | 4 位机场代码 |
| `destination` | string | 否 | — | 4 位机场代码 |
| `aircraftRegistrationNo` | string | 否 | — | 最长 32 |
| `finishReason` | enum | 否 | — | `FlightFinishReason` 之一 |
| `page` | integer | 否 | 1 | 最小 1 |
| `pageSize` | integer | 否 | 20 | 只允许 20、50、100 |
| `sortBy` | string | 否 | `endedAt` | `startedAt`、`endedAt`、`flightNo`、`pointCount` |
| `sortDirection` | string | 否 | `desc` | `asc` 或 `desc` |

两个时间端点均存在时，时间跨度不得超过 31 天。只提供 `endedFrom` 时，后端以 `min(endedFrom + 31 天, 当前时间)` 作为上界；只提供 `endedTo` 时，后端以 `endedTo - 31 天` 作为下界。排序字段必须由后端白名单映射，并始终追加 `id` 作为稳定排序键。

### 4.2 成功响应

`data`：

```json
{
  "items": [],
  "page": 1,
  "pageSize": 20,
  "total": 314,
  "totalPages": 16
}
```

`items` 为 `FlightHistorySessionSummary[]`。

## 5. 查询单个历史航段元数据

`GET /api/v1/flight-history/sessions/{sessionId}`

- `sessionId` 为历史会话 UUID。
- 成功时 `data` 为完整 `FlightHistorySessionSummary`。
- 不存在返回 404 `RESOURCE_NOT_FOUND`。

## 6. 查询回放轨迹

`GET /api/v1/flight-history/sessions/{sessionId}/track`

### 6.1 查询参数

| 参数 | 类型 | 必填 | 默认 | 规则 |
| --- | --- | ---: | --- | --- |
| `from` | ISO 8601 | 否 | 会话开始时间 | 必须落在会话范围内 |
| `to` | ISO 8601 | 否 | 会话结束时间 | 必须落在会话范围内且不早于 `from` |
| `maxPoints` | integer | 否 | 3600 | 最小 2，最大 3600 |

后端按 `sampleAt, frameCount, sourceQarSampleId` 升序返回。若原始点数超过 `maxPoints`，使用等距抽样，并保留时间范围内第一个和最后一个点；不插值、不预测。

### 6.2 成功响应

```json
{
  "session": {
    "id": "c21f57a9-d9e1-4ecc-9d3c-d9757a4b4753",
    "flightNo": "CA4732",
    "origin": "ZBAA",
    "destination": "ZSHC",
    "aircraftRegistrationNo": "B-1012",
    "aircraftModel": "Airbus A330-200",
    "airlineCode": "CA",
    "startedAt": "2026-07-24T08:00:00+08:00",
    "endedAt": "2026-07-24T10:18:00+08:00",
    "durationSeconds": 8280,
    "pointCount": 828,
    "finishReason": "LANDED",
    "archivedAt": "2026-07-24T10:19:04+08:00"
  },
  "rangeStartAt": "2026-07-24T08:00:00+08:00",
  "rangeEndAt": "2026-07-24T10:18:00+08:00",
  "sourcePointCount": 828,
  "returnedPointCount": 828,
  "sampled": false,
  "track": []
}
```

## 7. 错误场景

| 场景 | HTTP | 错误码 |
| --- | ---: | --- |
| 未登录或 JWT 无效 | 401 | `UNAUTHORIZED` |
| 当前账号非 `ACTIVE` | 403 | `ACCOUNT_DISABLED` |
| 参数、时间范围、排序字段非法 | 400 | `VALIDATION_ERROR` |
| 历史会话不存在 | 404 | `RESOURCE_NOT_FOUND` |
| 数据库不可用 | 503 | `DATABASE_UNAVAILABLE` |

错误响应不得返回 SQL、约束名称、轨迹库内部表名或堆栈。
