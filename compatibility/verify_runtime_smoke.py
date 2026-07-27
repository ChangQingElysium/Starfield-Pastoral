#!/usr/bin/env python3
"""Verify that a captured Minecraft runtime log completed a real smoke test."""

from __future__ import annotations

import argparse
import re
from dataclasses import dataclass
from pathlib import Path


ABI_FAILURE_PATTERNS = (
    r"\bNoSuchMethodError\b",
    r"\bNoSuchFieldError\b",
    r"\bAbstractMethodError\b",
    r"\bIncompatibleClassChangeError\b",
    r"\bNoClassDefFoundError\b",
    r"\bVerifyError\b",
    r"\bMixinApplyError\b",
    r"\bInvalidMixinException\b",
    r"\bInjectionError\b",
    r"\bCritical injection failure\b",
)

CLIENT_WORLD_MARKERS = (
    ("resource reload", r"Reloading ResourceManager:"),
    ("player login", r"logged in with entity id"),
    ("player data load", r"logged in, loaded Stardew data"),
    ("server stop", r"MinecraftServer/]: Stopping server"),
    ("world save", r"MinecraftServer/]: Saving worlds"),
    ("complete save", r"ThreadedAnvilChunkStorage: All dimensions are saved"),
)

GAME_TEST_MARKERS = (
    ("resource reload", r"Loaded \d+ recipes"),
    ("server start", r"Started game test server"),
    ("tests complete", r"All \d+ required tests passed"),
    ("server stop", r"MinecraftServer/]: Stopping server"),
    ("world save", r"MinecraftServer/]: Saving worlds"),
    ("complete save", r"ThreadedAnvilChunkStorage: All dimensions are saved"),
)

DEDICATED_WORLD_MARKERS = (
    ("resource reload", r"Loaded \d+ recipes"),
    ("world preparation", r'Preparing level "world"'),
    ("server start", r"Done \([\d.]+s\)!"),
    ("server stop", r"MinecraftServer/]: Stopping server"),
    ("world save", r"MinecraftServer/]: Saving worlds"),
    ("complete save", r"ThreadedAnvilChunkStorage: All dimensions are saved"),
)

NETWORK_WORLD_MARKERS = (
    ("resource reload", r"Loaded \d+ recipes"),
    ("server start", r"Done \([\d.]+s\)!"),
    (
        "initial capability negotiation",
        r"Negotiated \d+ Stardew network capabilities on server",
    ),
    ("initial player login", r"logged in with entity id"),
    ("initial player data load", r"logged in, loaded Stardew data"),
    ("initial player logout", r"logged out, saved Stardew data"),
    (
        "reconnect capability negotiation",
        r"Negotiated \d+ Stardew network capabilities on server",
    ),
    ("reconnect player login", r"logged in with entity id"),
    ("reconnect player data load", r"logged in, loaded Stardew data"),
    ("reconnect player logout", r"logged out, saved Stardew data"),
    ("server stop", r"MinecraftServer/]: Stopping server"),
    ("world save", r"MinecraftServer/]: Saving worlds"),
    ("complete save", r"ThreadedAnvilChunkStorage: All dimensions are saved"),
)

TIME_PATTERN = re.compile(
    r"\[(?:[^\]]*?\s)?(?P<hour>\d{2}):(?P<minute>\d{2}):"
    r"(?P<second>\d{2})\.(?P<millis>\d{3})\]"
)


@dataclass(frozen=True)
class SmokeResult:
    scenario: str
    duration_seconds: float
    markers: tuple[str, ...]


def _timestamp_seconds(line: str) -> float | None:
    match = TIME_PATTERN.search(line)
    if match is None:
        return None
    return (
        int(match.group("hour")) * 3600
        + int(match.group("minute")) * 60
        + int(match.group("second"))
        + int(match.group("millis")) / 1000.0
    )


def _elapsed_seconds(start: float, end: float) -> float:
    elapsed = end - start
    if elapsed < 0:
        elapsed += 24 * 3600
    return elapsed


def verify_log(
    text: str,
    scenario: str,
    minimum_session_seconds: float | None = None,
) -> SmokeResult:
    marker_specs = {
        "client-world": CLIENT_WORLD_MARKERS,
        "game-test": GAME_TEST_MARKERS,
        "dedicated-world": DEDICATED_WORLD_MARKERS,
        "network-world": NETWORK_WORLD_MARKERS,
    }.get(scenario)
    if marker_specs is None:
        raise ValueError(f"unsupported runtime smoke scenario: {scenario}")
    if minimum_session_seconds is None:
        minimum_session_seconds = {
            "client-world": 10.0,
            "game-test": 0.0,
            "dedicated-world": 5.0,
            "network-world": 10.0,
        }[scenario]

    for pattern in ABI_FAILURE_PATTERNS:
        match = re.search(pattern, text, flags=re.IGNORECASE)
        if match is not None:
            line = text.count("\n", 0, match.start()) + 1
            raise ValueError(
                f"ABI or Mixin failure matched {pattern!r} at line {line}"
            )

    lines = text.splitlines()
    marker_lines: list[int] = []
    marker_names: list[str] = []
    search_from = 0
    for name, pattern in marker_specs:
        compiled = re.compile(pattern)
        matched_line = next(
            (
                index
                for index in range(search_from, len(lines))
                if compiled.search(lines[index])
            ),
            None,
        )
        if matched_line is None:
            raise ValueError(f"missing ordered runtime marker: {name}")
        marker_lines.append(matched_line)
        marker_names.append(name)
        search_from = matched_line + 1

    session_start_name = {
        "client-world": "player login",
        "network-world": "initial player login",
    }.get(scenario, "server start")
    session_start_index = marker_names.index(session_start_name)
    start_seconds = _timestamp_seconds(lines[marker_lines[session_start_index]])
    stop_seconds = _timestamp_seconds(
        lines[marker_lines[marker_names.index("server stop")]]
    )
    if start_seconds is None or stop_seconds is None:
        raise ValueError("runtime markers do not contain parseable timestamps")

    duration = _elapsed_seconds(start_seconds, stop_seconds)
    if duration < minimum_session_seconds:
        raise ValueError(
            f"runtime session lasted {duration:.3f}s; "
            f"minimum is {minimum_session_seconds:.3f}s"
        )

    if scenario == "network-world":
        for label, login_name, logout_name in (
            (
                "initial",
                "initial player login",
                "initial player logout",
            ),
            (
                "reconnect",
                "reconnect player login",
                "reconnect player logout",
            ),
        ):
            login_seconds = _timestamp_seconds(
                lines[marker_lines[marker_names.index(login_name)]]
            )
            logout_seconds = _timestamp_seconds(
                lines[marker_lines[marker_names.index(logout_name)]]
            )
            if login_seconds is None or logout_seconds is None:
                raise ValueError(
                    f"{label} network session markers do not contain "
                    "parseable timestamps"
                )
            connected_duration = _elapsed_seconds(
                login_seconds, logout_seconds
            )
            if connected_duration < minimum_session_seconds:
                raise ValueError(
                    f"{label} network session lasted "
                    f"{connected_duration:.3f}s; minimum is "
                    f"{minimum_session_seconds:.3f}s"
                )

    return SmokeResult(scenario, duration, tuple(marker_names))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("log", type=Path)
    parser.add_argument(
        "--scenario",
        choices=(
            "client-world",
            "game-test",
            "dedicated-world",
            "network-world",
        ),
        default="client-world",
    )
    parser.add_argument(
        "--minimum-session-seconds",
        type=float,
        default=None,
    )
    arguments = parser.parse_args()

    result = verify_log(
        arguments.log.read_text(encoding="utf-8", errors="replace"),
        arguments.scenario,
        arguments.minimum_session_seconds,
    )
    print(
        f"verified {result.scenario} runtime smoke: "
        f"{result.duration_seconds:.3f}s, "
        f"{len(result.markers)} ordered markers"
    )


if __name__ == "__main__":
    main()
