# 驾驶舱视频配置 API 契约

## 1. 读取配置

```text
GET /api/v1/passenger-realtime/cockpit-video
Authorization: Bearer <platform JWT>
```

本接口只返回前端播放所需的公开配置，不返回 RTSP 源地址、RTSP 账号密码或 MediaMTX 内部配置。

### 1.1 启用响应

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "enabled": true,
    "protocol": "WEBRTC",
    "playbackUrl": "http://127.0.0.1:8889/cockpit"
  },
  "traceId": "..."
}
```

### 1.2 关闭响应

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "enabled": false,
    "protocol": "WEBRTC"
  },
  "traceId": "..."
}
```

`playbackUrl` 为空时由统一 JSON 规则省略。

## 2. 字段规则

| 字段 | 类型 | 规则 |
| --- | --- | --- |
| `enabled` | boolean | 是否允许前端创建播放器。 |
| `protocol` | string | 本阶段固定为 `WEBRTC`。 |
| `playbackUrl` | string/null | MediaMTX 对浏览器提供的 HTTP(S) 播放页地址。 |

## 3. 后端配置契约

```yaml
cabin:
  cockpit-video:
    enabled: ${COCKPIT_VIDEO_ENABLED:false}
    playback-url: ${COCKPIT_VIDEO_PLAYBACK_URL:}
```

校验规则：

- `enabled=false` 时允许地址为空，API 不返回地址。
- `enabled=true` 时地址必填，必须是带主机名的绝对 `http` 或 `https` URL。
- 播放 URL 不得包含 URL user-info，即不允许 `http://user:pass@host/...`。
- 校验失败属于部署配置错误，应阻止应用启动。

## 4. 前端 iframe 契约

前端保留 `playbackUrl` 已有查询参数，并设置：

```text
controls=true
muted=true
autoplay=true
playsInline=true
disablepictureinpicture=false
```

iframe 允许 `autoplay; fullscreen; picture-in-picture`。前端不向 MediaMTX 传递平台 JWT，也不在 URL 中追加 RTSP 凭据。
