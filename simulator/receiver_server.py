from __future__ import annotations

import argparse
import json
import socket
import threading
import time
from collections import Counter, defaultdict
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any

from udp_simulator.config import SimulatorConfig


LISTEN_MESSAGE_TYPES = [
    "qar.frame",
    "smart_window.status",
    "ife_633.behavior",
    "ife_cockrell.behavior",
]


class ReceiverState:
    def __init__(self) -> None:
        self.lock = threading.Lock()
        self.started_at = time.time()
        self.packet_counts: Counter[str] = Counter()
        self.last_seen: dict[str, float] = {}
        self.flight_state: dict[str, Any] = {}
        self.windows: dict[int, dict[str, Any]] = {}
        self.user_current_behaviors: dict[str, dict[str, Any]] = {}
        self.last_errors: list[str] = []

    def update(self, message_type: str, payload: Any) -> None:
        with self.lock:
            self.packet_counts[message_type] += 1
            self.last_seen[message_type] = time.time()
            try:
                if message_type == "qar.frame":
                    self.flight_state = payload if isinstance(payload, dict) else {}
                elif message_type == "smart_window.status":
                    self._update_windows(payload)
                elif message_type in {"ife_633.behavior", "ife_cockrell.behavior"}:
                    self._update_ife(payload)
            except Exception as exc:
                self._record_error(f"{message_type}: {exc}")

    def snapshot(self) -> dict[str, Any]:
        with self.lock:
            video_counter: Counter[str] = Counter()
            music_counter: Counter[str] = Counter()
            for current in self.user_current_behaviors.values():
                behavior_type = current.get("behaviorType")
                if behavior_type == "MOVIE_PLAY":
                    video_counter.update(current.get("types", []))
                elif behavior_type == "MUSIC_PLAY":
                    music_counter.update(current.get("types", []))

            rows: dict[int, list[dict[str, Any]]] = defaultdict(list)
            for window_id, window in sorted(self.windows.items()):
                side_sequence = window_id if window_id <= 58 else window_id - 58
                rows[side_sequence].append(window)

            return {
                "uptimeSeconds": int(time.time() - self.started_at),
                "packetCounts": dict(self.packet_counts),
                "lastSeen": {key: int(time.time() - value) for key, value in self.last_seen.items()},
                "flightState": dict(self.flight_state),
                "videoRanking": video_counter.most_common(),
                "musicRanking": music_counter.most_common(),
                "windowRows": [
                    {
                        "row": row,
                        "windows": values,
                    }
                    for row, values in sorted(rows.items())
                ],
                "userMediaCount": len(self.user_current_behaviors),
                "lastErrors": list(self.last_errors[-10:]),
            }

    def _update_windows(self, payload: Any) -> None:
        items = self._items_from_payload(payload)
        for item in items:
            window_id = int(item["windowId"])
            self.windows[window_id] = {
                "windowId": window_id,
                "zoneId": item.get("zoneId"),
                "brightnessLevel": item.get("brightnessLevel"),
                "connectStatus": item.get("connectStatus"),
                "status": item.get("status"),
                "timestamp": item.get("timestamp"),
            }

    def _update_ife(self, payload: Any) -> None:
        items = self._items_from_payload(payload)
        for item in items:
            pax_info = item.get("paxInfo", {})
            behavior = item.get("behaviorInfo", {})
            user_id = pax_info.get("userId")
            if not user_id:
                continue
            behavior_type = behavior.get("behaviorType")
            if behavior_type == "MOVIE_PLAY":
                types = self._split_types(behavior.get("contentType", ""))
            elif behavior_type == "MUSIC_PLAY":
                types = self._split_types(behavior.get("musicType", ""))
            else:
                types = []
            self.user_current_behaviors[user_id] = {
                "behaviorType": behavior_type,
                "types": types,
            }

    def _items_from_payload(self, payload: Any) -> list[dict[str, Any]]:
        if isinstance(payload, dict):
            if isinstance(payload.get("items"), list):
                return [item for item in payload["items"] if isinstance(item, dict)]
            if isinstance(payload.get("payload"), list):
                return [item for item in payload["payload"] if isinstance(item, dict)]
            if isinstance(payload.get("payload"), dict):
                return [payload["payload"]]
            return [payload]
        if isinstance(payload, list):
            return [item for item in payload if isinstance(item, dict)]
        return []

    def _split_types(self, value: str) -> list[str]:
        return [part.strip() for part in str(value).split("/") if part.strip()]

    def _record_error(self, message: str) -> None:
        self.last_errors.append(message)
        self.last_errors = self.last_errors[-20:]


def make_handler(state: ReceiverState) -> type[BaseHTTPRequestHandler]:
    class DashboardHandler(BaseHTTPRequestHandler):
        def do_GET(self) -> None:
            if self.path == "/" or self.path.startswith("/?"):
                self._send_html(DASHBOARD_HTML)
            elif self.path.startswith("/api/state"):
                self._send_json(state.snapshot())
            else:
                self.send_error(404, "Not Found")

        def log_message(self, format: str, *args: Any) -> None:
            return

        def _send_html(self, body: str) -> None:
            data = body.encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)

        def _send_json(self, body: Any) -> None:
            data = json.dumps(body, ensure_ascii=False).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Cache-Control", "no-store")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)

    return DashboardHandler


def udp_listener(state: ReceiverState, message_type: str, host: str, port: int, stop_event: threading.Event) -> None:
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.bind((host, port))
    sock.settimeout(0.5)
    print(f"listening {message_type} on udp://{host}:{port}")
    try:
        while not stop_event.is_set():
            try:
                data, _addr = sock.recvfrom(65535)
            except socket.timeout:
                continue
            try:
                payload = json.loads(data.decode("utf-8"))
                state.update(message_type, payload)
            except Exception as exc:
                with state.lock:
                    state._record_error(f"{message_type}: decode failed: {exc}")
    finally:
        sock.close()


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="UDP receiver dashboard for the simulator.")
    parser.add_argument("--config", default="simulator_config.example.json", help="Path to simulator config JSON.")
    parser.add_argument("--host", default="127.0.0.1", help="HTTP dashboard host.")
    parser.add_argument("--port", type=int, default=8080, help="HTTP dashboard port.")
    parser.add_argument("--udp-bind-host", default="127.0.0.1", help="UDP bind host.")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    config = SimulatorConfig.from_file(args.config)
    state = ReceiverState()
    stop_event = threading.Event()

    threads = []
    for message_type in LISTEN_MESSAGE_TYPES:
        port = config.ports[message_type]
        thread = threading.Thread(
            target=udp_listener,
            args=(state, message_type, args.udp_bind_host, port, stop_event),
            daemon=True,
        )
        thread.start()
        threads.append(thread)

    server = ThreadingHTTPServer((args.host, args.port), make_handler(state))
    print(f"dashboard http://{args.host}:{args.port}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("stopping receiver")
    finally:
        stop_event.set()
        server.server_close()
        for thread in threads:
            thread.join(timeout=1)


DASHBOARD_HTML = r"""<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>UDP 数据模拟器接收端</title>
  <style>
    :root {
      color-scheme: light;
      --bg: #f7f8fb;
      --panel: #ffffff;
      --text: #18202f;
      --muted: #667085;
      --line: #d8dde8;
      --accent: #2563eb;
      --ok: #15945b;
      --warn: #c27803;
      --bad: #c2410c;
    }
    * { box-sizing: border-box; }
    body {
      margin: 0;
      font-family: "Microsoft YaHei", "Segoe UI", Arial, sans-serif;
      background: var(--bg);
      color: var(--text);
    }
    header {
      padding: 18px 24px;
      border-bottom: 1px solid var(--line);
      background: var(--panel);
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 16px;
    }
    h1 { margin: 0; font-size: 20px; }
    main {
      padding: 18px 24px 28px;
      display: grid;
      gap: 18px;
    }
    section {
      background: var(--panel);
      border: 1px solid var(--line);
      border-radius: 8px;
      padding: 16px;
    }
    h2 { margin: 0 0 12px; font-size: 16px; }
    .grid {
      display: grid;
      grid-template-columns: repeat(4, minmax(150px, 1fr));
      gap: 10px;
    }
    .metric {
      border: 1px solid var(--line);
      border-radius: 6px;
      padding: 10px;
      min-height: 72px;
      background: #fbfcff;
    }
    .label {
      color: var(--muted);
      font-size: 12px;
      margin-bottom: 6px;
    }
    .value {
      font-size: 18px;
      font-weight: 700;
      overflow-wrap: anywhere;
    }
    .two {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 18px;
    }
    table {
      border-collapse: collapse;
      width: 100%;
      font-size: 13px;
    }
    th, td {
      border-bottom: 1px solid var(--line);
      padding: 8px 8px;
      text-align: left;
      vertical-align: middle;
    }
    th { color: var(--muted); font-weight: 600; background: #f5f7fb; }
    .bar {
      display: grid;
      grid-template-columns: minmax(76px, 120px) 1fr 44px;
      align-items: center;
      gap: 8px;
      margin: 7px 0;
    }
    .bar-track {
      height: 10px;
      background: #e8edf6;
      border-radius: 999px;
      overflow: hidden;
    }
    .bar-fill {
      height: 100%;
      background: var(--accent);
    }
    .window-table td {
      font-family: Consolas, "Courier New", monospace;
    }
    .chip {
      display: inline-flex;
      min-width: 58px;
      justify-content: center;
      align-items: center;
      margin: 2px;
      padding: 4px 6px;
      border-radius: 5px;
      border: 1px solid var(--line);
      background: #f7fafc;
      white-space: nowrap;
    }
    .chip.ok { border-color: #b7e2cc; background: #eefaf3; color: var(--ok); }
    .chip.warn { border-color: #f5d58b; background: #fff8df; color: var(--warn); }
    .chip.bad { border-color: #f2b8a5; background: #fff0eb; color: var(--bad); }
    .statusline { color: var(--muted); font-size: 13px; }
    .empty { color: var(--muted); padding: 8px 0; }
    @media (max-width: 980px) {
      .grid { grid-template-columns: repeat(2, minmax(130px, 1fr)); }
      .two { grid-template-columns: 1fr; }
    }
    @media (max-width: 560px) {
      header { align-items: flex-start; flex-direction: column; }
      main { padding: 14px; }
      .grid { grid-template-columns: 1fr; }
    }
  </style>
</head>
<body>
  <header>
    <h1>UDP 数据模拟器接收端</h1>
    <div class="statusline" id="status">等待数据...</div>
  </header>
  <main>
    <section>
      <h2>飞机飞行状态</h2>
      <div class="grid" id="flightGrid"></div>
    </section>
    <section class="two">
      <div>
        <h2>视频类型排名</h2>
        <div id="videoRanking"></div>
      </div>
      <div>
        <h2>音乐类型排名</h2>
        <div id="musicRanking"></div>
      </div>
    </section>
    <section>
        <h2>左右舷窗状态</h2>
      <div id="windowTable"></div>
    </section>
  </main>
  <script>
    const flightFields = [
      ["FLIGHT NUMBER", "航班号"],
      ["ORIGIN", "起点"],
      ["DESTINATION", "终点"],
      ["time", "QAR 时间"],
      ["PRES POSN LAT - FMC", "纬度"],
      ["PRES POSN LONG - FMC", "经度"],
      ["BARO COR ALT NO. 1", "高度 ft"],
      ["GROUNDSPEED", "地速 kt"],
      ["COMPUTED AIRSPEED", "空速 kt"],
      ["TRACK ANGLE TRUE - FMC", "航向角"],
      ["BODY PITCH RATE", "俯仰"],
      ["BODY ROLL RATE", "横滚"],
      ["DISTANCE TO GO", "剩余航程 nm"],
      ["DESTINATION ETA", "预计到达"],
      ["LT MAIN FUEL QTY", "左油箱"],
      ["RT MAIN FUEL QTY", "右油箱"]
    ];

    function renderFlight(flight) {
      const grid = document.getElementById("flightGrid");
      grid.innerHTML = flightFields.map(([key, label]) => `
        <div class="metric">
          <div class="label">${label}</div>
          <div class="value">${escapeHtml(flight[key] ?? "-")}</div>
        </div>
      `).join("");
    }

    function renderRanking(id, rows) {
      const target = document.getElementById(id);
      if (!rows || rows.length === 0) {
        target.innerHTML = '<div class="empty">等待 IFE 数据...</div>';
        return;
      }
      const max = Math.max(...rows.map(row => row[1]), 1);
      target.innerHTML = rows.map(([name, count]) => `
        <div class="bar">
          <div>${escapeHtml(name)}</div>
          <div class="bar-track"><div class="bar-fill" style="width:${Math.max(4, count / max * 100)}%"></div></div>
          <strong>${count}</strong>
        </div>
      `).join("");
    }

    function renderWindows(rows) {
      const target = document.getElementById("windowTable");
      if (!rows || rows.length === 0) {
        target.innerHTML = '<div class="empty">等待舷窗数据...</div>';
        return;
      }
      target.innerHTML = `
        <table class="window-table">
          <thead><tr><th>排</th><th>舱段</th><th>舷窗状态</th></tr></thead>
          <tbody>
            ${rows.map(row => `
              <tr>
                <td>${row.row}</td>
                <td>${zoneName(row.windows[0]?.zoneId)}</td>
                <td>${row.windows.map(renderWindowChip).join("")}</td>
              </tr>
            `).join("")}
          </tbody>
        </table>
      `;
    }

    function renderWindowChip(window) {
      const status = window.status || "UNKNOWN";
      const connected = window.connectStatus === true || window.connectStatus === 1;
      let cls = "ok";
      if (!connected || status === "FAULT") cls = "bad";
      else if (status === "TEST") cls = "warn";
      return `<span class="chip ${cls}">#${window.windowId} L${window.brightnessLevel} ${connected ? "连" : "断"} ${escapeHtml(status)}</span>`;
    }

    function zoneName(zoneId) {
      if (zoneId === 1) return "前舱";
      if (zoneId === 2) return "中舱";
      if (zoneId === 3) return "后舱";
      return "-";
    }

    function escapeHtml(value) {
      return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;");
    }

    async function refresh() {
      try {
        const response = await fetch("/api/state", {cache: "no-store"});
        const data = await response.json();
        renderFlight(data.flightState || {});
        renderRanking("videoRanking", data.videoRanking || []);
        renderRanking("musicRanking", data.musicRanking || []);
        renderWindows(data.windowRows || []);
        const counts = data.packetCounts || {};
        document.getElementById("status").textContent =
          `运行 ${data.uptimeSeconds}s，用户 ${data.userMediaCount}，QAR ${counts["qar.frame"] || 0}，舷窗 ${counts["smart_window.status"] || 0}，IFE ${counts["ife_633.behavior"] || 0}`;
      } catch (error) {
        document.getElementById("status").textContent = `接收端异常：${error}`;
      }
    }

    refresh();
    setInterval(refresh, 1000);
  </script>
</body>
</html>
"""


if __name__ == "__main__":
    main()
