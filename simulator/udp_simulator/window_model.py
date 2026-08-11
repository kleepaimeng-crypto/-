from __future__ import annotations

import random
from dataclasses import dataclass

from .scenario import ScenarioContext, compact_time


@dataclass
class WindowState:
    window_id: int
    zone_id: int
    brightness_level: int
    connect_status: bool
    status: str


class SmartWindowModel:
    def __init__(self, context: ScenarioContext, window_count: int, rng: random.Random) -> None:
        if window_count != 118:
            raise ValueError(f"C929-700 windowCount must be 118, got {window_count}")
        self.context = context
        self.window_count = window_count
        self.rng = rng
        self.windows = [self._initial_window(i) for i in range(1, window_count + 1)]

    def update_payload(self) -> dict:
        items = []
        for window in self.windows:
            if self.rng.random() < 0.30:
                window.brightness_level = max(0, min(10, window.brightness_level + self.rng.choice([-1, 1])))
            window.connect_status = self.rng.random() >= 0.01
            roll = self.rng.random()
            if roll < 0.002:
                window.status = "TEST"
            elif roll < 0.007:
                window.status = "FAULT"
            else:
                window.status = "NORMAL"
            items.append(
                {
                    "windowId": window.window_id,
                    "zoneId": window.zone_id,
                    "brightnessLevel": window.brightness_level,
                    "connectStatus": window.connect_status,
                    "status": window.status,
                    "timestamp": compact_time(self.context.simulated_now),
                }
            )
        return {
            "messageType": "smart_window.status",
            "sentAt": self.context.simulated_now.isoformat(),
            "total": len(items),
            "items": items,
        }

    def _initial_window(self, window_id: int) -> WindowState:
        side_sequence = window_id if window_id <= 59 else window_id - 59
        if side_sequence <= 20:
            zone_id = 1
        elif side_sequence <= 39:
            zone_id = 2
        else:
            zone_id = 3
        return WindowState(
            window_id=window_id,
            zone_id=zone_id,
            brightness_level=self.rng.randint(3, 8),
            connect_status=True,
            status="NORMAL",
        )
