# 用户管理实施切片

## 1. 实施原则

- 按“契约 → 数据库 → 后端查询 → 后端写操作与权限 → 前端 API → 页面 → 全量验证”执行。
- 每个切片都应可独立验证。
- 只修改用户管理直接依赖的文件。
- 保留工作区已有改动，不重构飞机轨迹、乘客实时动态和数据管理。
- 未完成数据库与后端契约前，不用静态假数据实现页面。

## 2. 切片 0：契约确认

范围：

- 评审本目录 `SPEC.md`、`API.md`、`schema.md`、`SLICES.md`、`AGENTS.md`。
- 确认角色、状态、软删除、访问权限和最后超级管理员保护。

完成标准：

- 文档没有未决事项、占位内容或互相冲突的字段。
- 用户确认本目录可以作为代码实现基线。

## 3. 切片 1：数据库与认证兼容

新增：

- `backend/src/main/resources/db/migration/V10__add_user_management.sql`

修改：

- `backend/src/main/java/com/cabin/login/service/BootstrapAdminService.java`
- `backend/src/main/java/com/cabin/login/service/AuthService.java`
- `backend/src/main/java/com/cabin/common/security/JwtAuthenticationFilter.java`
- `backend/src/main/java/com/cabin/common/security/JsonAccessDeniedHandler.java`
- `backend/src/main/java/com/cabin/common/response/ResponseCode.java`
- 相关登录、JWT 和安全测试。

目标：

- 完成角色和状态迁移。
- bootstrap 管理员使用 `SUPER_ADMIN + ACTIVE`。
- 非 `ACTIVE` 用户不能登录或继续使用 JWT。
- 增加 403 `ACCESS_DENIED`，不再把普通权限不足错误写成账号禁用。

验证：

- Flyway 从空库和已有 V9 数据库升级到 V10。
- 登录、JWT、禁用账号回归测试通过。

## 4. 切片 2：用户列表后端

建议新增：

```text
backend/src/main/java/com/cabin/user/
├─ controller/UserController.java
├─ service/UserService.java
├─ mapper/UserMapper.java
├─ entity/UserRow.java
└─ dto/
   ├─ UserQuery.java
   └─ UserSummaryResponse.java
```

修改：

- `backend/src/main/java/com/cabin/config/SecurityConfig.java`

目标：

- 实现 `GET /api/v1/users`。
- 服务端分页、白名单排序、稳定排序。
- `/api/v1/users/**` 只允许 `ROLE_SUPER_ADMIN`。
- 不返回密码哈希和删除原因。

测试：

- Controller：认证、权限、参数校验和响应结构。
- Service：分页、排序映射和空结果。
- Mapper/数据库：总数和分页顺序。

## 5. 切片 3：添加、编辑、删除

新增 DTO：

```text
UserCreateRequest.java
UserUpdateRequest.java
UserDeleteRequest.java
```

修改：

- 用户 Controller、Service、Mapper。
- `backend/src/main/java/com/cabin/log/service/AuditLogService.java`

目标：

- 实现 POST、PATCH、DELETE。
- BCrypt 密码哈希。
- 用户名/邮箱唯一冲突映射。
- 乐观锁。
- 自我锁定保护。
- 最后一个 `ACTIVE SUPER_ADMIN` 保护。
- 软删除字段和审计同事务写入。

测试：

- 创建成功和重复用户名/邮箱。
- 编辑成功和版本冲突。
- 当前用户自我降级、冻结、删除全部失败。
- 最后超级管理员不能降级、冻结或删除。
- 两个超级管理员时允许调整其中一个。
- 已删除用户不能编辑或重复删除。
- 审计中没有密码和密码哈希。

## 6. 切片 4：前端类型、API 和路由

新增：

- `frontend/src/api/users.ts`

修改：

- `frontend/src/api/types.ts`
- `frontend/src/router/index.ts`
- `frontend/src/router/authGuard.ts`
- `frontend/src/router/authGuard.test.ts`
- `frontend/src/views/LoginView.vue`

目标：

- 增加 `UserRole`、`UserStatus`、用户管理 DTO。
- 增加用户列表和写操作 API 封装。
- 增加 `/users` 路由。
- 路由守卫识别 `SUPER_ADMIN`。
- 非超级管理员不能通过直接输入地址进入页面。

验证：

- TypeScript 不使用 `any`。
- API 请求 path、method、body 与契约一致。
- 认证和角色路由测试通过。

## 7. 切片 5：用户管理页面

新增：

- `frontend/src/views/UserManagementView.vue`
- `frontend/src/styles/views/userManagement.css`
- `frontend/src/views/UserManagementView.test.ts`

修改：

- `frontend/src/styles/index.css`
- `frontend/src/views/WorkspaceView.vue`
- `frontend/src/views/FlightTrackView.vue`
- `frontend/src/views/PassengerRealtimeView.vue`

目标：

- 参考图的信息层级和深色视觉。
- 表格、分页、服务端排序。
- 添加、编辑和删除对话框。
- loading、error、empty、normal。
- 已删除用户操作禁用。
- 超级管理员导航可点击；其他角色不显示可点击入口。
- 不引入无动作的复选框。

测试：

- 首次加载和空态。
- 排序、翻页、页大小变化发出正确请求。
- 添加默认值为 `USER + PENDING`。
- 编辑携带 `expectedVersion`。
- 删除必须填写原因并二次确认。
- API 错误展示且不丢失已有列表。

## 8. 切片 6：回归与手工验收

后端：

```powershell
Set-Location backend
.\mvnw.cmd test
.\mvnw.cmd verify
```

前端：

```powershell
Set-Location frontend
npm run typecheck
npm run test
npm run build
```

数据库和部署：

```powershell
docker compose -f deploy/docker-compose.yml config
docker compose -f deploy/docker-compose.yml ps
```

手工验收：

1. 使用超级管理员登录并进入用户管理。
2. 分别创建 `SUPER_ADMIN`、`ADMIN`、`USER`。
3. 验证分页、排序、添加、编辑和删除。
4. 验证 `PENDING`、`FROZEN`、`DELETED` 均不能登录。
5. 验证普通管理员和普通用户不能访问页面或接口。
6. 验证当前账号和最后超级管理员保护。
7. 验证冻结或删除已登录用户后，下一次请求清理会话。
8. 检查审计日志没有密码或密码哈希。
9. 在 1920×1080 和 1366 宽度下检查布局。
10. 检查 Git diff 不包含无关格式化或用户已有改动。

## 9. 预计影响范围

直接影响：

- `app_user` 数据约束。
- 登录和 JWT 状态检查。
- Spring Security 访问拒绝响应。
- 当前用户前端 DTO 的角色联合类型。
- 三个现有页面的用户管理导航入口。
- 审计日志动作。

不应影响：

- UDP 接收和解析。
- 数据管理业务逻辑。
- 标签、批注和数据生命周期。
- 乘客实时动态查询。
- 飞机轨迹会话和地图。
- 模拟器代码。
- 文件导入导出未完成模块。
