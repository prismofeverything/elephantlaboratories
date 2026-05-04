#!/usr/bin/env python3
"""Release tooling for prismofeverything.

Subcommands:

    track <wav-path> <cover-path>
        Add a new track to ~/prismofeverything. Derives the name from the
        WAV filename (stripping a trailing Bitwig " YYYY-MM-DD HHMM"),
        encodes WAV→MP3 via ffmpeg, copies the cover with its real
        extension, prompts for a story on stdin, and writes <name>.txt
        with today's date.

    sync
        rsync ~/prismofeverything to the remote server. The running server
        rescans the tracks dir on every /api/tracks request, so new tracks
        appear without a restart.

    build
        Build an uberjar from the current working tree, scp it to the
        remote, and restart the server under nohup. Mirrors the organism
        deploy pattern.

    ship
        Upload the already-built local jar and restart the server. Use
        this to retry after a broken-pipe scp without rebuilding.

    restart
        Restart the remote server using the jar already on the remote.
"""

import argparse
import datetime
import os
import re
import shlex
import shutil
import sys

from pathlib import Path


# Bypass CPython's subprocess module — go through libc system(3) (fork+exec
# /bin/sh), matching what a shell invocation would do. Two of these calls have
# previously hung the system, and we suspect subprocess module behaviour
# (posix_spawn/vfork, signal/fd inheritance) over the child commands themselves.
def run(cmd, cwd=None):
    quoted = ' '.join(shlex.quote(str(a)) for a in cmd)
    if cwd is not None:
        quoted = f'cd {shlex.quote(str(cwd))} && {quoted}'
    print('$', ' '.join(str(a) for a in cmd), file=sys.stderr)
    rc = os.system(quoted)
    if rc != 0:
        raise SystemExit(f'command failed (status={rc}): {cmd[0]}')


# ── Track creation ───────────────────────────────────────────────────────────

PRISM_DIR = Path.home() / 'prismofeverything'

# Bitwig export suffix: " 2024-03-15 2138" before the extension.
BITWIG_SUFFIX = re.compile(r'\s+\d{4}-\d{2}-\d{2}\s+\d{4}$')

COVER_EXTENSIONS = {'.png', '.jpg', '.jpeg'}


def derive_name(wav_path):
    stem = wav_path.stem
    return BITWIG_SUFFIX.sub('', stem).strip()


def cover_extension(cover_path):
    ext = cover_path.suffix.lower()
    if ext == '.jpe':
        ext = '.jpeg'
    if ext not in COVER_EXTENSIONS:
        raise SystemExit(f'unsupported cover extension: {ext} (want one of {sorted(COVER_EXTENSIONS)})')
    return ext


def prompt_story():
    print('Enter story (end with Ctrl-D on its own line):', file=sys.stderr)
    story = sys.stdin.read().strip()
    if not story:
        raise SystemExit('story is empty — aborting')
    return story


def encode_mp3(wav_path, mp3_path):
    run(['ffmpeg', '-y', '-i', str(wav_path),
         '-codec:a', 'libmp3lame', '-q:a', '2',
         str(mp3_path)])


def cmd_track(args):
    wav_path = Path(args.wav).expanduser().resolve()
    cover_path = Path(args.cover).expanduser().resolve()

    if not wav_path.is_file():
        raise SystemExit(f'wav not found: {wav_path}')
    if not cover_path.is_file():
        raise SystemExit(f'cover not found: {cover_path}')

    name = derive_name(wav_path)
    if not name:
        raise SystemExit(f'could not derive a track name from {wav_path.name}')

    cover_ext = cover_extension(cover_path)

    track_root = PRISM_DIR / name / name
    if track_root.exists():
        raise SystemExit(f'track already exists: {track_root}')

    story = prompt_story()

    track_root.mkdir(parents=True)

    encode_mp3(wav_path, track_root / f'{name}.mp3')
    shutil.copyfile(cover_path, track_root / f'{name}{cover_ext}')

    today = datetime.date.today().isoformat()
    (track_root / f'{name}.txt').write_text(f'{today}\n\n{story}\n')

    print(f'released: {track_root}')


# ── Push to remote server ────────────────────────────────────────────────────

REMOTE_HOST = 'ryan@elephantlaboratories.com'
REMOTE_APP_DIR = '~/elephantlaboratories'
REMOTE_TRACKS_DIR = '/home/ryan/prismofeverything'
REMOTE_JAR = f'{REMOTE_APP_DIR}/elephantlaboratories.jar'
REMOTE_LOG = f'{REMOTE_APP_DIR}/elephantlaboratories.log'
REMOTE_PID = f'{REMOTE_APP_DIR}/elephantlaboratories.pid'

REPO_ROOT = Path(__file__).resolve().parent.parent
LOCAL_JAR = REPO_ROOT / 'target' / 'uberjar' / 'elephantlaboratories.jar'


def sync_tracks():
    print('=== Syncing tracks to remote ===', file=sys.stderr)
    src = str(PRISM_DIR) + '/'
    dst = f'{REMOTE_HOST}:{REMOTE_TRACKS_DIR}/'
    run(['rsync', '-avz', src, dst])


def build_jar():
    print('=== Building uberjar ===', file=sys.stderr)
    run(['lein', 'uberjar'], cwd=str(REPO_ROOT))
    if not LOCAL_JAR.is_file():
        raise SystemExit(f'expected jar not produced: {LOCAL_JAR}')
    size_mb = LOCAL_JAR.stat().st_size / (1024 * 1024)
    print(f'built: {LOCAL_JAR} ({size_mb:.1f} MB)', file=sys.stderr)


def ship_jar():
    print('=== Uploading jar to remote ===', file=sys.stderr)
    if not LOCAL_JAR.is_file():
        raise SystemExit(f'no local jar to ship: {LOCAL_JAR} (run `release.py build` first)')
    run(['ssh', REMOTE_HOST, f'mkdir -p {REMOTE_APP_DIR}'])
    run(['scp', str(LOCAL_JAR), f'{REMOTE_HOST}:{REMOTE_JAR}'])


def restart_server():
    print('=== Restarting server ===', file=sys.stderr)
    # Mirror organism's pattern: kill the old PID if running, then nohup the new jar.
    remote_script = f'''
set -e
cd {REMOTE_APP_DIR}
if [ -f elephantlaboratories.pid ]; then
  kill $(cat elephantlaboratories.pid) 2>/dev/null || true
  rm -f elephantlaboratories.pid
  sleep 2
fi
nohup java -Dclojure.main.report=stderr \\
  -cp elephantlaboratories.jar clojure.main \\
  -m elephantlaboratories.core \\
  > elephantlaboratories.log 2>&1 &
echo $! > elephantlaboratories.pid
sleep 3
if kill -0 $(cat elephantlaboratories.pid) 2>/dev/null; then
  echo "Server started (pid $(cat elephantlaboratories.pid))"
else
  echo "ERROR: server failed to start. Last log lines:"
  tail -30 elephantlaboratories.log
  exit 1
fi
'''
    run(['ssh', REMOTE_HOST, f'bash -lc {shell_quote(remote_script)}'])


def shell_quote(s):
    # Single-quote for bash, escaping any embedded single quotes.
    return "'" + s.replace("'", "'\\''") + "'"


def cmd_sync(args):
    sync_tracks()


def cmd_build(args):
    build_jar()
    ship_jar()
    restart_server()


def cmd_ship(args):
    ship_jar()
    restart_server()


def cmd_restart(args):
    restart_server()


# ── CLI ──────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest='cmd', required=True)

    p_track = sub.add_parser('track', help='create a new track locally')
    p_track.add_argument('wav', help='path to source WAV')
    p_track.add_argument('cover', help='path to cover image (png/jpg/jpeg)')
    p_track.set_defaults(func=cmd_track)

    p_sync = sub.add_parser('sync', help='rsync tracks to remote (no restart needed)')
    p_sync.set_defaults(func=cmd_sync)

    p_build = sub.add_parser('build', help='build uberjar, ship it, restart server')
    p_build.set_defaults(func=cmd_build)

    p_ship = sub.add_parser('ship', help='upload existing local jar and restart server')
    p_ship.set_defaults(func=cmd_ship)

    p_restart = sub.add_parser('restart', help='restart the remote server')
    p_restart.set_defaults(func=cmd_restart)

    args = parser.parse_args()
    args.func(args)


if __name__ == '__main__':
    main()
