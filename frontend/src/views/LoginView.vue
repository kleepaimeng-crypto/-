<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ApiClientError } from '../api/http'
import { authSession } from '../auth/session'
import PlatformBrand from '../components/PlatformBrand.vue'

interface LoginForm {
  username: string
  password: string
}

const formRef = ref<FormInstance>()
const form = reactive<LoginForm>({ username: '', password: '' })
const rules: FormRules<LoginForm> = {
  username: [
    { required: true, message: '请输入管理员账号', trigger: 'blur' },
    { max: 64, message: '账号长度不能超过 64 个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { max: 256, message: '密码长度不能超过 256 个字符', trigger: 'blur' },
  ],
}
const submitting = ref(false)
const errorMessage = ref('')
const traceId = ref('')
const router = useRouter()
const route = useRoute()
const redirectTarget = computed(() => (typeof route.query.redirect === 'string' ? route.query.redirect : '/'))

async function submit(): Promise<void> {
  errorMessage.value = ''
  traceId.value = ''
  if (!formRef.value || !(await formRef.value.validate().catch(() => false))) {
    return
  }

  submitting.value = true
  try {
    await authSession.login({ username: form.username, password: form.password })
    await router.replace(redirectTarget.value)
  } catch (error: unknown) {
    if (error instanceof ApiClientError) {
      errorMessage.value = error.message
      traceId.value = error.traceId
    } else {
      errorMessage.value = '暂时无法连接到平台服务，请稍后重试'
    }
  } finally {
    form.password = ''
    submitting.value = false
  }
}
</script>

<template>
  <main class="login-shell">
    <div class="login-shell__grid" aria-hidden="true"></div>
    <div class="login-shell__route login-shell__route--one" aria-hidden="true"></div>
    <div class="login-shell__route login-shell__route--two" aria-hidden="true"></div>

    <header class="login-header">
      <PlatformBrand />
      <div class="login-header__status">
        <span class="status-light"></span>
        安全接入
      </div>
    </header>

    <section class="login-stage">
      <div class="login-context">
        <p class="login-context__eyebrow">CABIN DATA NETWORK</p>
        <h1>统一数据链路<br /><span>管理入口</span></h1>
        <p class="login-context__summary">管理员身份验证后进入数据工作台。</p>
        <div class="login-context__telemetry" aria-hidden="true">
          <span>7 UDP CHANNELS</span>
          <span>POSTGRESQL 18</span>
          <span>TRACE ENABLED</span>
        </div>
      </div>

      <div class="login-panel">
        <div class="login-panel__heading">
          <span class="login-panel__index">01</span>
          <div>
            <h2>管理员登录</h2>
            <p>使用部署环境配置的管理员账号</p>
          </div>
        </div>

        <el-alert
          v-if="errorMessage"
          class="login-panel__alert"
          :title="errorMessage"
          type="error"
          :closable="false"
          show-icon
        >
          <template v-if="traceId" #default>
            <span>追踪号：{{ traceId }}</span>
          </template>
        </el-alert>

        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
          <el-form-item label="管理员账号" prop="username">
            <el-input
              v-model="form.username"
              autocomplete="username"
              maxlength="64"
              placeholder="请输入账号"
              size="large"
            />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              autocomplete="current-password"
              maxlength="256"
              placeholder="请输入密码"
              show-password
              size="large"
              type="password"
              @keyup.enter="submit"
            />
          </el-form-item>
          <el-button class="login-panel__submit" type="primary" :loading="submitting" native-type="submit" size="large">
            {{ submitting ? '正在验证' : '安全登录' }}
          </el-button>
        </el-form>

        <p class="login-panel__notice">登录行为将写入安全审计，密码和 Token 不会进入日志。</p>
      </div>
    </section>

    <footer class="login-footer">
      <span>前中后舱网联数据显示平台</span>
      <span>AUTHORIZED ACCESS ONLY</span>
    </footer>
  </main>
</template>
