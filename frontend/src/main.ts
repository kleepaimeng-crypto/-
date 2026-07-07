import { createApp } from 'vue'
import { ElAlert, ElButton, ElForm, ElFormItem, ElInput } from 'element-plus'
import 'element-plus/theme-chalk/base.css'
import 'element-plus/theme-chalk/el-alert.css'
import 'element-plus/theme-chalk/el-button.css'
import 'element-plus/theme-chalk/el-form.css'
import 'element-plus/theme-chalk/el-form-item.css'
import 'element-plus/theme-chalk/el-icon.css'
import 'element-plus/theme-chalk/el-input.css'
import 'element-plus/theme-chalk/el-loading.css'
import App from './App.vue'
import { router } from './router'
import './styles/index.css'

createApp(App)
  .component('ElAlert', ElAlert)
  .component('ElButton', ElButton)
  .component('ElForm', ElForm)
  .component('ElFormItem', ElFormItem)
  .component('ElInput', ElInput)
  .use(router)
  .mount('#app')
