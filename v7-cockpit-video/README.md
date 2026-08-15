# 驾驶舱实时视频接入阶段契约

本目录定义驾驶舱 RTSP 视频经 MediaMTX 转换为 WebRTC 后，在“乘客实时动态”页面播放的产品、接口、安全和实施边界。

## 必读顺序

1. `SPEC.md`：功能范围、数据流和验收标准。
2. `API.md`：后端配置接口及前端消费契约。
3. `schema.md`：无数据库变更的数据边界。
4. `SLICES.md`：实施顺序与验证命令。
5. `AGENTS.md`：开发约束和禁止事项。

## 核心决策

- 甲方保持现有 RTSP 输出，MediaMTX 在地面机主动拉流并提供 WebRTC。
- 前端使用 MediaMTX 官方 iframe 播放页，不引入 `reader.js`、HLS 或新的前端依赖。
- Java 后端只返回是否启用和 WebRTC 播放地址，不代理、转码、录制或健康检测视频流。
- RTSP 源地址及源端账号密码只保存在 MediaMTX 配置中，不进入 Java 配置、API、前端或数据库。
- 当前部署模型为浏览器、平台和 MediaMTX 位于同一台 Windows 地面机，使用 HTTP 和 `127.0.0.1`。

## 配置示例

Java 后端环境变量：

```properties
COCKPIT_VIDEO_ENABLED=true
COCKPIT_VIDEO_PLAYBACK_URL=http://127.0.0.1:8889/cockpit
```

MediaMTX 中的 RTSP `source` 由部署人员单独配置。不得把带账号密码的 RTSP URL 填入 `COCKPIT_VIDEO_PLAYBACK_URL`。
