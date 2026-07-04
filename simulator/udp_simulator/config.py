from __future__ import annotations

import json
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any


UDP_HOST = "127.0.0.1"

PORT_QAR_FRAME = 8090
PORT_GROUND_TASK = 8091
PORT_GROUND_TRAFFIC_RECORD = 8092
PORT_GROUND_SESSION_SUMMARY = 8093
PORT_SMART_WINDOW_STATUS = 8094
PORT_IFE_633_BEHAVIOR = 8095
PORT_IFE_COCKRELL_BEHAVIOR = 8096

DEFAULT_SEND_INTERVAL_SECONDS = 10.0

DEFAULT_PORTS = {
    "qar.frame": PORT_QAR_FRAME,
    "ground.task": PORT_GROUND_TASK,
    "ground.traffic_record": PORT_GROUND_TRAFFIC_RECORD,
    "ground.session_summary": PORT_GROUND_SESSION_SUMMARY,
    "smart_window.status": PORT_SMART_WINDOW_STATUS,
    "ife_633.behavior": PORT_IFE_633_BEHAVIOR,
    "ife_cockrell.behavior": PORT_IFE_COCKRELL_BEHAVIOR,
}

DEFAULT_INTERVALS = {
    "qar.frame": DEFAULT_SEND_INTERVAL_SECONDS,
    "ground.task": DEFAULT_SEND_INTERVAL_SECONDS,
    "ground.traffic_record": DEFAULT_SEND_INTERVAL_SECONDS,
    "ground.session_summary": DEFAULT_SEND_INTERVAL_SECONDS,
    "smart_window.status": DEFAULT_SEND_INTERVAL_SECONDS,
    "ife_633.behavior": DEFAULT_SEND_INTERVAL_SECONDS,
    "ife_cockrell.behavior": DEFAULT_SEND_INTERVAL_SECONDS,
}


@dataclass
class SimulatorConfig:
    udp_host: str = UDP_HOST
    ports: dict[str, int] = field(default_factory=lambda: dict(DEFAULT_PORTS))
    send_intervals_seconds: dict[str, float] = field(default_factory=lambda: dict(DEFAULT_INTERVALS))
    passenger_count: int = 320
    window_count: int = 200
    window_rows: int = 50
    ife_page_size: int = 50
    random_seed: int | None = None

    @classmethod
    def from_file(cls, path: str | Path | None) -> "SimulatorConfig":
        config = cls()
        if not path:
            return config

        raw_path = Path(path)
        if not raw_path.exists():
            raise FileNotFoundError(f"Config file not found: {raw_path}")

        data = json.loads(raw_path.read_text(encoding="utf-8"))
        config.udp_host = data.get("udpHost", config.udp_host)
        config.passenger_count = int(data.get("passengerCount", config.passenger_count))
        config.window_count = int(data.get("windowCount", config.window_count))
        config.window_rows = int(data.get("windowRows", config.window_rows))
        config.ife_page_size = int(data.get("ifePageSize", config.ife_page_size))
        config.random_seed = data.get("randomSeed", config.random_seed)

        ports = data.get("ports", {})
        for key, value in ports.items():
            if key in config.ports:
                config.ports[key] = int(value)

        intervals = data.get("sendIntervalsSeconds", {})
        default_interval = intervals.get("default")
        if default_interval is not None:
            for key in config.send_intervals_seconds:
                config.send_intervals_seconds[key] = float(default_interval)
        for key, value in intervals.items():
            if key in config.send_intervals_seconds:
                config.send_intervals_seconds[key] = float(value)

        return config

    def endpoint(self, message_type: str) -> tuple[str, int]:
        return self.udp_host, self.ports[message_type]


def json_default(value: Any) -> str:
    return str(value)

