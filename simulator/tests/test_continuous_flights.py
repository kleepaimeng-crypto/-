from __future__ import annotations

import random
import unittest
from datetime import datetime, timedelta, timezone

from udp_simulator.config import SimulatorConfig
from udp_simulator.flight_model import FlightModel
from udp_simulator.scenario import AIRPORTS, create_scenario, create_scenario_for_route
from udp_simulator.simulator import DataSimulator


class ContinuousFlightTests(unittest.TestCase):
    def test_airports_support_twenty_directed_routes(self) -> None:
        routes = {
            (origin.code, destination.code)
            for origin in AIRPORTS
            for destination in AIRPORTS
            if origin.code != destination.code
        }
        self.assertEqual(20, len(routes))

    def test_all_directed_routes_complete_every_flight_phase(self) -> None:
        expected = ["departure", "climb", "cruise", "descent", "landing", "landed"]
        start = datetime(2026, 7, 28, 8, 0, tzinfo=timezone(timedelta(hours=8)))
        for route_index, origin in enumerate(AIRPORTS):
            for destination in AIRPORTS:
                if origin.code == destination.code:
                    continue
                rng = random.Random(10_000 + route_index)
                context = create_scenario_for_route(
                    282,
                    59,
                    rng,
                    origin,
                    destination,
                    1,
                    start,
                )
                model = FlightModel.create(context, rng)
                phases: list[str] = []
                for _ in range(1_000):
                    context.simulated_now += timedelta(seconds=60)
                    model.advance(60)
                    if not phases or phases[-1] != model.phase:
                        phases.append(model.phase)
                    if model.finished:
                        break
                self.assertEqual(expected, phases, f"{origin.code}->{destination.code}")

    def test_flight_model_runs_all_phases_and_finishes_at_destination(self) -> None:
        context = create_scenario(282, 59, random.Random(20260703))
        model = FlightModel.create(context, random.Random(20260704))
        phases: list[str] = []
        remaining_distances: list[int] = []
        final_payload: dict | None = None

        for _ in range(500):
            context.simulated_now += timedelta(seconds=60)
            payload = model.advance(60)
            if not phases or phases[-1] != context.phase:
                phases.append(context.phase)
            remaining_distances.append(int(payload["DISTANCE TO GO"]))
            if model.finished:
                final_payload = payload
                break

        self.assertEqual(
            ["departure", "climb", "cruise", "descent", "landing", "landed"],
            phases,
        )
        self.assertTrue(all(
            left >= right
            for left, right in zip(remaining_distances, remaining_distances[1:])
        ))
        self.assertIsNotNone(final_payload)
        assert final_payload is not None
        self.assertEqual("GROUND", final_payload["AIR GND ON GND"])
        self.assertLessEqual(float(final_payload["BARO COR ALT NO. 1"]), 200)
        self.assertLessEqual(float(final_payload["GROUNDSPEED"]), 40)
        self.assertEqual("0", final_payload["DISTANCE TO GO"])
        self.assertEqual(f"{context.destination.lat:.9f}", final_payload["PRES POSN LAT - FMC"])
        self.assertEqual(f"{context.destination.lon:.9f}", final_payload["PRES POSN LONG - FMC"])
        self.assertEqual("finished", context.status)
        self.assertIsNotNone(context.ended_at)

    def test_next_flights_keep_aircraft_passengers_and_avoid_immediate_return(self) -> None:
        simulator = DataSimulator(SimulatorConfig(random_seed=20260703), dry_run=True)
        self.addCleanup(simulator.close)
        passengers = simulator.passengers
        passenger_identity = [id(passenger) for passenger in passengers]
        passenger_pnrs = [passenger.pnr for passenger in passengers]
        windows = simulator.window_model.windows
        window_identity = [id(window) for window in windows]
        seen_flights = {simulator.context.flight_number}
        seen_tasks = {simulator.context.task_id}

        for expected_sequence in range(2, 5):
            previous = self._finish_current_flight(simulator)
            final_task = simulator.ground_model.task_payload()["payload"]
            final_sessions = simulator.ground_model.session_payload()[0]["items"]
            self.assertEqual("finished", final_task["status"])
            self.assertEqual("landed", final_task["phase"])
            self.assertIsNotNone(final_task["endedAt"])
            self.assertTrue(all(item["status"] == "finished" for item in final_sessions))
            self.assertEqual([], simulator._ground_traffic_payloads(10))
            self.assertEqual([], simulator._ife_633_payloads(10))
            self.assertEqual([], simulator._ife_cockrell_payloads(10))

            simulator._advance_time(10)
            first_payload = simulator._qar_payloads(10)[0]
            current = simulator.context
            self.assertEqual(expected_sequence, current.segment_sequence)
            self.assertEqual(previous.destination.code, current.origin.code)
            self.assertNotIn(
                current.destination.code,
                {previous.origin.code, current.origin.code},
            )
            self.assertNotIn(current.flight_number, seen_flights)
            self.assertNotIn(current.task_id, seen_tasks)
            self.assertEqual(1, first_payload["frameCount"])
            self.assertEqual("GROUND", first_payload["AIR GND ON GND"])
            self.assertEqual("running", current.status)
            self.assertEqual("departure", current.phase)
            self.assertIs(passengers, simulator.passengers)
            self.assertEqual(passenger_identity, [id(passenger) for passenger in simulator.passengers])
            self.assertEqual(passenger_pnrs, [passenger.pnr for passenger in simulator.passengers])
            self.assertIs(windows, simulator.window_model.windows)
            self.assertEqual(window_identity, [id(window) for window in simulator.window_model.windows])
            self.assertIs(current, simulator.window_model.context)
            self.assertIs(current, simulator.ife_model.context)
            self.assertIs(current, simulator.ground_model.context)
            seen_flights.add(current.flight_number)
            seen_tasks.add(current.task_id)

    def test_fixed_seed_produces_the_same_route_sequence(self) -> None:
        left = DataSimulator(SimulatorConfig(random_seed=20260703), dry_run=True)
        right = DataSimulator(SimulatorConfig(random_seed=20260703), dry_run=True)
        self.addCleanup(left.close)
        self.addCleanup(right.close)

        left_routes = self._collect_routes(left, 3)
        right_routes = self._collect_routes(right, 3)

        self.assertEqual(left_routes, right_routes)

    def _collect_routes(self, simulator: DataSimulator, count: int) -> list[tuple[str, str, str]]:
        routes = [(
            simulator.context.origin.code,
            simulator.context.destination.code,
            simulator.context.flight_number,
        )]
        while len(routes) < count:
            self._finish_current_flight(simulator)
            simulator._advance_time(10)
            simulator._qar_payloads(10)
            routes.append((
                simulator.context.origin.code,
                simulator.context.destination.code,
                simulator.context.flight_number,
            ))
        return routes

    def _finish_current_flight(self, simulator: DataSimulator):
        for _ in range(100):
            simulator._advance_time(600)
            simulator._qar_payloads(600)
            if simulator.pending_next_flight:
                return simulator.context
        self.fail("Flight did not reach its destination")


if __name__ == "__main__":
    unittest.main()
