from __future__ import annotations

import json
import socket
from dataclasses import dataclass
from typing import Any

from .config import SimulatorConfig, json_default


@dataclass
class SendResult:
    message_type: str
    host: str
    port: int
    bytes_sent: int


class UdpSender:
    def __init__(self, config: SimulatorConfig, dry_run: bool = False) -> None:
        self.config = config
        self.dry_run = dry_run
        self.socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)

    def close(self) -> None:
        self.socket.close()

    def send_json(self, message_type: str, payload: Any) -> SendResult:
        data = json.dumps(payload, ensure_ascii=False, default=json_default, separators=(",", ":")).encode("utf-8")
        host, port = self.config.endpoint(message_type)
        if not self.dry_run:
            self.socket.sendto(data, (host, port))
        return SendResult(message_type=message_type, host=host, port=port, bytes_sent=len(data))

