# 科可瑞尔 IFE 单事件实施规则

## 1. 必读顺序

开发前按顺序阅读：

1. `v6-ife_corkrell/SPEC.md`
2. `v6-ife_corkrell/API.md`
3. `v6-ife_corkrell/schema.md`
4. `v6-ife_corkrell/SLICES.md`
5. 本文件
6. `v1-docs` 基础契约、`v2-phase2-contracts` 乘客实时契约、模拟器接口文档和当前相关源码

如文档冲突，优先级为 `SPEC.md`、`API.md`、`schema.md`、`SLICES.md`、本文件。客户固定 JSON 与内部旧批量格式冲突时，以客户格式为准，并同步修正内部契约。

## 2. 范围约束

只实现：

- 科可瑞尔 UDP `8096` 单用户事件接收。
- 事件历史入库与当前状态表。
- 当前航班、座位级最新状态读取。
- 科可瑞尔事件模拟器和相关前端空闲状态展示。

禁止顺手实现：

- 修改客户 JSON 格式、增加客户必须发送的字段或把单事件重新包装为批量数据。
- 修改 633 IFE 的协议、端口或生成逻辑。
- Redis、消息队列、WebSocket、SSE、对象存储或全量架构重构。
- 修改轨迹、舷窗、用户管理、数据管理产品流程或无关样式。

## 3. 协议与数据规则

- UDP `8096` 每包只含一个固定科可瑞尔事件对象。
- `flightId + seatNo` 是当前状态唯一键；不得只用 `passengerId` 或 `seatNo`。
- 科可瑞尔必须复用现有 UDP 当前飞行上下文；QAR 是当前飞行判断及航线信息的权威来源。
- IFE 的 `flightId` 与当前上下文不一致时，只保存该事件历史和其所属航班状态，不得由 IFE 切换全局飞行上下文。
- `timestamp` 是事件时间；旧事件只能进入历史表，不得覆盖当前状态。
- 客户字段为 `errorDesc`，后端内部可映射为 `errorDescription`，但不得拒绝客户字段。
- 未收到事件时后端行为字段为空；前端展示为空闲，不得填充电影、音乐、浏览或购物内容。
- 每条事件仍对应一条 `data_record` 和一条 `ife_cockrell_behavior`，以支持数据管理与审计。
- 当前状态表只保存最新状态，不能替代历史事件表。
- PNR、IP、完整订单、原始报文和封面 Base64 不得默认暴露给乘客实时接口。

## 4. 数据库规则

- 只通过新的 Flyway `V13` 创建 `ife_cockrell_current_state`；禁止修改已发布迁移。
- `ife_cockrell_behavior` 保持追加式历史表，不新增会覆盖历史的唯一约束。
- 历史事件插入和当前状态 UPSERT 必须在同一事务中。
- UPSERT 必须限制旧事件不覆盖新事件，且处理相同事件时间的稳定顺序。
- 282 座位突发写入必须按独立主键行安全处理；不得先删除整航班状态再重建。

## 5. 后端规则

- 解析器的科可瑞尔分支只接受固定根对象，不再调用批量 `items` 解析。
- UDP 端口映射仍决定数据类型；不得要求客户加 `messageType`。
- Controller 只处理协议，Service 处理事务和业务规则，Mapper 只处理 SQL。
- 当前快照查询必须补齐固定座位列表，返回顺序稳定。
- 数据库、解析或映射失败不得返回 SQL、内部表名或堆栈。

## 6. 模拟器规则

- 保持 UTF-8 与端口 `8096`。
- 科可瑞尔模拟器每次发送一名乘客的完整固定事件 JSON，不发 `items` 分页包。
- 突发场景通过短时间连续发送多个独立 UDP 包实现，不能用一个数组替代。
- 保持固定随机种子和可复现测试。
- 不修改 633 IFE 的数据结构或端口。

## 7. 前端规则

- 前端不直接接收 UDP；继续通过集中 API 获取快照。
- `behaviorType` 或 `eventAt` 为空的座位显示“空闲”，不得展示固定假内容。
- 自动刷新不得清除座位选择或重置滚动位置。
- 不显示 PNR、IP、原始报文、完整订单或封面 Base64。

## 8. 验证要求

至少运行：

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

必须覆盖：单事件、乱序事件、单座位更新、282 座位突发、航班切换、初始空闲状态、历史查询和实时查询。

## 9. 禁止事项

- 禁止批量删除文件或目录。
- 禁止使用 `del /s`、`rd /s`、`rmdir /s`、`Remove-Item -Recurse`、`rm -rf`。
- 禁止升级依赖、格式化整个仓库或删除用户已有改动。
- 禁止为了让页面有内容而用 `IDLE` 或固定假数据替代未收到的事件。
