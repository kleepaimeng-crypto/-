from __future__ import annotations

import random
import unittest
from collections import Counter

from receiver_server import ReceiverState
from udp_simulator.config import SimulatorConfig
from udp_simulator.ground_model import GroundModel
from udp_simulator.ife_model import IfeModel
from udp_simulator.passengers import C929_700_SEATS, build_passengers
from udp_simulator.scenario import create_scenario
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

    def test_passengers_and_ife_keep_existing_wire_shape(self) -> None:
        rng = random.Random(20260703)
        passengers = build_passengers(282, rng)
        context = create_scenario(282, 59, rng)
        model = IfeModel(context, passengers, rng)

        pages_633 = model.build_633_pages(50)
        pages_cockrell = model.build_cockrell_pages(50)
        self.assertEqual([50, 50, 50, 50, 50, 32], [len(page["items"]) for page in pages_633])
        self.assertEqual([50, 50, 50, 50, 50, 32], [len(page["items"]) for page in pages_cockrell])
        self.assertTrue(all(page["total"] == 282 for page in pages_633 + pages_cockrell))

        item = pages_cockrell[0]["items"][0]
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

    def test_default_config_matches_confirmed_aircraft(self) -> None:
        config = SimulatorConfig()
        self.assertEqual(282, config.passenger_count)
        self.assertEqual(118, config.window_count)
        self.assertEqual(59, config.window_rows)

        with self.assertRaisesRegex(ValueError, "passengerCount must be 282"):
            build_passengers(320, random.Random(1))

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
