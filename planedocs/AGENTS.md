# 单机飞机实时轨迹展示开发约束

> 本文件限定 `planedocs` 对应功能的后续开发边界。目标是把当前项目已有 QAR 模拟数据做成单机飞机实时轨迹页面，而不是迁移旧项目完整 flightmap 系统。

## 1. 必读顺序

开发或改文档前按以下顺序阅读：

1. `planedocs/SREC.md`：功能边界和当前项目事实来源。
2. `planedocs/API.md`：接口路径、DTO 和空态规则。
3. `planedocs/schema.md`：数据库复用方案和索引建议。
4. 本文件：开发约束、禁止事项和验收清单。

如文档冲突，优先级为：

1. `SREC.md`
2. `API.md`
3. `schema.md`
4. 当前项目源码事实

如果文档和源码不一致，必须明确指出依据，不要静默按旧项目假设实现。

## 2. 当前项目约束

| 项 | 约束 |
| --- | --- |
| 语言 | 沟通、文档、注释默认简体中文 |
| 编码 | UTF-8 |
| 前端 | 当前项目 Vue 3 + Vite，不另起 React 项目 |
| 后端 | 当前项目 Spring Boot + MyBatis + Flyway |
| 数据库 | PostgreSQL，优先复用已有 `qar_sample` |
| 数据来源 | 模拟器 `qar.frame`，当前只有单架飞机 |
| 地图 | 使用 `frontend/map/tiles_street` 的 EPSG:3857 XYZ 离线瓦片，前端用 OpenLayers 渲染 |
| 缓存 | Redis 不是必需项；首期可不用 |
| 轮询 | 前端不低于 5 秒 |

开发命令如需 Python，优先使用用户环境：

```powershell
E:\developTool\anaconda3\envs\myenv\python.exe
```

如果该环境不可用，再使用当前系统 `python`。

## 3. 必须遵守的复用边界

只实现当前项目需要的单机轨迹能力：

- 当前单架飞机最新有效 QAR 快照。
- 当前单架飞机最近轨迹点。
- 航班、起降机场、航司和飞机基础信息展示。
- 地图飞机标记、轨迹线和参考图中的曲线面板。
- 必要时新增 `qar_sample` 查询索引。

不得顺手迁移旧项目能力：

- 多架飞机列表和多机切换。
- 旧项目 `system_position`、`flight_status` 事实表。
- Redis 实时状态缓存。
- 历史航段列表和历史回放，除非用户后续明确要求。
- 用户、角色、权限、JWT 登录等旧项目模块。
- 数据管理、Excel、日志、备份恢复等无关模块。
- WiFi、卫星通信、乘客网络、流量统计等非轨迹模块。

## 4. 数据规则

- `qar_sample` 是轨迹点事实表，不再新增重复轨迹事实表。
- `data_record` 只作为元数据补充，不作为轨迹点来源。
- 当前模拟器 QAR 机场代码是四字 ICAO，不要改成三字码。
- `altitude_ft` 单位是 ft，不要在 API 或 UI 中误写为 meters。
- `ground_speed_kt > 100` 视为飞行中。
- 当前有效数据窗口默认最近 `5` 分钟。
- 当前轨迹窗口默认最近 `24` 小时，并由后端抽样控制返回点数。
- 所有轨迹查询必须按 `sample_at` 升序返回给前端。
- 经纬度为空的点不能用于地图绘制。
- 航向优先使用 `track_angle_deg`；为空时用 `heading_deg`。

## 5. API 实现要求

首期必需接口：

```text
GET /api/flight-track/current
```

实现要求：

- 返回字段与 `API.md` 一致。
- 无当前有效 QAR 时返回成功响应和 `data: null`。
- 不把 `sample_at`、`ground_speed_kt` 等数据库下划线字段直接暴露给前端。
- 机场、航司映射缺失时返回原始代码或“未知”，不报错。
- 后端异常沿用当前项目统一错误响应。
- Controller、Service、Mapper 放在独立 flight track 相关包中，不塞进 passenger 模块。

## 6. 前端实现要求

- 新增页面应接入当前 Vue Router 和现有平台导航。
- 页面名称建议 `FlightTrackView.vue`，样式建议独立放在 `frontend/src/styles/views/flightTrack.css`。
- 地图和图表页面应以真实接口数据驱动，不用静态截图冒充 UI。
- 图表数据来自接口 `track`，不要前端自造轨迹点。
- 页面不可见时暂停轮询。
- 请求失败时保留上一次成功数据并提示错误。
- `data: null` 时展示空态，不要让页面白屏。
- 参考图是布局和信息层级参考，不要求逐像素复刻。

## 7. 数据库实现要求

- 首期只补 `qar_sample` 查询索引即可。
- 若新增迁移，使用 Flyway 新版本文件，例如 `V7__add_flight_track_query_support.sql`。
- 不要修改已发布迁移文件内容，除非用户明确要求重建数据库。
- 不要给高频 QAR 表新增不必要外键。
- 不要把机场/航司字典表做成首期必需项。静态映射足够时不要建表。

## 8. 代码修改原则

- 遵循 `andrej-karpathy-skills:karpathy-guidelines`：先明确假设，做最小闭环，避免过度抽象。
- 修改范围要小，优先新增 flight track 模块，不重构无关代码。
- 只清理自己改动产生的无用代码，不清理旧的无关死代码。
- 测试随风险添加：后端至少覆盖查询服务和 DTO 映射；前端至少跑现有类型检查或构建。
- 如果地图 SDK 或离线瓦片资源缺失，要记录为实施前置条件，不要硬编码不可用路径。

## 9. 禁止事项

- 禁止批量删除文件或目录。
- 禁止使用：
  - `del /s`
  - `rd /s`
  - `rmdir /s`
  - `Remove-Item -Recurse`
  - `rm -rf`
- 需要删除文件时，只能一次删除一个明确路径的文件。
- 不要修改与飞机实时轨迹展示无关的模块。
- 不要把旧项目 Redis、多机数据结构或历史回放作为首期依赖。
- 不要混用 ft 和 meters。
- 不要把前端地图页面做成纯静态图片。

## 10. 验收清单

后续实现完成时至少验证：

- 模拟器 `qar.frame` 能持续入库 `qar_sample`。
- `GET /api/flight-track/current` 在有数据时返回当前单机轨迹。
- 停止模拟器超过 `5` 分钟后接口返回 `data: null`。
- `track` 按 `sampleAt` 升序。
- `latestPoint` 等于 `track` 最后一条。
- 前端能展示飞机、轨迹线、飞行状态卡片和曲线面板。
- 飞机图标朝向优先由最后两个真实轨迹点计算；单点时使用 `trackAngleDeg` 或 `headingDeg`。
- 高度单位展示为 ft，地速单位展示为 kt。
- 不新增旧项目 `system_position`、`flight_status`。
- 不影响现有登录、数据管理、乘客实时动态和模拟器其他接口。
