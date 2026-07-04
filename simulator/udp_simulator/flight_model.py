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
    progress: float
    altitude_ft: float
    ground_speed_kt: float
    air_speed_kt: float
    left_fuel_qty: float
    right_fuel_qty: float
    center_fuel_qty: float
    frame_count: int = 0

    @classmethod
    def create(cls, context: ScenarioContext, rng: random.Random) -> "FlightModel":
        return cls(
            context=context,
            rng=rng,
            progress=rng.uniform(0.35, 0.65),
            altitude_ft=rng.uniform(33000, 37000),
            ground_speed_kt=rng.uniform(455, 490),
            air_speed_kt=rng.uniform(275, 315),
            left_fuel_qty=rng.uniform(10500, 13000),
            right_fuel_qty=rng.uniform(10500, 13000),
            center_fuel_qty=rng.uniform(4000, 7500),
        )

    def advance(self, elapsed_seconds: float) -> dict:
        self.frame_count += 1
        route_nm = max(
            1.0,
            haversine_nm(
                self.context.origin.lat,
                self.context.origin.lon,
                self.context.destination.lat,
                self.context.destination.lon,
            ),
        )
        self.ground_speed_kt = self._bounded(self.ground_speed_kt + self.rng.uniform(-4, 4), 430, 510)
        self.air_speed_kt = self._bounded(self.air_speed_kt + self.rng.uniform(-3, 3), 250, 330)
        self.altitude_ft = self._bounded(self.altitude_ft + self.rng.uniform(-80, 80), 31000, 39000)
        self.progress = min(0.95, self.progress + (self.ground_speed_kt * elapsed_seconds / 3600) / route_nm)

        fuel_burn = elapsed_seconds * self.rng.uniform(0.08, 0.16)
        self.left_fuel_qty = max(0, self.left_fuel_qty - fuel_burn * self.rng.uniform(0.98, 1.02))
        self.right_fuel_qty = max(0, self.right_fuel_qty - fuel_burn * self.rng.uniform(0.98, 1.02))
        self.center_fuel_qty = max(0, self.center_fuel_qty - fuel_burn * self.rng.uniform(0.5, 0.8))

        lat, lon = interpolate_lat_lon(self.context.origin, self.context.destination, self.progress)
        bearing = bearing_degrees(
            self.context.origin.lat,
            self.context.origin.lon,
            self.context.destination.lat,
            self.context.destination.lon,
        )
        track = (bearing + self.rng.uniform(-2.5, 2.5)) % 360
        heading = (track + self.rng.uniform(-1.0, 1.0)) % 360
        remaining_nm = max(0, route_nm * (1 - self.progress))
        eta_minutes = remaining_nm / max(1, self.ground_speed_kt) * 60

        return {
            "AIR GND ON GND": "AIR",
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
            "BODY PITCH RATE": f"{self.rng.uniform(-1.0, 3.0):.2f}",
            "BODY ROLL RATE": f"{self.rng.uniform(-4.5, 4.5):.2f}",
            "LT MAIN FUEL QTY": f"{self.left_fuel_qty:.0f}",
            "RT MAIN FUEL QTY": f"{self.right_fuel_qty:.0f}",
            "CENTER MAIN FUEL QTY": f"{self.center_fuel_qty:.0f}",
            "LOW FUEL QTY TANK1/2": "false",
            "frameCount": self.frame_count,
            "time": qar_time(self.context.simulated_now),
        }

    @staticmethod
    def _bounded(value: float, lower: float, upper: float) -> float:
        return min(upper, max(lower, value))

    @staticmethod
    def _eta_text(minutes: float) -> str:
        whole_minutes = int(minutes)
        seconds = int((minutes - whole_minutes) * 60)
        return f"{whole_minutes}:{seconds:02d}.0"

