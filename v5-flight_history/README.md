# 飞机轨迹历史回放阶段契约

本目录定义“前中后舱网联数据显示平台”飞机轨迹历史回放的产品、接口、数据和实施边界。

## 必读顺序

1. `SPEC.md`：范围、收尾规则、回放交互和验收标准。
2. `API.md`：历史航段查询和轨迹读取 REST API。
3. `schema.md`：现有数据库内 `flight_history` Schema 的表、索引和归档规则。
4. `SLICES.md`：实施顺序、文件范围和验证标准。
5. `AGENTS.md`：开发约束和禁止事项。

同时必须阅读基础和相关阶段契约：

- `../v1-docs/SPEC.md`
- `../v1-docs/API.md`
- `../v1-docs/schema.md`
- `../v1-docs/AGENTS.md`
- `../v3-planedocs/SREC.md`
- `../v3-planedocs/API.md`
- `../v3-planedocs/schema.md`

## 授权边界

本阶段只新增单机飞机的历史轨迹归档与回放能力：

- 在现有 `cabin_data_platform` 数据库中建立独立 `flight_history` Schema。
- 对已结束飞行会话进行异步、可重试归档。
- 查询历史航段并按时间范围筛选。
- 在地图上播放真实 QAR 轨迹，并联动状态卡和曲线图。
- 支持进度拖动、播放/暂停和 0.5×、1×、2×、4×、8× 倍速。

本阶段不实现多机实时监控、预测航迹、编辑历史数据、导出历史数据、跨库部署、删除历史轨迹或新的权限/RBAC 模型。

## 核心决策

- 历史数据仍放在当前 PostgreSQL 数据库 `cabin_data_platform`，不新建数据库服务。
- 历史表放在独立 PostgreSQL Schema `flight_history`，与实时 `public` 表逻辑隔离。
- 历史归档数据不执行自动删除；实时库既有保留策略不得删除已归档数据。
- `flight_session` 继续是实时会话事实来源，`qar_sample` 继续是实时 QAR 点事实来源；历史 Schema 只保存归档副本，不反向驱动实时页面。
- 收尾由新 QAR 的会话边界判断和定时收尾任务共同完成；归档任务只处理 `FINISHED` 会话。
