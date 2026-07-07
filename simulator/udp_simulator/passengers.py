from __future__ import annotations

import random
from dataclasses import dataclass

from .scenario import random_pnr


VIDEO_TYPES = ["爱情", "都市", "青春", "奇幻", "武侠", "古装", "科幻", "猎奇", "竞技", "传奇", "逆袭"]
MUSIC_TYPES = ["流行", "摇滚", "民谣", "电子", "舞曲", "说唱", "轻音乐", "爵士", "乡村", "R&B", "古典"]

BUSINESS_SEAT_LETTERS = ("A", "C", "D", "H", "J", "L")
ECONOMY_SEAT_LETTERS = ("A", "C", "D", "E", "F", "H", "J", "L")
ECONOMY_SEAT_LETTERS_WITHOUT_F = ("A", "C", "D", "E", "H", "J", "L")


@dataclass(frozen=True)
class SeatDefinition:
    seat_no: str
    row: int
    letter: str
    cabin_class: str


def _build_a330_200_seats() -> tuple[SeatDefinition, ...]:
    seats: list[SeatDefinition] = []

    def add_row(row: int, letters: tuple[str, ...], cabin_class: str) -> None:
        seats.extend(SeatDefinition(f"{row}{letter}", row, letter, cabin_class) for letter in letters)

    for row in range(11, 16):
        add_row(row, BUSINESS_SEAT_LETTERS, "BUSINESS")
    add_row(31, ECONOMY_SEAT_LETTERS_WITHOUT_F, "ECONOMY")
    for row in range(32, 54):
        add_row(row, ECONOMY_SEAT_LETTERS, "ECONOMY")
    for row in range(54, 57):
        add_row(row, ECONOMY_SEAT_LETTERS_WITHOUT_F, "ECONOMY")
    add_row(57, ("D", "E", "H"), "ECONOMY")
    return tuple(seats)


A330_200_SEATS = _build_a330_200_seats()


@dataclass
class Passenger:
    index: int
    user_id: str
    pnr: str
    seat_no: str
    cabin_class: str
    device_id: str
    terminal_id: str
    display_terminal_id: str
    video_preferences: list[str]
    music_preferences: list[str]


def build_passengers(count: int, rng: random.Random) -> list[Passenger]:
    if count != len(A330_200_SEATS):
        raise ValueError(f"A330-200 passengerCount must be {len(A330_200_SEATS)}, got {count}")

    passengers: list[Passenger] = []
    for idx, seat in enumerate(A330_200_SEATS, start=1):
        pref_video_count = rng.randint(1, 3)
        pref_music_count = rng.randint(1, 3)
        passengers.append(
            Passenger(
                index=idx,
                user_id=f"PAX-{idx:05d}",
                pnr=random_pnr(rng),
                seat_no=seat.seat_no,
                cabin_class=seat.cabin_class,
                device_id=f"SVDU-{seat.row:02d}-{seat.letter}",
                terminal_id=f"T-{idx:04d}",
                display_terminal_id=seat.seat_no,
                video_preferences=rng.sample(VIDEO_TYPES, pref_video_count),
                music_preferences=rng.sample(MUSIC_TYPES, pref_music_count),
            )
        )
    return passengers
