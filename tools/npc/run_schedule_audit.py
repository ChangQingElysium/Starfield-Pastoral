#!/usr/bin/env python3
"""Run two normal-length game days on an isolated copy of an offline save."""
import argparse
import datetime
import fcntl
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--save', type=Path, required=True, help='Offline save or a consistent backup; never modified')
    parser.add_argument('--port', type=int, default=25575)
    args = parser.parse_args()
    source = args.save.resolve()
    if not (source / 'level.dat').is_file():
        parser.error('--save must contain level.dat')
    root = Path(__file__).resolve().parents[2]
    stamp = datetime.datetime.now().strftime('%Y%m%d-%H%M%S-%f')
    run = root / 'build' / 'npc-schedule-audits' / stamp
    run.mkdir(parents=True)
    # Minecraft uses an exclusive lock on session.lock. Hold it while copying so
    # a running server cannot mutate the source halfway through the snapshot.
    with (source / 'session.lock').open('rb') as lock:
        try:
            fcntl.lockf(lock, fcntl.LOCK_SH | fcntl.LOCK_NB)
        except BlockingIOError:
            parser.error('The source save is open. Use an offline save or a consistent backup.')
        shutil.copytree(source, run / 'world', ignore=shutil.ignore_patterns('session.lock'))
    (run / 'eula.txt').write_text('eula=true\n')
    (run / 'server.properties').write_text(
        f'level-name=world\nserver-ip=127.0.0.1\nserver-port={args.port}\n'
        'online-mode=false\nview-distance=12\nsimulation-distance=12\n'
        'enable-rcon=false\nspawn-protection=0\n')
    report = run / 'report.json'
    log = run / 'server.log'
    command = [str(root / 'gradlew'), 'runServer', f'-PserverRunDir={run}',
               '-PnpcHeadlessScheduleTest=true', '-PnpcScheduleAudit=true',
               f'-PnpcScheduleAuditReport={report}', '--no-daemon', '--console=plain']
    print(f'Audit copy: {run}\nLog: {log}', flush=True)
    with log.open('w') as output:
        result = subprocess.run(command, cwd=root, stdout=output, stderr=subprocess.STDOUT,
                                env=os.environ.copy())
    if result.returncode or not report.is_file():
        print(f'Server failed; inspect {log}', file=sys.stderr)
        return 2
    data = json.loads(report.read_text())
    problems = {actor: state['issues'] for actor, state in data['actors'].items() if state['issues']}
    observed = sum(state['samples'] > 0 for state in data['actors'].values())
    print(f"Status: {data['status']}; observed: {observed}/{len(data['actors'])}; actors with issues: {len(problems)}")
    for actor, issues in problems.items():
        print(f'  {actor}: ' + ', '.join(issues))
    print(f'Report: {report}')
    return 0 if data['status'] == 'complete' and not data['fatal'] and not problems else 1


if __name__ == '__main__':
    sys.exit(main())
