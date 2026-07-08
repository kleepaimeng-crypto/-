# 飞机实时轨迹展示可复用需求说明

> 说明：本项目未发现现成 `SPEC.md`。本文件按现有文档、流程图、前后端实现和 SQL 结构提取“飞机实时轨迹展示”部分需求，作为后续 `schema.md` 的需求依据。

## 1. 目标

飞机实时轨迹展示用于在地图上展示当前活跃飞机的位置、朝向、速度、高度和最新航段轨迹。该能力应能从其他项目的数据接入层复用，只要求新项目持续写入飞机位置点和飞行状态数据，不绑定本项目的登录、数据管理、日志、备份和其他 UDP 业务表。

## 2. 复用范围

纳入范围：

- 接收或导入系统位置数据，形成按飞机和时间排序的轨迹点。
- 接收或导入飞行状态数据，补充航班号、航空公司、起飞机场、目的机场、预计到达时间和剩余距离。
- 提供活跃飞机最新位置快照，用于地图上绘制飞机标记。
- 用户点击某架飞机后，提供该飞机最新活跃航段的完整轨迹点。
- 支持内存缓存优先、数据库兜底的实时查询策略。

不纳入范围：

- 用户登录、权限、审计日志、数据导入导出、数据库备份。
- WiFi、卫星通信、设备状态、乘客网络、流量统计等非轨迹展示数据。
- 地图瓦片、飞机图标、航空公司图片等静态前端资源。
- `TASKS.md`、`SLICES.md` 等开发切片文档。

## 3. 事实来源

- `PROJECT_GUIDE.md`：确认后端使用 Spring Boot、PostgreSQL、Redis，`flightmap` 模块提供实时航班地图和历史航迹查询。
- `SLAICD.md`：确认 `flight_status` 与 `system_position` 的原始下发字段。
- `飞行轨迹展示.png`：确认前端轮询活跃快照、点击飞机后查询完整轨迹的流程。
- `FlightMapController.java` / `FlightMapServiceImpl.java`：确认接口、活跃判断、航段提取和缓存兜底逻辑。
- `FlightSnapshotDTO.java` / `FlightTrackDTO.java` / `FlightPointDTO.java` / `FlightSegmentDTO.java`：确认前端响应字段。
- `SystemPosition.java` / `FlightStatus.java` / `aviation.sql`：确认现有数据库字段。
- `RearchMap.vue` / `FlightTracking.js`：确认实时地图实际消费字段。

## 4. 数据来源

实时轨迹展示只依赖两类数据：

| 数据 | 当前表名 | data_id | 作用 |
|---|---|---:|---|
| 系统位置数据 | `system_position` | `217` / `0xD9` | 轨迹点事实表，提供经纬度、高度、航向、姿态角、速度和时间戳 |
| 飞行状态数据 | `flight_status` | `210` / `0xD2` | 航班补充信息，提供航班号、起降机场、剩余时间、剩余距离等 |

`PROJECT_GUIDE.md` 中个别 data_id 描述与实体和 SQL 不一致。复用时以实体、Mapper 和 SQL 为准：`system_position.data_id = 217`，`flight_status.data_id = 210`。

## 5. 核心业务规则

### 5.1 活跃飞机快照

- 前端约每 5 秒请求一次活跃飞机快照。
- 后端优先从内存缓存读取每架飞机最新快照。
- 缓存无数据或数据不可用时，后端从 `system_position` 查询每架飞机最近一条位置点。
- 当前实现的数据库兜底新鲜度窗口为最近 10 分钟。
- 当前实现的活跃速度阈值为地速 `ground_speed > 100` Knots。
- 最新位置点低于或等于阈值、时间戳缺失、超出新鲜度窗口时，不应出现在活跃飞机列表中。

### 5.2 最新活跃航段

- 用户点击地图上的飞机后，前端按 `airId` 请求该飞机最新活跃航段。
- 当前实现查询该飞机过去 8 小时内的全部位置点，并按 `time_stamp` 升序排序。
- 后端从最新点向前查找最近一个 `ground_speed < 100` Knots 的中断点。
- 中断点之后的点构成最新活跃航段；如果最后一个点已经低于阈值，则认为没有活跃航段。
- 航段开始时间取第一条轨迹点 `time_stamp`，结束时间取最后一条轨迹点 `time_stamp`。

### 5.3 航班状态补充

- 快照和轨迹展示需要用 `flight_status` 补充航班信息。
- 当前实现主要按 `flight_num` 查询最新飞行状态；若没有匹配状态，仍应返回位置数据和基础航班号。
- `flight_num` 可为空字符串，但不建议为 `NULL`。
- 航空公司名称由航班号前两位映射得到。
- 机场名称由机场代码映射得到。
- 当前代码中的机场映射使用 PEK、PVG、CAN 等三字码；SLA 文档描述为四字码。复用时必须在入库或映射层统一机场代码体系，避免同一字段混用三字码和四字码。

## 6. 数据字段契约

### 6.1 轨迹点

轨迹点来自 `system_position`，展示层至少需要以下字段：

| 字段 | 说明 |
|---|---|
| `airId` | 飞机注册号/机尾号 |
| `flightNum` | 航班号 |
| `timeStamp` | 轨迹点时间戳，Unix 秒 |
| `latitude` | 纬度，单位 degrees |
| `longitude` | 经度，单位 degrees |
| `altitude` | 高度，系统位置数据中为 meters |
| `trueHeading` | 真航向，单位 degrees，用于飞机图标旋转 |
| `pitchAngle` | 俯仰角，单位 degrees |
| `rollAngle` | 横滚角，单位 degrees |
| `bodyPitchRate` | 俯仰率 |
| `bodyRollRate` | 横滚率 |
| `headingAngularRate` | 航向角速率 |
| `groundSpeed` | 地速，单位 Knots，用于活跃判断和速度图表 |

### 6.2 活跃快照

活跃快照每架飞机一条，至少包含：

| 字段 | 说明 |
|---|---|
| `airId` | 飞机注册号/机尾号 |
| `flightNum` | 航班号 |
| `airlineName` | 航空公司名称，由后端映射 |
| `orAirportCode` | 起飞机场代码 |
| `orAirportName` | 起飞机场名称，由后端映射 |
| `desAirportCode` | 目的机场代码 |
| `desAirportName` | 目的机场名称，由后端映射 |
| `timeToGo` | 预计到达剩余时间，分钟，允许为空 |
| `distanceToGo` | 预计到达剩余距离，miles，允许为空 |
| `latestPoint` | 最新轨迹点 |

### 6.3 完整轨迹

完整轨迹用于点击飞机后的详情展示，至少包含：

| 字段 | 说明 |
|---|---|
| `airId` | 飞机注册号/机尾号 |
| `flightNum` | 航班号 |
| `airlineName` | 航空公司名称 |
| `orAirportCode` / `orAirportName` | 起飞机场代码和名称 |
| `desAirportCode` / `desAirportName` | 目的机场代码和名称 |
| `timeToGo` | 预计到达剩余时间 |
| `distanceToGo` | 预计到达剩余距离 |
| `startTime` | 航段开始 Unix 秒 |
| `endTime` | 航段结束 Unix 秒 |
| `track` | 按时间升序排列的轨迹点数组 |

## 7. 接口行为

### 7.1 查询活跃飞机快照

- 方法：`GET`
- 路径：`/flightmap/active-tracks`
- 入参：无
- 返回：活跃飞机快照数组。
- 空数据：返回空数组，不应返回错误。
- 失败场景：数据库异常、缓存异常且数据库兜底失败时返回服务端错误。

### 7.2 查询单机最新活跃航段

- 方法：`GET`
- 路径：`/flightmap/active-tracks/{airId}/latest`
- 入参：`airId`，飞机注册号/机尾号。
- 返回：完整轨迹对象。
- 空数据：未找到活跃航段时返回 `null` 或等价空对象，由前端按空态处理。

## 8. 性能要求

- 活跃快照查询必须优先走内存缓存。
- 数据库兜底查询不得全表扫描，应限制时间窗口，并依赖 `system_position(air_id, time_stamp)` 或等价索引。
- 单机轨迹查询必须按 `air_id + time_stamp` 使用索引。
- `flight_status` 查询最新状态必须按 `flight_num + time_stamp` 使用索引。
- 前端轮询间隔建议不低于 5 秒；高并发项目应增加缓存或推送机制。

## 9. 数据质量要求

- `time_stamp` 使用 Unix 秒，代表机载数据发送时间。
- `received_at` 使用地面系统接收时间，用于排查延迟和乱序。
- 经纬度允许为空，但为空的轨迹点不能用于地图绘制。
- `ground_speed` 为空时按非活跃处理。
- 同一飞机、同一航班、同一时间戳的重复位置点应通过唯一约束或入库去重控制。
- 位置点可能乱序到达，查询轨迹时必须按 `time_stamp` 升序排序。

## 10. 验收标准

- 数据库存在 `system_position` 和 `flight_status` 后，后端可按 `schema.md` 建表并写入最小样例数据。
- 写入至少 2 个同一飞机、地速大于 100 Knots 的位置点后，活跃快照能返回该飞机最新位置。
- 点击该飞机后，能返回过去 8 小时内按时间升序排列的最新活跃航段。
- 轨迹点包含经纬度、航向、速度、高度和姿态角，前端可绘制飞机标记、轨迹线和速度/高度图表。
- 缺失飞行状态时，位置轨迹仍可展示；缺失机场或航空公司映射时，后端可返回原始代码或“未知”。
