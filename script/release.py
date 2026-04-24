"""Release a new track to ~/prismofeverything.

Usage:
    python script/release.py <wav-path> <cover-path>

Derives the track name from the WAV filename, stripping a trailing
Bitwig timestamp of the form " YYYY-MM-DD HHMM". The script then:

  - encodes the WAV to MP3 with ffmpeg
  - copies the cover image, preserving its real extension
  - prompts for a story on stdin (terminated by EOF / Ctrl-D)
  - writes <name>.txt with today's date, a blank line, then the story

The resulting layout is:
    ~/prismofeverything/<name>/<name>/<name>.mp3
    ~/prismofeverything/<name>/<name>/<name>.<png|jpg|jpeg>
    ~/prismofeverything/<name>/<name>/<name>.txt
"""

import datetime
import re
import shutil
import subprocess
import sys

from pathlib import Path


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
    subprocess.run(
        ['ffmpeg', '-y', '-i', str(wav_path),
         '-codec:a', 'libmp3lame', '-q:a', '2',
         str(mp3_path)],
        check=True)


def release(wav_path, cover_path):
    wav_path = wav_path.expanduser().resolve()
    cover_path = cover_path.expanduser().resolve()

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


def main():
    if len(sys.argv) != 3:
        print(__doc__, file=sys.stderr)
        raise SystemExit(2)

    release(Path(sys.argv[1]), Path(sys.argv[2]))


if __name__ == '__main__':
    main()
