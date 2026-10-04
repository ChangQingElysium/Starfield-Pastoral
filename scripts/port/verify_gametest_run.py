#!/usr/bin/env python3
"""A successful Gradle task is not proof that any GameTests ran."""

from pathlib import Path
import re
import sys


def verify(log: str, process_status: int) -> list[str]:
    errors = []
    if process_status:
        errors.append(f"Gradle exited with status {process_status}")
    if "No test batches were given!" in log:
        errors.append("no test batches were selected")
    started = re.findall(r"(\d+) tests are now running!", log)
    completed = re.findall(r"(\d+) GAME TESTS COMPLETE", log)
    if not completed or int(completed[-1]) == 0:
        errors.append("no non-empty GameTest completion summary")
    elif not started or int(started[-1]) != int(completed[-1]):
        errors.append("started/completed test counts disagree")
    failed = re.findall(r"(\d+) required tests failed", log)
    if failed and int(failed[-1]):
        errors.append(f"{failed[-1]} required tests failed")
    passed = re.findall(r"All (\d+) required tests passed", log)
    if not passed or int(passed[-1]) == 0:
        errors.append("required-test success summary is missing")
    elif completed and int(passed[-1]) > int(completed[-1]):
        errors.append("required-test success count exceeds completed tests")
    if "Failed to load datapacks" in log or "BUILD FAILED" in log:
        errors.append("runtime/build failure in log")
    if "Couldn't parse element loot_tables:" in log:
        errors.append("loot-table parse failure in log")
    fatal = (
        "Exception stopping the server", "Exception caught during firing event",
        "Exception in server tick loop", "Encountered an unexpected exception",
        "MixinTransformerError", "InjectionError", 'Exception in thread "main"',
    )
    if any(message in log for message in fatal):
        errors.append("fatal startup/runtime/shutdown error in log")
    return errors


def main() -> int:
    if len(sys.argv) != 3:
        print("Usage: verify_gametest_run.py <log> <process-status>", file=sys.stderr)
        return 2
    errors = verify(Path(sys.argv[1]).read_text(encoding="utf-8"), int(sys.argv[2]))
    if errors:
        print("GameTest verification failed: " + "; ".join(errors), file=sys.stderr)
        return 1
    print("GameTest verification passed: non-empty run completed with all required tests passing")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
