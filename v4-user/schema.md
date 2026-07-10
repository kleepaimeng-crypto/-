# 用户管理数据库演进方案

## 1. Schema 决策

本期复用 `app_user`，不新增角色表、权限表或用户角色关系表。数据库通过新 Flyway 迁移从现有固定管理员模型演进为三角色、四状态模型。

新迁移文件：

```text
backend/src/main/resources/db/migration/V10__add_user_management.sql
```

禁止修改已经发布的 `V2__create_core_tables.sql` 和 `V5__create_core_indexes_and_triggers.sql`。

## 2. 现有结构

现有 `app_user` 已包含：

- UUID 主键。
- 用户名、密码哈希、邮箱。
- `role_code`，当前约束只允许 `ADMIN`。
- `status`，当前只允许 `ACTIVE`、`DISABLED`。
- 最近登录时间。
- 乐观锁 `version`。
- 创建和更新时间。
- 用户名、邮箱不区分大小写唯一索引。
- `updated_at` 自动更新时间触发器。

`app_user` 被文件任务、数据记录、标签、批注和审计日志引用，因此不能采用物理删除。

## 3. 目标字段

| 字段 | PostgreSQL 类型 | 约束/默认值 | 说明 |
| --- | --- | --- | --- |
| `id` | uuid | PK | 用户 ID |
| `username` | varchar(64) | NOT NULL，保持现有 1～64 长度约束 | 规范化登录名；新建和编辑由服务层执行更严格格式校验 |
| `password_hash` | varchar(255) | NOT NULL | BCrypt 哈希 |
| `email` | varchar(254) | NULL | 历史数据允许空；新建和编辑由服务层要求非空 |
| `role_code` | varchar(32) | NOT NULL，默认 `USER` | `SUPER_ADMIN`、`ADMIN`、`USER` |
| `status` | varchar(16) | NOT NULL，默认 `PENDING` | `ACTIVE`、`PENDING`、`FROZEN`、`DELETED` |
| `last_login_at` | timestamptz | NULL | 最近成功登录时间 |
| `version` | integer | NOT NULL，默认 1，CHECK > 0 | 乐观锁 |
| `deleted_at` | timestamptz | NULL | 软删除时间 |
| `deleted_by` | uuid | NULL，FK -> `app_user.id`，ON DELETE RESTRICT | 删除操作人 |
| `delete_reason` | varchar(500) | NULL | 删除原因 |
| `created_at` | timestamptz | NOT NULL | 创建时间 |
| `updated_at` | timestamptz | NOT NULL | 更新时间 |

## 4. 约束

角色约束：

```sql
CHECK (role_code IN ('SUPER_ADMIN', 'ADMIN', 'USER'))
```

状态约束：

```sql
CHECK (status IN ('ACTIVE', 'PENDING', 'FROZEN', 'DELETED'))
```

删除字段一致性：

```sql
CHECK (
    (
        status = 'DELETED'
        AND deleted_at IS NOT NULL
        AND deleted_by IS NOT NULL
        AND length(btrim(delete_reason)) BETWEEN 1 AND 500
    )
    OR
    (
        status <> 'DELETED'
        AND deleted_at IS NULL
        AND deleted_by IS NULL
        AND delete_reason IS NULL
    )
)
```

数据库保留现有用户名 trim 后 1～64 长度约束，避免历史账号因新格式收紧导致迁移失败。新建和编辑由服务层执行 `^[A-Za-z0-9._-]{3,64}$` 校验，并在写入前把用户名和邮箱 trim、转小写。数据库现有不区分大小写唯一索引继续作为最终一致性保障。

## 5. 索引

保留：

```sql
CREATE UNIQUE INDEX uk_app_user_username_ci
    ON app_user (lower(username));

CREATE UNIQUE INDEX uk_app_user_email_ci
    ON app_user (lower(email))
    WHERE email IS NOT NULL;
```

将现有状态索引调整为适合分页列表的组合索引：

```sql
CREATE INDEX idx_app_user_status_created
    ON app_user (status, created_at DESC, id);

CREATE INDEX idx_app_user_role_created
    ON app_user (role_code, created_at DESC, id);
```

默认列表按 `created_at DESC, id` 查询。用户名、邮箱排序可以使用现有唯一索引；预计用户规模较小，不为每个排序组合建立复合索引。只有实测出现瓶颈后再新增。

## 6. V10 迁移顺序

迁移必须按以下顺序执行：

1. 删除旧的角色和状态 CHECK 约束。
2. 增加 `deleted_at`、`deleted_by`、`delete_reason`。
3. 把现有 `role_code='ADMIN'` 更新为 `SUPER_ADMIN`。
4. 把现有 `status='DISABLED'` 更新为 `FROZEN`。
5. 修改列默认值：角色默认 `USER`，状态默认 `PENDING`。
6. 新增三角色和四状态 CHECK 约束。
7. 新增删除字段一致性约束，保留现有用户名长度约束。
8. 调整状态索引并新增角色索引。
9. 保留现有 `trg_app_user_updated_at` 触发器。

迁移不得：

- 删除或重建 `app_user`。
- 删除任何现有用户。
- 修改用户密码哈希。
- 为满足新用户名格式而自动改写历史登录名。
- 清空用户名或邮箱。
- 改写审计日志和其他表中的用户外键。

## 7. 数据兼容

迁移映射：

| 旧值 | 新值 |
| --- | --- |
| `role_code=ADMIN` | `role_code=SUPER_ADMIN` |
| `status=ACTIVE` | `status=ACTIVE` |
| `status=DISABLED` | `status=FROZEN` |

把所有现有管理员迁移为超级管理员的原因是避免升级后无人可以访问用户管理。部署后如果存在多个历史管理员，由超级管理员在页面中按实际职责调整角色。

新建用户默认 `USER + PENDING`。应用启动时创建的 bootstrap 管理员使用 `SUPER_ADMIN + ACTIVE`。

## 8. 软删除

删除 SQL 必须包含版本和状态条件：

```sql
UPDATE app_user
SET status = 'DELETED',
    deleted_at = now(),
    deleted_by = :operatorId,
    delete_reason = :reason,
    version = version + 1
WHERE id = :userId
  AND version = :expectedVersion
  AND status <> 'DELETED';
```

禁止执行 `DELETE FROM app_user`。

已删除用户继续占用用户名和邮箱。这样可以保持登录名、审计目标和历史业务外键的一一对应关系。

## 9. 最后超级管理员保护

将 `ACTIVE SUPER_ADMIN` 改为其他角色、改为非 `ACTIVE` 或软删除前，事务必须：

1. 锁定所有 `status='ACTIVE' AND role_code='SUPER_ADMIN'` 的用户行。
2. 确认排除目标用户后仍至少有一行。
3. 执行带版本条件的更新。
4. 写入审计日志。
5. 提交事务。

只做无锁 `COUNT(*)` 会产生并发竞态，不符合本期安全要求。

## 10. 查询规则

分页查询必须：

- 返回全部状态，包括 `DELETED`。
- 使用白名单映射排序列。
- 使用独立 `COUNT(*)` 计算总数。
- 始终追加 `id` 作为稳定排序键。
- 不查询或返回 `password_hash`、`deleted_by`、`delete_reason`。

## 11. 验证

在一次性测试数据库验证：

1. 从 V1 顺序迁移到 V10 成功。
2. 已有管理员角色和禁用状态映射正确。
3. 原用户 ID、密码哈希和历史外键不变。
4. 三角色、四状态和删除一致性约束生效。
5. 用户名、邮箱不区分大小写唯一约束生效。
6. 软删除不会破坏审计及业务表外键。
7. `updated_at` 触发器和 `version` 更新正常。
