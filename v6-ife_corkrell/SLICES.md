# 科可瑞尔 IFE 单事件实施切片

## 1. 实施原则

- 按“固定协议确认 → 数据库迁移 → UDP 解析与入库 → 当前状态查询 → 模拟器 → 前端 → 联调验证”实施。
- 客户固定 JSON 是唯一外部协议事实来源；代码适配协议，不反向要求客户调整。
- 历史事件与当前状态职责分离，不以覆盖历史换取实时展示。
- 每个切片保持最小可验证闭环，不重构 633 IFE、QAR、舷窗、轨迹或用户管理。

## 2. 切片 0：契约确认

范围：评审本目录全部文档、客户 `KKRE接口测试_输出JSON格式.docx` 和现有座位图。

确认事项：

- UDP `8096` 的客户单事件格式固定。
- `errorDesc` 字段映射。
- 客户座位号（如 `03D`、`05F`）与 C929 页面座位号的映射。
- 633 IFE 不在本期变更范围。

完成标准：无未确认的座位映射、字段名称或航班切换歧义。

## 3. 切片 1：当前状态 Schema

新增：

```text
backend/src/main/resources/db/migration/V13__add_ife_cockrell_current_state.sql
```

目标：

- 创建 `ife_cockrell_current_state`、主键、约束和查询索引。
- 不修改既有 `ife_cockrell_behavior` 历史表和已发布迁移。

验证：空库迁移、V12 升级、同航班同座位 UPSERT、不同座位突发写入和乱序保护。

## 4. 切片 2：UDP 单事件解析与历史入库

修改：

```text
backend/src/main/java/com/cabin/udp/service/UdpPayloadParser.java
backend/src/main/java/com/cabin/udp/service/UdpIngestService.java
backend/src/main/java/com/cabin/udp/mapper/UdpIngestMapper.java
```

目标：

- 8096 直接解析 `sysInfo`、`paxInfo`、`behaviorInfo`、`extInfo`。
- 正确映射 `errorDesc`。
- 一条报文写入一条 `data_record` 和一条 `ife_cockrell_behavior`。
- 在同一事务内执行当前状态 UPSERT。

验证：客户样例、缺少必填字段、非法行为类型、单座位更新、乱序事件和相同事件时间。

## 5. 切片 3：乘客实时快照

修改：

```text
backend/src/main/java/com/cabin/passenger/service/PassengerRealtimeService.java
backend/src/main/java/com/cabin/passenger/mapper/PassengerRealtimeMapper.java
backend/src/main/java/com/cabin/passenger/dto/
```

目标：

- 从当前状态表读取当前航班的科可瑞尔最新状态。
- 固定返回 282 个座位；未收到事件的座位保持行为字段为空，由前端展示为空闲。
- 影音排行只统计已知电影与音乐状态。
- 不向实时接口泄露 PNR、原始 JSON、完整订单和封面 Base64。

验证：初始空闲座位、单座位更新、282 座位突发、航班切换和页面选中状态保留。

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
- 提供单事件、普通突发、全舱突发场景。
- 全舱突发连续发送 282 个独立 UDP 数据报。

验证：每包结构、端口、UTF-8 编码、固定随机种子、三类场景的事件数量和座位覆盖。

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
- 保留现有轮询、座位选择和滚动位置。
- 按 `seatNo` 更新快照，不为未收到事件的座位填充假数据。

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

1. 单事件历史入库和当前状态更新。
2. 10、50、282 条独立事件突发。
3. 同座位新旧事件顺序。
4. 当前航班切换和初始空闲座位。
5. 数据管理页面事件历史与乘客实时页面当前状态的一致性。
6. 不影响 UDP 8090～8095 的既有接口。
