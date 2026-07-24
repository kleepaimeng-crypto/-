# 飞机轨迹历史回放实施切片

## 1. 实施原则

- 按“契约 → 迁移 → 会话收尾 → 归档 → 查询 API → 前端回放 → 全量验证”实施。
- 复用 `flight_session` 和 `qar_sample`，不创建第二套实时轨迹事实表。
- 历史表与实时表职责单向：实时会话结束后归档，历史查询不反写实时库。
- 每个切片保持最小可验证闭环，不重构乘客实时、用户管理或数据管理。

## 2. 切片 0：契约确认

范围：评审本目录全部文档，确认收尾阈值、Schema 隔离、永久保留、API 和回放交互。

完成标准：术语、字段、结束原因和权限无冲突；用户确认本目录为实现基线。

## 3. 切片 1：历史 Schema 与迁移

新增：

```text
backend/src/main/resources/db/migration/V11__add_flight_history.sql
```

目标：

- 创建 `flight_history` Schema。
- 创建历史会话、历史 QAR 点、归档任务表和索引。
- 不修改既有发布迁移，不删除实时数据。

验证：空库迁移、从 V10 升级、重复迁移、历史 Schema 约束和索引。

## 4. 切片 2：会话收尾与归档任务投递

建议新增：

```text
backend/src/main/java/com/cabin/flighthistory/
├─ service/FlightSessionClosingService.java
├─ service/FlightHistoryArchiveScheduler.java
├─ mapper/FlightHistoryArchiveMapper.java
└─ entity/
```

目标：

- 每分钟收尾断流会话和稳定落地会话。
- 对新航班、帧号重置、超时、落地写入明确结束原因。
- 为完成会话创建幂等归档任务。

验证：四种结束原因、与 UDP 入库并发、重复任务投递、无误结束正在接收数据的会话。

## 5. 切片 3：异步归档与重试

目标：

- 批量复制已结束会话及其 QAR 点至 `flight_history`。
- 单事务完成历史会话、轨迹点和任务状态更新。
- 失败记录脱敏错误摘要并按退避时间重试。

验证：成功归档、复制中故障回滚、幂等重跑、失败重试、归档后实时数据清理不影响历史查询。

## 6. 切片 4：历史查询 API

新增：

```text
backend/src/main/java/com/cabin/flighthistory/
├─ controller/FlightHistoryController.java
├─ service/FlightHistoryQueryService.java
├─ mapper/FlightHistoryQueryMapper.java
└─ dto/
```

目标：

- 实现历史航段分页筛选、详情和轨迹读取接口。
- 使用排序字段白名单、时间范围上限和后端等距抽样。
- 对任意 `ACTIVE` 角色开放只读访问。

验证：参数校验、权限、空列表、稳定排序、最大点数、首尾点保留和 404。

## 7. 切片 5：前端历史回放

建议新增：

```text
frontend/src/api/flightHistory.ts
frontend/src/composables/useFlightHistoryPlayback.ts
frontend/src/views/FlightHistoryView.vue
frontend/src/styles/views/flightHistory.css
```

目标：

- 启用“飞机轨迹回放系统”导航与 `/flight-history` 路由。
- 实现筛选、航段列表、轨迹加载和四态页面。
- 实现地图已飞轨迹、飞机定位、状态卡、图表游标、进度条、播放/暂停、回到起点和五档倍速。

验证：进度拖动、每种倍速、切换航段、离开页面停止播放、后台错误保留上次成功数据、1366 与 1920 宽度布局。

## 8. 切片 6：联调与验收

目标：

- 为模拟器或测试夹具提供起飞、巡航、下降、落地和断流数据；当前模拟器仅生成巡航 `AIR` 数据，不能覆盖落地收尾。
- 运行后端、前端和 PostgreSQL 迁移验证。
- 完成数据增长、备份和永久保留检查。

必跑命令：

```powershell
Set-Location backend
.\mvnw.cmd test
.\mvnw.cmd verify

Set-Location ..\frontend
npm run typecheck
npm run test
npm run build
```

完成标准：满足 `SPEC.md` 全部验收标准，且工作区 diff 不含与历史回放无关的改动。
