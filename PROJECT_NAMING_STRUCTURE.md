# 前中后舱网联数据显示平台命名规范

> 本文档约束本项目的目录、代码、接口、数据库、脚本、文档和生成文件命名。编写或调整命名前，必须先核对 `SPEC.md`、`API.md`、`schema.md`、`SLICES.md` 和对应目录的 `AGENTS.md`，不得用命名调整绕过首期范围和契约。

## 1. 适用范围

本规范适用于：

- `backend/`：Java 21、Spring Boot、MyBatis、Flyway 后端服务。
- `frontend/`：Vue 3、TypeScript、Vite、Element Plus 前端应用。
- `simulator/`：已有 UDP 数据模拟器。
- `deploy/`：Docker Compose、部署配置和运行示例。
- `docs/`：需求来源、界面图、开发手册、验证记录和交付文档。
- `storage/`：本地导入、导出、错误文件和运行期产物；不提交 Git。

首期业务范围只包括管理员登录、7 路 UDP 入库、数据管理、标签批注、软删除恢复、CSV 导入、CSV/PDF 导出和审计。命名不得提前引入未进入首期范围的轨迹实时、轨迹回放、统计报告、用户管理、乘客动态、推荐、PHM、ACARS 或视频业务。

## 2. 总体原则

### 2.1 命名必须稳定表达职责

命名优先回答三个问题：

- 属于哪个业务概念？
- 承担什么职责？
- 是哪一类对象？

推荐结构：

```text
业务概念 + 职责 + 类型
```

示例：

```text
DataRecordController.java
DataRecordService.java
DataRecordListItemResponse.java
data_record
data-records.ts
V2__create_core_tables.sql
```

### 2.2 风格统一

| 场景 | 风格 | 示例 |
| --- | --- | --- |
| Java 类、Vue 组件、TypeScript 类型 | PascalCase | `DataRecordService`、`DataRecordTable.vue`、`DataRecordDetail` |
| Java 方法、Java 字段、TypeScript 变量、API 查询参数 | camelCase | `receivedAt`、`dataTypeCode`、`pageSize` |
| Java 包名 | 小写连续词，必要时按包分层 | `com.cabin.datarecord`、`com.cabin.smartwindow` |
| REST path、前端路由、前端 API 文件 | kebab-case | `/api/v1/data-records`、`data-records.ts` |
| 数据库表、字段、索引、约束 | snake_case | `data_record`、`source_device_code`、`idx_record_received_at` |
| 枚举值、错误码、数据类型代码 | UPPER_SNAKE_CASE | `GROUND_TASK`、`VALIDATION_ERROR` |
| Python 模块、函数、变量 | snake_case | `run_simulator.py`、`build_payload` |
| 环境变量 | UPPER_SNAKE_CASE | `POSTGRES_PASSWORD`、`SPRING_DATASOURCE_URL` |

禁止在同一层级混用拼音、中文、英文缩写和不同大小写风格。源码路径、包名、脚本名和配置名优先使用英文 ASCII；中文可以用于交付文档、界面图、用户手册和需求来源文件。

### 2.3 缩写必须固定

允许的固定缩写：

| 缩写 | 含义 | 规则 |
| --- | --- | --- |
| API | Application Programming Interface | Java/TS 类型中写作 `API`，路径中写作 `api` |
| CSV | Comma-Separated Values | 类型中写作 `CSV`，路径或文件名中写作 `csv` |
| DTO | Data Transfer Object | 如确需使用，统一写作 `DTO` |
| IFE | In-Flight Entertainment | Java/TS 类型中写作 `IFE633`、`IFECockrell` |
| JWT | JSON Web Token | Java/TS 类型中写作 `JWT` |
| PDF | Portable Document Format | 类型中写作 `PDF`，文件名中写作 `pdf` |
| QAR | Quick Access Recorder | Java/TS 类型中写作 `QAR`，数据库中写作 `qar` |
| UDP | User Datagram Protocol | 类型中写作 `UDP`，包名和路径中写作 `udp` |
| UUID | Universally Unique Identifier | 类型中写作 `UUID` |

不新增缩写时不得自行创造短词。新增缩写必须先补充本表，并同步检查 `API.md`、`schema.md` 和前后端类型。

## 3. 根目录结构

项目根目录保持如下结构：

```text
cabin/
├─ backend/                 # Spring Boot 服务
├─ frontend/                # Vue 应用；尚未创建时保留此目标名
├─ simulator/               # 已有 UDP 模拟器
├─ deploy/                  # Docker Compose 与部署配置
├─ docs/                    # 需求来源、界面图、开发手册、验证记录
├─ storage/                 # 本地导入、导出、错误文件；不提交 Git
├─ SPEC.md
├─ API.md
├─ schema.md
├─ SLICES.md
├─ PROJECT_NAMING_STRUCTURE.md
└─ AGENTS.md
```

规则：

- 不用版本号、日期、`source-code`、`final`、`new`、`backup` 命名源码目录。
- 不移动、重命名或覆盖现有需求 Word、界面 PNG、模拟器文档和模拟器代码，除非用户明确要求。
- `storage/`、日志、缓存、构建产物和数据库卷信息不得提交。
- 如果新增顶层目录，必须先确认它是否属于首期范围；属于契约变化时同步更新 `AGENTS.md` 或开发手册。

## 4. 后端命名

### 4.1 Maven 与应用名

当前 Maven 坐标：

```text
groupId: com.cabin
artifactId: cabin-data-platform-backend
主包名: com.cabin
启动类: BackendApplication
```

不要用中文、版本号或数据库类型命名 artifact，例如不要使用 `cabin-pg-backend-v1`。数据库类型、Spring Boot 版本和运行要求写入文档或构建文件，不写进模块名。

### 4.2 Java 包结构

包名使用小写，不使用下划线或短横线。推荐按业务概念分包，再在包内按职责分层：

```text
backend/src/main/java/com/cabin/
├─ BackendApplication.java
├─ common/
│  ├─ exception/
│  ├─ response/
│  └─ trace/
├─ config/
├─ auth/
├─ appuser/
├─ datatype/
├─ datarecord/
├─ tag/
├─ annotation/
├─ audit/
├─ filejob/
├─ importjob/
├─ exportjob/
├─ udp/
├─ qar/
├─ simulationtask/
├─ traffic/
├─ session/
├─ smartwindow/
├─ ife633/
└─ ifecockrell/
```

说明：

- `common` 只放统一响应、异常、traceId、分页、通用工具等跨业务能力。
- `config` 只放 Spring、安全、线程池、UDP 映射、文件存储等配置。
- `auth` 只处理登录、JWT 和当前用户。
- `appuser` 表示首期管理员账号实体；不要命名为 `user` 后顺手扩展用户管理。
- `filejob` 对应数据库 `file_job`；面向接口时可分为 `importjob` 和 `exportjob`。
- 七类模拟器业务表按稳定概念分包，包名中不保留下划线：`smartwindow`、`ifecockrell`。

包内目录按需创建，不创建空目录：

```text
datarecord/
├─ controller/
├─ service/
├─ mapper/
├─ model/
├─ dto/
├─ parser/
└─ validator/
```

### 4.3 Java 类名

| 类型 | 命名格式 | 示例 |
| --- | --- | --- |
| 启动类 | `BackendApplication` | `BackendApplication.java` |
| Controller | `{Business}Controller` | `DataRecordController.java` |
| Service | `{Business}Service` | `DataRecordService.java` |
| Service 实现 | `{Business}ServiceImpl` | `DataRecordServiceImpl.java` |
| MyBatis Mapper | `{Entity}Mapper` | `DataRecordMapper.java` |
| 数据库模型 | `{Entity}` | `DataRecord.java`、`FileJob.java` |
| 请求体 | `{Action}{Business}Request` 或 `{Business}{Action}Request` | `LoginRequest.java`、`BatchDeleteRecordsRequest.java` |
| 查询参数对象 | `{Business}Query` | `DataRecordQuery.java` |
| 响应体 | `{Business}Response`、`{Business}DetailResponse` | `LoginResponse.java`、`DataRecordDetailResponse.java` |
| 列表项 | `{Business}ListItemResponse` | `DataRecordListItemResponse.java` |
| 枚举 | `{Business}Code` 或 `{Business}Status` | `ParseStatus.java`、`ResponseCode.java` |
| 配置类 | `{Responsibility}Config` | `UdpIngestConfig.java` |
| 过滤器 | `{Responsibility}Filter` | `TraceIdFilter.java` |
| 异常 | `{Business}Exception` | `BusinessException.java` |
| 解析器 | `{DataType}Parser` | `QARParser.java`、`IFECockrellParser.java` |

避免：

```text
DataVO.java
InfoDTO.java
CommonService.java
NewController.java
Test2Mapper.java
UserController.java  # 首期没有用户管理页面
```

### 4.4 Java 方法、字段和常量

- 方法和字段使用 camelCase：`findRecords`、`receivedAt`、`sourceDeviceCode`。
- 常量使用 UPPER_SNAKE_CASE：`DEFAULT_PAGE_SIZE`、`TRACE_ID_HEADER`。
- 集合使用复数：`recordIds`、`tagIds`、`dataTypes`。
- 布尔值用肯定语义：`enabled`、`deleted`、`available`，避免 `flag`、`statusFlag`。
- 时间字段使用 `Instant`、`OffsetDateTime` 或明确时区类型，不使用无时区字符串跨层传递。
- 不把完整原始报文、密码、JWT 或数据库密码写入日志变量名和日志模板。

### 4.5 后端测试命名

当前后端测试使用 `*Tests.java` 风格，继续保持：

```text
BackendApplicationTests.java
ResponseTests.java
DataRecordServiceTests.java
DataRecordControllerTests.java
UdpIngestIntegrationTests.java
```

测试包路径必须与被测代码包路径一致。集成测试用 `IntegrationTests` 后缀，避免用 `Test2`、`ManualTest`、`TempTest`。

## 5. REST API 命名

### 5.1 基础路径

所有业务接口以 `API.md` 为准，基础路径固定：

```text
/api/v1
```

资源路径使用 kebab-case 和复数资源名：

```text
/api/v1/auth/login
/api/v1/auth/me
/api/v1/data-options
/api/v1/data-records
/api/v1/data-records/{recordId}
/api/v1/data-records/{recordId}/metadata
/api/v1/data-records/batch-delete
/api/v1/data-records/{recordId}/restore
/api/v1/tags
/api/v1/data-records/tags/batch
/api/v1/data-records/{recordId}/annotations
/api/v1/imports
/api/v1/imports/templates/{dataTypeCode}
/api/v1/exports
/api/v1/audit-logs
```

不要使用：

```text
/getData
/dataRecord/page
/DataView/list
/api/v1/user-management
```

### 5.2 参数和响应字段

- JSON 字段、查询参数、路径参数使用 camelCase，并与 `API.md` 完全一致。
- 数据库字段转换为 API 字段时只做风格转换，不改业务含义：`source_device_code` -> `sourceDeviceCode`。
- 路径参数使用 `{recordId}`、`{tagId}`、`{jobId}`、`{annotationId}`。
- 分页字段固定为 `page`、`pageSize`、`total`、`totalPages`。
- `traceId` 必须统一拼写，禁止混用 `traceID`、`trace_id`。

### 5.3 错误码和枚举

错误码和枚举值使用 UPPER_SNAKE_CASE：

```text
OK
VALIDATION_ERROR
RESOURCE_NOT_FOUND
RESOURCE_CONFLICT
DATABASE_UNAVAILABLE
```

数据类型代码固定为：

```text
QAR
GROUND_TASK
GROUND_TRAFFIC_RECORD
GROUND_SESSION_SUMMARY
SMART_WINDOW_STATUS
IFE_633_BEHAVIOR
IFE_COCKRELL_BEHAVIOR
```

不得新增 `PHM`、`ACARS`、`VIDEO` 等首期未承诺的数据类型接口或业务表；如果作为停用字典项存在，也不得提供导入模板和解析详情承诺。

## 6. 数据库命名

### 6.1 固定约定

| 项 | 命名 |
| --- | --- |
| 数据库名 | `cabin_data_platform` |
| 应用用户 | `cabin_app` |
| Schema | `public` |
| 表名 | snake_case，单数或稳定业务名，不加 `tb_` |
| 字段名 | snake_case |
| 主键 | `id` |
| 外键字段 | `{target}_id` 或 `{target}_code` |
| 时间字段 | `created_at`、`updated_at`、`started_at`、`completed_at` 等 |
| 软删除字段 | `is_deleted`、`deleted_at`、`deleted_by`、`delete_reason` |
| 乐观锁 | `version` |

首期固定 15 张表：

```text
app_user
data_type
file_job
data_record
tag
data_record_tag
data_annotation
audit_log
qar_sample
simulation_task
traffic_record
session_summary
smart_window_status
ife_633_behavior
ife_cockrell_behavior
```

未经 `SPEC.md` 和 `schema.md` 变更确认，不得新增 PHM、ACARS、视频、用户角色、权限点或其他业务表。

### 6.2 索引、约束和触发器

命名格式：

| 类型 | 格式 | 示例 |
| --- | --- | --- |
| 普通索引 | `idx_{table}_{columns}` | `idx_record_received_at` |
| BRIN 索引 | `brin_{table}_{column}` | `brin_traffic_window_start` |
| 唯一约束 | `uk_{table}_{columns}` | `uk_tag_name_ci` |
| 检查约束 | `ck_{table}_{rule}` | `ck_record_payload_present` |
| 外键约束 | `fk_{table}_{target}` | `fk_data_record_data_type` |
| 触发器 | `trg_{table}_{action}` | `trg_data_record_updated_at` |
| 函数 | 动词短语 snake_case | `set_updated_at`、`prevent_audit_mutation` |

约束名必须表达业务规则，不能用数据库自动生成名作为迁移中的长期契约。

### 6.3 Flyway 文件

Flyway 迁移文件放在：

```text
backend/src/main/resources/db/migration/
```

文件名格式：

```text
V{number}__{action}_{object}.sql
```

当前拆分建议：

```text
V1__enable_pgcrypto.sql
V2__create_core_tables.sql
V3__create_management_tables.sql
V4__create_simulator_business_tables.sql
V5__create_core_indexes_and_triggers.sql
V6__seed_data_types.sql
```

规则：

- 编号只递增，不重排已合并迁移。
- 一个迁移文件只做一类清晰变更。
- 种子数据文件使用 `seed` 命名，只放字典和必要初始化，不写真实密码。
- Schema 变更必须同步 `schema.md`，不能只改 SQL。
- 应用配置中的 `spring.flyway.locations` 必须与实际目录一致。

## 7. 数据来源与模拟器命名

### 7.1 UDP 消息类型

模拟器消息类型使用小写加点号，必须与 `SPEC.md` 和 `schema.md` 一致：

| 端口 | 消息类型 | 数据类型代码 | 业务表 |
| ---: | --- | --- | --- |
| 8090 | `qar.frame` | `QAR` | `qar_sample` |
| 8091 | `ground.task` | `GROUND_TASK` | `simulation_task` |
| 8092 | `ground.traffic_record` | `GROUND_TRAFFIC_RECORD` | `traffic_record` |
| 8093 | `ground.session_summary` | `GROUND_SESSION_SUMMARY` | `session_summary` |
| 8094 | `smart_window.status` | `SMART_WINDOW_STATUS` | `smart_window_status` |
| 8095 | `ife_633.behavior` | `IFE_633_BEHAVIOR` | `ife_633_behavior` |
| 8096 | `ife_cockrell.behavior` | `IFE_COCKRELL_BEHAVIOR` | `ife_cockrell_behavior` |

端口、数据类型、来源设备和解析器名必须集中配置，不允许散落魔法数字。

### 7.2 来源系统和设备

固定来源系统：

```text
SIMULATOR
```

固定来源设备编码：

```text
SIM-QAR
SIM-GROUND
SIM-WINDOW
SIM-IFE-633
SIM-IFE-COCKRELL
```

注意：“设备”在本项目首期表示报文来源设备，不表示乘客座椅终端。乘客终端使用 `terminalId`、`displayTerminalId`、`seatNo`、`seatLabel` 等字段表达，不得命名为 `sourceDevice`。

### 7.3 Python 模拟器

保留当前目录：

```text
simulator/
├─ run_simulator.py
├─ receiver_server.py
├─ simulator_config.example.json
└─ udp_simulator/
   ├─ config.py
   ├─ flight_model.py
   ├─ ground_model.py
   ├─ ife_model.py
   ├─ passengers.py
   ├─ scenario.py
   ├─ simulator.py
   ├─ udp_sender.py
   └─ window_model.py
```

规则：

- Python 文件、函数、变量使用 snake_case。
- 类名使用 PascalCase。
- 模拟器配置文件使用 `simulator_config.example.json` 作为示例；真实本地配置不得提交。
- IFE 633 和 IFE Cockrell 必须复用乘客、偏好和行为生成逻辑，字段适配可以命名为 `ife_633`、`ife_cockrell`。
- 不随意改变模拟器报文结构；确需变更时先同步 `simulator/接口格式Schema.md` 和项目契约。

## 8. 前端命名

### 8.1 目录结构

目标结构：

```text
frontend/
├─ public/
├─ src/
│  ├─ api/
│  ├─ assets/
│  ├─ components/
│  ├─ composables/
│  ├─ router/
│  ├─ stores/
│  ├─ styles/
│  ├─ types/
│  ├─ utils/
│  ├─ views/
│  ├─ App.vue
│  └─ main.ts
├─ package.json
└─ vite.config.ts
```

目录名使用小写复数或稳定框架约定。不要使用 `network/`、`pages2/`、`newViews/` 这类含义不稳定的目录。

### 8.2 Vue 与 TypeScript 文件

| 类型 | 命名格式 | 示例 |
| --- | --- | --- |
| 页面组件 | PascalCase | `LoginView.vue`、`DataManagementView.vue` |
| 通用组件 | PascalCase | `DataRecordTable.vue`、`TagSelector.vue` |
| 业务组件目录 | kebab-case | `data-record/`、`file-job/` |
| API 文件 | kebab-case | `data-records.ts`、`audit-logs.ts` |
| 类型文件 | kebab-case 或 `api.ts` | `data-record.ts`、`api.ts` |
| composable | `use{Business}.ts` | `useAutoRefresh.ts`、`useDataRecords.ts` |
| store | `use{Business}Store.ts` | `useAuthStore.ts` |
| 测试文件 | `{Component}.spec.ts` | `DataRecordTable.spec.ts` |

前端 API 文件建议：

```text
src/api/auth.ts
src/api/data-options.ts
src/api/data-records.ts
src/api/tags.ts
src/api/annotations.ts
src/api/imports.ts
src/api/exports.ts
src/api/audit-logs.ts
```

### 8.3 路由和状态

路由 path 使用 kebab-case：

```text
/login
/data-records
/imports
/exports
```

路由 name 使用 PascalCase：

```text
Login
DataRecords
Imports
Exports
```

前端状态、页面、API 和类型必须使用同一词根：

```text
views/DataManagementView.vue
components/data-record/DataRecordTable.vue
api/data-records.ts
types/data-record.ts
```

不要在同一业务中混用 `DataView`、`DataList`、`RecordManager`、`MessageLog` 表示同一个数据管理列表。

### 8.4 前端字段

- API DTO 字段与 `API.md` 保持 camelCase：`aircraftRegistrationNo`、`sourceDeviceCode`。
- 表格列 key 使用 API 字段名，不另起缩写。
- UI 展示文案可以使用中文，但变量名不得使用拼音。
- 禁止为了还原界面写固定业务假数据文件；示例数据只能用于测试，并以 `fixture` 或 `mock` 明确命名。

## 9. 文件、导入导出和存储命名

### 9.1 storage 目录

运行期文件统一放入 `storage/`，不提交 Git：

```text
storage/
├─ imports/
├─ exports/
├─ errors/
└─ temp/
```

推荐生成文件名：

```text
{jobType}-{dataTypeCode}-{YYYYMMDD-HHmmss}-{jobId}.{ext}
```

示例：

```text
import-QAR-20260704-153000-7f0c2100.csv
export-GROUND_TRAFFIC_RECORD-20260704-160000-8c19c207.csv
error-QAR-20260704-153500-7f0c2100.csv
export-QAR-20260704-160500-8c19c207.pdf
```

数据库只保存安全原文件名、生成文件名和 `storage/` 下相对路径。不得直接使用用户上传文件名作为磁盘路径。

### 9.2 CSV 模板

CSV 模板名使用数据类型代码和用途：

```text
qar-import-template.csv
ground-task-import-template.csv
ground-traffic-record-import-template.csv
ground-session-summary-import-template.csv
```

CSV 表头与导入契约保持稳定。新增或重命名字段必须同步 `API.md`、`schema.md`、导入校验和前端下载逻辑。

### 9.3 日志和备份

日志文件建议：

```text
backend-dev.log
backend-prod.log
udp-ingest-20260704.log
```

备份文件建议：

```text
20260704-153000-postgres-full.dump
20260704-160000-storage-export.zip
```

日志、备份和运行产物不进入源码目录和 Git 仓库。命名中不得包含真实密码、JWT、完整连接串或用户隐私信息。

## 10. 配置和部署命名

### 10.1 环境变量

环境变量使用 UPPER_SNAKE_CASE：

```text
POSTGRES_DB
POSTGRES_USER
POSTGRES_PASSWORD
POSTGRES_PORT
POSTGRES_VOLUME_NAME
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
BOOTSTRAP_ADMIN_USERNAME
BOOTSTRAP_ADMIN_PASSWORD
JWT_SECRET
```

`.env.example` 只能使用占位值，例如 `CHANGE_ME`。真实 `.env` 不提交。

### 10.2 Spring 配置

推荐：

```text
application.properties
application-dev.properties
application-test.properties
application-prod.properties
```

或统一使用 YAML：

```text
application.yml
application-dev.yml
application-test.yml
application-prod.yml
```

同一项目内不要混用两套风格。公共配置放基础文件，环境差异放 `application-{env}`。

### 10.3 deploy 目录

当前 Compose 文件固定为：

```text
deploy/docker-compose.yml
```

后续增加部署文档时使用场景命名：

```text
deploy/nginx-reverse-proxy.md
deploy/windows-local-dev.md
deploy/linux-production.md
```

部署文件不得包含真实密码、生产域名密钥或本机绝对私有路径。

## 11. 文档命名

### 11.1 根级契约文档

根级契约文档保持当前固定命名：

```text
SPEC.md
API.md
schema.md
SLICES.md
AGENTS.md
PROJECT_NAMING_STRUCTURE.md
```

这些文档是开发契约，不使用日期或版本号改名。内容变更通过 Git 历史追溯。

### 11.2 docs 目录

`docs/` 可以保留需求来源 Word、界面 PNG 和中文交付材料。新增开发文档建议使用英文 kebab-case：

```text
docs/development-guide.md
docs/verification-record-20260704.md
docs/deployment-guide.md
docs/api-change-log.md
```

正式交付文档可以使用中文和日期：

```text
软件测试报告-20260704.docx
验收记录-20260704.docx
```

不使用：

```text
最终版.md
最新版.docx
新建文本文档.md
接口说明final2.md
```

### 11.3 文档与契约同步

- 产品行为或验收改变：先改 `SPEC.md`。
- Method、path、请求、响应、权限或错误改变：同步改 `API.md`。
- 表、字段、类型、约束、索引或保留策略改变：同步改 `schema.md` 并新增 Flyway 迁移。
- 切片边界改变：同步改 `SLICES.md`。
- 目录、技术栈、验证命令或禁止事项改变：同步改 `AGENTS.md`。

## 12. 跨层词根对齐

同一业务概念在数据库、后端、前端、接口和文档中必须尽量使用同一词根：

| 业务概念 | 数据库 | REST path | Java/TS | 前端文件 |
| --- | --- | --- | --- | --- |
| 数据记录 | `data_record` | `/data-records` | `DataRecord` | `data-records.ts` |
| 数据类型 | `data_type` | `/data-options` | `DataType` | `data-options.ts` |
| 文件任务 | `file_job` | `/imports`、`/exports` | `FileJob`、`ImportJob`、`ExportJob` | `imports.ts`、`exports.ts` |
| 标签 | `tag` | `/tags` | `Tag` | `tags.ts` |
| 数据标签关系 | `data_record_tag` | `/data-records/tags/batch` | `DataRecordTag` | `tags.ts` |
| 批注 | `data_annotation` | `/annotations` | `DataAnnotation` | `annotations.ts` |
| 审计日志 | `audit_log` | `/audit-logs` | `AuditLog` | `audit-logs.ts` |
| QAR 采样 | `qar_sample` | 数据详情解析摘要 | `QARSample` | `data-records.ts` |
| 仿真任务 | `simulation_task` | 数据详情解析摘要 | `SimulationTask` | `data-records.ts` |
| 流量窗口 | `traffic_record` | 数据详情解析摘要 | `TrafficRecord` | `data-records.ts` |
| 会话摘要 | `session_summary` | 数据详情解析摘要 | `SessionSummary` | `data-records.ts` |
| 智能舷窗 | `smart_window_status` | 数据详情解析摘要 | `SmartWindowStatus` | `data-records.ts` |
| IFE 633 行为 | `ife_633_behavior` | 数据详情解析摘要 | `IFE633Behavior` | `data-records.ts` |
| IFE Cockrell 行为 | `ife_cockrell_behavior` | 数据详情解析摘要 | `IFECockrellBehavior` | `data-records.ts` |

如果历史实现已经使用不同词根，不要直接大范围重命名；先记录映射关系，再在当前切片内做最小必要调整。

## 13. 禁止项

禁止使用以下命名方式：

- `new`、`old`、`final`、`final2`、`temp`、`copy`、`bak` 表示版本或状态。
- 源码路径中使用中文、空格、括号、特殊符号。
- 在同一目录层级混用 `camelCase`、`PascalCase`、`snake_case`、`kebab-case`。
- 用技术栈命名业务模块，例如 `data-pg`、`mysql-version`。
- 未进入首期范围却创建 `phm`、`acars`、`video`、`passenger-profile`、`recommendation` 等业务包、表、接口或页面。
- 把“来源设备”和“乘客终端”混为同一个命名概念。
- 用 `any`、`Map<String,Object>` 或模糊 DTO 名称掩盖 API 字段不一致。
- 将真实密码、JWT、数据库连接串、原始报文全文写进日志文件名、变量名或示例配置。

## 14. 新增模块命名流程

新增业务模块或文件前按顺序确认：

1. 查 `SPEC.md`：该模块是否属于首期范围。
2. 查 `API.md`：是否已有路径、请求、响应、错误码和枚举。
3. 查 `schema.md`：是否已有表、字段、索引和约束。
4. 查 `SLICES.md`：是否属于当前切片。
5. 确定统一业务词根，例如 `data-record`、`file-job`、`smart-window`。
6. 映射各层命名：REST path、Java 包、Java 类、TS 类型、数据库表、前端文件。
7. 如需改契约，先改文档和迁移，再改代码。
8. 提交前检查是否引入首期外范围、无关重命名或拼写不一致。

示例：

```text
业务词根: data-record
REST path: /api/v1/data-records
Java 包: com.cabin.datarecord
Java 类: DataRecordController / DataRecordService / DataRecordMapper
TS API: src/api/data-records.ts
Vue 组件: DataRecordTable.vue
数据库表: data_record
文档章节: 数据记录接口 / data_record
```

## 15. 最小检查清单

提交命名相关变更前检查：

- 是否仍符合 `SPEC.md` 的首期范围？
- 是否同步 `API.md`、`schema.md`、`SLICES.md` 或 `AGENTS.md` 中受影响内容？
- 同一业务概念是否跨前端、后端、数据库、接口使用同一词根？
- 数据库表是否仍为首期 15 张，或已完成正式契约变更？
- REST path 是否使用 `/api/v1`、kebab-case 和资源名？
- JSON 字段是否与 `API.md` camelCase 契约一致？
- Java 包是否小写，类名后缀是否表达职责？
- 前端组件、路由、API 文件和类型是否使用统一命名？
- Flyway 文件是否在 `db/migration/`，编号是否只递增？
- 是否存在 `new`、`final`、`temp`、`copy`、拼音、拼写错误或重复概念？
- 是否避免了未授权的大范围重命名和无关格式化？
