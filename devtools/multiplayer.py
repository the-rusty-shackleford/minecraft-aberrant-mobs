"""Run the two-client gravity gate with one rendering client at a time.

Copyright 2026 Rusty Shackleford and nfx. AGPL-3.0-or-later.
Requires: Java 21, DISPLAY naming the already checked test display, no other client.
Effects: runs only the loopback fixture; logs under run/multiplayer/driver; cleans up
its own process groups on failure. Never creates a display or touches a live server.
"""

import os
import signal
import socket
import subprocess
import time
from dataclasses import dataclass
from pathlib import Path
from typing import BinaryIO

ROOT = Path(__file__).resolve().parents[1]
LOGS = ROOT / "run/multiplayer/driver"


@dataclass(frozen=True)
class Run:
    """AF: one owned Gradle process group and its output. RI: log stream stays open."""

    process: subprocess.Popen[bytes]
    path: Path
    stream: BinaryIO

    def text(self) -> str:
        """effects: reads current output without blocking."""
        return self.path.read_text(errors="replace")


def start(role: str) -> Run:
    """effects: starts one isolated run; throws: OSError on launch failure."""
    path = LOGS / f"{role.lower()}.log"
    stream = path.open("wb")
    try:
        process = subprocess.Popen(
            ["./gradlew", f"runMultiplayer{role}", "--offline"],
            cwd=ROOT,
            stdout=stream,
            stderr=subprocess.STDOUT,
            start_new_session=True,
        )
    except OSError:
        stream.close()
        raise
    return Run(process, path, stream)


def ready(run: Run, marker: str) -> None:
    """effects: waits at most 180s for readiness; throws: RuntimeError on early exit."""
    deadline = time.monotonic() + 180
    while time.monotonic() < deadline:
        text = run.text()
        if marker in text:
            return
        if run.process.poll() is not None or "multiplayer: FAIL" in text:
            raise RuntimeError(f"Startup failed; see {run.path}")
        time.sleep(0.2)
    raise RuntimeError(f"Startup timed out; see {run.path}")


def main() -> None:
    """effects: runs all gates and closes owned processes; throws: RuntimeError on failure."""
    if not os.environ.get("DISPLAY"):
        raise RuntimeError("Set DISPLAY to the existing, host-checked test display")
    with socket.socket() as probe:
        if probe.connect_ex(("127.0.0.1", 25579)) == 0:
            raise RuntimeError(
                "Loopback port 25579 is in use; no processes were started"
            )
    LOGS.mkdir(parents=True, exist_ok=True)
    runs: list[Run] = []
    try:
        server = start("Server")
        runs.append(server)
        ready(server, "Done (")
        wearer = start("Wearer")
        runs.append(wearer)
        ready(wearer, "multiplayer: NON_RENDERING_READY")
        print("Wearer finished joining and disabled rendering; starting observer", flush=True)
        observer = start("Observer")
        runs.append(observer)
        deadline = time.monotonic() + 240
        while any(run.process.poll() is None for run in runs):
            for run in runs:
                if "multiplayer: FAIL" in run.text() or run.process.poll() not in (
                    None,
                    0,
                ):
                    raise RuntimeError(f"Experiment failed; see {run.path}")
            if time.monotonic() > deadline:
                raise RuntimeError(f"Experiment timed out; see {LOGS}")
            time.sleep(0.2)
        for run in runs:
            text = run.text()
            if (
                "multiplayer: PASS all checks ran" not in text
                or "BUILD SUCCESSFUL" not in text
            ):
                raise RuntimeError(f"Missing completion; see {run.path}")
            if (
                "FrameWearer moved wrongly" in text
                or "FrameWearer moved too quickly" in text
            ):
                raise RuntimeError(f"Movement correction; see {run.path}")
        print(
            "PASS: both clients and server completed without movement corrections",
            flush=True,
        )
    finally:
        for run in reversed(runs):
            if run.process.poll() is None:
                os.killpg(run.process.pid, signal.SIGTERM)
        for run in reversed(runs):
            try:
                run.process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                os.killpg(run.process.pid, signal.SIGKILL)
                run.process.wait()
            run.stream.close()


if __name__ == "__main__":
    main()
