# 驾驶舱实时视频产品规格

## 1. 文档信息

| 项 | 内容 |
| --- | --- |
| 数据源 | 甲方 RTSP 视频流 |
| 转流服务 | MediaMTX，RTSP 转 WebRTC |
| 页面 | 乘客实时动态—驾驶舱实时监控 |
| 前端 | Vue 3 + TypeScript + MediaMTX iframe |
| 后端 | Java 21 + Spring Boot |
| 数据库 | 无变更 |

## 2. 目标与范围

平台应在现有驾驶舱监控区域嵌入 MediaMTX WebRTC 播放页，并由登录后可访问的 Java API 下发启用状态和播放地址。

本阶段实现：

- 可外部化的 WebRTC 播放配置和配置校验。
- 独立的驾驶舱视频配置 API。
- iframe 播放、默认静音、自动播放、控件、全屏和手动重载。
- 配置读取中、未启用和配置读取失败状态。

本阶段不实现：

- MediaMTX 安装、RTSP 拉流配置或 Windows 服务注册。
- Java 转发视频、FFmpeg 转码、录像、截图和回放。
- MediaMTX 在线探测、视频轨道状态探测或与平台 JWT 联动授权。
- HLS、原生 WHEP `reader.js`、WebSocket 或 SSE。

## 3. 业务规则

- 视频配置默认关闭，关闭时页面保持明确的“未启用”占位。
- 启用后必须配置绝对 `http` 或 `https` 播放地址；空地址、`rtsp` 地址或带用户信息的 URL 应使后端启动失败。
- 前端只在驾驶舱视频组件首次挂载时读取配置，不随乘客快照的 2 秒轮询重复请求。
- 手动重载在有效配置下只重建 iframe；配置未启用或读取失败时重新请求配置 API。
- iframe `load` 只表示 MediaMTX 播放页已加载，不得将其显示为“视频源在线”。
- MediaMTX 未启动、无流或断流不得影响乘客快照、舷窗和页面其他功能。

## 4. 安全与部署

- 配置 API 继承现有 Spring Security 规则，未登录请求不可访问。
- 后端 API 只返回 MediaMTX WebRTC 播放 URL，不得返回 RTSP 源、源端账号密码或 `mediamtx.yml` 内容。
- 同机部署时使用 `http://127.0.0.1:8889/cockpit`，MediaMTX WebRTC HTTP 和媒体端口应限制为本机访问。
- 改为局域网多终端时，必须重新评审 MediaMTX 绑定地址、防火墙、访问控制和 WebRTC ICE 候选地址。
- 平台改为 HTTPS 时，MediaMTX 播放页也必须使用 HTTPS，避免浏览器拦截混合内容。

## 5. 验收标准

1. 配置关闭时 API 和页面均显示未启用，不创建 iframe。
2. 配置合法本地 MediaMTX URL 后，页面自动嵌入播放页并支持控件与全屏。
3. URL 已有查询参数不被覆盖，官方播放参数被正确追加。
4. 配置只请求一次，乘客自动刷新不重建播放器。
5. 暂停 MediaMTX 或停止 RTSP 流后，页面其他数据仍正常刷新。
6. 替换环境变量并重启后端后，无需修改或重新编译前后端代码。
