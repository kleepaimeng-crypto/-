import { mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { ApiClientError } from '../api/http'
import UserManagementView from './UserManagementView.vue'

const usersApi = vi.hoisted(() => ({
  getUsers: vi.fn(),
  createUser: vi.fn(),
  updateUser: vi.fn(),
  deleteUser: vi.fn(),
}))

vi.mock('../api/users', () => usersApi)
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
}))
vi.mock('../auth/session', () => ({
  authSession: {
    state: {
      user: {
        id: 'current-user',
        username: 'root',
        email: 'root@example.com',
        roleCode: 'SUPER_ADMIN',
      },
    },
    logout: vi.fn(),
  },
}))

const emptyPage = { items: [], page: 1, pageSize: 20, total: 0, totalPages: 0 }
let wrapper: VueWrapper | undefined

describe('UserManagementView', () => {
  beforeEach(() => {
    usersApi.getUsers.mockResolvedValue(emptyPage)
    usersApi.createUser.mockResolvedValue(user('new-user'))
    usersApi.updateUser.mockResolvedValue(user('target-user'))
    usersApi.deleteUser.mockResolvedValue(null)
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    vi.clearAllMocks()
  })

  it('loads the first page with the documented defaults', async () => {
    wrapper = mount(UserManagementView)
    await settleRequests()

    expect(usersApi.getUsers).toHaveBeenCalledWith({
      page: 1,
      pageSize: 20,
      sortBy: 'createdAt',
      sortDirection: 'desc',
    })
    expect(wrapper.text()).toContain('暂无用户数据')
  })

  it('sorts username on the server', async () => {
    usersApi.getUsers.mockResolvedValue({
      ...emptyPage,
      items: [user('target-user')],
      total: 1,
      totalPages: 1,
    })
    wrapper = mount(UserManagementView)
    await settleRequests()
    usersApi.getUsers.mockClear()

    await wrapper.find('th button').trigger('click')
    await settleRequests()

    expect(usersApi.getUsers).toHaveBeenCalledWith(expect.objectContaining({
      sortBy: 'username',
      sortDirection: 'asc',
    }))
  })

  it('creates a pending normal user by default', async () => {
    wrapper = mount(UserManagementView)
    await settleRequests()

    await wrapper.find('.user-add-button').trigger('click')
    const inputs = wrapper.findAll('.user-dialog input')
    await inputs[0]?.setValue('operator01')
    await inputs[1]?.setValue('operator01@example.com')
    await inputs[2]?.setValue('abc123')
    await inputs[3]?.setValue('abc123')
    await wrapper.find('.user-dialog .button--accent').trigger('click')
    await settleRequests()

    expect(usersApi.createUser).toHaveBeenCalledWith({
      username: 'operator01',
      email: 'operator01@example.com',
      password: 'abc123',
      roleCode: 'USER',
      status: 'PENDING',
    })
  })

  it('keeps form validation errors inside the active dialog', async () => {
    wrapper = mount(UserManagementView)
    await settleRequests()

    await wrapper.find('.user-add-button').trigger('click')
    await wrapper.find('.user-dialog .button--accent').trigger('click')
    await nextTick()

    expect(wrapper.find('.user-dialog .inline-error').text()).toContain('用户名')
    expect(wrapper.find('.user-feedback').exists()).toBe(false)
  })

  it('shows the backend field validation message instead of a generic error', async () => {
    usersApi.createUser.mockRejectedValue(new ApiClientError(
      400,
      'VALIDATION_ERROR',
      '参数校验失败',
      [{ field: 'password', reason: '初始密码长度必须为 6–72 个字符' }],
      'trace-id',
    ))
    wrapper = mount(UserManagementView)
    await settleRequests()

    await wrapper.find('.user-add-button').trigger('click')
    const inputs = wrapper.findAll('.user-dialog input')
    await inputs[0]?.setValue('operator01')
    await inputs[1]?.setValue('operator01@example.com')
    await inputs[2]?.setValue('abc123')
    await inputs[3]?.setValue('abc123')
    await wrapper.find('.user-dialog .button--accent').trigger('click')
    await settleRequests()

    expect(wrapper.find('.user-dialog .inline-error').text())
      .toBe('初始密码长度必须为 6–72 个字符')
  })
})

function user(id: string) {
  return {
    id,
    username: 'operator01',
    email: 'operator01@example.com',
    roleCode: 'USER' as const,
    status: 'ACTIVE' as const,
    lastLoginAt: null,
    version: 1,
    createdAt: '2026-07-09T10:00:00+08:00',
    updatedAt: '2026-07-09T10:00:00+08:00',
    deletedAt: null,
  }
}

async function settleRequests(): Promise<void> {
  await Promise.resolve()
  await Promise.resolve()
  await nextTick()
}
