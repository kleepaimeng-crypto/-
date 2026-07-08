# 飞机实时轨迹展示迁移开发约束

> 本文件给后续开发代理或开发者使用，限定“飞机实时轨迹展示”复用任务的技术栈、边界、目录和禁止事项。本文不要求创建 `TASKS.md` 或 `SLICES.md`。

## 1. 语言与编码

- 文档、注释和沟通默认使用简体中文。
- 文件编码使用 UTF-8。
- 读取项目文件时按 UTF-8 处理。
- 不要为了迎合需求而改写事实；遇到文档、代码、数据库不一致时，必须明确指出依据和取舍。

## 2. 需求文档顺序

开发或迁移前必须先阅读以下文档：

1. `docs/SREC.md`：飞机实时轨迹展示需求边界。
2. `docs/API.md`：接口路径、参数、响应和错误场景。
3. `docs/schema.md`：数据库表、字段、约束、关系、索引和示例数据。
4. `docs/AGENTS.md`：开发约束和禁止事项。

若文档之间出现冲突，处理优先级如下：

1. `docs/SREC.md` 定义功能边界。
2. `docs/API.md` 定义接口行为。
3. `docs/schema.md` 定义数据库结构。
4. 当前项目源码只作为事实来源和参考实现，不自动扩大复用范围。

## 3. 复用边界

只实现或迁移以下能力：

- 活跃飞机最新位置快照。
- 单机最新活跃航段完整轨迹。
- 轨迹点持久化表 `system_position`。
- 飞行状态补充表 `flight_status`。
- 后端用于航空公司、机场名称的映射能力。
- 可选：历史航段列表和历史航段详情接口。

不得顺手迁移以下模块：

- 用户、角色、权限、JWT 登录。
- 数据管理、Excel 导入导出、批量删除。
- 系统日志、操作日志、数据日志。
- 数据库备份恢复。
- WiFi、卫星通信、设备状态、乘客网络、流量统计等非轨迹展示数据。
- 前端静态图片资源、地图瓦片包、部署脚本，除非宿主项目明确需要。

## 4. 技术栈约束

后端推荐沿用：

| 项 | 约束 |
|---|---|
| 数据库 | PostgreSQL，按 `docs/schema.md` 建表 |
| 缓存 | 可使用 Java 内存缓存；是否使用 Redis 由宿主项目决定 |
| 时间戳 | 业务时间统一使用 Unix 秒 |
| 响应格式 | 遵循 `docs/API.md` 的 `{ code, info, data }` |
| 命名 | 数据库下划线，API JSON 驼峰 |

前端或调用方推荐沿用：

| 项 | 约束 |
|---|---|
| 轮询间隔 | 不低于 5 秒 |
| 地图绘制 | 使用 `longitude`、`latitude` 定位 |
| 飞机朝向 | 优先使用 `trueHeading` |
| 活跃判断 | 不在前端重复判定，后端只返回活跃飞机 |
| 轨迹排序 | 前端接收后仍可按 `timeStamp` 升序兜底排序 |

## 5. 数据规则

- `system_position` 是轨迹点事实表，不要新建重复事实表。
- `flight_status` 只用于补充航班业务信息，不作为实时轨迹点来源。
- `system_position.data_id` 固定为 `217`。
- `flight_status.data_id` 固定为 `210`。
- `ground_speed > 100` Knots 表示活跃飞行状态。
- 活跃快照数据库兜底查询只查最近 10 分钟，避免全表扫描。
- 单机最新活跃航段查询默认只查过去 8 小时。
- 位置点可能乱序到达，任何轨迹查询都必须按 `time_stamp` 升序。
- 机场代码必须在入库或映射层统一，不能长期混用三字码和四字码。

## 6. API 实现要求

必需接口：

- `GET /flightmap/active-tracks`
- `GET /flightmap/active-tracks/{airId}/latest`

可选接口：

- `GET /flightmap/history-tracks`
- `GET /flightmap/history-tracks/detail`

接口实现必须满足：

- 返回字段与 `docs/API.md` 一致。
- 无活跃飞机返回空数组，不返回错误。
- 未找到单机活跃航段返回成功响应和 `data: null`。
- 后端异常返回 `code=0001`，并提供明确 `info`。
- 不直接暴露数据库字段名给前端，除非字段本身就是 API 契约。

## 7. 数据库实现要求

- 后端可以直接按 `docs/schema.md` 建表。
- 必须创建 `system_position(air_id, time_stamp DESC)` 等价索引。
- 必须创建 `flight_status(flight_num, time_stamp DESC)` 等价索引。
- 不建议给 `system_position` 和 `flight_status` 建硬外键。
- 高频写入场景应做幂等或唯一约束处理，避免同一飞机、同一航班、同一秒重复位置点。
- 若宿主项目使用 TimescaleDB，可在 `system_position.time_stamp` 或派生时间列上做时序优化，但不得改变 API 字段契约。

## 8. 开发与修改原则

- 优先做最小可用闭环：建表、写入位置和状态、查活跃快照、查单机轨迹。
- 不添加未被 `SREC.md` 要求的配置项、抽象层或业务模块。
- 不重命名 `airId`、`flightNum`、`latestPoint`、`track` 等 API 字段。
- 不把历史回放做成实时展示的必需依赖。
- 不把航空公司和机场字典强制建表；最小方案允许后端映射。
- 若要扩展字段，必须保持现有字段向后兼容。

## 9. 禁止事项

- 禁止批量删除文件或目录。
- 禁止使用 `del /s`、`rd /s`、`rmdir /s`、`Remove-Item -Recurse`、`rm -rf`。
- 需要删除文件时，只能一次删除一个明确路径的文件。
- 不要修改与飞机实时轨迹展示无关的模块。
- 不要把当前项目的大 SQL 历史数据作为迁移必需内容。
- 不要在没有确认单位的情况下混用 ft 和 meters；`system_position.altitude` 按 meters，`flight_status.altitude` 按 ft。

## 10. 验收清单

- 已按 `docs/schema.md` 创建 `system_position` 和 `flight_status`。
- 已写入最小样例数据或等价测试数据。
- `GET /flightmap/active-tracks` 可返回活跃飞机快照。
- `GET /flightmap/active-tracks/{airId}/latest` 可返回完整 `track`。
- `track` 中轨迹点按 `timeStamp` 升序。
- 快照中 `latestPoint` 为该飞机最新有效位置点。
- 数据库查询命中索引，没有对 `system_position` 做无时间范围的全表扫描。
- 缺失飞行状态时，轨迹位置仍能展示。
- 缺失航空公司或机场映射时，接口仍返回原始代码或“未知”，不报错。
