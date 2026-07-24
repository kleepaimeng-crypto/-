# 前中后舱网联数据分析平台接口格式 Schema

> 来源：`前中后舱网联数据分析平台.docx`  
> 用途：整理所有接口的数据格式、字段、枚举和嵌套结构，便于后续开发数据模拟器。  
> 修订约定：模拟器阶段忽略认证、HTTP GET、分页查询等要求；所有数据均作为 UDP payload 发送到本地端口。  
> 说明：文档中未明确 UDP 本地端口号、错误响应和部分字段约束，本 schema 仅按原文可确认内容整理。

## 0. 模拟器统一传输约定

- 传输协议：UDP。
- 发送目标：本机地址，建议默认 `127.0.0.1`。
- 端口：按数据类型配置，本文件暂不固定端口号。
- 编码：JSON 类数据统一 UTF-8。
- 认证：忽略，不需要 `X-API-Key`。
- HTTP 方法：忽略，不需要实现 GET 接口。
- 数据模式：模拟器主动按时间、频率或场景脚本向本地 UDP 端口推送数据。

### 0.1 推荐本地 UDP 通道

| 通道 | 数据对象 | Payload 格式 | 本地端口 |
| --- | --- | --- | --- |
| `qar.frame` | QAR 数据帧 | JSON | 待配置 |
| `ground.task` | 地面任务数据 | JSON | 待配置 |
| `ground.traffic_record` | 流量明细窗口数据 | JSON | 待配置 |
| `ground.session_summary` | 会话摘要数据 | JSON | 待配置 |
| `smart_window.status` | 智能舷窗状态 | JSON | 待配置 |
| `ife_633.behavior` | 633 IFE 乘客行为 | JSON | 待配置 |
| `ife_cockrell.behavior` | 科克瑞尔 IFE 乘客行为 | JSON | 待配置 |

## 1. QAR 数据格式

### 1.1 数据格式

- 格式：JSON
- 字符编码：未明确，建议按 UTF-8 处理
- 来源：前中后舱数据仿真工控机，现阶段为 QAR 数据
- 模拟器传输：作为 UDP JSON payload 发送到本地端口
- 推荐消息类型：`qar.frame`

### 1.2 示例

```json
{
  "AIR GND ON GND": "GROUND",
  "BARO COR ALT NO. 1": "0",
  "COMPUTED AIRSPEED": "45",
  "DESTINATION": "ZSHC",
  "DESTINATION ETA": "0:26.2",
  "DISTANCE TO GO": "0",
  "FLIGHT NUMBER": "4783",
  "GROUNDSPEED": "0",
  "ORIGIN": "ZSQD",
  "PRES POSN LAT - FMC": "36.411113024",
  "PRES POSN LONG - FMC": "120.09225375",
  "TRACK ANGLE TRUE - FMC": "71.71824",
  "frameCount": 1517,
  "time": "23:04:55"
}
```

### 1.3 关键字段

| 字段 | 建议类型 | 含义 |
| --- | --- | --- |
| `FLIGHT NUMBER` | string | 航班号 |
| `BARO COR ALT NO. 1` | string/number | 高度 |
| `GROUNDSPEED` | string/number | 地速 |
| `TRACK ANGLE TRUE - FMC` | string/number | 实际航行方向 |
| `CAPT DISPLAY HEADING` | string/number | 选择航行方向 |
| `COMPUTED AIRSPEED` | string/number | 实际飞行速度 |
| `PRES POSN LAT - FMC` | string/number | 纬度 |
| `PRES POSN LONG - FMC` | string/number | 经度 |
| `ORIGIN` | string | 起点 |
| `DESTINATION` | string | 目的地 |
| `DESTINATION ETA` | string | 预计到达时间 |
| `DISTANCE TO GO` | string/number | 总剩余航程 |
| `AIR GND ON GND` | string | 地面状态标记 |
| `BODY PITCH RATE` | string/number | 机体俯仰角 |
| `BODY ROLL RATE` | string/number | 机体滚转角 |
| `LT MAIN FUEL QTY` | string/number | 左主油箱油量 |
| `RT MAIN FUEL QTY` | string/number | 右主油箱油量 |
| `CENTER MAIN FUEL QTY` | string/number | 中央油箱油量 |
| `LOW FUEL QTY TANK1/2` | string/boolean | 油箱低油量告警 |
| `frameCount` | integer | 帧计数 |
| `time` | string | 时间 |

## 2. 乘客数据仿真工控机 UDP 数据消息

原文中的外部开放接口在模拟器中不按 HTTP 查询实现，统一改为 UDP 推送数据消息。原 `GET` 路径仅作为数据来源标识，不作为开发要求。

### 2.1 UDP 消息封装建议

可以直接发送业务对象；如果需要统一路由，建议增加轻量消息封装：

```json
{
  "messageType": "ground.task",
  "sentAt": "2026-06-10T19:20:00+08:00",
  "payload": {}
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `messageType` | string | UDP 消息类型，例如 `ground.task`、`ground.traffic_record`、`ground.session_summary` |
| `sentAt` | string | 模拟器发送时间 |
| `payload` | object/array | 业务数据对象，见下方各消息 schema |

### 2.2 批量发送格式，可选

如需一次 UDP 包发送多条记录，可使用数组 payload：

```json
{
  "messageType": "ground.traffic_record",
  "sentAt": "2026-06-10T19:42:20+08:00",
  "payload": []
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `payload` | object | 单条业务数据 |
| `payload` | array | 多条业务数据 |

### 2.3 UDP 消息 `ground.task`

用途：模拟地面端任务数据。来源对应原 `/external/v1/tasks`。

#### Payload：Task

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `taskId` | string | 任务业务标识 |
| `flightNo` | string | 航班号 |
| `scenarioName` | string | 场景名称 |
| `status` | string | 任务状态，例如 `registered`、`running`、`paused`、`finished` |
| `phase` | string | 当前飞行阶段，例如 `cruise` |
| `terminalCount` | integer | 终端数量 |
| `startedAt` | string | 任务开始时间 |
| `endedAt` | string/null | 任务结束时间 |
| `downlinkTargetMbps` | number | 下行回灌峰值目标 |
| `statisticsWindowSeconds` | integer | 统计窗口秒数 |
| `totalBytes` | integer | 累计字节数 |
| `failureReason` | string/null | 失败原因 |
| `rerunSourceTaskId` | string/null | 重跑来源任务 |
| `archived` | boolean | 是否归档 |

#### Payload 示例

```json
{
  "taskId": "CA-FLIGHT-20260610-001",
  "flightNo": "CA1234",
  "scenarioName": "上海浦东 -> 北京首都 巡航压测",
  "status": "running",
  "phase": "cruise",
  "terminalCount": 400,
  "startedAt": "2026-06-10T19:20:00+08:00",
  "endedAt": null,
  "downlinkTargetMbps": 600.0,
  "statisticsWindowSeconds": 5,
  "totalBytes": 9812574208,
  "failureReason": null,
  "rerunSourceTaskId": null,
  "archived": false
}
```

备注：历史 fixture `contracts/fixtures/task-list.page1.json` 中出现过 `scenarioId`；当前 FastAPI 响应模型以 `flightNo` 为准。

### 2.4 UDP 消息 `ground.traffic_record`

用途：模拟流量明细窗口数据。来源对应原 `/external/v1/traffic-records`、`/external/v1/tasks/{task_id}/traffic`、`/external/v1/tasks/{task_id}/terminals/{terminal_id}/traffic`。

#### Payload：TrafficRecord

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `windowStart` | string | 统计窗口开始时间 |
| `windowEnd` | string | 统计窗口结束时间 |
| `taskId` | string | 任务业务标识 |
| `terminalId` | string | 后端真实终端标识，例如 `T-0001` |
| `displayTerminalId` | string | 前端展示终端标识，例如 `A47` |
| `seatLabel` | string | 座位号 |
| `application` | string | 应用类型或业务标签 |
| `protocol` | string | 协议，例如 `TCP`、`UDP` |
| `direction` | string | 方向，例如 `downlink` |
| `bytesCount` | integer | 该窗口字节数 |
| `packetCount` | integer | 该窗口包数 |
| `throughputMbps` | number | 该窗口平均吞吐 |
| `peakMbps` | number | 峰值吞吐 |
| `recordStatus` | string | 记录状态，例如 `recorded`、`delayed` |

### 2.5 按任务过滤语义

模拟器不实现查询过滤。需要“某任务流量”时，直接在 `TrafficRecord.taskId` 中填入对应任务业务标识。

### 2.6 UDP 消息 `ground.session_summary`

用途：模拟会话摘要数据。来源对应原 `/external/v1/session-summaries`。

#### Payload：SessionSummary

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `sessionId` | string | 会话标识 |
| `taskId` | string | 任务业务标识 |
| `terminalId` | string | 后端真实终端标识 |
| `displayTerminalId` | string | 前端展示终端标识 |
| `seatLabel` | string | 座位号 |
| `application` | string | 应用类型或业务标签 |
| `protocol` | string | 协议 |
| `startedAt` | string | 会话开始时间 |
| `durationSeconds` | integer | 会话持续秒数 |
| `uplinkBytes` | integer | 上行字节数 |
| `downlinkBytes` | integer | 下行字节数 |
| `averageThroughputMbps` | number | 平均吞吐 |
| `peakThroughputMbps` | number | 峰值吞吐 |
| `status` | string | 会话状态 |

### 2.7 按任务和终端过滤语义

模拟器不实现路径参数。需要“指定任务、指定终端的流量”时，直接在 `TrafficRecord.taskId` 和 `TrafficRecord.terminalId` 中填入对应值。

## 3. 633 CCS 智能舷窗接口

### 3.1 基本信息

| 项 | 值 |
| --- | --- |
| 接口 ID | `INT-003` |
| 源系统 | 633 CCS 智能舷窗 |
| 目标系统 | FLYHUB 机载链路管理服务软件 |
| 功能描述 | CIU 传输智能舷窗明暗挡位数据给 FLYHUB，再由 FLYHUB 转发到平台 |
| 协议类型 | UDP，本地端口 |
| 数据格式 | JSON |
| 字符编码 | UTF-8 |

### 3.2 JSON Schema

| 字段名 | 类型 | 说明 | 取值 | 必填 |
| --- | --- | --- | --- | --- |
| `windowId` | int | 舷窗编号 | `1~118` | 是 |
| `zoneId` | int | 舱位区域 | `1=前舱`，`2=中舱`，`3=后舱` | 是 |
| `brightnessLevel` | int | 明暗挡位 | `0~10`，0 最暗，10 最亮 | 是 |
| `connectStatus` | bool | 连通性状态 | `1=连通`，`0=不连通` | 是 |
| `status` | string | 舷窗状态 | `NORMAL`、`FAULT`、`TEST` | 是 |
| `timestamp` | string | 时间戳 | `yyyy-MM-dd HH:mm:ss.sss` | 是 |

### 3.3 status 枚举

| 枚举值 | 含义 |
| --- | --- |
| `NORMAL` | 正常 |
| `FAULT` | 异常 |
| `TEST` | 测试 |

## 4. 633 IFE 接口

### 4.1 基本信息

| 项 | 值 |
| --- | --- |
| 接口 ID | `INT-001` |
| 源系统 | 633 IFE |
| 目标系统 | 前中后舱网联数据软件平台 |
| 功能描述 | 传输乘客业务行为，包括电影播放、音乐播放、头等舱投屏及乘客通过 WAP 上网 |
| 协议类型 | UDP，本地端口 |
| 数据格式 | JSON |
| 字符编码 | UTF-8 |

### 4.2 顶层结构

```json
{
  "sysInfo": {},
  "paxInfo": {},
  "behaviorInfo": {},
  "extInfo": {}
}
```

| 字段名 | 数据类型 | 长度 | 说明 | 取值/示例 | 必填 |
| --- | --- | --- | --- | --- | --- |
| `sysInfo` | object | - | 系统基础信息，第一层嵌套 | 见 `sysInfo` | 是 |
| `paxInfo` | object | - | 乘客核心信息，第一层嵌套 | 见 `paxInfo` | 是 |
| `behaviorInfo` | object | - | 业务行为信息，第一层嵌套，核心场景层 | 见业务场景表 | 是 |
| `extInfo` | object | - | 扩展字段，第一层嵌套，可选 | 见 `extInfo` | 否 |

### 4.3 sysInfo

| 字段名 | 数据类型 | 长度 | 说明 | 取值/示例 | 必填 |
| --- | --- | --- | --- | --- | --- |
| `timestamp` | string | 24 | 时间戳 | `2026-04-08 15:30:22.123` | 是 |
| `flightId` | string | 20 | 航班号 | `CZ633`、`CA123` | 是 |

### 4.4 paxInfo

| 字段名 | 数据类型 | 长度 | 说明 | 取值/示例 | 必填 |
| --- | --- | --- | --- | --- | --- |
| `pnr` | string | 16 | 订座编码 | `ABC123` | 是 |
| `seatNo` | string | 8 | 座位号 | `A12`、`F32` | 是 |
| `cabinClass` | string | 8 | 舱位等级 | `ECONOMY`、`FIRST` | 是 |
| `deviceId` | string | 24 | 终端设备 ID | `SVDU-01-05`、`ANDROID-1234` | 是 |
| `userId` | string | 32 | 乘客标识 | `PAX-00001` | 是 |

### 4.5 extInfo

| 字段名 | 数据类型 | 长度 | 说明 | 取值/示例 | 必填 |
| --- | --- | --- | --- | --- | --- |
| `errorCode` | string | 8 | 错误码，异常时填写 | `0000`、`1001` | 否 |
| `errorDesc` | string | 64 | 错误描述 | `投屏设备连接超时` | 否 |

### 4.6 behaviorInfo：MOVIE_PLAY 看电影

| 字段名 | 数据类型 | 长度 | 说明 | 取值/示例 | 必填 |
| --- | --- | --- | --- | --- | --- |
| `behaviorType` | string | 16 | 业务行为类型标识 | `MOVIE_PLAY` | 是 |
| `contentId` | string | 32 | 影片唯一 ID | `MOV-001-2026`、`MOV-002-2026` | 是 |
| `contentName` | string | 64 | 影片名称 | `流浪地球 3` | 是 |
| `contentType` | string | 16 | 影片类别 | `科幻`、`喜剧` | 是 |
| `contentDuration` | int | 8 | 影片总时长，单位分钟 | `157`、`128`、`180` | 是 |
| `playAction` | string | 16 | 播放动作 | `PLAY`、`PAUSE`、`STOP`、`SEEK` | 是 |
| `playPosition` | int | 8 | 播放进度，单位秒 | `360`、`600`、`180` | 是，`PLAY`/`SEEK` 时 |
| `resolution` | string | 16 | 播放分辨率 | `720P`、`1080P`、`4K` | 是 |

### 4.7 behaviorInfo：MUSIC_PLAY 听音乐

| 字段名 | 数据类型 | 长度 | 说明 | 取值/示例 | 必填 |
| --- | --- | --- | --- | --- | --- |
| `behaviorType` | string | 16 | 业务行为类型标识 | `MUSIC_PLAY` | 是 |
| `musicId` | string | 32 | 音乐唯一 ID | `MUS-005-2026`、`MUS-006-2026` | 是 |
| `musicName` | string | 64 | 音乐名称 | `青花瓷` | 是 |
| `musicType` | string | 16 | 音乐类型 | `中国风` | 是 |
| `artist` | string | 32 | 艺术家/歌手 | `周杰伦` | 是 |
| `album` | string | 32 | 专辑名称 | `魔杰座` | 否 |
| `playAction` | string | 16 | 播放动作 | `PLAY`、`PAUSE`、`NEXT`、`PREV` | 是 |
| `playPosition` | int | 8 | 播放进度，单位秒 | `120`、`80`、`150` | 是，`PLAY` 时 |
| `volume` | int | 3 | 播放音量，0-100，0 为静音 | `70`、`50`、`90` | 是 |

### 4.8 behaviorInfo：CAST_SCREEN 头等舱投屏

| 字段名 | 数据类型 | 长度 | 说明 | 取值/示例 | 必填 |
| --- | --- | --- | --- | --- | --- |
| `behaviorType` | string | 16 | 业务行为类型标识 | `CAST_SCREEN` | 是 |
| `targetDevice` | string | 24 | 投屏目标设备 | `SVDU-F01`、`SVDU-F02`、`HEAD-CABIN-SCREEN-01` | 是 |
| `castAction` | string | 16 | 投屏动作 | `CAST`、`STOP` | 是 |
| `castStatus` | string | 16 | 投屏状态 | `CONNECTED`、`FAILED`、`DISCONNECTED` | 是 |
| `resolution` | string | 16 | 投屏分辨率 | `720P`、`1080P`、`4K` | 是 |
| `castDuration` | int | 8 | 投屏时长，单位秒，仅投屏成功时填写 | `1800`、`3600`、`600` | 否 |

### 4.9 behaviorInfo：WAP_BROWSING WAP 上网

| 字段名 | 数据类型 | 长度 | 说明 | 取值/示例 | 必填 |
| --- | --- | --- | --- | --- | --- |
| `behaviorType` | string | 16 | 业务行为类型标识 | `WAP_BROWSING` | 是 |
| `sessionId` | string | 32 | 上网会话唯一 ID | `WAP-SESSION-001`、`WAP-SESSION-002` | 是 |
| `srcIp` | string | 18 | 终端 IP 地址 | `192.168.1.100` | 是 |
| `dstIp` | string | 18 | 目标 IP 地址 | `180.101.49.11` | 是 |
| `dstDomain` | string | 64 | 目标域名，IP 访问时为空 | `www.baidu.com`、`www.weixin.com` | 否 |
| `protocol` | string | 8 | 网络协议 | `HTTP`、`HTTPS`、`TCP`、`UDP` | 是 |
| `port` | int | 5 | 目标端口 | `80`、`443`、`8080` | 是 |

### 4.10 633 IFE 枚举

#### behaviorType

| 枚举值 | 含义 |
| --- | --- |
| `MOVIE_PLAY` | 电影播放行为 |
| `MUSIC_PLAY` | 音乐播放行为 |
| `CAST_SCREEN` | 头等舱投屏行为 |
| `WAP_BROWSING` | WAP 上网行为 |

#### cabinClass

| 枚举值 | 含义 |
| --- | --- |
| `FIRST` | 头等舱 |
| `BUSINESS` | 公务舱 |
| `ECONOMY` | 经济舱 |

#### playAction

| 枚举值 | 含义 |
| --- | --- |
| `PLAY` | 开始播放 |
| `PAUSE` | 暂停播放 |
| `STOP` | 停止播放 |
| `SEEK` | 进度拖动，仅电影 |
| `NEXT` | 下一首，仅音乐 |
| `PREV` | 上一首，仅音乐 |

#### resolution

| 枚举值 | 含义 |
| --- | --- |
| `720P` | 高清 |
| `1080P` | 全高清 |
| `4K` | 超高清 |

#### castAction

| 枚举值 | 含义 |
| --- | --- |
| `CAST` | 开始投屏 |
| `STOP` | 停止投屏 |

#### castStatus

| 枚举值 | 含义 |
| --- | --- |
| `CONNECTED` | 投屏连接成功 |
| `FAILED` | 投屏失败 |
| `DISCONNECTED` | 投屏断开 |

#### protocol

| 枚举值 | 含义 |
| --- | --- |
| `HTTP` | HTTP 协议 |
| `HTTPS` | HTTPS 加密协议 |
| `TCP` | TCP 协议 |
| `UDP` | UDP 协议 |

#### coverMimeType

| 枚举值 | 说明 |
| --- | --- |
| `jpeg` | JPEG 格式图片 |
| `png` | PNG 格式图片 |
| `webp` | WebP 格式图片 |

## 5. 科克瑞尔 IFE 接口

### 5.1 基本信息

| 项 | 值 |
| --- | --- |
| 接口 ID | `INT-001` |
| 源系统 | 科克瑞尔 IFE |
| 目标系统 | 前中后舱网联数据软件平台 |
| 功能描述 | 传输乘客业务行为，包括看电影、看音乐、看新闻、购物 |
| 协议类型 | UDP，本地端口 |
| 数据格式 | JSON |
| 字符编码 | UTF-8 |

### 5.2 顶层结构

```json
{
  "sysInfo": {},
  "paxInfo": {},
  "behaviorInfo": {},
  "extInfo": {}
}
```

顶层结构与 633 IFE 一致：

| 字段名 | 数据类型 | 长度 | 说明 | 取值/示例 | 必填 |
| --- | --- | --- | --- | --- | --- |
| `sysInfo` | object | - | 系统基础信息，第一层嵌套 | 见 `sysInfo` | 是 |
| `paxInfo` | object | - | 乘客核心信息，第一层嵌套 | 见 `paxInfo` | 是 |
| `behaviorInfo` | object | - | 业务行为信息，第一层嵌套，核心场景层 | 见业务场景表 | 是 |
| `extInfo` | object | - | 扩展字段，第一层嵌套，可选 | 见 `extInfo` | 否 |

### 5.3 sysInfo

| 字段名 | 数据类型 | 长度 | 说明 | 取值/示例 | 必填 |
| --- | --- | --- | --- | --- | --- |
| `timestamp` | string | 24 | 时间戳 | `2026-04-08 15:30:22.123` | 是 |
| `flightId` | string | 20 | 航班号 | `CZ633`、`CA123` | 是 |

### 5.4 paxInfo

| 字段名 | 数据类型 | 长度 | 说明 | 取值/示例 | 必填 |
| --- | --- | --- | --- | --- | --- |
| `pnr` | string | 16 | 订座编码 | `ABC123` | 是 |
| `seatNo` | string | 8 | 座位号 | `A12`、`F32` | 是 |
| `cabinClass` | string | 8 | 舱位等级 | `ECONOMY`、`FIRST` | 是 |
| `deviceId` | string | 24 | 终端设备 ID | `SVDU-01-05`、`ANDROID-1234` | 是 |
| `userId` | string | 32 | 乘客标识 | `PAX-00001` | 是 |

### 5.5 extInfo

| 字段名 | 数据类型 | 长度 | 说明 | 取值/示例 | 必填 |
| --- | --- | --- | --- | --- | --- |
| `errorCode` | string | 8 | 错误码，异常时填写 | `0000`、`1001` | 否 |
| `errorDesc` | string | 64 | 错误描述 | `投屏设备连接超时` | 否 |

### 5.6 behaviorInfo：MOVIE_PLAY 看电影

| 字段名 | 数据类型 | 长度 | 说明 | 取值/示例 | 必填 |
| --- | --- | --- | --- | --- | --- |
| `behaviorType` | string | 16 | 业务行为类型标识 | `MOVIE_PLAY` | 是 |
| `contentId` | string | 32 | 影片唯一 ID | `MOV-001-2026`、`MOV-002-2026` | 是 |
| `contentName` | string | 64 | 影片名称 | `流浪地球 3` | 是 |
| `contentType` | string | 16 | 影片类别 | `科幻`、`喜剧` | 是 |
| `contentDuration` | int | 8 | 影片总时长，单位分钟 | `157`、`128`、`180` | 是 |
| `playAction` | string | 16 | 播放动作 | `PLAY`、`PAUSE`、`STOP`、`SEEK` | 是 |
| `playPosition` | int | 8 | 播放进度，单位秒 | `360`、`600`、`180` | 是，`PLAY`/`SEEK` 时 |
| `resolution` | string | 16 | 播放分辨率 | `720P`、`1080P`、`4K` | 是 |
| `coverBase64` | string | <=524288 | 封面图片 | Base64 编码串 | 否 |
| `coverMimeType` | string | 32 | 媒体类型 | `jpeg`、`png` | 否 |
| `coverChecksum` | string | 64 | 封面数据校验和，SHA256 | 十六进制字符串 | 否 |

### 5.7 behaviorInfo：MUSIC_PLAY 听音乐

| 字段名 | 数据类型 | 长度 | 说明 | 取值/示例 | 必填 |
| --- | --- | --- | --- | --- | --- |
| `behaviorType` | string | 16 | 业务行为类型标识 | `MUSIC_PLAY` | 是 |
| `musicId` | string | 32 | 音乐唯一 ID | `MUS-005-2026`、`MUS-006-2026` | 是 |
| `musicName` | string | 64 | 音乐名称 | `青花瓷` | 是 |
| `musicType` | string | 16 | 音乐类型 | `中国风` | 是 |
| `artist` | string | 32 | 艺术家/歌手 | `周杰伦` | 是 |
| `album` | string | 32 | 专辑名称 | `魔杰座` | 否 |
| `playAction` | string | 16 | 播放动作 | `PLAY`、`PAUSE`、`NEXT`、`PREV` | 是 |
| `playPosition` | int | 8 | 播放进度，单位秒 | `120`、`80`、`150` | 是，`PLAY` 时 |
| `volume` | int | 3 | 播放音量，0-100，0 为静音 | `70`、`50`、`90` | 是 |
| `coverBase64` | string | <=524288 | 封面图片 | Base64 编码串 | 否 |
| `coverMimeType` | string | 32 | 媒体类型 | `jpeg`、`png` | 否 |
| `coverChecksum` | string | 64 | 封面数据校验和，SHA256 | 十六进制字符串 | 否 |

### 5.8 behaviorInfo：WAP_BROWSING 看新闻

| 字段名 | 数据类型 | 长度 | 说明 | 取值/示例 | 必填 |
| --- | --- | --- | --- | --- | --- |
| `behaviorType` | string | 16 | 业务行为类型标识 | `WAP_BROWSING` | 是 |
| `sessionId` | string | 32 | 上网会话唯一 ID | `WAP-SESSION-001`、`WAP-SESSION-002` | 是 |
| `srcIp` | string | 18 | 终端 IP 地址 | `192.168.1.100` | 是 |
| `dstIp` | string | 18 | 目标 IP 地址 | `180.101.49.11` | 是 |
| `dstDomain` | string | 64 | 目标域名，IP 访问时为空 | `www.baidu.com`、`www.weixin.com` | 否 |
| `protocol` | string | 8 | 网络协议 | `HTTP`、`HTTPS`、`TCP`、`UDP` | 是 |
| `port` | int | 5 | 目标端口 | `80`、`443`、`8080` | 是 |
| `trafficBytes` | long | 16 | 累计上网流量，单位字节 | `10240`、`1048576` | 是 |
| `url` | string | 256 | 访问 URL 地址 | `https://www.baidu.com/s?wd=机票`、`https://weixin.qq.com` | 是 |

### 5.9 behaviorInfo：SHOPPING 购物

#### 主字段

| 字段名 | 数据类型 | 长度 | 说明 | 取值/示例 | 必填 |
| --- | --- | --- | --- | --- | --- |
| `behaviorType` | string | 16 | 业务行为类型标识 | `SHOPPING` | 是 |
| `orderList` | array | - | 购物订单列表，可多个 | 数组 | 是 |

#### orderList 子项

| 字段名 | 数据类型 | 长度 | 说明 | 取值/示例 | 必填 |
| --- | --- | --- | --- | --- | --- |
| `orderId` | string | 32 | 订单唯一编号 | `ORDER-20260408-001` | 是，`BUY`/`PAY`/`CANCEL` 时 |
| `totalPrice` | decimal | 8 | 订单总金额，元 | `209.00` | 是 |
| `shopAction` | string | 16 | 订单操作类型 | `ADD`、`BUY`、`PAY`、`CANCEL` | 是 |
| `payStatus` | string | 16 | 支付状态 | `UNPAID`、`PAID`、`FAILED`、`REFUND` | 是 |
| `payType` | string | 16 | 支付方式 | `ALIPAY`、`WECHAT`、`CREDITCARD` | 否 |
| `goodsList` | array | - | 商品列表，单个订单下多个商品 | 数组 | 是 |

#### goodsList 子项

| 字段名 | 数据类型 | 长度 | 说明 | 取值/示例 | 必填 |
| --- | --- | --- | --- | --- | --- |
| `goodsId` | string | 32 | 商品 ID | `GOODS-001` | 是 |
| `goodsName` | string | 64 | 商品名称 | `航空纪念模型` | 是 |
| `goodsType` | string | 16 | 商品类型 | `DUTYFREE`、`FOOD`、`DIGITAL`、`SOUVENIR` | 是 |
| `quantity` | int | 4 | 购买数量 | `1`、`2`、`3` | 是 |
| `unitPrice` | decimal | 8 | 商品单价，元 | `199.00` | 是 |
| `coverBase64` | string | <=524288 | 商品封面 Base64 | Base64 字符串 | 是 |
| `coverMimeType` | string | 32 | 封面媒体类型 | `image/jpeg`、`image/png` | 是 |

### 5.10 科克瑞尔 IFE 枚举

#### behaviorType

| 枚举值 | 含义 |
| --- | --- |
| `MOVIE_PLAY` | 电影播放行为 |
| `MUSIC_PLAY` | 音乐播放行为 |
| `WAP_BROWSING` | 看新闻行为 |
| `SHOPPING` | 购物行为 |

#### cabinClass

| 枚举值 | 含义 |
| --- | --- |
| `FIRST` | 头等舱 |
| `BUSINESS` | 公务舱 |
| `ECONOMY` | 经济舱 |

#### playAction

| 枚举值 | 含义 |
| --- | --- |
| `PLAY` | 开始播放 |
| `PAUSE` | 暂停播放 |
| `STOP` | 停止播放 |
| `SEEK` | 进度拖动，仅电影 |
| `NEXT` | 下一首，仅音乐 |
| `PREV` | 上一首，仅音乐 |

#### resolution

| 枚举值 | 含义 |
| --- | --- |
| `720P` | 高清 |
| `1080P` | 全高清 |
| `4K` | 超高清 |

#### protocol

| 枚举值 | 含义 |
| --- | --- |
| `HTTP` | HTTP 协议 |
| `HTTPS` | HTTPS 加密协议 |
| `TCP` | TCP 协议 |
| `UDP` | UDP 协议 |

#### coverMimeType

| 枚举值 | 说明 |
| --- | --- |
| `jpeg` | JPEG 格式图片 |
| `png` | PNG 格式图片 |
| `webp` | WebP 格式图片 |

#### shopAction

| 枚举值 | 含义 |
| --- | --- |
| `ADD` | 加入购物车 |
| `BUY` | 提交订单/购买 |
| `PAY` | 支付订单 |
| `CANCEL` | 取消订单 |

#### payStatus

| 枚举值 | 含义 |
| --- | --- |
| `UNPAID` | 未支付 |
| `PAID` | 已支付 |
| `FAILED` | 支付失败 |
| `REFUND` | 已退款 |

#### payType

| 枚举值 | 含义 |
| --- | --- |
| `ALIPAY` | 支付宝 |
| `WECHAT` | 微信支付 |
| `CREDITCARD` | 信用卡 |

#### goodsType

| 枚举值 | 含义 |
| --- | --- |
| `DUTYFREE` | 免税品 |
| `FOOD` | 餐食饮品 |
| `DIGITAL` | 数码产品 |
| `SOUVENIR` | 纪念品 |

## 6. 模拟器开发建议的数据对象索引

| 模拟对象 | 对应接口/结构 | 推荐用途 |
| --- | --- | --- |
| `QarFrame` | QAR JSON | 飞机轨迹、姿态、高度、速度、油量模拟 |
| `Task` | `/external/v1/tasks` | 地面任务列表和任务状态模拟 |
| `TrafficRecord` | `/external/v1/traffic-records` 等 | 终端流量窗口数据模拟 |
| `SessionSummary` | `/external/v1/session-summaries` | 终端会话聚合数据模拟 |
| `SmartWindowStatus` | 633 CCS 智能舷窗 JSON | 舷窗亮度、连接、故障状态模拟 |
| `IfePassengerBehavior633` | 633 IFE JSON | 电影、音乐、投屏、WAP 行为模拟 |
| `IfePassengerBehaviorCockrell` | 科克瑞尔 IFE JSON | 电影、音乐、新闻、购物行为模拟 |
