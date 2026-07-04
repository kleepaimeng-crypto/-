from __future__ import annotations

import argparse
import json

from udp_simulator.config import SimulatorConfig
from udp_simulator.simulator import DataSimulator


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Local UDP data simulator.")
    parser.add_argument("--config", help="Path to a JSON config file.", default=None)
    parser.add_argument("--once", action="store_true", help="Send one batch for every interface and exit.")
    parser.add_argument("--dry-run", action="store_true", help="Build payloads without sending UDP packets.")
    parser.add_argument("--summary", action="store_true", help="Print generated scenario summary at startup.")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    config = SimulatorConfig.from_file(args.config)
    simulator = DataSimulator(config, dry_run=args.dry_run)
    try:
        if args.summary:
            print(json.dumps(simulator.summary(), ensure_ascii=False, indent=2))
        if args.once:
            results = simulator.run_once()
            for result in results:
                mode = "built" if args.dry_run else "sent"
                print(f"{mode} {result.message_type} -> {result.host}:{result.port} ({result.bytes_sent} bytes)")
        else:
            simulator.run_forever()
    finally:
        simulator.close()


if __name__ == "__main__":
    main()

