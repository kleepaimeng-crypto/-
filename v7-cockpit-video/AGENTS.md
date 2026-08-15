# 驾驶舱实时视频实施规则

## 1. 必读顺序

1. `SPEC.md`
2. `API.md`
3. `schema.md`
4. `SLICES.md`
5. 本文件
6. 当前乘客实时页、Spring Security、部署配置及 MediaMTX 官方文档

如有冲突，优先级为 `SPEC.md`、`API.md`、`schema.md`、`SLICES.md`、本文件。

## 2. 边界

只实现：

- WebRTC 播放地址的后端配置、校验和已登录读取 API。
- 乘客实时页面中的 MediaMTX iframe 播放和明确状态。
- 配置、前后端单测和部署文档。

禁止顺手实现：

- 把 RTSP 拉流、转码或 WebRTC 媒体传输放入 Java 后端。
- 将 RTSP 源、账号密码或 MediaMTX 管理配置返回前端。
- 新增数据库表、迁移、录像、截图或回放。
- 下载或打包 MediaMTX/FFmpeg，修改甲方 RTSP 协议。
- 把视频配置加入乘客 2 秒快照轮询。
- 修改轨迹、IFE、QAR、舷窗、数据管理和用户管理业务。

## 3. 实施规则

- Controller 只处理 HTTP 信封，Service 输出业务 DTO，配置属性负责规范化和启动校验。
- 配置 API 不增加 Spring Security `permitAll`。
- `enabled=false` 时不向 API 暴露已填写但未启用的地址。
- 前端必须使用 URL API 追加查询参数，不得用字符串拼接破坏已有参数。
- iframe 默认静音、自动播放并允许全屏；不得将 iframe `load` 当作视频源在线证明。
- 视频配置失败必须局部降级，不得影响乘客快照和舷窗请求。

## 4. 验证与安全

- 至少执行后端 `test`/`verify` 和前端 `typecheck`/`test`/`build`。
- 自动化测试覆盖启用、关闭、无效 URL、页面状态、播放参数和重载。
- 联调前检查接口响应和前端构建产物中不包含 `rtsp://`、RTSP 用户名和密码。
- 禁止为调试把甲方密码提交到仓库、日志、截图或测试固定值。
