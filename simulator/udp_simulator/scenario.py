from __future__ import annotations

import math
import random
import string
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone


CN_TZ = timezone(timedelta(hours=8))


@dataclass(frozen=True)
class Airport:
    code: str
    city: str
    name: str
    lat: float
    lon: float


AIRPORTS = [
    Airport("ZBAA", "北京", "北京首都国际机场", 40.0799, 116.6031),
    Airport("ZSPD", "上海", "上海浦东国际机场", 31.1443, 121.8083),
    Airport("ZGGG", "广州", "广州白云国际机场", 23.3924, 113.2988),
    Airport("ZUUU", "成都", "成都双流国际机场", 30.5785, 103.9471),
    Airport("ZSHC", "杭州", "杭州萧山国际机场", 30.2369, 120.4324),
]


@dataclass
class ScenarioContext:
    task_id: str
    flight_number: str
    origin: Airport
    destination: Airport
    scenario_start_time: datetime
    simulated_now: datetime
    aircraft_model: str
    passenger_count: int
    window_rows: int

    @property
    def scenario_name(self) -> str:
        return f"{self.origin.city} -> {self.destination.city} 巡航模拟"


def create_scenario(passenger_count: int, window_rows: int, rng: random.Random) -> ScenarioContext:
    origin, destination = rng.sample(AIRPORTS, 2)
    now = datetime.now(CN_TZ).replace(microsecond=0)
    flight_number = f"CA{rng.randint(1000, 9999)}"
    task_id = f"{flight_number}-FLIGHT-{now.strftime('%Y%m%d')}-001"
    return ScenarioContext(
        task_id=task_id,
        flight_number=flight_number,
        origin=origin,
        destination=destination,
        scenario_start_time=now,
        simulated_now=now,
        aircraft_model="COMAC C929-700",
        passenger_count=passenger_count,
        window_rows=window_rows,
    )


def iso_time(value: datetime) -> str:
    return value.isoformat()


def compact_time(value: datetime) -> str:
    return value.strftime("%Y-%m-%d %H:%M:%S.") + f"{value.microsecond // 1000:03d}"


def qar_time(value: datetime) -> str:
    return value.strftime("%H:%M:%S")


def random_pnr(rng: random.Random) -> str:
    alphabet = string.ascii_uppercase + string.digits
    return "".join(rng.choice(alphabet) for _ in range(6))


def haversine_nm(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    radius_nm = 3440.065
    phi1 = math.radians(lat1)
    phi2 = math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlambda = math.radians(lon2 - lon1)
    a = math.sin(dphi / 2) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(dlambda / 2) ** 2
    return 2 * radius_nm * math.atan2(math.sqrt(a), math.sqrt(1 - a))


def bearing_degrees(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    phi1 = math.radians(lat1)
    phi2 = math.radians(lat2)
    dlambda = math.radians(lon2 - lon1)
    y = math.sin(dlambda) * math.cos(phi2)
    x = math.cos(phi1) * math.sin(phi2) - math.sin(phi1) * math.cos(phi2) * math.cos(dlambda)
    return (math.degrees(math.atan2(y, x)) + 360) % 360


def interpolate_lat_lon(origin: Airport, destination: Airport, progress: float) -> tuple[float, float]:
    progress = max(0.0, min(1.0, progress))
    lat = origin.lat + (destination.lat - origin.lat) * progress
    lon = origin.lon + (destination.lon - origin.lon) * progress
    return lat, lon
