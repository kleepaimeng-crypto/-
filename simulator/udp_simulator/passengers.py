from __future__ import annotations

import random
from dataclasses import dataclass

from .scenario import random_pnr


VIDEO_TYPES = ["爱情", "都市", "青春", "奇幻", "武侠", "古装", "科幻", "猎奇", "竞技", "传奇", "逆袭"]
MUSIC_TYPES = ["流行", "摇滚", "民谣", "电子", "舞曲", "说唱", "轻音乐", "爵士", "乡村", "R&B", "古典"]

C929_FIRST_ROWS_SEAT_LETTERS = ("A", "D", "G", "K")
C929_BUSINESS_SEAT_LETTERS = ("A", "C", "D", "G", "H", "K")
C929_ECONOMY_SEAT_LETTERS = ("A", "B", "C", "D", "E", "F", "G", "H", "K")


@dataclass(frozen=True)
class SeatDefinition:
    seat_no: str
    row: int
    letter: str
    cabin_class: str


def _build_c929_700_seats() -> tuple[SeatDefinition, ...]:
    seats: list[SeatDefinition] = []

    def add_row(row: int, letters: tuple[str, ...], cabin_class: str) -> None:
        seats.extend(SeatDefinition(f"{letter}{row}", row, letter, cabin_class) for letter in letters)

    for row in range(11, 13):
        add_row(row, C929_FIRST_ROWS_SEAT_LETTERS, "BUSINESS")
    for row in range(13, 18):
        add_row(row, C929_BUSINESS_SEAT_LETTERS, "BUSINESS")
    for row in range(31, 44):
        add_row(row, C929_ECONOMY_SEAT_LETTERS, "ECONOMY")
    add_row(44, ("D", "E", "F"), "ECONOMY")
    add_row(45, ("A", "C", "D", "E", "F", "H", "K"), "ECONOMY")
    for row in range(46, 59):
        add_row(row, C929_ECONOMY_SEAT_LETTERS, "ECONOMY")
    return tuple(seats)


C929_700_SEATS = _build_c929_700_seats()


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
    if count != len(C929_700_SEATS):
        raise ValueError(f"C929-700 passengerCount must be {len(C929_700_SEATS)}, got {count}")

    passengers: list[Passenger] = []
    for idx, seat in enumerate(C929_700_SEATS, start=1):
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
