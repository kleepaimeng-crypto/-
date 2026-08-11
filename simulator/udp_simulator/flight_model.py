from __future__ import annotations

import random
from dataclasses import dataclass

from .scenario import (
    ScenarioContext,
    bearing_degrees,
    haversine_nm,
    interpolate_lat_lon,
    qar_time,
)


@dataclass
class FlightModel:
    context: ScenarioContext
    rng: random.Random
    route_nm: float
    traveled_nm: float
    progress: float
    phase: str
    cruise_altitude_ft: float
    cruise_speed_kt: float
    climb_distance_nm: float
    descent_distance_nm: float
    approach_distance_nm: float
    altitude_ft: float
    ground_speed_kt: float
    air_speed_kt: float
    left_fuel_qty: float
    right_fuel_qty: float
    center_fuel_qty: float
    frame_count: int = 0
    departure_emitted: bool = False

    @classmethod
    def create(cls, context: ScenarioContext, rng: random.Random) -> "FlightModel":
        route_nm = max(
            1.0,
            haversine_nm(
                context.origin.lat,
                context.origin.lon,
                context.destination.lat,
                context.destination.lon,
            ),
        )
        cruise_altitude_limit = max(12_000.0, route_nm * 100.0)
        cruise_altitude_ft = min(rng.uniform(33_000, 37_000), cruise_altitude_limit)
        return cls(
            context=context,
            rng=rng,
            route_nm=route_nm,
            traveled_nm=0.0,
            progress=0.0,
            phase="departure",
            cruise_altitude_ft=cruise_altitude_ft,
            cruise_speed_kt=rng.uniform(455, 490),
            climb_distance_nm=max(10.0, min(120.0, route_nm * 0.20)),
            descent_distance_nm=max(10.0, min(120.0, route_nm * 0.25)),
            approach_distance_nm=max(3.0, min(10.0, route_nm * 0.05)),
            altitude_ft=0.0,
            ground_speed_kt=0.0,
            air_speed_kt=0.0,
            left_fuel_qty=rng.uniform(10_500, 13_000),
            right_fuel_qty=rng.uniform(10_500, 13_000),
            center_fuel_qty=rng.uniform(4_000, 7_500),
        )

    @property
    def finished(self) -> bool:
        return self.phase == "landed"

    def advance(self, elapsed_seconds: float) -> dict:
        self.frame_count += 1
        elapsed_seconds = max(0.0, float(elapsed_seconds))

        if self.phase == "departure" and not self.departure_emitted:
            self.departure_emitted = True
            self.ground_speed_kt = self.rng.uniform(0, 15)
            self.air_speed_kt = 0.0
            self.altitude_ft = 0.0
            self._sync_context()
            return self._payload()

        if self.phase == "departure":
            self.phase = "climb"

        if not self.finished:
            remaining_nm = max(0.0, self.route_nm - self.traveled_nm)
            self._select_phase(remaining_nm)
            self._update_flight_parameters(remaining_nm)
            self._burn_fuel(elapsed_seconds)
            self.traveled_nm = min(
                self.route_nm,
                self.traveled_nm + self.ground_speed_kt * elapsed_seconds / 3600,
            )
            self.progress = self.traveled_nm / self.route_nm
            if self.traveled_nm >= self.route_nm:
                self._land()
            else:
                self._select_phase(self.route_nm - self.traveled_nm)

        self._sync_context()
        return self._payload()

    def _select_phase(self, remaining_nm: float) -> None:
        if remaining_nm <= 0:
            self.phase = "landed"
        elif remaining_nm <= self.approach_distance_nm:
            self.phase = "landing"
        elif remaining_nm <= self.descent_distance_nm:
            self.phase = "descent"
        elif self.traveled_nm < self.climb_distance_nm:
            self.phase = "climb"
        else:
            self.phase = "cruise"

    def _update_flight_parameters(self, remaining_nm: float) -> None:
        if self.phase == "climb":
            ratio = self._ratio(self.traveled_nm, self.climb_distance_nm)
            speed = self._lerp(180.0, self.cruise_speed_kt, ratio)
            altitude = self._lerp(0.0, self.cruise_altitude_ft, ratio)
            self.ground_speed_kt = self._bounded(speed + self.rng.uniform(-3, 3), 175, 510)
            self.altitude_ft = self._bounded(altitude + self.rng.uniform(-40, 40), 0, self.cruise_altitude_ft)
        elif self.phase == "cruise":
            self.ground_speed_kt = self._bounded(
                self.ground_speed_kt + self.rng.uniform(-4, 4),
                430,
                510,
            )
            self.altitude_ft = self._bounded(
                self.cruise_altitude_ft + self.rng.uniform(-80, 80),
                max(12_000, self.cruise_altitude_ft - 500),
                self.cruise_altitude_ft + 500,
            )
        elif self.phase == "descent":
            span = max(1.0, self.descent_distance_nm - self.approach_distance_nm)
            ratio = self._ratio(self.descent_distance_nm - remaining_nm, span)
            speed = self._lerp(self.cruise_speed_kt, 180.0, ratio)
            altitude = self._lerp(self.cruise_altitude_ft, 2_000.0, ratio)
            self.ground_speed_kt = self._bounded(speed + self.rng.uniform(-3, 3), 175, 510)
            self.altitude_ft = self._bounded(altitude + self.rng.uniform(-30, 30), 1_500, self.cruise_altitude_ft)
        elif self.phase == "landing":
            ratio = self._ratio(self.approach_distance_nm - remaining_nm, self.approach_distance_nm)
            speed = self._lerp(180.0, 30.0, ratio)
            altitude = self._lerp(2_000.0, 0.0, ratio)
            self.ground_speed_kt = self._bounded(speed + self.rng.uniform(-2, 2), 20, 185)
            self.altitude_ft = self._bounded(altitude + self.rng.uniform(-10, 10), 0, 2_050)

        self.air_speed_kt = self._bounded(
            self.ground_speed_kt * 0.65 + self.rng.uniform(-3, 3),
            120,
            330,
        )

    def _burn_fuel(self, elapsed_seconds: float) -> None:
        fuel_burn = elapsed_seconds * self.rng.uniform(0.08, 0.16)
        self.left_fuel_qty = max(0, self.left_fuel_qty - fuel_burn * self.rng.uniform(0.98, 1.02))
        self.right_fuel_qty = max(0, self.right_fuel_qty - fuel_burn * self.rng.uniform(0.98, 1.02))
        self.center_fuel_qty = max(0, self.center_fuel_qty - fuel_burn * self.rng.uniform(0.5, 0.8))

    def _land(self) -> None:
        self.traveled_nm = self.route_nm
        self.progress = 1.0
        self.phase = "landed"
        self.altitude_ft = 0.0
        self.ground_speed_kt = 0.0
        self.air_speed_kt = 0.0

    def _sync_context(self) -> None:
        self.context.phase = self.phase
        if self.finished:
            self.context.status = "finished"
            if self.context.ended_at is None:
                self.context.ended_at = self.context.simulated_now
        else:
            self.context.status = "running"
            self.context.ended_at = None

    def _payload(self) -> dict:
        lat, lon = interpolate_lat_lon(
            self.context.origin,
            self.context.destination,
            self.progress,
        )
        bearing = bearing_degrees(
            self.context.origin.lat,
            self.context.origin.lon,
            self.context.destination.lat,
            self.context.destination.lon,
        )
        is_ground = self.phase in {"departure", "landed"}
        track = (bearing + (0 if is_ground else self.rng.uniform(-2.5, 2.5))) % 360
        heading = (track + (0 if is_ground else self.rng.uniform(-1.0, 1.0))) % 360
        remaining_nm = max(0.0, self.route_nm - self.traveled_nm)
        eta_speed = max(1.0, self.cruise_speed_kt if self.phase == "departure" else self.ground_speed_kt)
        eta_minutes = remaining_nm / eta_speed * 60

        return {
            "AIR GND ON GND": "GROUND" if is_ground else "AIR",
            "BARO COR ALT NO. 1": f"{self.altitude_ft:.0f}",
            "COMPUTED AIRSPEED": f"{self.air_speed_kt:.0f}",
            "DESTINATION": self.context.destination.code,
            "DESTINATION ETA": self._eta_text(eta_minutes),
            "DISTANCE TO GO": f"{remaining_nm:.0f}",
            "FLIGHT NUMBER": self.context.flight_number,
            "GROUNDSPEED": f"{self.ground_speed_kt:.0f}",
            "ORIGIN": self.context.origin.code,
            "PRES POSN LAT - FMC": f"{lat:.9f}",
            "PRES POSN LONG - FMC": f"{lon:.9f}",
            "TRACK ANGLE TRUE - FMC": f"{track:.3f}",
            "CAPT DISPLAY HEADING": f"{heading:.3f}",
            "BODY PITCH RATE": f"{self._pitch():.2f}",
            "BODY ROLL RATE": f"{self._roll():.2f}",
            "LT MAIN FUEL QTY": f"{self.left_fuel_qty:.0f}",
            "RT MAIN FUEL QTY": f"{self.right_fuel_qty:.0f}",
            "CENTER MAIN FUEL QTY": f"{self.center_fuel_qty:.0f}",
            "LOW FUEL QTY TANK1/2": str(
                self.left_fuel_qty < 1_000 or self.right_fuel_qty < 1_000
            ).lower(),
            "frameCount": self.frame_count,
            "time": qar_time(self.context.simulated_now),
        }

    def _pitch(self) -> float:
        if self.phase == "climb":
            return self.rng.uniform(2.0, 5.0)
        if self.phase in {"descent", "landing"}:
            return self.rng.uniform(-4.0, -0.5)
        if self.phase == "cruise":
            return self.rng.uniform(-1.0, 3.0)
        return 0.0

    def _roll(self) -> float:
        if self.phase in {"departure", "landed"}:
            return 0.0
        return self.rng.uniform(-4.5, 4.5)

    @staticmethod
    def _ratio(value: float, total: float) -> float:
        if total <= 0:
            return 1.0
        return min(1.0, max(0.0, value / total))

    @staticmethod
    def _lerp(start: float, end: float, ratio: float) -> float:
        return start + (end - start) * ratio

    @staticmethod
    def _bounded(value: float, lower: float, upper: float) -> float:
        return min(upper, max(lower, value))

    @staticmethod
    def _eta_text(minutes: float) -> str:
        whole_minutes = int(minutes)
        seconds = int((minutes - whole_minutes) * 60)
        return f"{whole_minutes}:{seconds:02d}.0"
