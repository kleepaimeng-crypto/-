# AGENTS.md

本项目实现本地 UDP 数据模拟器。开发时必须遵守：

- 所有接口通过 UDP 发送到本地端口。
- 端口全局定义，默认从 8090 开始递增。
- 发送周期默认 10 秒，各接口可单独配置。
- 模拟中只允许出现 1 架飞机，所有接口共享同一 taskId、航班号、起降点、时间和乘客上下文。
- 优先保证 qar.frame、smart_window.status、ife_633.behavior 三类接口合理性。
- ife_633.behavior 和 ife_cockrell.behavior 必须共用乘客、偏好和行为生成逻辑，仅做字段适配。
- 默认机型为 Airbus A330-200，使用 237 个已确认座位和 116 个舷窗；不得通过新增 UDP 字段传递 SVG 或左右侧映射。
- 详细 schema 见 接口格式Schema.md。
- 详细开发规则见 数据模拟器开发指导.md。
