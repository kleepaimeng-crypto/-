from __future__ import annotations

import random
import unittest
from collections import Counter

from receiver_server import ReceiverState
from udp_simulator.config import SimulatorConfig
from udp_simulator.entertainment_catalog import MUSIC_WORKS, VIDEO_WORKS
from udp_simulator.ground_model import GroundModel
from udp_simulator.ife_model import IfeModel
from udp_simulator.passengers import (
    C929_700_SEATS,
    MUSIC_TYPES,
    VIDEO_TYPES,
    build_passengers,
)
from udp_simulator.scenario import create_scenario
from udp_simulator.simulator import DataSimulator
from udp_simulator.window_model import SmartWindowModel


class C929LayoutTests(unittest.TestCase):
    def test_seat_manifest_has_expected_capacity_and_special_rows(self) -> None:
        seat_numbers = [seat.seat_no for seat in C929_700_SEATS]
        cabin_counts = Counter(seat.cabin_class for seat in C929_700_SEATS)

        self.assertEqual(282, len(seat_numbers))
        self.assertEqual(282, len(set(seat_numbers)))
        self.assertEqual({"BUSINESS": 38, "ECONOMY": 244}, dict(cabin_counts))
        self.assertEqual({"A11", "D11", "G11", "K11"}, self._row(11))
        self.assertEqual({"A13", "C13", "D13", "G13", "H13", "K13"}, self._row(13))
        self.assertEqual({"A31", "B31", "C31", "D31", "E31", "F31", "G31", "H31", "K31"}, self._row(31))
        self.assertEqual({"D44", "E44", "F44"}, self._row(44))
        self.assertEqual({"A45", "C45", "D45", "E45", "F45", "H45", "K45"}, self._row(45))
        self.assertEqual({"A58", "B58", "C58", "D58", "E58", "F58", "G58", "H58", "K58"}, self._row(58))
        for invalid in ("A01", "B11", "C11", "J31", "A44", "B45", "11A"):
            self.assertNotIn(invalid, seat_numbers)
        self.assertTrue(all(seat[0].isalpha() and seat[1:].isdigit() for seat in seat_numbers))

    def test_passengers_and_ife_keep_confirmed_wire_shape(self) -> None:
        rng = random.Random(20260703)
        passengers = build_passengers(282, rng)
        context = create_scenario(282, 59, rng)
        model = IfeModel(context, passengers, rng)

        event_633 = model.build_633_event()
        events_cockrell = model.build_cockrell_events("full", 50)
        self.assertEqual(282, len(events_cockrell))
        self.assertEqual({"sysInfo", "paxInfo", "behaviorInfo", "extInfo"}, set(event_633))
        self.assertNotIn("items", event_633)
        self.assertNotIn("messageType", event_633)

        item = events_cockrell[0]
        self.assertEqual({"sysInfo", "paxInfo", "behaviorInfo", "extInfo"}, set(item))
        self.assertEqual({"timestamp", "flightId"}, set(item["sysInfo"]))
        self.assertEqual({"pnr", "seatNo", "cabinClass", "deviceId", "userId"}, set(item["paxInfo"]))
        self.assertFalse(any("svg" in key.lower() for key in self._all_keys(item)))

        task = GroundModel(context, passengers, rng).task_payload()
        self.assertEqual(282, task["payload"]["terminalCount"])
        self.assertEqual("COMAC C929-700", context.aircraft_model)

        ground = GroundModel(context, passengers, rng)
        traffic_item = ground.traffic_payload()[0]["items"][0]
        session_item = ground.session_payload()[0]["items"][0]
        self.assertRegex(item["paxInfo"]["seatNo"], r"^[A-Z][0-9]{2}$")
        self.assertRegex(traffic_item["seatLabel"], r"^[A-Z][0-9]{2}$")
        self.assertEqual(traffic_item["seatLabel"], traffic_item["displayTerminalId"])
        self.assertRegex(session_item["seatLabel"], r"^[A-Z][0-9]{2}$")
        self.assertEqual(session_item["seatLabel"], session_item["displayTerminalId"])

    def test_entertainment_catalog_is_complete_and_ife_metadata_stays_consistent(self) -> None:
        self.assertEqual(24, len(VIDEO_WORKS))
        self.assertEqual(24, len(MUSIC_WORKS))
        all_works = VIDEO_WORKS + MUSIC_WORKS
        self.assertEqual(48, len({work.work_code for work in all_works}))
        self.assertTrue(
            {"星海远航", "云端恋曲", "逆袭之路", "长安奇谈", "银河竞技场", "都市风暴"}
            <= {work.title for work in VIDEO_WORKS}
        )
        self.assertTrue(
            {"云上节拍", "夜航民谣", "蓝色爵士", "星光电子", "古典晨曦", "摇滚航线"}
            <= {work.title for work in MUSIC_WORKS}
        )

        video_genre_counts = Counter(genre for work in VIDEO_WORKS for genre in work.genres)
        music_genre_counts = Counter(genre for work in MUSIC_WORKS for genre in work.genres)
        self.assertEqual(set(VIDEO_TYPES), set(video_genre_counts))
        self.assertEqual(set(MUSIC_TYPES), set(music_genre_counts))
        self.assertTrue(all(video_genre_counts[genre] >= 4 for genre in VIDEO_TYPES))
        self.assertTrue(all(music_genre_counts[genre] >= 4 for genre in MUSIC_TYPES))

        rng = random.Random(20260727)
        passengers = build_passengers(282, rng)
        model = IfeModel(create_scenario(282, 59, rng), passengers, rng)
        video_by_code = {work.work_code: work for work in VIDEO_WORKS}
        music_by_code = {work.work_code: work for work in MUSIC_WORKS}

        for item in model.build_cockrell_events("full", 50):
            info = item["behaviorInfo"]
            if info["behaviorType"] == "MOVIE_PLAY":
                work = video_by_code[info["contentId"]]
                self.assertEqual(work.title, info["contentName"])
                self.assertEqual("/".join(work.genres), info["contentType"])
                self.assertEqual(work.duration_seconds // 60, info["contentDuration"])
                self.assertIn(info["playAction"], {"PLAY", "PAUSE"})
            elif info["behaviorType"] == "MUSIC_PLAY":
                work = music_by_code[info["musicId"]]
                self.assertEqual(work.title, info["musicName"])
                self.assertEqual("/".join(work.genres), info["musicType"])
                self.assertEqual(work.creator_name, info["artist"])
                self.assertEqual(work.collection_name or "", info["album"])
                self.assertIn(info["playAction"], {"PLAY", "PAUSE"})

    def test_default_config_matches_confirmed_aircraft(self) -> None:
        config = SimulatorConfig()
        self.assertEqual(282, config.passenger_count)
        self.assertEqual(118, config.window_count)
        self.assertEqual(59, config.window_rows)
        self.assertEqual("single", config.ife_cockrell_mode)
        self.assertEqual(50, config.ife_cockrell_burst_size)
        self.assertEqual(5.0, config.send_intervals_seconds["ife_cockrell.behavior"])

        with self.assertRaisesRegex(ValueError, "passengerCount must be 282"):
            build_passengers(320, random.Random(1))

    def test_cockrell_modes_emit_one_event_per_selected_passenger(self) -> None:
        rng = random.Random(20260810)
        passengers = build_passengers(282, rng)
        model = IfeModel(create_scenario(282, 59, rng), passengers, rng)

        self.assertEqual(1, len(model.build_cockrell_events("single", 50)))
        burst = model.build_cockrell_events("burst", 50)
        self.assertEqual(50, len(burst))
        self.assertEqual(50, len({item["paxInfo"]["seatNo"] for item in burst}))
        self.assertEqual(282, len(model.build_cockrell_events("full", 50)))

    def test_cockrell_sends_a_full_snapshot_before_single_events(self) -> None:
        simulator = DataSimulator(SimulatorConfig(random_seed=20260810), dry_run=True)
        self.addCleanup(simulator.close)

        initial = simulator._ife_cockrell_payloads(10)
        later = simulator._ife_cockrell_payloads(10)

        self.assertEqual(282, len(initial))
        self.assertEqual(282, len({item["paxInfo"]["seatNo"] for item in initial}))
        self.assertEqual(1, len(later))

        simulator._start_next_flight()
        next_flight_initial = simulator._ife_cockrell_payloads(10)
        self.assertEqual(282, len(next_flight_initial))

    def test_633_sends_only_one_event_without_startup_snapshot(self) -> None:
        simulator = DataSimulator(SimulatorConfig(random_seed=20260810), dry_run=True)
        self.addCleanup(simulator.close)

        initial = simulator._ife_633_payloads(10)
        later = simulator._ife_633_payloads(10)

        self.assertEqual(1, len(initial))
        self.assertEqual(1, len(later))
        self.assertEqual({"sysInfo", "paxInfo", "behaviorInfo", "extInfo"}, set(initial[0]))

        simulator._start_next_flight()
        self.assertEqual(1, len(simulator._ife_633_payloads(10)))

    def test_smart_windows_are_118_with_symmetric_zone_counts(self) -> None:
        rng = random.Random(20260703)
        context = create_scenario(282, 59, rng)
        payload = SmartWindowModel(context, 118, rng).update_payload()

        self.assertEqual(118, payload["total"])
        self.assertEqual(list(range(1, 119)), [item["windowId"] for item in payload["items"]])
        self.assertEqual({"windowId", "zoneId", "brightnessLevel", "connectStatus", "status", "timestamp"}, set(payload["items"][0]))
        for side in (payload["items"][:59], payload["items"][59:]):
            self.assertEqual({1: 20, 2: 19, 3: 20}, dict(Counter(item["zoneId"] for item in side)))

        receiver = ReceiverState()
        receiver.update("smart_window.status", payload)
        rows = receiver.snapshot()["windowRows"]
        self.assertEqual(59, len(rows))
        self.assertEqual([1, 60], [item["windowId"] for item in rows[0]["windows"]])
        self.assertEqual([59, 118], [item["windowId"] for item in rows[-1]["windows"]])

    def test_receiver_ranks_only_each_passengers_current_overall_behavior(self) -> None:
        receiver = ReceiverState()
        receiver.update("ife_633.behavior", self._ife_payload([
            self._ife_item("PAX-00001", "MOVIE_PLAY", contentType="奇幻/科幻"),
            self._ife_item("PAX-00002", "MUSIC_PLAY", musicType="民谣"),
        ]))
        first = receiver.snapshot()
        self.assertEqual([("奇幻", 1), ("科幻", 1)], first["videoRanking"])
        self.assertEqual([("民谣", 1)], first["musicRanking"])

        receiver.update("ife_cockrell.behavior", self._ife_payload([
            self._ife_item("PAX-00001", "WAP_BROWSING"),
            self._ife_item("PAX-00002", "MOVIE_PLAY", contentType="爱情"),
        ]))
        current = receiver.snapshot()
        self.assertEqual([("爱情", 1)], current["videoRanking"])
        self.assertEqual([], current["musicRanking"])

    def _ife_payload(self, items: list[dict]) -> dict:
        return {"items": items}

    def _ife_item(self, user_id: str, behavior_type: str, **details: str) -> dict:
        return {
            "paxInfo": {"userId": user_id},
            "behaviorInfo": {"behaviorType": behavior_type, **details},
        }

    def _row(self, row: int) -> set[str]:
        return {seat.seat_no for seat in C929_700_SEATS if seat.row == row}

    def _all_keys(self, value: object) -> list[str]:
        if isinstance(value, dict):
            return [str(key) for key in value] + [key for item in value.values() for key in self._all_keys(item)]
        if isinstance(value, list):
            return [key for item in value for key in self._all_keys(item)]
        return []


if __name__ == "__main__":
    unittest.main()
