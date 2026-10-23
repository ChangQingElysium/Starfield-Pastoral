#!/usr/bin/env python3
"""Start, save and reopen the remapped Fabric artifact in a new isolated world."""
from __future__ import annotations

import json
import os
from pathlib import Path
import re
import secrets
import signal
import socket
import struct
import subprocess
import tempfile
import time

from verify_runtime_smoke import ABI_FAILURE_PATTERNS, PALETTE_FAILURE_PATTERNS


def free_port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


def read_exact(sock, size):
    result = b""
    while len(result) < size:
        chunk = sock.recv(size - len(result))
        if not chunk:
            raise RuntimeError("RCON closed before its complete response")
        result += chunk
    return result


def packet(sock, kind, text, request=7):
    body = struct.pack("<ii", request, kind) + text.encode() + b"\0\0"
    sock.sendall(struct.pack("<i", len(body)) + body)
    size, = struct.unpack("<i", read_exact(sock, 4))
    if not 10 <= size <= 4_194_304:
        raise RuntimeError(f"Invalid RCON response size: {size}")
    reply = read_exact(sock, size)
    identity, _ = struct.unpack("<ii", reply[:8])
    if identity != request:
        raise RuntimeError("RCON authentication/request failed")
    return reply[8:-2].decode()


def main():
    root = Path(__file__).resolve().parents[1]
    reports = root / "fabric/build/reports/release-smoke"
    reports.mkdir(parents=True, exist_ok=True)
    # Every invocation owns a fresh directory; never reset a developer's server world.
    run = Path(tempfile.mkdtemp(prefix="world-", dir=reports))
    config = reports / (run.name + "-config")
    config.mkdir()
    port, rcon = free_port(), free_port()
    while rcon == port:
        rcon = free_port()
    password = secrets.token_hex(16)
    (config / "eula.txt").write_text("eula=true\n")
    (config / "server.properties").write_text(
        f"server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\n"
        f"enable-rcon=true\nrcon.port={rcon}\nrcon.password={password}\n"
        "level-name=world\nlevel-seed=6302\nview-distance=2\nsimulation-distance=2\n"
        "max-players=2\nspawn-protection=0\nmax-tick-time=120000\n")
    results = []
    for cycle in ("create", "reopen"):
        output = reports / f"{cycle}.log"
        with output.open("w") as stream:
            process = subprocess.Popen([
                str(root / "gradlew"), "-p", "fabric", "runFabricCandidateServer",
                f"-PfabricCandidateRunDir={run}", f"-PfabricSmokeConfig={config}",
                "--no-daemon", "--console=plain",
            ], cwd=root, stdout=stream, stderr=subprocess.STDOUT, start_new_session=True)
            try:
                deadline = time.monotonic() + 600
                while True:
                    log = output.read_text(errors="replace")
                    if process.poll() is not None:
                        raise RuntimeError(f"Fabric server exited before startup: {output}")
                    if "RCON running on" in log and re.search(r"Done \([\d.]+s\)!", log):
                        break
                    if time.monotonic() >= deadline:
                        raise RuntimeError(f"Fabric startup exceeded 600 seconds: {output}")
                    time.sleep(1)
                with socket.create_connection(("127.0.0.1", rcon), timeout=30) as sock:
                    packet(sock, 3, password)
                    if cycle == "create":
                        packet(sock, 2, 'data modify storage stardewcraft:release_smoke marker set value "FABRIC_SAVE_MARKER_OK"')
                    response = packet(sock, 2, "data get storage stardewcraft:release_smoke marker")
                    if "FABRIC_SAVE_MARKER_OK" not in response:
                        raise RuntimeError(f"Saved storage marker missing during {cycle}: {response}")
                    packet(sock, 2, "save-all flush")
                    packet(sock, 2, "stop")
                code = process.wait(timeout=120)
                log = output.read_text(errors="replace")
                if code != 0:
                    raise RuntimeError(f"Fabric shutdown exited {code}: {output}")
                for marker in ("Stopping server", "Saving worlds", "All dimensions are saved"):
                    if marker not in log:
                        raise RuntimeError(f"Missing {marker!r}: {output}")
                failures = ABI_FAILURE_PATTERNS + PALETTE_FAILURE_PATTERNS + (
                    r"Error saving", r"Failed to save", r"Exception stopping", r"Failed to start the minecraft server",
                )
                for pattern in failures:
                    if re.search(pattern, log):
                        raise RuntimeError(f"Runtime failure {pattern!r}: {output}")
                results.append({"cycle": cycle, "exit": code, "save_marker": True, "all_dimensions_saved": True})
                print(f"Fabric production {cycle}: startup, saved marker and complete shutdown passed", flush=True)
            finally:
                if process.poll() is None:
                    os.killpg(process.pid, signal.SIGTERM)
                    try:
                        process.wait(timeout=15)
                    except subprocess.TimeoutExpired:
                        os.killpg(process.pid, signal.SIGKILL)
                        process.wait()
    (reports / "summary.json").write_text(json.dumps({"world": str(run), "results": results}, indent=2) + "\n")


if __name__ == "__main__":
    main()
