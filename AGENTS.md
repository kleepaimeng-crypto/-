# AGENTS.md

本文件约束 `E:\前中后舱网联数据显示平台` 整个项目。进入子目录时还必须遵守该目录内更具体的 `AGENTS.md`；例如 `simulator/AGENTS.md` 继续约束模拟器实现。子目录规则可以补充细节，但不得绕过根级需求、接口、数据库和安全约束。

## 1. 开发前必读

开始任何功能前，按顺序阅读：

1. `SPEC.md`：产品范围、用户行为和验收标准。
2. `API.md`：前后端接口契约。
3. `schema.md`：PostgreSQL 表、字段、约束和索引。
4. `SLICES.md`：当前切片、依赖和完成标准。
5. 当前目录及父目录的 `AGENTS.md`。

如文档冲突，先停止实现并由负责人确认，禁止自行挑选最方便的版本。通常以 `SPEC.md` 的产品范围为最高基线，再同步修订其他契约。

## 2. 首期范围

首期只实现：

- 管理员登录。
- 7 路 UDP 数据接收和统一入库。
- 数据列表、筛选、分页、排序和详情。
- 标签、批注、软删除、恢复和审计。
- QAR、任务、流量、会话四类 CSV 导入。
- 按筛选结果导出 CSV/PDF 及导入导出历史。

不得擅自开发轨迹实时、轨迹回放、统计报告、用户管理、乘客动态、推荐、PHM、ACARS 或视频业务。需要新增范围时，先更新 `SPEC.md` 并确认。

## 3. 技术栈

### 前端

- Vue 3、TypeScript、Vite 8、Element Plus。
- Node.js 22 LTS。
- 使用组合式 API 和 `<script setup lang="ts">`。
- API 类型必须与 `API.md` 一致，禁止使用 `any` 绕过字段不一致。

### 后端

- Java 21、Spring Boot 4.1.x、Maven。
- PostgreSQL JDBC、MyBatis、Redis、Flyway、Spring Security、Bean Validation。
- 时间使用 `Instant`、`OffsetDateTime` 或明确时区的类型，禁止用无时区字符串在层间传递。
- 数据库批量写入和大文件导出必须流式/分批处理。

### 数据库与部署

- Docker Compose 运行 PostgreSQL 18。
- 数据库名 `cabin_data_platform`，应用用户 `cabin_app`。
- 密码和 JWT 密钥只通过 `.env` 或运行环境注入。
- 所有 Schema 变更必须使用 Flyway；禁止手工改库后不补迁移。
- 禁止使用 ORM 自动建表；如启用 JPA，只允许 `ddl-auto=validate`。

## 4. 目录约定

目标结构：

```text
E:\前中后舱网联数据显示平台
├─ backend/                 Spring Boot 服务
├─ frontend/                Vue 应用
├─ simulator/               已有 UDP 模拟器
├─ deploy/                  Docker Compose 与部署配置示例
├─ docs/                    开发手册与验证记录
├─ storage/                 本地导入、导出和错误文件；不提交 Git
├─ SPEC.md
├─ API.md
├─ schema.md
├─ SLICES.md
└─ AGENTS.md
```

不要移动、重命名或覆盖现有需求 Word、界面 PNG、模拟器文档和代码，除非用户明确要求。

## 5. 契约同步规则

- 产品行为或验收改变：先改 `SPEC.md`。
- Method、path、请求、响应、权限或错误改变：同步改 `API.md`。
- 表、字段、类型、约束、索引或保留策略改变：同步改 `schema.md` 并新增 Flyway 迁移。
- 开发顺序或切片边界改变：同步改 `SLICES.md`。
- 技术栈、目录、验证命令或禁止事项改变：同步改根级 `AGENTS.md`。
- 不允许代码先改、文档长期欠账。

## 6. 数据与数据库规则

- 一份 UDP 数据报对应一条 `data_record`。
- 平台在入库时生成 UUID，不要求模拟器提供 `messageId`。
- 原始报文写入后不可更新；合法 JSON 存 `raw_payload`，解析失败原文存 `raw_text`。
- 七类模拟器数据首期全部保存到 `data_record.raw_payload`，并解析到 `qar_sample`、`simulation_task`、`traffic_record`、`session_summary`、`smart_window_status`、`ife_633_behavior`、`ife_cockrell_behavior`。
- “设备”表示报文来源设备，不表示乘客终端。
- 业务时间和审计时间使用 PostgreSQL `timestamptz`。
- 删除使用软删除并写审计；物理清理由 7+7 天保留任务执行。
- 首期 Schema 固定为 15 张表：8 张公共表加上述 7 张模拟器业务表。未经 `SPEC.md` 变更不得增加其他业务表。
- 禁止为尚无稳定字段定义的 PHM、ACARS、视频等数据创建猜测字段或专表。
- 任何 SQL 必须考虑外键、唯一约束、检查约束、索引和回滚/恢复影响。

## 7. 后端规范

- 分层保持清晰：Controller 只处理协议和校验，Service 处理事务与业务，Repository/DAO 处理数据库访问。
- 对外响应和错误结构严格遵循 `API.md`；禁止直接返回 Entity、堆栈或数据库错误。
- 所有输入使用 Bean Validation，并对文件名、相对路径和下载权限做服务端校验。
- 所有管理员写操作必须写 `audit_log`；审计值必须脱敏。
- JWT、密码、数据库密码和完整原始报文不得写入普通日志。
- 修改管理元数据使用乐观锁 `version`。
- UDP 端口、来源设备和测试飞机映射使用类型安全配置，不得散落魔法数字。
- 模拟器仅允许按已确认方案修正流量统计窗口；不得随意改变其他报文结构。
- 导入和导出为异步任务；大结果禁止一次性加载到内存。

## 8. 前端规范

- 页面以现有深色界面稿为视觉基线，但可修正明确的文案错误和可用性问题。
- 数据管理页必须处理 loading、error、empty 和正常四种状态。
- 服务端负责筛选、排序和分页；前端不得下载全量后再过滤。
- 原始报文和解析摘要只能只读展示，不提供可编辑控件。
- 删除必须二次确认；批量操作必须显示选中数量和结果摘要。
- 自动刷新默认 5 秒，可暂停，且不能打断编辑或导致用户跳页。
- API 请求集中封装，统一注入 JWT、traceId 关联信息和错误处理。
- 组件 Props、Emits、API DTO 和表格字段使用显式 TypeScript 类型。
- 不为了“还原效果”写固定业务假数据；真实列表必须来自后端 API。

## 9. 文件与安全

- 不提交 `.env`、真实密码、JWT 密钥、数据库数据目录、上传文件、导出文件、日志、缓存和构建产物。
- 提供 `.env.example` 时只能使用占位值，例如 `CHANGE_ME`。
- 不把用户上传的原文件名直接当磁盘路径；使用系统生成文件名并保存安全基名。
- CSV 防止公式注入：以 `=`, `+`, `-`, `@` 开头的导出文本按安全策略转义。
- PDF 中文字体必须来自明确的可再分发字体资源，并在部署文档记录。
- 不执行破坏性 Git 或数据库命令，除非用户明确要求并确认目标。

## 10. 开发流程

按 SCPITR 执行：

1. Spec：确认 `SPEC.md` 范围。
2. Contract：确认 `API.md` 和 `schema.md`。
3. Plan：确认当前 `SLICES.md` 切片。
4. Implement：只实现当前垂直切片。
5. Test：运行类型检查、测试、构建和手动验收。
6. Review：检查契约偏离、字段不一致、边界遗漏和无关 diff。

不要顺手升级依赖、改登录、重构无关模块或格式化整个仓库。遇到工作区已有用户改动时必须保留，无法安全合并时再询问。

## 11. 验证命令

项目骨架创建后，每个切片至少运行：

```powershell
# 部署配置
docker compose -f deploy/docker-compose.yml config
docker compose -f deploy/docker-compose.yml ps

# 后端
Set-Location backend
.\mvnw.cmd test
.\mvnw.cmd verify

# 前端
Set-Location frontend
npm run typecheck
npm run test
npm run build
```

如项目实际脚本名称改变，必须同步更新本节和开发手册。不能因为命令失败就跳过验证；先定位并修复，确实受外部环境阻塞时明确记录。

## 12. 完成汇报

每次切片完成后汇报：

- 实现了什么，明确到用户可见行为。
- 修改了哪些模块和契约文档。
- 运行了哪些验证命令及结果。
- 是否存在未覆盖边界、性能风险或环境阻塞。
- diff 中是否包含无关修改。

没有验证结果的“代码已写完”不视为完成。
