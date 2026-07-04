from __future__ import annotations

import random

from .passengers import Passenger
from .scenario import ScenarioContext, iso_time


class GroundModel:
    def __init__(self, context: ScenarioContext, passengers: list[Passenger], rng: random.Random) -> None:
        self.context = context
        self.passengers = passengers
        self.rng = rng
        self.total_bytes = 9_000_000_000

    def task_payload(self) -> dict:
        self.total_bytes += self.rng.randint(5_000_000, 50_000_000)
        return {
            "messageType": "ground.task",
            "sentAt": iso_time(self.context.simulated_now),
            "payload": {
                "taskId": self.context.task_id,
                "flightNo": self.context.flight_number,
                "scenarioName": self.context.scenario_name,
                "status": "running",
                "phase": "cruise",
                "terminalCount": len(self.passengers),
                "startedAt": iso_time(self.context.scenario_start_time),
                "endedAt": None,
                "downlinkTargetMbps": 600.0,
                "statisticsWindowSeconds": 5,
                "totalBytes": self.total_bytes,
                "failureReason": None,
                "rerunSourceTaskId": None,
                "archived": False,
            },
        }

    def traffic_payload(self, page_size: int = 50) -> list[dict]:
        items = []
        sampled = self.rng.sample(self.passengers, min(len(self.passengers), page_size))
        for passenger in sampled:
            application = self.rng.choice(["高清视频", "音乐", "网页浏览"])
            throughput = self._throughput(application)
            bytes_count = int(throughput * 1_000_000 / 8 * 5)
            items.append(
                {
                    "windowStart": iso_time(self.context.simulated_now),
                    "windowEnd": iso_time(self.context.simulated_now),
                    "taskId": self.context.task_id,
                    "terminalId": passenger.terminal_id,
                    "displayTerminalId": passenger.display_terminal_id,
                    "seatLabel": passenger.seat_no,
                    "application": application,
                    "protocol": self.rng.choice(["TCP", "UDP"]),
                    "direction": "downlink",
                    "bytesCount": bytes_count,
                    "packetCount": self.rng.randint(500, 15000),
                    "throughputMbps": round(throughput, 2),
                    "peakMbps": round(throughput * self.rng.uniform(1.02, 1.25), 2),
                    "recordStatus": "recorded",
                }
            )
        return [
            {
                "messageType": "ground.traffic_record",
                "sentAt": iso_time(self.context.simulated_now),
                "page": 1,
                "pageSize": len(items),
                "total": len(items),
                "items": items,
            }
        ]

    def session_payload(self, page_size: int = 50) -> list[dict]:
        items = []
        sampled = self.rng.sample(self.passengers, min(len(self.passengers), page_size))
        elapsed = int((self.context.simulated_now - self.context.scenario_start_time).total_seconds())
        for passenger in sampled:
            application = self.rng.choice(["高清视频", "音乐", "网页浏览"])
            peak = self._throughput(application) * self.rng.uniform(1.1, 1.6)
            avg = peak * self.rng.uniform(0.55, 0.85)
            items.append(
                {
                    "sessionId": f"SES-{passenger.index:06d}",
                    "taskId": self.context.task_id,
                    "terminalId": passenger.terminal_id,
                    "displayTerminalId": passenger.display_terminal_id,
                    "seatLabel": passenger.seat_no,
                    "application": application,
                    "protocol": self.rng.choice(["TCP", "UDP"]),
                    "startedAt": iso_time(self.context.scenario_start_time),
                    "durationSeconds": max(0, elapsed),
                    "uplinkBytes": self.rng.randint(10_000_000, 900_000_000),
                    "downlinkBytes": self.rng.randint(100_000_000, 9_000_000_000),
                    "averageThroughputMbps": round(avg, 2),
                    "peakThroughputMbps": round(peak, 2),
                    "status": "active",
                }
            )
        return [
            {
                "messageType": "ground.session_summary",
                "sentAt": iso_time(self.context.simulated_now),
                "page": 1,
                "pageSize": len(items),
                "total": len(items),
                "items": items,
            }
        ]

    def _throughput(self, application: str) -> float:
        if application == "高清视频":
            return self.rng.uniform(5.0, 12.0)
        if application == "音乐":
            return self.rng.uniform(0.2, 1.2)
        return self.rng.uniform(1.0, 4.0)

