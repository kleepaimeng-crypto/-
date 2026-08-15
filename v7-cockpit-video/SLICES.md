# 驾驶舱视频实施切片

## 1. 实施顺序

### 切片 1：配置契约

- 新增 `cabin.cockpit-video.enabled` 和 `playback-url`。
- 配置默认关闭，启用时校验绝对 HTTP(S) URL 且禁止 user-info。
- 向 `.env.example` 和容器部署环境传递两个配置项。

验证：关闭配置、合法地址、空地址、RTSP URL 和带凭据 URL。

### 切片 2：后端配置 API

- 新增独立 Controller、Service 和 DTO。
- 使用统一 `Response` 信封和 Trace ID。
- 保持接口为已登录用户访问，不增加 `permitAll`。

验证：启用/关闭响应和统一 JSON 结构。

### 切片 3：前端播放组件

- 新增独立组件和 API 类型，替换静态占位。
- 首次挂载请求配置，不纳入乘客数据轮询。
- 嵌入 MediaMTX iframe，追加官方播放参数和 iframe 权限。
- 实现加载、未启用、配置失败和手动重载。

验证：各状态 DOM、URL 参数、iframe 重建和请求次数。

### 切片 4：联调回归

- 使用 OBS/RTSP 和 MediaMTX 本地流进行手工播放验收。
- 停止流和 MediaMTX，验证乘客实时数据仍可刷新。
- 替换环境变量并重启后端，验证无需重新构建。

## 2. 自动化验证

```powershell
Set-Location backend
.\mvnw.cmd test
.\mvnw.cmd verify

Set-Location ..\frontend
npm run typecheck
npm run test
npm run build
```

若当前 Windows Maven Wrapper 无法启动，可使用项目兼容的本地 Maven 执行相同的 `test` 和 `verify` 目标，并在交付中记录原因。
