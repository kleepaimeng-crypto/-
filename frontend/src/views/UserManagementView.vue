<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { authSession } from '../auth/session'
import { ApiClientError } from '../api/http'
import {
  createUser,
  deleteUser,
  getUsers,
  updateUser,
} from '../api/users'
import type {
  UserCreatePayload,
  UserQuery,
  UserUpdatePayload,
} from '../api/users'
import type { UserRole, UserStatus, UserSummaryDto } from '../api/types'
import FixedCanvasShell from '../components/FixedCanvasShell.vue'
import PlatformBrand from '../components/PlatformBrand.vue'

const router = useRouter()
const userHeaderAssetUrl = '/assets/用户管理表头.svg'
const users = ref<UserSummaryDto[]>([])
const loading = ref(false)
const actionLoading = ref(false)
const listError = ref('')
const actionError = ref('')
const pageError = ref('')
const actionNotice = ref('')
const page = ref(1)
const pageSize = ref<20 | 50 | 100>(20)
const total = ref(0)
const totalPages = ref(0)
const sort = reactive<Pick<UserQuery, 'sortBy' | 'sortDirection'>>({
  sortBy: 'createdAt',
  sortDirection: 'desc',
})

const createOpen = ref(false)
const editOpen = ref(false)
const deleteOpen = ref(false)
const activeUser = ref<UserSummaryDto | null>(null)
const confirmPassword = ref('')
const createForm = reactive<UserCreatePayload>({
  username: '',
  email: '',
  password: '',
  roleCode: 'USER',
  status: 'PENDING',
})
const editForm = reactive<UserUpdatePayload>({
  username: '',
  email: '',
  roleCode: 'USER',
  status: 'PENDING',
  expectedVersion: 1,
})
const deleteReason = ref('')

const pageNumbers = computed(() => {
  if (totalPages.value <= 5) {
    return Array.from({ length: totalPages.value }, (_, index) => index + 1)
  }
  const start = Math.min(Math.max(1, page.value - 1), totalPages.value - 2)
  return [start, start + 1, start + 2]
})

async function loadUsers(background = false): Promise<void> {
  if (!background) loading.value = true
  listError.value = ''
  pageError.value = ''
  try {
    const result = await getUsers({
      page: page.value,
      pageSize: pageSize.value,
      sortBy: sort.sortBy,
      sortDirection: sort.sortDirection,
    })
    users.value = result.items
    total.value = result.total
    totalPages.value = result.totalPages
  } catch (error) {
    if (!background) {
      users.value = []
      total.value = 0
      totalPages.value = 0
      listError.value = errorMessage(error, '用户列表加载失败')
    } else {
      pageError.value = errorMessage(error, '列表刷新失败')
    }
  } finally {
    loading.value = false
  }
}

function changeSort(field: UserQuery['sortBy']): void {
  if (sort.sortBy === field) {
    sort.sortDirection = sort.sortDirection === 'asc' ? 'desc' : 'asc'
  } else {
    sort.sortBy = field
    sort.sortDirection = 'asc'
  }
  page.value = 1
  void loadUsers()
}

function sortMark(field: UserQuery['sortBy']): string {
  if (sort.sortBy !== field) return '↕'
  return sort.sortDirection === 'asc' ? '↑' : '↓'
}

function goToPage(nextPage: number): void {
  if (nextPage < 1 || nextPage > totalPages.value || nextPage === page.value) return
  page.value = nextPage
  void loadUsers()
}

function changePageSize(): void {
  page.value = 1
  void loadUsers()
}

function openCreate(): void {
  Object.assign(createForm, {
    username: '',
    email: '',
    password: '',
    roleCode: 'USER' as UserRole,
    status: 'PENDING' as const,
  })
  confirmPassword.value = ''
  actionError.value = ''
  actionNotice.value = ''
  createOpen.value = true
}

async function submitCreate(): Promise<void> {
  const validation = validateCreate()
  if (validation) {
    actionError.value = validation
    return
  }
  actionLoading.value = true
  actionError.value = ''
  actionNotice.value = ''
  try {
    await createUser({
      ...createForm,
      username: createForm.username.trim(),
      email: createForm.email.trim(),
    })
    createOpen.value = false
    actionNotice.value = '用户添加成功。'
    page.value = 1
    await loadUsers()
  } catch (error) {
    actionError.value = formErrorMessage(error, '用户添加失败')
  } finally {
    createForm.password = ''
    confirmPassword.value = ''
    actionLoading.value = false
  }
}

function openEdit(user: UserSummaryDto): void {
  if (user.status === 'DELETED') return
  activeUser.value = user
  Object.assign(editForm, {
    username: user.username,
    email: user.email ?? '',
    roleCode: user.roleCode,
    status: user.status as Exclude<UserStatus, 'DELETED'>,
    expectedVersion: user.version,
  })
  actionError.value = ''
  actionNotice.value = ''
  editOpen.value = true
}

async function submitEdit(): Promise<void> {
  if (!activeUser.value) return
  const validation = validateIdentity(editForm.username ?? '', editForm.email ?? '')
  if (validation) {
    actionError.value = validation
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    await updateUser(activeUser.value.id, {
      username: editForm.username?.trim(),
      email: editForm.email?.trim(),
      roleCode: editForm.roleCode,
      status: editForm.status,
      expectedVersion: editForm.expectedVersion,
    })
    editOpen.value = false
    actionNotice.value = '用户信息已更新。'
    await loadUsers()
  } catch (error) {
    actionError.value = formErrorMessage(error, '用户更新失败')
  } finally {
    actionLoading.value = false
  }
}

function openDelete(user: UserSummaryDto): void {
  if (user.status === 'DELETED' || user.id === authSession.state.user?.id) return
  activeUser.value = user
  deleteReason.value = ''
  actionError.value = ''
  actionNotice.value = ''
  deleteOpen.value = true
}

async function submitDelete(): Promise<void> {
  if (!activeUser.value || !deleteReason.value.trim()) {
    actionError.value = '请填写删除原因。'
    return
  }
  actionLoading.value = true
  actionError.value = ''
  try {
    await deleteUser(activeUser.value.id, deleteReason.value.trim(), activeUser.value.version)
    deleteOpen.value = false
    actionNotice.value = '用户已删除。'
    await loadUsers()
  } catch (error) {
    actionError.value = formErrorMessage(error, '用户删除失败')
  } finally {
    actionLoading.value = false
  }
}

function validateCreate(): string {
  const identityError = validateIdentity(createForm.username, createForm.email)
  if (identityError) return identityError
  if (createForm.password.length < 6 || createForm.password.length > 72) {
    return '初始密码长度必须为 6–72 个字符。'
  }
  if (createForm.password !== confirmPassword.value) return '两次输入的密码不一致。'
  return ''
}

function validateIdentity(username: string, email: string): string {
  if (!/^[A-Za-z0-9._-]{3,64}$/.test(username.trim())) {
    return '用户名需为 3–64 位字母、数字、点、下划线或连字符。'
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim()) || email.trim().length > 254) {
    return '请输入有效邮箱。'
  }
  return ''
}

function roleLabel(role: UserRole): string {
  return {
    SUPER_ADMIN: '超级管理员',
    ADMIN: '管理员',
    USER: '普通用户',
  }[role]
}

function statusLabel(status: UserStatus): string {
  return {
    ACTIVE: '激活',
    PENDING: '未激活',
    FROZEN: '冻结',
    DELETED: '删除',
  }[status]
}

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof ApiClientError ? `${error.message}（${error.code}）` : fallback
}

function formErrorMessage(error: unknown, fallback: string): string {
  if (!(error instanceof ApiClientError)) return fallback
  const detail = error.details[0]
  return detail?.reason || `${error.message}（${error.code}）`
}

async function logout(): Promise<void> {
  authSession.logout()
  await router.replace('/login')
}

onMounted(() => {
  void loadUsers()
})
</script>

<template>
  <FixedCanvasShell shell-class="user-shell">
    <header class="workspace-header">
      <PlatformBrand compact />
      <nav class="workspace-nav" aria-label="主导航">
        <button class="workspace-nav__item" @click="router.push('/')">数据管理</button>
        <button class="workspace-nav__item" @click="router.push('/flight-track')">飞机轨迹实时系统</button>
        <button class="workspace-nav__item" disabled>飞机轨迹回放系统</button>
        <button class="workspace-nav__item" disabled>数据统计</button>
        <button class="workspace-nav__item is-active">用户管理</button>
        <button class="workspace-nav__item" @click="router.push('/passenger-realtime')">乘客实时动态</button>
      </nav>
      <div class="workspace-header__account">
        <span class="account-dot"></span>
        <span>{{ authSession.state.user?.username }}</span>
        <button class="text-action" @click="logout">退出</button>
      </div>
    </header>

    <section class="user-layout">
      <header class="user-titlebar panel-title">
        <img class="user-titlebar__asset" :src="userHeaderAssetUrl" alt="" aria-hidden="true" />
        <span></span>
        <h2>用户管理</h2>
        <button class="user-add-button" type="button" @click="openCreate">用户添加</button>
      </header>

      <div v-if="actionNotice || pageError" class="user-feedback" :class="{ 'is-error': pageError }">
        {{ pageError || actionNotice }}
      </div>

      <section class="user-table-panel" aria-label="用户列表">
        <table class="user-table">
          <thead>
            <tr>
              <th class="user-table__index">序号</th>
              <th>
                <button type="button" @click="changeSort('username')">用户名 <span>{{ sortMark('username') }}</span></button>
              </th>
              <th>
                <button type="button" @click="changeSort('roleCode')">权限 <span>{{ sortMark('roleCode') }}</span></button>
              </th>
              <th>
                <button type="button" @click="changeSort('email')">邮箱 <span>{{ sortMark('email') }}</span></button>
              </th>
              <th>
                <button type="button" @click="changeSort('status')">状态 <span>{{ sortMark('status') }}</span></button>
              </th>
              <th class="user-table__actions">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(user, index) in users" :key="user.id" :class="{ 'is-deleted': user.status === 'DELETED' }">
              <td>{{ (page - 1) * pageSize + index + 1 }}</td>
              <td class="user-table__username">{{ user.username }}</td>
              <td>{{ roleLabel(user.roleCode) }}</td>
              <td>{{ user.email || '—' }}</td>
              <td>
                <span class="user-status" :class="`user-status--${user.status.toLowerCase()}`">
                  {{ statusLabel(user.status) }}
                </span>
              </td>
              <td>
                <button
                  class="user-row-action"
                  type="button"
                  :disabled="user.status === 'DELETED'"
                  @click="openEdit(user)"
                >编辑</button>
                <button
                  class="user-row-action user-row-action--danger"
                  type="button"
                  :disabled="user.status === 'DELETED' || user.id === authSession.state.user?.id"
                  @click="openDelete(user)"
                >删除</button>
              </td>
            </tr>
          </tbody>
        </table>

        <div v-if="loading" class="user-table-state">
          <span class="loader"></span>
          <strong>正在加载用户</strong>
        </div>
        <div v-else-if="listError" class="user-table-state user-table-state--error">
          <span>USER_SERVICE_ERROR</span>
          <strong>{{ listError }}</strong>
          <button class="button button--ghost" type="button" @click="loadUsers()">重新加载</button>
        </div>
        <div v-else-if="users.length === 0" class="user-table-state">
          <span>NO_USERS</span>
          <strong>暂无用户数据</strong>
        </div>
      </section>

      <footer class="user-table-footer">
        <div>
          <span>总 <strong>{{ total }}</strong> 条</span>
          <select v-model="pageSize" aria-label="每页条数" @change="changePageSize">
            <option :value="20">20条/页</option>
            <option :value="50">50条/页</option>
            <option :value="100">100条/页</option>
          </select>
        </div>
        <nav class="user-pagination" aria-label="用户列表分页">
          <button type="button" :disabled="page <= 1" @click="goToPage(page - 1)">上一页</button>
          <button
            v-for="pageNumber in pageNumbers"
            :key="pageNumber"
            type="button"
            :class="{ 'is-current': pageNumber === page }"
            @click="goToPage(pageNumber)"
          >{{ pageNumber }}</button>
          <span v-if="totalPages > 5">…</span>
          <button
            v-if="totalPages > 5 && !pageNumbers.includes(totalPages)"
            type="button"
            @click="goToPage(totalPages)"
          >{{ totalPages }}</button>
          <button type="button" :disabled="page >= totalPages" @click="goToPage(page + 1)">下一页</button>
        </nav>
      </footer>
    </section>

    <footer class="workspace-footer">
      <span>部件号：XXXXXXXXXXXXXXXXX</span>
      <span>版本号：V0.1</span>
    </footer>

    <div v-if="createOpen" class="overlay" @click.self="createOpen = false">
      <section class="dialog-panel user-dialog" role="dialog" aria-modal="true" aria-labelledby="create-user-title">
        <header>
          <div>
            <span class="section-kicker">USER CREATE</span>
            <h2 id="create-user-title">添加用户</h2>
          </div>
          <button class="close-button" type="button" aria-label="关闭" @click="createOpen = false">×</button>
        </header>
        <div class="form-grid">
          <label>用户名<input v-model="createForm.username" autocomplete="off" maxlength="64" /></label>
          <label>邮箱<input v-model="createForm.email" type="email" autocomplete="off" maxlength="254" /></label>
          <label>初始密码<input v-model="createForm.password" type="password" autocomplete="new-password" maxlength="72" /></label>
          <label>确认密码<input v-model="confirmPassword" type="password" autocomplete="new-password" maxlength="72" /></label>
          <label>权限
            <select v-model="createForm.roleCode">
              <option value="SUPER_ADMIN">超级管理员</option>
              <option value="ADMIN">管理员</option>
              <option value="USER">普通用户</option>
            </select>
          </label>
          <label>状态
            <select v-model="createForm.status">
              <option value="PENDING">未激活</option>
              <option value="ACTIVE">激活</option>
            </select>
          </label>
        </div>
        <p v-if="actionError" class="inline-error">{{ actionError }}</p>
        <footer>
          <button class="button button--ghost" type="button" @click="createOpen = false">取消</button>
          <button class="button button--accent" type="button" :disabled="actionLoading" @click="submitCreate">
            {{ actionLoading ? '提交中' : '添加用户' }}
          </button>
        </footer>
      </section>
    </div>

    <div v-if="editOpen" class="overlay" @click.self="editOpen = false">
      <section class="dialog-panel user-dialog" role="dialog" aria-modal="true" aria-labelledby="edit-user-title">
        <header>
          <div>
            <span class="section-kicker">USER EDIT</span>
            <h2 id="edit-user-title">编辑用户</h2>
          </div>
          <button class="close-button" type="button" aria-label="关闭" @click="editOpen = false">×</button>
        </header>
        <div class="form-grid">
          <label>用户名<input v-model="editForm.username" maxlength="64" /></label>
          <label>邮箱<input v-model="editForm.email" type="email" maxlength="254" /></label>
          <label>权限
            <select v-model="editForm.roleCode" :disabled="activeUser?.id === authSession.state.user?.id">
              <option value="SUPER_ADMIN">超级管理员</option>
              <option value="ADMIN">管理员</option>
              <option value="USER">普通用户</option>
            </select>
          </label>
          <label>状态
            <select v-model="editForm.status" :disabled="activeUser?.id === authSession.state.user?.id">
              <option value="ACTIVE">激活</option>
              <option value="PENDING">未激活</option>
              <option value="FROZEN">冻结</option>
            </select>
          </label>
        </div>
        <p v-if="activeUser?.id === authSession.state.user?.id" class="user-dialog__hint">
          当前账号不能修改自身权限或状态。
        </p>
        <p v-if="actionError" class="inline-error">{{ actionError }}</p>
        <footer>
          <button class="button button--ghost" type="button" @click="editOpen = false">取消</button>
          <button class="button button--accent" type="button" :disabled="actionLoading" @click="submitEdit">
            {{ actionLoading ? '保存中' : '保存修改' }}
          </button>
        </footer>
      </section>
    </div>

    <div v-if="deleteOpen" class="overlay" @click.self="deleteOpen = false">
      <section class="dialog-panel dialog-panel--small user-dialog" role="dialog" aria-modal="true" aria-labelledby="delete-user-title">
        <header>
          <div>
            <span class="section-kicker">USER DELETE</span>
            <h2 id="delete-user-title">删除用户</h2>
          </div>
          <button class="close-button" type="button" aria-label="关闭" @click="deleteOpen = false">×</button>
        </header>
        <p class="dialog-hint">
          用户“{{ activeUser?.username }}”删除后不可恢复，但历史操作记录仍会保留。
        </p>
        <label class="field-label">删除原因
          <textarea v-model="deleteReason" rows="4" maxlength="500"></textarea>
        </label>
        <p v-if="actionError" class="inline-error">{{ actionError }}</p>
        <footer>
          <button class="button button--ghost" type="button" @click="deleteOpen = false">取消</button>
          <button class="button button--danger" type="button" :disabled="actionLoading" @click="submitDelete">
            {{ actionLoading ? '删除中' : '确认删除' }}
          </button>
        </footer>
      </section>
    </div>
  </FixedCanvasShell>
</template>
