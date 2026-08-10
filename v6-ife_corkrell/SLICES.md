# 科可瑞尔 IFE 单事件实施切片

## 1. 实施原则

- 按“固定协议确认 → UDP 解析与入库 → QAR 会话内存状态 → 模拟器 → 前端 → 联调验证”实施。
- 客户固定 JSON 是唯一外部协议事实来源；代码适配协议，不反向要求客户调整。
- 历史事件持久入库，当前状态只保存在后端内存；不以覆盖历史换取实时展示。
- 每个切片保持最小可验证闭环，不重构 633 IFE、QAR、舷窗、轨迹或用户管理。

## 2. 切片 0：契约确认

范围：评审本目录全部文档、客户 `KKRE接口测试_输出JSON格式.docx` 和现有座位图。

确认事项：

- UDP `8096` 的客户单事件格式固定。
- `errorDesc` 字段映射。
- 客户座位号（如 `03D`、`05F`）与 C929 页面座位号的映射。
- 科可瑞尔事件与现有 QAR 当前飞行上下文的复用和航班不匹配处理。
- 633 IFE 不在本期变更范围。

完成标准：无未确认的座位映射、字段名称或航班切换歧义。

## 3. 切片 1：数据库边界确认

目标：

- 不新增 Flyway 迁移，不清理旧科可瑞尔数据，不修改 `ife_cockrell_behavior`。
- KKRE 单事件继续固定写入 `item_no = 1`。

验证：既有科可瑞尔历史可查询，新单事件写入不破坏 `(record_id, item_no)` 约束。

## 4. 切片 2：UDP 单事件解析与历史入库

修改：

```text
backend/src/main/java/com/cabin/udp/service/UdpPayloadParser.java
backend/src/main/java/com/cabin/udp/service/UdpIngestService.java
backend/src/main/java/com/cabin/udp/service/CurrentFlightContextService.java
backend/src/main/java/com/cabin/flighttrack/service/FlightSessionService.java
backend/src/main/java/com/cabin/udp/mapper/UdpIngestMapper.java
```

目标：

- 8096 直接解析 `sysInfo`、`paxInfo`、`behaviorInfo`、`extInfo`。
- 正确映射 `errorDesc`。
- 一条报文写入一条 `data_record` 和一条 `ife_cockrell_behavior`。
- 事务提交成功后更新后端内存缓存。
- 复用当前 QAR 航班会话补齐管理字段；仅匹配会话的 IFE 以 `flightSessionId + seatNo` 更新缓存，IFE 不得切换 QAR 当前飞行上下文。
- 对重复航班号，仅事件时间不早于当前会话 `startedAt - 5 分钟` 的匹配事件可以更新缓存；会话结束或切换时清除旧缓存。

验证：客户样例、缺少必填字段、非法行为类型、单座位更新、乱序事件和相同事件时间。

## 5. 切片 3：乘客实时快照

修改：

```text
backend/src/main/java/com/cabin/passenger/service/PassengerRealtimeService.java
backend/src/main/java/com/cabin/passenger/mapper/PassengerRealtimeMapper.java
backend/src/main/java/com/cabin/passenger/dto/
```

目标：

- 从后端内存缓存读取当前 QAR 航班会话的科可瑞尔最新状态；缓存缺失时不从历史表重建。
- 当前航班必须由现有 QAR 航班会话确定，不能使用最新 IFE 事件推断。
- 本期仅返回 KKRE 状态，不合并 633 IFE 状态；633 的接收、协议和历史查询不变。
- 固定返回 282 个座位；未收到事件的座位保持行为字段为空，由前端展示为空闲。
- 影音排行只统计已知电影与音乐状态。
- 不向实时接口泄露 PNR、原始 JSON、完整订单和封面 Base64。

验证：初始空闲座位、单座位更新、282 座位突发、QAR 会话切换、重复航班号的迟到事件、IFE 航班不匹配、服务重启后等待新事件和页面选中状态保留。

## 6. 切片 4：科可瑞尔事件模拟器

修改：

```text
simulator/udp_simulator/ife_model.py
simulator/udp_simulator/simulator.py
simulator/tests/
simulator/README.md
simulator/接口格式Schema.md
simulator/数据模拟器开发指导.md
```

目标：

- 移除科可瑞尔全员分页生成和 `items` 包装。
- 每个发送包直接生成一名乘客的客户固定 JSON。
- 每个航段首次发送全舱初始化：连续发送 282 个独立 UDP 数据报；同一航段后续默认随机发送单座位事件。
- 保留 `single`、`burst`、`full` 三种测试模式。
- 保持现有 QAR 优先的定时任务发送顺序，不增加上下文就绪等待；包含已确认的 `playAction = PLAY`、`PAUSE` 场景。

验证：每包结构、端口、UTF-8 编码、固定随机种子、QAR 优先发送顺序、上下文尚未就绪的容错、每航段首轮 282 座位覆盖、后续随机单座位事件，以及 `PLAY`、`PAUSE` 展示语义。

## 7. 切片 5：前端状态展示

修改：

```text
frontend/src/api/types.ts
frontend/src/composables/usePassengerRealtime.ts
frontend/src/components/passenger/
frontend/src/styles/views/passengerRealtime.css
```

目标：

- 当 `behaviorType` 或 `eventAt` 为空时展示“空闲”。
- 已确认的 `playAction = PLAY`、`PAUSE` 均展示为对应影音行为的真实操作，不得视作空闲。
- 保留现有轮询、座位选择和滚动位置。
- 按 `seatNo` 更新快照，不为未收到事件的座位填充假数据。
- 将乘客实时轮询间隔从 5 秒调整为 2 秒；282 条事件自最后一条数据报到达起，页面完整显示目标不超过 3 秒。

验证：初始空闲状态、局部座位刷新、背景刷新失败保留上次成功内容、窄宽度布局。

## 8. 切片 6：联调与验收

必跑命令：

```powershell
Set-Location simulator
E:\developTool\anaconda3\envs\myenv\python.exe -m unittest discover -s tests -v

Set-Location ..\backend
.\mvnw.cmd test
.\mvnw.cmd verify

Set-Location ..\frontend
npm run typecheck
npm run test
npm run build
```

联调重点：

1. 单事件历史入库和内存状态更新。
2. 10、50、282 条独立事件突发。
3. 同座位新旧事件顺序。
4. 当前航班切换和初始空闲座位。
5. 数据管理页面事件历史与乘客实时页面当前状态的一致性。
6. 不影响 UDP 8090～8095 的既有接口。
