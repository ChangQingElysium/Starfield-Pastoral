#!/usr/bin/env python3
"""Install and launch the isolated Fabric development client, without account secrets."""
from __future__ import annotations

import argparse
import fcntl
import hashlib
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import urllib.request
import zipfile

FABRIC = Path(__file__).resolve().parent
ROOT = FABRIC.parent
LOCAL = FABRIC / '.local'
MODS = LOCAL / 'client-mods'
GAME = FABRIC / 'run-client'
LOCK = FABRIC / 'client-mods.lock.json'
DEFAULT_OPTIONS = {
    'lang': 'zh_cn', 'guiScale': '4', 'renderDistance': '21',
    'simulationDistance': '18', 'maxFps': '140', 'enableVsync': 'true',
    'fullscreen': 'false', 'overrideWidth': '1280', 'overrideHeight': '720',
    'autoJump': 'false',
}


def require_idle() -> None:
    # Inspect locally, never print process arguments (launchers may carry credentials).
    # ucomm is the executable's short name, even if its full path contains spaces.
    processes = subprocess.run(['/bin/ps', '-axo', 'pid=,ucomm=,args='],
                               check=True, text=True, errors='replace', capture_output=True).stdout
    for line in processes.splitlines():
        parts = line.strip().split(None, 2)
        if len(parts) == 3 and is_client_process(parts[1], parts[2]):
            raise RuntimeError('Minecraft 客户端已经运行，请先退出游戏，再启动 Fabric。')


def is_client_process(executable: str, arguments: str) -> bool:
    if executable != 'java':
        return False
    if any(main in arguments for main in ('net.fabricmc.loader.impl.launch.knot.KnotClient',
                                          'net.minecraft.client.main.Main')):
        return True
    if 'net.fabricmc.devlaunchinjector.Main' in arguments:
        return '-Dfabric.dli.env=client' in arguments
    if any(main in arguments for main in ('net.neoforged.devlaunch.Main',
                                          'cpw.mods.bootstraplauncher.BootstrapLauncher',
                                          'cpw.mods.modlauncher.Launcher')):
        return any(marker in arguments.lower() for marker in
                   ('clientrunprogramargs.txt', 'neoforgeclient', 'forgeclient', 'fmlclient'))
    return False


def java_home() -> Path:
    candidates = [Path(os.environ['JAVA_HOME'])] if os.environ.get('JAVA_HOME') else []
    candidates += [Path.home() / '.jdks/temurin-21/Contents/Home']
    candidates += sorted((Path.home() / '.gradle/jdks').glob('*/**/Contents/Home'))
    for home in candidates:
        binary = home / 'bin/java'
        if not binary.is_file():
            continue
        version = subprocess.run([str(binary), '-version'], text=True, capture_output=True)
        if version.returncode == 0 and 'version "21.' in version.stderr:
            return home
    raise RuntimeError('未找到 Java 21；请设置 JAVA_HOME 为 Java 21 的 Contents/Home。')


def read_lock() -> dict:
    manifest = json.loads(LOCK.read_text())
    if manifest['minecraft'] != '1.21.1':
        raise RuntimeError('测试模组锁文件的 Minecraft 版本不匹配。')
    return manifest


def verified(path: Path, mod: dict) -> bool:
    if not path.is_file() or path.stat().st_size != mod['size']:
        return False
    return hashlib.sha512(path.read_bytes()).hexdigest() == mod['sha512']


def install(manifest: dict) -> None:
    MODS.mkdir(parents=True, exist_ok=True)
    for mod in manifest['mods']:
        target = MODS / mod['filename']
        if not verified(target, mod):
            print(f"下载 {mod['slug']} {mod['version_number']}", flush=True)
            request = urllib.request.Request(mod['url'], headers={'User-Agent': 'StardewCraft-Fabric-DevSetup/1.0'})
            with tempfile.NamedTemporaryFile(dir=MODS, suffix='.download', delete=False) as output:
                temporary = Path(output.name)
                try:
                    with urllib.request.urlopen(request, timeout=90) as response:
                        while chunk := response.read(1024 * 1024):
                            output.write(chunk)
                    output.flush()
                    if not verified(temporary, mod):
                        raise RuntimeError(f"下载校验失败：{mod['filename']}")
                    os.replace(temporary, target)
                finally:
                    temporary.unlink(missing_ok=True)
        print(f"已核对 {mod['slug']} {mod['version_number']}", flush=True)
    nested = nested_libraries(manifest, extract=True)
    (MODS / 'nested-lock.json').write_text(json.dumps(nested, ensure_ascii=False, indent=2) + '\n')


def nested_libraries(manifest: dict, *, extract: bool) -> list[dict]:
    """Loom strips outer jars[] metadata; explicitly provide the trusted nested closure."""
    found: dict[str, dict] = {}
    identities: dict[str, str] = {}
    provided: set[str] = set()

    def collect_provided(data: bytes) -> None:
        with zipfile.ZipFile(io.BytesIO(data)) as jar:
            if 'fabric.mod.json' not in jar.namelist():
                return
            metadata = json.loads(jar.read('fabric.mod.json'))
            provided.add(metadata['id'])
            for child in metadata.get('jars', []):
                collect_provided(jar.read(child['file']))

    for mod in manifest['mods']:
        if mod.get('project_runtime_provided'):
            collect_provided((MODS / mod['filename']).read_bytes())

    def visit(data: bytes, owner: str) -> None:
        with zipfile.ZipFile(io.BytesIO(data)) as jar:
            if 'fabric.mod.json' not in jar.namelist():
                return
            metadata = json.loads(jar.read('fabric.mod.json'))
            for child in metadata.get('jars', []):
                embedded = jar.read(child['file'])
                digest = hashlib.sha256(embedded).hexdigest()
                if digest in found:
                    continue
                with zipfile.ZipFile(io.BytesIO(embedded)) as nested:
                    meta = json.loads(nested.read('fabric.mod.json')) if 'fabric.mod.json' in nested.namelist() else None
                identity = meta['id'] if meta else Path(child['file']).name
                if identity in provided:
                    continue
                if identity in identities and identities[identity] != digest:
                    raise RuntimeError(f'内嵌库 ID 冲突：{identity}')
                identities[identity] = digest
                relative = 'nested/' + digest + '.jar'
                target = MODS / relative
                record = {'filename': relative, 'id': identity, 'fabric_mod': meta is not None,
                          'sha256': digest, 'sha512': hashlib.sha512(embedded).hexdigest(),
                          'size': len(embedded), 'owner': owner}
                if extract:
                    target.parent.mkdir(exist_ok=True)
                    if not verified(target, record):
                        target.write_bytes(embedded)
                elif not verified(target, record):
                    raise RuntimeError(f'内嵌库缺失或校验失败：{identity}；请运行 install。')
                found[digest] = record
                visit(embedded, identity)

    for mod in manifest['mods']:
        if not mod.get('project_runtime_provided'):
            visit((MODS / mod['filename']).read_bytes(), mod['slug'])
    return list(found.values())


def profile() -> None:
    GAME.mkdir(parents=True, exist_ok=True)
    config = GAME / 'config'
    config.mkdir(exist_ok=True)
    options = GAME / 'options.txt'
    if not options.exists():
        options.write_text(''.join(f'{key}:{value}\n' for key, value in DEFAULT_OPTIONS.items()))
    iris = config / 'iris.properties'
    if not iris.exists():
        iris.write_text('enableShaders=false\n')
    # User worlds, resource packs, shader packs and later settings are never overwritten.
    for folder in ('mods', 'saves', 'resourcepacks', 'shaderpacks'):
        (GAME / folder).mkdir(exist_ok=True)


def inspect(manifest: dict) -> None:
    rows = []
    ids: dict[str, str] = {}
    for mod in manifest['mods']:
        path = MODS / mod['filename']
        if not verified(path, mod):
            raise RuntimeError(f"模组缺失或 SHA512 不匹配：{path.name}")
        with zipfile.ZipFile(path) as jar:
            meta = json.loads(jar.read('fabric.mod.json'))
        if meta['id'] in ids:
            raise RuntimeError(f"模组 ID 重复：{meta['id']}")
        ids[meta['id']] = path.name
        rows.append({'id': meta['id'], 'version': meta['version'], 'file': path.name,
                     'sha512': mod['sha512'], 'depends': meta.get('depends', {})})
    nested = nested_libraries(manifest, extract=False)
    recorded = json.loads((MODS / 'nested-lock.json').read_text())
    if recorded != nested:
        raise RuntimeError('内嵌库索引与已核对的原始 JAR 不匹配；请重新运行 install。')
    # Loom loads these remapped JARs from the classpath. A second copy in mods/ is unsafe.
    unexpected = sorted(p.name for p in (GAME / 'mods').glob('*.jar'))
    if unexpected:
        raise RuntimeError('run-client/mods 中发现额外 JAR，请移走后再启动，避免重复加载：' + ', '.join(unexpected))
    report = {'minecraft': manifest['minecraft'], 'loader': manifest['loader'],
              'loader_version': manifest['loader_version'],
              'game_directory': str(GAME), 'java_home': str(java_home()),
              'installed': rows, 'nested_libraries': nested, 'window_launched': False}
    destination = FABRIC / 'build/reports/client-environment-install.json'
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    print(f'Fabric 辅助模组 {len(rows)} 个、内嵌库 {len(nested)} 个，全部通过文件哈希与元数据检查。', flush=True)


def gradle(*tasks: str) -> None:
    environment = os.environ.copy()
    environment['JAVA_HOME'] = str(java_home())
    command = [str(ROOT / 'gradlew'), '-p', str(FABRIC), *tasks,
               '-PfabricClientTestMods=true', '--console=plain']
    if 'runClient' not in tasks:
        subprocess.run(command, cwd=ROOT, env=environment, check=True)
        return
    # Failures before Minecraft initializes have no latest.log yet. Preserve
    # Java/Gradle output while streaming it to the user's existing terminal.
    log = GAME / 'logs/launcher.log'
    log.parent.mkdir(parents=True, exist_ok=True)
    print(f'启动阶段日志：{log}', flush=True)
    with log.open('w', encoding='utf-8') as output:
        with subprocess.Popen(command, cwd=ROOT, env=environment, stdout=subprocess.PIPE,
                              stderr=subprocess.STDOUT, text=True, encoding='utf-8',
                              errors='replace', bufsize=1) as child:
            for line in child.stdout:
                sys.stdout.write(line)
                sys.stdout.flush()
                output.write(line)
                output.flush()
            result = child.wait()
        if result:
            raise RuntimeError(f'启动失败（退出码 {result}），请查看 {log}')


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=('install', 'check', 'prepare', 'verify-launch', 'launch'), nargs='?', default='launch')
    parser.add_argument('--debug', action='store_true', help='启动后等待本机 IDE 连接 Java 调试端口 5005')
    args = parser.parse_args()
    if args.debug and args.action != 'launch':
        parser.error('--debug 只适用于 launch')
    LOCAL.mkdir(parents=True, exist_ok=True)
    with (LOCAL / 'client-launch.lock').open('w') as guard:
        try:
            fcntl.flock(guard, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise RuntimeError('Fabric 启动或安装任务正在运行，请勿重复启动。')
        require_idle()
        manifest = read_lock()
        if args.action != 'check':
            install(manifest)
            profile()
        inspect(manifest)
        if args.action == 'prepare':
            gradle('checkFabricClientSnapshot')
            print('启动资源与冻结快照检查完成，未启动游戏窗口。')
        elif args.action == 'verify-launch':
            gradle('runClient', '-PfabricClientCommandProbe=true')
            print('真实启动任务与 Java 命令检查完成；探针已在 Minecraft 运行前退出。')
        elif args.action == 'launch':
            print('启动 Fabric 1.21.1；游戏使用独立存档，保留此终端直到游戏退出。', flush=True)
            gradle('runClient', *(['-PfabricClientDebug=true'] if args.debug else []))


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, OSError, ValueError, subprocess.CalledProcessError) as error:
        print(f'Fabric：{error}', file=sys.stderr)
        sys.exit(1)
