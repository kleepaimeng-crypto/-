from __future__ import annotations

import random
import time
from collections.abc import Callable
from dataclasses import dataclass
from datetime import timedelta

from .config import SimulatorConfig
from .flight_model import FlightModel
from .ground_model import GroundModel
from .ife_model import IfeModel
from .passengers import build_passengers
from .scenario import choose_next_destination, create_scenario, create_scenario_for_route
from .udp_sender import SendResult, UdpSender
from .window_model import SmartWindowModel


@dataclass
class ScheduledJob:
    message_type: str
    interval_seconds: float
    next_run_at: float
    build_payloads: Callable[[float], list[dict]]


class DataSimulator:
    def __init__(self, config: SimulatorConfig, dry_run: bool = False) -> None:
        self.config = config
        self.rng = random.Random(config.random_seed)
        self.context = create_scenario(config.passenger_count, config.window_rows, self.rng)
        self.used_flight_numbers = {self.context.flight_number}
        self.pending_next_flight = False
        self.passengers = build_passengers(config.passenger_count, self.rng)
        self.flight_model = FlightModel.create(self.context, self.rng)
        self.window_model = SmartWindowModel(self.context, config.window_count, self.rng)
        self.ife_model = IfeModel(self.context, self.passengers, self.rng)
        self.ground_model = GroundModel(self.context, self.passengers, self.rng)
        self.cockrell_snapshot_task_id: str | None = None
        self.sender = UdpSender(config, dry_run=dry_run)
        self.jobs = self._build_jobs()

    def close(self) -> None:
        self.sender.close()

    def run_once(self) -> list[SendResult]:
        results: list[SendResult] = []
        for job in self.jobs:
            for payload in job.build_payloads(job.interval_seconds):
                results.append(self.sender.send_json(job.message_type, payload))
        return results

    def run_forever(self) -> None:
        try:
            last_clock = time.monotonic()
            while True:
                now = time.monotonic()
                elapsed = now - last_clock
                if elapsed > 0:
                    self._advance_time(elapsed)
                    last_clock = now
                sent_any = False
                for job in self.jobs:
                    if now >= job.next_run_at:
                        for payload in job.build_payloads(job.interval_seconds):
                            result = self.sender.send_json(job.message_type, payload)
                            print(
                                f"sent {result.message_type} -> {result.host}:{result.port} "
                                f"({result.bytes_sent} bytes)"
                            )
                        job.next_run_at = now + job.interval_seconds
                        sent_any = True
                if not sent_any:
                    time.sleep(0.2)
        finally:
            self.close()

    def summary(self) -> dict:
        return {
            "taskId": self.context.task_id,
            "flightNumber": self.context.flight_number,
            "origin": self.context.origin.code,
            "destination": self.context.destination.code,
            "aircraftModel": self.context.aircraft_model,
            "passengerCount": len(self.passengers),
            "windowCount": self.config.window_count,
            "segmentSequence": self.context.segment_sequence,
            "phase": self.context.phase,
        }

    def _build_jobs(self) -> list[ScheduledJob]:
        now = time.monotonic()
        intervals = self.config.send_intervals_seconds
        return [
            ScheduledJob("qar.frame", intervals["qar.frame"], now, self._qar_payloads),
            ScheduledJob("ground.task", intervals["ground.task"], now, self._ground_task_payloads),
            ScheduledJob("ground.traffic_record", intervals["ground.traffic_record"], now, self._ground_traffic_payloads),
            ScheduledJob("ground.session_summary", intervals["ground.session_summary"], now, self._ground_session_payloads),
            ScheduledJob("smart_window.status", intervals["smart_window.status"], now, self._window_payloads),
            ScheduledJob("ife_633.behavior", intervals["ife_633.behavior"], now, self._ife_633_payloads),
            ScheduledJob("ife_cockrell.behavior", intervals["ife_cockrell.behavior"], now, self._ife_cockrell_payloads),
        ]

    def _advance_time(self, elapsed_seconds: float) -> None:
        self.context.simulated_now += timedelta(seconds=elapsed_seconds)

    def _qar_payloads(self, elapsed_seconds: float) -> list[dict]:
        if self.pending_next_flight:
            self._start_next_flight()
        payload = self.flight_model.advance(elapsed_seconds)
        if self.flight_model.finished:
            self.pending_next_flight = True
            self._schedule_final_snapshots()
        return [payload]

    def _ground_task_payloads(self, elapsed_seconds: float) -> list[dict]:
        return [self.ground_model.task_payload()]

    def _ground_traffic_payloads(self, elapsed_seconds: float) -> list[dict]:
        if self.context.status == "finished":
            return []
        return self.ground_model.traffic_payload()

    def _ground_session_payloads(self, elapsed_seconds: float) -> list[dict]:
        return self.ground_model.session_payload()

    def _window_payloads(self, elapsed_seconds: float) -> list[dict]:
        return [self.window_model.update_payload()]

    def _ife_633_payloads(self, elapsed_seconds: float) -> list[dict]:
        if self.context.status == "finished":
            return []
        return self.ife_model.build_633_pages(self.config.ife_page_size)

    def _ife_cockrell_payloads(self, elapsed_seconds: float) -> list[dict]:
        if self.context.status == "finished":
            return []
        if self.cockrell_snapshot_task_id != self.context.task_id:
            self.cockrell_snapshot_task_id = self.context.task_id
            return self.ife_model.build_cockrell_events("full", self.config.ife_cockrell_burst_size)
        return self.ife_model.build_cockrell_events(
            self.config.ife_cockrell_mode,
            self.config.ife_cockrell_burst_size,
        )

    def _start_next_flight(self) -> None:
        previous = self.context
        origin = previous.destination
        destination = choose_next_destination(previous.origin, origin, self.rng)
        next_context = create_scenario_for_route(
            passenger_count=self.config.passenger_count,
            window_rows=self.config.window_rows,
            rng=self.rng,
            origin=origin,
            destination=destination,
            segment_sequence=previous.segment_sequence + 1,
            simulated_now=previous.simulated_now,
            excluded_flight_numbers=self.used_flight_numbers,
        )
        self.used_flight_numbers.add(next_context.flight_number)
        self.context = next_context
        self.flight_model = FlightModel.create(next_context, self.rng)
        self.ground_model = GroundModel(next_context, self.passengers, self.rng)
        self.ife_model = IfeModel(next_context, self.passengers, self.rng)
        self.window_model.context = next_context
        self.pending_next_flight = False

    def _schedule_final_snapshots(self) -> None:
        for job in self.jobs:
            if job.message_type in {"ground.task", "ground.session_summary"}:
                job.next_run_at = 0
