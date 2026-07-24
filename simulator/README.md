# 本地 UDP 数据模拟器

本项目按 `接口格式Schema.md` 和 `数据模拟器开发指导.md` 实现本地 UDP 数据模拟器。

默认场景为 COMAC C929-700：282 名乘客（商务舱 38、经济舱 244）和 118 个智慧舷窗（左右各 59）。座位号统一为字母加排号格式（例如 `A11`）；座位和舷窗只改变现有字段的取值与数量，不扩展 UDP JSON 字段。

## 运行

只构建一轮数据，不发送 UDP：

```powershell
python run_simulator.py --once --dry-run --summary
```

发送一轮数据到本地端口：

```powershell
python run_simulator.py --once --summary
```

持续发送：

```powershell
python run_simulator.py --config simulator_config.example.json --summary
```

## 接收端展示

启动 HTML 接收端：

```powershell
python receiver_server.py --config simulator_config.example.json
```

浏览器打开：

```text
http://127.0.0.1:8080
```

接收端会监听以下 UDP 数据并展示：

- 飞机飞行状态：来自 `qar.frame`
- 视频/音乐类型排名：来自 `ife_633.behavior` 和 `ife_cockrell.behavior`
- 左右配对的舷窗状态：来自 `smart_window.status`

可以先开接收端，再另开一个终端运行模拟器：

```powershell
python run_simulator.py --config simulator_config.example.json --summary
```

## 默认端口

| 消息类型 | 端口 |
| --- | ---: |
| `qar.frame` | 8090 |
| `ground.task` | 8091 |
| `ground.traffic_record` | 8092 |
| `ground.session_summary` | 8093 |
| `smart_window.status` | 8094 |
| `ife_633.behavior` | 8095 |
| `ife_cockrell.behavior` | 8096 |

默认发送周期为 10 秒，每个接口可在 `simulator_config.example.json` 中单独配置。

## 验证

```powershell
python -m unittest discover -s tests -v
python run_simulator.py --once --dry-run --summary --config simulator_config.example.json
```
