# 前中后舱网联数据显示平台垂直切片计划（首期）

## 1. 使用方式

本文件把首期开发拆成可独立验证的垂直切片。除 Slice 0/1 的公共基线外，每个切片必须同时覆盖必要的数据库、后端、前端和测试，不能先堆完全部后端再集中补页面。

统一技术基线：Vue 3 + TypeScript + Element Plus；Java 21 + Spring Boot + MyBatis + Redis；Docker Compose + PostgreSQL 18。

执行规则：

1. 开始切片前核对 `SPEC.md`、`API.md`、`schema.md` 和根目录 `AGENTS.md`。
2. 一次只实现一个切片，不顺手开发后续页面或重构无关模块。
3. 接口或字段确需变更时，先更新契约文档并由负责人确认。
4. 完成后运行测试、类型检查和构建，再 Review diff。
5. 前一切片未通过完成标准，不进入依赖它的下一切片。

## 2. 总览

| 顺序 | 切片 | 可演示结果 | 主要依赖 |
| ---: | --- | --- | --- |
| 0 | 契约与工程基线 | 文档和目录约束完整 | 无 |
| 1 | Docker 数据库与迁移 | PostgreSQL 可启动，18 表自动创建 | Slice 0 |
| 2 | 管理员登录与公共后端 | 管理员可登录，统一错误与审计可用 | Slice 1 |
| 3 | UDP 入库闭环 | 7 路模拟数据进入统一目录和 7 张业务表 | Slice 1、2 |
| 4 | 数据列表与详情 | 页面可查真实数据和只读原报文 | Slice 3 |
| 5 | 标签、批注、删除恢复 | 数据管理操作形成审计闭环 | Slice 4 |
| 6 | 流量统计 | 乘客数据流量按任务、应用和终端可视化 | Slice 3 |
| 7 | 智慧舷窗展示 | 全机舷窗亮度、连通性和故障状态可视化 | Slice 3 |
| 8 | 分类型 CSV 导入 | 模板、导入任务和错误报告可用 | Slice 3、5 |
| 9 | CSV/PDF 导出 | 筛选结果可异步导出并下载 | Slice 4、5 |
| 10 | 联调、保留策略与验收 | 首期验收清单全部通过 | Slice 1–9 |

## 3. Slice 0：契约与工程基线

### 目标

锁定首期范围、接口、数据库、切片和 Agent 规则，避免实现阶段继续脑补需求。

### 产出

- `SPEC.md`、`API.md`、`schema.md`、`SLICES.md`、根级 `AGENTS.md`。
- 项目目录约定：`backend/`、`frontend/`、`simulator/`、`deploy/`、`docs/`、`storage/`。
- `.gitignore` 方案：忽略 `.env`、构建产物、日志、上传/导出文件和本地数据库卷信息。

### 测试与完成标准

- 五份文档中的技术栈、枚举、数据类型和首期边界一致。
- `schema.md` 定义 18 张首期表：8 张公共表、7 张模拟器业务表和 3 张展示派生表。
- `simulator/AGENTS.md` 保留并作为子目录补充约束。
- 文档不含真实密码、JWT 或无来源业务字段。

## 4. Slice 1：Docker 数据库与 Flyway

### 页面

无。

### 接口

仅后端健康检查：应用健康、数据库健康、Flyway 状态。不得暴露数据库密码。

### 数据库

- Docker Compose 启动 PostgreSQL 18，数据库 `cabin_data_platform`，应用用户 `cabin_app`。
- 使用命名卷持久化并配置 `pg_isready` 健康检查。
- 按 `schema.md` 建立 18 张表、外键、检查约束、索引和种子数据。
- 禁用 ORM 自动建表。

### 测试

- `docker compose config`。
- 从空卷启动、停止、重启后数据仍存在。
- Flyway 首次迁移和重复启动均成功。
- Testcontainers 使用 PostgreSQL 18 验证全部迁移。

### 完成标准

一条 Compose 命令可以启动健康数据库；后端连接后自动完成迁移，表数和种子数据符合 Schema。

## 5. Slice 2：管理员登录与公共后端

### 页面

- 简洁登录页。
- 路由守卫：未登录跳转登录页；JWT 失效清理会话。

### 接口

- `POST /api/v1/auth/login`
- `GET /api/v1/auth/me`

### 数据库

- `app_user`
- `audit_log`

### 后端公共能力

- JWT、BCrypt、统一响应、统一异常、参数校验、traceId、分页类型。
- 登录成功/失败审计和限流。
- 启动时按环境变量安全初始化管理员，不写默认明文密码。

### 测试

- 正确密码、错误密码、禁用账号、过期 JWT、缺少 JWT。
- 日志和审计快照不包含密码与 Token。
- 前端登录成功、错误提示和路由守卫测试。

### 完成标准

管理员可以安全登录并访问受保护的最小接口；所有错误使用 `API.md` 统一结构。

## 6. Slice 3：七路 UDP 入库

### 页面

无完整管理页；提供开发期接收统计或后端日志指标，不新增独立产品页面。

### 接口

- 后端内部 UDP 接收，不增加对外 HTTP 写入接口。
- 可在健康信息中提供各通道最近收包时间和失败计数，不能返回完整载荷。

### 数据库

- `data_type`：七类消息和端口映射。
- `data_record`：统一目录、公共筛选字段和完整原始 JSON。

### 实现

- 监听 8090–8096，端口映射严格按 `SPEC.md`。
- 后端生成 `data_record.id`，不要求模拟器提供 `messageId`。
- 补齐来源设备、接收时间、测试飞机、航班和航段等目录字段。
- 合法报文完整写入 `raw_payload`，并在同一事务写入对应业务表；非法 JSON 保存原文并标记 `FAILED`。
- 保留当前模拟器 `ground.traffic_record.windowStart` 与 `windowEnd` 可相等的窗口形态；数据库约束和解析逻辑允许 `window_end >= window_start`。
- QAR 报文没有外层 `sentAt`，只有 `time=HH:mm:ss`；后端使用接收日期与 QAR `time` 合成 `sent_at` 和 `qar_sample.sample_at`。
- `smart_window.status.timestamp` 和 IFE `sysInfo.timestamp` 为无时区文本，后端按 `Asia/Shanghai` 解析为 `timestamptz`。
- 舷窗报文不携带航班、航段和飞机字段，后端按当前模拟器上下文或默认测试飞机配置补齐 `data_record` 公共字段。
- `ife_cockrell.behavior` 的 `coverBase64` 只保留在父级 `data_record.raw_payload`；业务表仅保存 `cover_mime_type`、`cover_checksum` 和去除大字段后的 `behavior_detail`。

### 测试

- 为七类消息分别发送 fixture 数据报。
- 验证一份批量数据报只有一条 `data_record`，流量、会话、舷窗和 IFE 的 `items` 按序号拆入业务表，并仍可追溯到完整 JSONB。
- 验证 QAR、舷窗、IFE 三类时间字段按上述规则入库，且 `traffic_record.window_end = window_start` 的报文可以成功入库。
- 验证非法 UTF-8/JSON、缺字段、非法数值和未知端口配置。
- 连续运行现有模拟器，检查收包数量与入库数量。

### 完成标准

启动模拟器后七类消息均可追溯；公共管理字段正确提取，七类原报文完整保存且不可修改。

## 7. Slice 4：数据列表与详情

### 页面

- 按预设深色视觉实现数据管理主页面。
- 顶部筛选、服务端分页、排序、自动刷新开关。
- 详情抽屉展示管理信息、解析摘要、格式化原始 JSON/文本。
- loading、error、empty 三种状态完整。

### 接口

- `GET /api/v1/data-options`
- `GET /api/v1/data-records`
- `GET /api/v1/data-records/{recordId}`
- `PATCH /api/v1/data-records/{recordId}/metadata`

### 数据库

读取统一目录和数据类型；飞机、航班、设备信息直接来自 `data_record` 公共字段，仅修改允许的管理元数据。

### 测试

- 标签以外所有筛选、组合筛选、时间边界、分页和排序。
- 100,000 条目录记录下首屏常用查询目标不超过 1 秒。
- 自动刷新不打断编辑、选中状态和非第一页浏览。
- 确认 API 不提供修改原始报文的入口。

### 完成标准

管理员可从真实 UDP 数据完成“筛选列表 -> 查看详情”的完整操作，字段与界面稿和契约一致。

## 8. Slice 5：标签、批注、软删除与恢复

### 页面

- 标签管理弹窗或轻量面板。
- 单条/批量标签操作、单条/批量批注。
- 删除二次确认、已删除数据筛选和恢复入口。

### 接口

- `GET/POST/PATCH/DELETE /api/v1/tags...`
- `POST /api/v1/data-records/tags/batch`
- 记录批注查询、新增、批量新增、修改、删除接口。
- 单条/批量软删除和恢复接口。
- `GET /api/v1/audit-logs`

### 数据库

- `tag`、`data_record_tag`、`data_annotation`、`audit_log`
- 更新 `data_record` 的管理元数据和软删除字段，不修改原报文。

### 测试

- 标签重名、多对多关联、批量上限、批注长度。
- 乐观锁冲突、重复删除、未删除记录恢复、恢复期内恢复。
- 每次变更的操作前后审计快照。

### 完成标准

管理员可管理标签和批注，删除可恢复，所有写操作可审计且原始数据未改变。

## 9. Slice 6：流量统计

### 页面

- 在主导航开放“数据统计”中的流量统计视图，视觉基线参考第 6 张界面稿左侧“乘客影音统计”区域。
- 展示当前任务、统计窗口、总吞吐、峰值吞吐、累计流量、活跃终端数和会话数。
- 按应用类型展示流量条形排行，至少覆盖视频、音乐、网页浏览等模拟器应用名称。
- 支持按任务、应用、时间范围筛选；支持点击终端或座位查看该终端流量明细。
- loading、error、empty 和正常状态完整；自动刷新默认 5 秒，可暂停，不能打断终端详情查看。

### 接口

- `GET /api/v1/traffic-statistics/overview`
- `GET /api/v1/traffic-statistics/tasks/{taskId}/traffic`
- `GET /api/v1/traffic-statistics/tasks/{taskId}/terminals/{terminalId}/traffic`

平台前端不调用乘客数据仿真工控机的 `/external/v1/...` 路径；这些路径只作为上游主动拉取时的后端配置来源。若后续启用主动拉取，`X-API-Key` 只允许服务端通过环境变量读取。

### 数据库

- 读取 `simulation_task`、`traffic_record`、`session_summary`。
- 新增 `traffic_stat_snapshot` 作为可重算聚合快照。
- `packet_loss_rate` 当前保持 `NULL`，除非后续上游提供真实丢包字段或确认算法。

### 测试

- 按任务、时间窗口、应用类型和终端筛选统计。
- 验证统计值来自真实入库明细，不能从前端固定假数据生成。
- 验证无丢包来源时接口返回 `packetLossRate=null`，页面显示暂无数据。
- 验证 100,000 条流量明细下常用窗口查询和排行查询有索引支撑。

### 完成标准

管理员启动模拟器后，可以看到真实流量随时间刷新，并能按任务、应用和终端追溯到明细数据。

## 10. Slice 7：智慧舷窗展示

### 页面

- 在主导航开放“乘客实时动态”中的智慧舷窗展示视图，视觉基线参考第 6 张界面稿中部客舱和两侧“舷窗透光度”区域。
- 展示全机舷窗布局、左右侧、前/中/后舱分区、舷窗编号、亮度等级、连通性和状态。
- 支持显示区域汇总：平均亮度、断连数、故障数、测试状态数。
- 支持点击舷窗查看最近更新时间和来源记录 ID；原始报文仍通过数据详情只读查看。
- 自动刷新默认 5 秒，可暂停，不能导致当前选中舷窗丢失。


### 接口

- `GET /api/v1/smart-windows/display`

首期不实现舷窗控制、批量调光、远程下发指令或真实设备管理，只展示已入库状态。

### 数据库

- 读取 `smart_window_status` 和 `data_record`。
- 新增 `cabin_window_layout` 保存展示布局。
- 新增 `smart_window_current_status` 保存每个舷窗最新状态。
- `smart_window_current_status` 只由 UDP 入库或重算任务维护，页面不能直接修改。

### 测试

- 验证 200 个默认舷窗布局初始化完整。
- 验证同一 `window_id` 多次上报后最新状态正确覆盖。
- 验证乱序 UDP 包不会把较新的状态回写为旧状态。
- 验证无舷窗数据时页面显示 empty 状态，不使用固定业务假数据。
- 验证 1920x1080 和 1366 宽度下舷窗标签、亮度文字和状态点不重叠。

### 完成标准

管理员启动模拟器后，可以看到全机智慧舷窗亮度和状态随 UDP 数据刷新，并能按舱段识别异常、断连和测试状态。

## 11. Slice 8：分类型 CSV 导入

### 页面

- CSV 模板下载、数据类型选择、文件上传、进度和结果。
- 导入历史列表、任务详情、错误报告下载。

### 接口

- `GET /api/v1/imports/templates/{dataTypeCode}`
- `POST /api/v1/imports`
- `GET /api/v1/imports`
- `GET /api/v1/imports/{jobId}`
- `GET /api/v1/imports/{jobId}/error-file`

### 数据库

- `file_job`，其中 `job_type=IMPORT`。
- 导入成功行写入 `data_record` 和对应业务表，业务内容同时保留于原始 JSONB。

### 测试

- QAR、任务、流量、会话四种模板往返。
- 错误表头、缺字段、非法时间、非法枚举、坏行混合、空文件、50 MB/100,000 行边界。
- 幂等键重复提交和任务状态流转。

### 完成标准

四类 CSV 均可按模板导入；部分失败不会丢失成功行，错误 CSV 能准确定位每个坏行。

## 12. Slice 9：CSV/PDF 导出

### 页面

- 右侧导出配置、创建任务、导出历史和下载按钮。
- 导入历史与导出历史标题、字段和时间列正确区分。

### 接口

- `POST /api/v1/exports`
- `GET /api/v1/exports`
- `GET /api/v1/exports/{jobId}`
- `GET /api/v1/exports/{jobId}/file`

### 数据库

- `file_job`，其中 `job_type=EXPORT`。
- 下载动作写 `audit_log`。

### 测试

- 筛选快照在创建任务后不变化。
- CSV 中文、逗号、换行和 UTF-8 BOM。
- PDF 中文字体、分页表头、空结果和 5,000 行限制。
- CSV 100,000 行限制、文件过期和失败任务。

### 完成标准

当前筛选结果可异步导出；历史状态、行数、格式和下载文件一致，大结果不会一次性加载进 JVM 内存。

## 13. Slice 10：联调、保留策略与验收

### 页面

完成数据管理页在 1920x1080 和 1366 宽度下的布局检查；其他导航入口显示“待建设”且不可误入空白页面。

### 接口与数据库

- 运行 7+7 天保留任务的可控时间测试。
- 完成健康检查、错误处理、文件清理和审计查询。
- 不增加首期之外的业务接口或表。
- 验证 `traffic_stat_snapshot` 可重算，`smart_window_current_status` 能从 `smart_window_status` 重建。

### 测试

- 后端单元测试、PostgreSQL/Testcontainers 集成测试、UDP fixture 测试。
- 前端 `typecheck`、Vitest、Playwright 核心流程、生产构建。
- 从空环境执行 Docker、迁移、后端、前端和模拟器的完整启动演练。
- 按 `SPEC.md` 第 7 节逐项人工验收。
- Review diff：契约一致性、字段一致性、无关改动、敏感信息和剩余风险。

### 完成标准

首期验收项全部通过，运行和故障排查步骤写入 `docs/开发手册.md`，并形成可复现的最终验证记录。
