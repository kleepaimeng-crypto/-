# 用户管理 REST API 契约

## 1. 公共约定

- 基础路径：`/api/v1/users`。
- 认证：`Authorization: Bearer <JWT>`。
- 权限：全部接口仅允许 `ACTIVE SUPER_ADMIN`。
- 响应、分页、时间和 traceId 沿用 `../v1-docs/API.md`。
- 时间使用 ISO 8601 带时区字符串。
- 用户 ID 使用 UUID。
- 请求和响应均不得包含密码哈希。

### 1.1 相关业务权限

用户管理接口仍只允许 `ACTIVE SUPER_ADMIN`。数据管理记录查询接口允许三种角色访问；数据管理记录写操作只允许 `SUPER_ADMIN`、`ADMIN`：

- `PATCH /api/v1/data-records/**`
- `DELETE /api/v1/data-records/**`
- `POST /api/v1/data-records/batch-delete`
- `POST /api/v1/data-records/{recordId}/restore`
- `POST /api/v1/data-records/tags/batch`

`USER` 调用以上写接口返回 403 `ACCESS_DENIED`。前端对 `USER` 保留数据列表查看能力，但编辑、删除和批量删除按钮置灰不可点。

### 1.2 成功响应

```json
{
  "code": "OK",
  "message": "success",
  "data": {},
  "traceId": "6a5d4129e4de4b25"
}
```

### 1.3 分页响应

```json
{
  "items": [],
  "page": 1,
  "pageSize": 20,
  "total": 314,
  "totalPages": 16
}
```

`page` 从 1 开始。允许的 `pageSize` 为 20、50、100。

## 2. 枚举

### 2.1 `UserRole`

| 值 | 文案 |
| --- | --- |
| `SUPER_ADMIN` | 超级管理员 |
| `ADMIN` | 管理员 |
| `USER` | 普通用户 |

### 2.2 `UserStatus`

| 值 | 文案 |
| --- | --- |
| `ACTIVE` | 激活 |
| `PENDING` | 未激活 |
| `FROZEN` | 冻结 |
| `DELETED` | 删除 |

## 3. 用户对象

`UserSummary`：

```json
{
  "id": "8c19c207-8053-4bee-a76e-6590eaaee846",
  "username": "operator01",
  "email": "operator01@example.com",
  "roleCode": "USER",
  "status": "ACTIVE",
  "lastLoginAt": "2026-07-09T10:30:00+08:00",
  "version": 3,
  "createdAt": "2026-07-08T09:00:00+08:00",
  "updatedAt": "2026-07-09T10:00:00+08:00",
  "deletedAt": null
}
```

说明：

- 历史账号的 `email` 可以为 `null`，新建和编辑后必须为合法邮箱。
- `lastLoginAt`、`deletedAt` 可以为 `null`。
- 不返回 `passwordHash`、`deletedBy` 或 `deleteReason`。

## 4. 查询用户列表

`GET /api/v1/users`

### 4.1 查询参数

| 参数 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `page` | integer | 否 | 1 | 最小 1 |
| `pageSize` | integer | 否 | 20 | 只允许 20、50、100 |
| `sortBy` | string | 否 | `createdAt` | `username`、`roleCode`、`email`、`status`、`createdAt` |
| `sortDirection` | string | 否 | `desc` | `asc` 或 `desc` |

不提供前端自定义 SQL 字段。后端必须使用白名单映射排序列，并追加 `id` 作为稳定排序键。

列表包含 `DELETED` 用户。

### 4.2 成功响应

`data` 为分页响应，`items` 是 `UserSummary[]`。

### 4.3 错误

- 400 `VALIDATION_ERROR`
- 401 `UNAUTHORIZED`
- 403 `ACCESS_DENIED`
- 403 `ACCOUNT_DISABLED`
- 503 `DATABASE_UNAVAILABLE`

## 5. 添加用户

`POST /api/v1/users`

### 5.1 请求

```json
{
  "username": "operator01",
  "email": "operator01@example.com",
  "password": "<初始密码>",
  "roleCode": "USER",
  "status": "PENDING"
}
```

校验：

- `username`：3～64，正则 `^[A-Za-z0-9._-]+$`；服务端 trim 并转小写。
- `email`：合法邮箱，最长 254；服务端 trim 并转小写。
- `password`：6～72 个字符，不做 trim。
- `roleCode`：三种角色之一。
- `status`：只允许 `ACTIVE` 或 `PENDING`。

密码用 BCrypt 哈希后保存。请求对象的 `toString`、日志和异常不得输出密码。

### 5.2 成功响应

- HTTP 201。
- `data` 为创建后的 `UserSummary`。

### 5.3 错误

- 400 `VALIDATION_ERROR`
- 401 `UNAUTHORIZED`
- 403 `ACCESS_DENIED`
- 403 `ACCOUNT_DISABLED`
- 409 `RESOURCE_CONFLICT`：用户名或邮箱不区分大小写重复
- 503 `DATABASE_UNAVAILABLE`

冲突详情：

```json
{
  "code": "RESOURCE_CONFLICT",
  "message": "用户名已存在",
  "details": [
    {"field": "username", "reason": "duplicate"}
  ],
  "traceId": "6a5d4129e4de4b25"
}
```

## 6. 编辑用户

`PATCH /api/v1/users/{userId}`

### 6.1 请求

```json
{
  "username": "operator01",
  "email": "operator01@example.com",
  "roleCode": "ADMIN",
  "status": "ACTIVE",
  "expectedVersion": 3
}
```

规则：

- `expectedVersion` 必填且大于 0。
- `username`、`email`、`roleCode`、`status` 至少提供一个。
- 提供的 `status` 只能为 `ACTIVE`、`PENDING`、`FROZEN`。
- 不允许通过本接口修改密码。
- 目标为 `DELETED` 时拒绝编辑。
- 当前用户不能修改自己的角色或把自己的状态改为非 `ACTIVE`。
- 修改 `ACTIVE SUPER_ADMIN` 的角色或状态时，系统必须保证修改后仍至少有一个 `ACTIVE SUPER_ADMIN`。

### 6.2 成功响应

- HTTP 200。
- `data` 为更新后的 `UserSummary`。

### 6.3 错误

- 400 `VALIDATION_ERROR`
- 401 `UNAUTHORIZED`
- 403 `ACCESS_DENIED`
- 403 `ACCOUNT_DISABLED`
- 404 `RESOURCE_NOT_FOUND`
- 409 `RESOURCE_CONFLICT`：唯一冲突、版本冲突、自我锁定、最后超级管理员保护、目标已删除
- 503 `DATABASE_UNAVAILABLE`

版本冲突消息统一为“用户数据已变化，请刷新后重试”，不泄露数据库细节。

## 7. 删除用户

`DELETE /api/v1/users/{userId}`

删除为软删除。

### 7.1 请求

```json
{
  "reason": "账号不再使用",
  "expectedVersion": 3
}
```

规则：

- `reason` trim 后长度 1～500。
- `expectedVersion` 大于 0。
- 当前用户不能删除自己。
- 已删除用户不能重复删除。
- 删除 `ACTIVE SUPER_ADMIN` 时，系统必须保证删除后仍至少有一个 `ACTIVE SUPER_ADMIN`。

### 7.2 成功响应

- HTTP 200。
- `data` 为 `null`。

### 7.3 错误

- 400 `VALIDATION_ERROR`
- 401 `UNAUTHORIZED`
- 403 `ACCESS_DENIED`
- 403 `ACCOUNT_DISABLED`
- 404 `RESOURCE_NOT_FOUND`
- 409 `RESOURCE_CONFLICT`
- 503 `DATABASE_UNAVAILABLE`

## 8. 权限响应

未登录：

```json
{
  "code": "UNAUTHORIZED",
  "message": "缺少、过期或无效 JWT",
  "details": null,
  "traceId": "6a5d4129e4de4b25"
}
```

已登录但不是超级管理员：

```json
{
  "code": "ACCESS_DENIED",
  "message": "无权访问用户管理",
  "details": null,
  "traceId": "6a5d4129e4de4b25"
}
```

## 9. 认证接口兼容调整

`POST /api/v1/auth/login` 和 `GET /api/v1/auth/me` 的用户对象继续返回：

```json
{
  "id": "8c19c207-8053-4bee-a76e-6590eaaee846",
  "username": "admin",
  "email": "admin@example.com",
  "roleCode": "SUPER_ADMIN"
}
```

兼容变化：

- `roleCode` 从固定 `ADMIN` 扩展为三种角色。
- 只有 `ACTIVE` 可以登录和继续使用 JWT。
- `PENDING`、`FROZEN`、`DELETED` 均返回 403 `ACCOUNT_DISABLED`。
- 认证响应不新增用户管理状态字段，前端需要状态时以认证成功等价于 `ACTIVE`。

## 10. 审计

| API | 审计动作 | beforeValue | afterValue |
| --- | --- | --- | --- |
| POST | `CREATE_USER` | `null` | 新用户脱敏快照 |
| PATCH | `UPDATE_USER` | 修改前脱敏快照 | 修改后脱敏快照 |
| DELETE | `DELETE_USER` | 删除前脱敏快照 | `DELETED` 脱敏快照 |

脱敏快照只允许：`id`、`username`、`email`、`roleCode`、`status`、`version`。密码及密码哈希始终排除。

## 11. 并发与事务

- 添加用户的插入和审计处于同一事务。
- 编辑和删除使用 `WHERE id = ? AND version = ?` 乐观锁更新。
- 最后超级管理员校验、目标更新和审计处于同一事务。
- 最后超级管理员保护必须在事务中锁定相关 `ACTIVE SUPER_ADMIN` 行，避免并发操作同时通过校验。
- 数据库唯一约束是用户名和邮箱冲突的最终保障；服务层预检查只用于友好提示。
