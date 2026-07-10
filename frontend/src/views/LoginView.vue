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
    { required: true, message: '请输入用户账号', trigger: 'blur' },
    { max: 64, message: '账号长度不能超过 64 个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { max: 256, message: '密码长度不能超过 256 个字符', trigger: 'blur' },
  ],
}
const submitting = ref(false)
const errorMessage = ref('')
const errorDetail = ref('')
const router = useRouter()
const route = useRoute()
const redirectTarget = computed(() => (typeof route.query.redirect === 'string' ? route.query.redirect : '/'))

async function submit(): Promise<void> {
  errorMessage.value = ''
  errorDetail.value = ''
  if (!formRef.value || !(await formRef.value.validate().catch(() => false))) {
    return
  }

  submitting.value = true
  try {
    await authSession.login({ username: form.username, password: form.password })
    await router.replace(redirectTarget.value)
  } catch (error: unknown) {
    const displayError = resolveLoginError(error)
    errorMessage.value = displayError.title
    errorDetail.value = displayError.detail
  } finally {
    form.password = ''
    submitting.value = false
  }
}

function resolveLoginError(error: unknown): { title: string; detail: string } {
  if (!(error instanceof ApiClientError)) {
    return {
      title: '服务暂时不可用',
      detail: '请确认平台服务状态后重试。',
    }
  }

  if (error.code === 'UNAUTHORIZED') {
    return {
      title: '账号或密码不正确',
      detail: '请核对用户账号和密码。',
    }
  }

  if (error.code === 'ACCOUNT_DISABLED') {
    return {
      title: '账号已停用',
      detail: '请联系系统管理员处理。',
    }
  }

  if (error.code === 'TOO_MANY_REQUESTS') {
    return {
      title: '登录尝试过于频繁',
      detail: '请稍后再试。',
    }
  }

  return {
    title: '登录失败',
    detail: error.message || '请稍后重试。',
  }
}
</script>

<template>
  <main class="login-shell">
    <div class="login-shell__grid" aria-hidden="true"></div>
    <div class="login-shell__route login-shell__route--one" aria-hidden="true"></div>
    <div class="login-shell__route login-shell__route--two" aria-hidden="true"></div>
    <div class="login-ground-map" aria-hidden="true">
      <span class="login-ground-map__runway login-ground-map__runway--main"></span>
      <span class="login-ground-map__runway login-ground-map__runway--aux"></span>
      <span class="login-ground-map__taxiway login-ground-map__taxiway--one"></span>
      <span class="login-ground-map__taxiway login-ground-map__taxiway--two"></span>
      <span class="login-ground-map__node login-ground-map__node--a"></span>
      <span class="login-ground-map__node login-ground-map__node--b"></span>
      <span class="login-ground-map__node login-ground-map__node--c"></span>
      <span class="login-ground-map__node login-ground-map__node--d"></span>
    </div>

    <header class="login-header">
      <PlatformBrand />
      <div class="login-header__status">
        <span class="status-light"></span>
        安全接入
      </div>
    </header>

    <div class="login-company-strip" aria-label="中电科航空电子有限公司">
      <img src="/assets/logo-red.png" alt="CETC" />
      <span>中电科航空电子有限公司</span>
    </div>

    <section class="login-stage">
      <div class="login-context">
        <p class="login-context__eyebrow">GROUND SYSTEM</p>
        <h1>地面系统<br /><span>管理入口</span></h1>
        <p class="login-context__summary">请使用地面系统管理员账号登录平台。</p>
      </div>

      <div class="login-panel">
        <div class="login-panel__heading">
          <span class="login-panel__index">01</span>
          <div>
            <h2>地面系统登录</h2>
            <p>请输入管理员账号和密码完成身份验证</p>
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
          <template v-if="errorDetail" #default>
            <span>{{ errorDetail }}</span>
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
            {{ submitting ? '正在登录' : '登录' }}
          </el-button>
        </el-form>

        <p class="login-panel__notice">登录操作将记录审计，密码和令牌不会写入日志。</p>
      </div>
    </section>

    <footer class="login-footer">
      <span>前中后舱网联数据显示平台</span>
      <span>AUTHORIZED ACCESS ONLY</span>
    </footer>
  </main>
</template>
