# 驾驶舱视频数据边界

## 1. Schema 决策

本阶段不修改 PostgreSQL 结构，不新增 Flyway 迁移。

```text
RTSP 源 --拉流--> MediaMTX --WebRTC--> 浏览器
                                   ^
Java API --只返回播放地址----------|
```

## 2. 禁止落库的内容

- RTSP 源 URL、用户名和密码。
- WebRTC 媒体包、视频帧、截图和录像。
- MediaMTX 运行状态、会话信令和 ICE 候选信息。

## 3. 配置存储

- RTSP 源及其凭据：由部署人员保存在 MediaMTX 配置或其安全环境变量中。
- WebRTC 播放 URL：由 Java 应用环境变量提供，可在不重新编译代码的情况下替换。
- 前端：只在内存中保留本次页面会话读取到的播放配置，不写入 localStorage 或 sessionStorage。

## 4. 数据库验收

- 本功能不创建表、字段、索引、外键或数据迁移。
- 启动或播放视频时 Flyway 版本不变。
- 乘客、QAR、IFE、舷窗和轨迹数据表不受影响。
