# 单机飞机实时轨迹展示需求说明

> 本文档用于把旧项目的飞机轨迹演示改造成当前“前中后舱网联数据分析平台”的可实施方案。当前项目已有 Vue 3 前端、Spring Boot 后端、PostgreSQL 数据库和本地 UDP 模拟器。轨迹数据以模拟器 `qar.frame` 为事实来源，不再按旧项目的多机 `system_position`/`flight_status` 方案迁移。

## 1. 背景与目标

当前项目的模拟器已经按单架飞机生成连续 QAR 数据，并通过 UDP `8090` 端口发送 `qar.frame`。后端解析后写入 `data_record` 和 `qar_sample`，并通过 `flight_session` 区分同一模拟器的不同起飞批次。

目标是在平台中新增“飞机轨迹实时系统”页面，参考 `docs/前中后舱网联数据平台 2.png`：

- 中央为离线地图或地图底图上的单架飞机实时位置、航向和轨迹线。
- 左侧展示飞行状态卡片、经纬度曲线、海拔高度与地速曲线。
- 右侧展示航向、横滚、俯仰等姿态或方向曲线。
- 顶部导航与现有平台品牌、登录态和页面布局保持一致。
- 后端提供单机当前快照和当前航段轨迹 API，前端轮询刷新。

## 2. 范围

### 2.1 纳入范围

- 从当前 `flight_session` 的最新 QAR 点查询飞机位置。
- 只查询当前 `flight_session_id` 对应的轨迹点，并按时间升序返回。
- 从 `data_record` 或 `qar_sample` 补齐飞机注册号、机型、航司、航班号、起降机场。
- 为前端提供图表所需的经纬度、高度、地速、航向、横滚、俯仰等时间序列字段。
- 前端实现一个单机实时轨迹大屏页面，并接入平台导航。
- 地图使用 `frontend/map/tiles_street` 下的 EPSG:3857 XYZ 离线瓦片，前端通过 OpenLayers 渲染底图、飞机点和轨迹线。

### 2.2 不纳入范围

- 多架飞机同时展示。
- 旧项目 Redis 缓存、`system_position`、`flight_status`、历史多航段回放的整套迁移。
- 飞机调度、航班计划、航司/机场后台维护。
- 登录、权限、数据管理、乘客实时动态、舷窗、IFE、流量统计等无关模块改造。
- 大规模地图瓦片制作与部署。当前只接入项目已有 `map` 目录，不新增瓦片生产流程。

## 3. 当前事实来源

| 来源 | 当前项目位置 | 用途 |
| --- | --- | --- |
| QAR 模拟器 | `simulator/udp_simulator/flight_model.py` | 生成单架飞机的连续位置、速度、高度、航向等数据 |
| UDP 接入 | `backend/src/main/java/com/cabin/udp/service/UdpIngestService.java` | 接收并持久化模拟器数据 |
| QAR 解析 | `backend/src/main/java/com/cabin/udp/service/UdpPayloadParser.java` | 把 `qar.frame` 解析为 `qar_sample` 行 |
| QAR 表 | `backend/src/main/resources/db/migration/V4__create_simulator_business_tables.sql` | 当前轨迹展示的事实表 |
| 前端平台 | `frontend/src/router/index.ts`、`frontend/src/views` | 新增页面和导航入口 |

## 4. QAR 字段映射

轨迹展示只依赖 `qar.frame` 已有字段，不要求模拟器新增新协议。

| 展示/接口字段 | QAR 原始字段 | 当前表字段 | 说明 |
| --- | --- | --- | --- |
| `flightNo` | `FLIGHT NUMBER` | `qar_sample.flight_no` | 航班号 |
| `originAirportCode` | `ORIGIN` | `qar_sample.origin` | 起飞机场，当前为四字 ICAO |
| `destinationAirportCode` | `DESTINATION` | `qar_sample.destination` | 目的机场，当前为四字 ICAO |
| `sampleAt` | `time` + 接收日期 | `qar_sample.sample_at` | 采样时间 |
| `latitude` | `PRES POSN LAT - FMC` | `qar_sample.latitude` | 纬度 |
| `longitude` | `PRES POSN LONG - FMC` | `qar_sample.longitude` | 经度 |
| `altitudeFt` | `BARO COR ALT NO. 1` | `qar_sample.altitude_ft` | 高度，单位 ft |
| `groundSpeedKt` | `GROUNDSPEED` | `qar_sample.ground_speed_kt` | 地速，单位 kt |
| `trackAngleDeg` | `TRACK ANGLE TRUE - FMC` | `qar_sample.track_angle_deg` | 真航迹角，地图飞机朝向优先使用 |
| `headingDeg` | `CAPT DISPLAY HEADING` | `qar_sample.heading_deg` | 显示航向，可用于曲线对比 |
| `pitchDeg` | `BODY PITCH RATE` | `qar_sample.pitch_deg` | 当前字段名来自模拟协议，展示时标为“俯仰”或“俯仰量” |
| `rollDeg` | `BODY ROLL RATE` | `qar_sample.roll_deg` | 当前字段名来自模拟协议，展示时标为“横滚”或“横滚量” |
| `distanceToGoNm` | `DISTANCE TO GO` | `qar_sample.distance_to_go_nm` | 剩余航程，单位 NM |
| `destinationEtaText` | `DESTINATION ETA` | `qar_sample.destination_eta_text` | 到达剩余时间文本 |
| `frameCount` | `frameCount` | `qar_sample.frame_count` | 帧序号，可辅助排查乱序 |

## 5. 单机业务规则

### 5.1 当前航班选择

当前项目模拟器约束为单架飞机。后端选择最近更新的 `ACTIVE` 飞行会话作为当前航班。

如果未来数据库中存在多个历史航班，当前航班按以下顺序确定：

1. 查询最近更新的 `ACTIVE` 飞行会话及其 `latest_qar_sample_id`。
2. 若没有新鲜数据，返回成功响应和 `data: null`。
3. 当前航班号取该会话的 `flight_no`。
4. 当前轨迹只返回同一 `flight_session_id` 的点。

### 5.2 活跃判断

- `sample_at` 在最近 `5` 分钟内。
- `latitude`、`longitude` 不为空且在合法范围内。
- `ground_speed_kt > 100` 视为飞行中；若速度为空或低于阈值，页面展示空态或“未进入飞行状态”。
- 前端不重复判定活跃状态，只消费后端返回。

### 5.3 轨迹窗口

- 当前航段最多查询当前会话最近 `24` 小时的 QAR 点。
- 轨迹点必须按 `sample_at` 升序返回。
- 模拟器帧号重置、航线变化或断流超过 5 分钟时创建新会话，旧会话轨迹不会进入当前页面。

### 5.4 机场和航司名称

- QAR 使用四字 ICAO，例如 `ZBAA`、`ZSPD`、`ZGGG`、`ZUUU`、`ZSHC`。
- 最小方案在后端使用静态映射返回中文机场名称。
- 航司优先来自 `data_record.airline_code`，其次可由航班号前两位推断。
- 映射缺失时返回原始代码，不报错。

## 6. 前端展示要求

### 6.1 页面结构

页面命名建议：

- 路由：`/flight-track`
- 页面：`FlightTrackView.vue`
- API 文件：`frontend/src/api/flightTrack.ts`
- 样式：`frontend/src/styles/views/flightTrack.css`

参考图只定义信息层级，不要求像素级复刻。页面应和现有平台视觉一致：

- 顶部保留平台品牌和导航。
- 当前导航高亮“飞机轨迹实时系统”。
- 中央地图占主要视口。
- 左右信息面板贴边布局，避免遮挡飞机主视图。
- 单架飞机和轨迹线使用 OpenLayers 矢量图层绘制。轨迹线只连接后端返回的真实 QAR 点，不绘制预测线，不插值补点。

### 6.2 图表

图表数据来自同一份轨迹点：

| 面板 | 曲线 |
| --- | --- |
| 经纬度 | `latitude`、`longitude` |
| 海拔高度与地速 | `altitudeFt`、`groundSpeedKt` |
| 航向 | `trackAngleDeg`、`headingDeg` |
| 横滚 | `rollDeg` |
| 俯仰 | `pitchDeg` |

图表横轴使用 `sampleAt` 的本地时间文本。数据点过多时前端可抽样，但不得改变最新点。

### 6.3 轮询

- 前端默认每 `5` 秒请求一次当前轨迹。
- 页面不可见时暂停轮询。
- 请求失败时保留上一次成功数据，并展示轻量错误提示。
- 若后端返回 `data: null`，展示空态，不清空整个页面结构。

## 7. 后端能力要求

最小闭环只需要一个聚合接口：

- `GET /api/flight-track/current`

如果前端后续需要分离快照和轨迹，可增加：

- `GET /api/flight-track/current/snapshot`
- `GET /api/flight-track/current/points`

后端实现建议：

- 新增 `flight` 或 `flighttrack` 包，避免混入乘客实时动态模块。
- 使用 MyBatis 查询 `qar_sample` 和 `data_record`。
- 不引入 Redis 作为必需依赖。单机、10 秒一帧的模拟数据用数据库查询即可满足演示。
- 可选使用 Java 内存缓存保存最近一次聚合结果，TTL 不超过轮询间隔。

## 8. 验收标准

- 启动模拟器持续发送 `qar.frame` 后，`qar_sample` 持续新增有效轨迹点。
- `GET /api/flight-track/current` 返回当前航班信息、最新点和按时间升序的轨迹点。
- 没有 QAR 数据或数据超过 `5` 分钟时，接口返回成功且 `data: null`。
- 前端页面能显示单架飞机、轨迹线、航班卡片和五组图表。
- 飞机图标朝向优先使用 `trackAngleDeg`。
- 起降机场显示中文名称；映射缺失时显示原始 ICAO。
- 不新增旧项目的 `system_position`、`flight_status`，不把 Redis 作为必需组件。
- 不影响现有数据管理、登录、乘客实时动态和模拟器其他接口。
