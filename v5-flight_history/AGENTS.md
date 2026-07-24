# 飞机轨迹历史回放实施规则

## 1. 必读顺序

开发前按顺序阅读：

1. `v5-flight_history/SPEC.md`
2. `v5-flight_history/API.md`
3. `v5-flight_history/schema.md`
4. `v5-flight_history/SLICES.md`
5. 本文件
6. `v3-planedocs` 轨迹契约、`v1-docs` 基础契约和当前相关源码

如文档冲突，优先级为 `SPEC.md`、`API.md`、`schema.md`、`SLICES.md`、本文件；文档和源码不一致时必须明确记录依据，不得静默猜测。

## 2. 范围约束

只实现：

- 单机历史飞行会话收尾、归档、查询和回放。
- 现有数据库中的 `flight_history` Schema。
- 时间范围筛选、进度控制、倍速、地图/状态/图表联动。

禁止顺手实现：

- 多机回放、预测航迹、历史数据导出或编辑。
- 新数据库服务、Redis、消息队列、对象存储或全量架构重构。
- 乘客实时、用户管理、数据管理或模拟器无关功能。

## 3. 数据库规则

- 只通过新的 Flyway `V11` 创建 `flight_history` Schema 和历史表；禁止修改已发布迁移。
- 历史表不执行自动删除，实时清理 SQL 必须只作用于 `public` 表。
- 历史归档必须以 `source_flight_session_id` 和 `source_qar_sample_id` 保证幂等。
- 不为历史表保存原始 UDP payload 或复制无关业务表。
- 历史查询必须使用 `(session_id, sample_at, frame_count, id)` 索引顺序。
- 结束原因必须为 `LANDED`、`TIMEOUT`、`NEW_FLIGHT` 或 `FRAME_RESET`，不得留空或自造字符串。

## 4. 会话与归档规则

- 现有 QAR 会话归属规则为同来源、同航班/航线、时间连续、帧号未重置；不得简化为仅航班号。
- `AIR GND ON GND` 是落地判断主信号，地速和高度仅作辅助；不得仅凭低速结束会话。
- 收尾与 QAR 入库必须防并发竞态；更新需限制目标仍为 `ACTIVE`。
- 归档失败不得回滚或阻塞实时 UDP 入库；必须可重试并保留失败摘要。
- 归档成功后不得修改或删除来源实时会话、QAR 点。

## 5. 后端规则

- 代码使用独立 `com.cabin.flighthistory` 包；实时 `flighttrack` 只保留最小必要集成。
- Controller 只处理协议和校验，Service 负责收尾、事务和播放查询规则，Mapper 负责 SQL。
- API 只返回 DTO，不返回 Entity、数据库字段名、原始 payload、SQL 或堆栈。
- 排序字段必须白名单映射；查询点超上限时后端抽样并保留首尾点。
- 定时任务应可测试，时间来源可注入；禁止把当前时间散落为不可控静态调用。

## 6. 前端规则

- 使用 Vue 3 Composition API 和 `<script setup lang="ts">`。
- API 调用集中在 `frontend/src/api/flightHistory.ts`，DTO 使用显式 TypeScript 类型，禁止 `any`。
- 播放器只消费后端返回的真实点，不插值、不预测、不制造轨迹假数据。
- 切换航段、路由离开和组件卸载时必须停止播放计时器。
- 页面处理 loading、error、empty、normal；后台错误保留上一次成功数据。
- 地图朝向优先用相邻真实点计算，单点再回退到 `trackAngleDeg` 或 `headingDeg`。

## 7. 验证要求

至少运行：

```powershell
Set-Location backend
.\mvnw.cmd test
.\mvnw.cmd verify

Set-Location ..\frontend
npm run typecheck
npm run test
npm run build
```

并验证：

- `LANDED`、`TIMEOUT`、`NEW_FLIGHT`、`FRAME_RESET` 四种收尾。
- 与 UDP 入库并发时的收尾正确性。
- 归档幂等、失败重试、实时清理后的历史可读性和永久保留。
- 时间范围、分页、排序、轨迹抽样、首尾点保留。
- 播放、暂停、拖动、五档倍速、航段切换及页面卸载。
- 1920×1080、1366 宽度页面布局。

## 8. 禁止事项

- 禁止批量删除文件或目录。
- 禁止使用 `del /s`、`rd /s`、`rmdir /s`、`Remove-Item -Recurse`、`rm -rf`。
- 禁止修改模拟器既有 UDP 字段结构；如需落地测试场景，只能新增既有字段的合法取值变化。
- 禁止升级依赖、格式化整个仓库或重构无关模块。
- 禁止触碰工作区已有的无关改动。
