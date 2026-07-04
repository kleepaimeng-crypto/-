from __future__ import annotations

import random
from dataclasses import dataclass

from .scenario import random_pnr


SEAT_LETTERS = ["A", "B", "C", "D", "E", "F", "G", "H", "J", "K"]
VIDEO_TYPES = ["爱情", "都市", "青春", "奇幻", "武侠", "古装", "科幻", "猎奇", "竞技", "传奇", "逆袭"]
MUSIC_TYPES = ["流行", "摇滚", "民谣", "电子", "舞曲", "说唱", "轻音乐", "爵士", "乡村", "R&B", "古典"]


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


def cabin_class_for_row(row: int) -> str:
    if row <= 2:
        return "FIRST"
    if row <= 10:
        return "BUSINESS"
    return "ECONOMY"


def build_passengers(count: int, rng: random.Random) -> list[Passenger]:
    passengers: list[Passenger] = []
    row = 1
    seat_index = 0
    for idx in range(1, count + 1):
        seat_letter = SEAT_LETTERS[seat_index % len(SEAT_LETTERS)]
        seat_no = f"{row:02d}{seat_letter}"
        cabin_class = cabin_class_for_row(row)
        pref_video_count = rng.randint(1, 3)
        pref_music_count = rng.randint(1, 3)
        passengers.append(
            Passenger(
                index=idx,
                user_id=f"PAX-{idx:05d}",
                pnr=random_pnr(rng),
                seat_no=seat_no,
                cabin_class=cabin_class,
                device_id=f"SVDU-{row:02d}-{seat_letter}",
                terminal_id=f"T-{idx:04d}",
                display_terminal_id=seat_no,
                video_preferences=rng.sample(VIDEO_TYPES, pref_video_count),
                music_preferences=rng.sample(MUSIC_TYPES, pref_music_count),
            )
        )
        seat_index += 1
        if seat_index % len(SEAT_LETTERS) == 0:
            row += 1
    return passengers

