#!/usr/bin/env python3
"""Build the web-ready ORGANISM art from the source pieces repo.

The originals in ../organism/pieces are print-resolution (3–12 MB each,
up to 4801x4801). This resizes and re-encodes them into
resources/public/img/organism/ at sizes the site actually serves.

    ./script/organism_assets.py            build anything out of date
    ./script/organism_assets.py --force    rebuild everything
    ./script/organism_assets.py --list     show the manifest, build nothing

Source lives outside this repo; point at it with $ORGANISM_PIECES if it
isn't at ../organism/pieces. Images with real transparency are written as
PNG, everything else as JPEG — the art is flat and large, and JPEG at
quality 88 is a fraction of the size with no visible loss at these
dimensions.

Re-run it whenever the pieces repo regenerates its renders; it only
rebuilds what changed.
"""

import argparse
import os
import shlex
import shutil
import sys

from pathlib import Path

from PIL import Image, ImageDraw

try:
    import numpy
except ImportError:
    numpy = None


REPO_ROOT = Path(__file__).resolve().parent.parent
DEST = REPO_ROOT / 'resources' / 'public' / 'img' / 'organism'
# The trimmed, transparent cut-outs are worth keeping as a library in their
# own right — reusable anywhere, not just at the sizes this site happens to
# serve. They live under assets/ (full size) and assets/thumbs/ (grid size).
CUTOUTS = DEST / 'assets'
THUMBS = CUTOUTS / 'thumbs'
SOURCE = Path(os.environ.get(
    'ORGANISM_PIECES', REPO_ROOT.parent / 'organism' / 'pieces')).expanduser()
# The print masters. Everything under here has its true physical size baked in
# and is what actually went to the printer, so it is the authority for any
# component art — earlier intermediates elsewhere in the repo are stale.
PRINT_READY = SOURCE / 'prototype' / 'print_ready'
PHOTOS = Path(os.environ.get('ORGANISM_PHOTOS', Path.home() / 'Downloads')).expanduser()

JPEG_QUALITY = 88

# The 26 mutation cards. The names are printed on the card faces; this maps
# each source file to the name so the site can label them.
MUTATIONS = [
    ('card_00a', 'Accelerate'), ('card_00b', 'Avenge'),
    ('card_01a', 'Combust'),    ('card_01b', 'Commune'),
    ('card_02a', 'Drink'),      ('card_02b', 'Echo'),
    ('card_03a', 'Expand'),     ('card_03b', 'Jump'),
    ('card_04a', 'Merge'),      ('card_04b', 'Persist'),
    ('card_05a', 'Pulse'),      ('card_05b', 'Push'),
    ('card_06a', 'Rain'),       ('card_06b', 'Regenerate'),
    ('card_07a', 'Skip'),       ('card_07b', 'Project'),
    ('card_08a', 'Synchronize'),('card_08b', 'Slide'),
    ('card_09a', 'Contract'),   ('card_09b', 'Warp'),
    ('card_10a', 'Pillar'),     ('card_10b', 'Metamorphose'),
    ('card_11a', 'Immolate'),   ('card_11b', 'Inherit'),
    ('card_12a', 'Reclaim'),    ('card_12b', 'Transmute')]

PLAYER_COLORS = ['red', 'green', 'blue', 'yellow', 'purple']


def slug(name):
    return name.lower().replace(' ', '-')


# print_ready holds PDFs, not images. Render a page to PNG at a chosen pixel
# width with pdftoppm (poppler), which is already on the box.
def render_pdf(source, dest, width, page=1):
    dest.parent.mkdir(parents=True, exist_ok=True)
    stem = dest.with_suffix('')
    quoted = ' '.join(shlex.quote(str(a)) for a in [
        'pdftoppm', '-png', '-scale-to-x', str(width), '-scale-to-y', '-1',
        '-f', str(page), '-l', str(page), str(source), str(stem)])
    if os.system(quoted) != 0:
        raise SystemExit(f'pdftoppm failed on {source}')
    # pdftoppm appends a page suffix whose width depends on the page count
    produced = sorted(stem.parent.glob(stem.name + '-*.png'))
    if not produced:
        raise SystemExit(f'pdftoppm produced nothing for {source}')
    produced[0].replace(dest)
    for leftover in produced[1:]:
        leftover.unlink()
    return dest


def manifest():
    """(source, destination, longest edge in px) for everything the site uses."""
    items = [
        # The box — cover art carries the whole identity, so it appears at
        # hero size, at card size on the home page, and as a page backdrop.
        ('clip_assets/box_top.png',      'box-cover.jpg',      1400),
        ('clip_assets/box_top.png',      'box-cover-500.jpg',   500),
        ('clip_assets/box_wrap.png',     'box-wrap.jpg',       1600),

        # Boards, aids and the box now come from print_ready (see
        # PRINT_MASTERS below) — these older intermediates were superseded.

        # Table renders — the game as it actually looks in play
        ('scene/intro_3q_stack.png',     'scene-organisms.jpg', 1200),
        ('scene/intro_3q_food.png',      'scene-food.jpg',      1200),
        ('scene/abs_150.png',            'scene-board-dark.jpg',1200),
        ('scene/align_43.png',           'assets/board-render.png', 1000, 'circle'),
        ('scene/contact_sheet.png',      'scene-states.jpg',    1400),

        # The sculpted elements. Each sculpt_* file is a strip of three
        # views (side, top, angled); view 1 is the top-down silhouette, which
        # is the same shape the game prints on its cards and turn aid.
        ('renders/sculpt_EAT_sculpt_graft_overview.png',
                                'assets/piece-eat.png',   600, ('view', 1)),
        ('renders/sculpt_GROW_sculpt_graft_overview.png',
                                'assets/piece-grow.png',  600, ('view', 1)),
        ('renders/sculpt_MOVE_sculpt_graft_overview.png',
                                'assets/piece-move.png',  600, ('view', 1)),
        ('renders/sculpt_EAT_sculpt_graft_overview.png',
                                'assets/piece-eat-side.png',  500, ('view', 2)),
        ('renders/sculpt_GROW_sculpt_graft_overview.png',
                                'assets/piece-grow-side.png', 500, ('view', 2)),
        ('renders/sculpt_MOVE_sculpt_graft_overview.png',
                                'assets/piece-move-side.png', 500, ('view', 2)),
        ('scene/food_iso_top.png',       'assets/piece-food.png', 700, 'knockout'),
        ('renders/grafts_overview.png',  'piece-grafts.jpg',      900),
    ]

    for color in PLAYER_COLORS:
        items.append((f'layout_assets/plats/{color}.png',
                      f'assets/plat-{color}.png', 540, 'circle'))

    for source_name, name in MUTATIONS:
        items.append((f'layout_assets/cards/{source_name}.png',
                      f'assets/mutation-{slug(name)}.png', 1200, 'card'))
        items.append((f'layout_assets/cards/{source_name}.png',
                      f'assets/thumbs/mutation-{slug(name)}.png', 320, 'card'))

    return [item if len(item) == 4 else item + (None,) for item in items]


# Almost every ORGANISM component is a disc — the board, the player plateaus,
# the power board, all 26 mutation cards. The source files disagree about how
# they present that disc: some sit on white, some on transparency, and the
# card art is laid out for print so each file carries a crescent of the
# neighbouring card at one edge and is not centred the same way twice.
#
# Find the run of content straight through the middle of the image (immune to
# a detached sliver at the edge), take that as the disc, crop to it and clear
# everything outside to transparent. The components then drop onto a light or
# a dark section equally well.
# What counts as background differs per source: some files are already cut
# out, some sit on white, and the 3D renders sit on a flat studio grey. Take
# the alpha if there is any, otherwise sample the corners and treat whatever
# uniform colour they agree on as the backdrop.
def content_mask(rgba):
    array = numpy.array(rgba)
    if array[:, :, 3].min() < 250:
        return array[:, :, 3] > 16

    pixels = array[:, :, :3].astype(int)
    corners = numpy.array([pixels[0, 0], pixels[0, -1],
                           pixels[-1, 0], pixels[-1, -1]])
    if corners.ptp(axis=0).max() > 24:          # corners disagree — assume white
        return ~(pixels > 240).all(axis=2)
    background = corners.mean(axis=0)
    return numpy.abs(pixels - background).max(axis=2) > 24


def trim_to_circle(image, layout='cutout'):
    """Crop to the component's disc and clear everything outside it.

    Two kinds of source, and guessing between them does not work — a card's
    sliver is small enough to look square, so it has to be declared:

    'cutout'  an already-isolated component (the board, a plateau, the power
              board, a studio render). Its content bounding box *is* the disc,
              and taking it whole tolerates the gaps between the board's hexes.

    'card'    one card off a print sheet, which carries a crescent of its
              neighbour at one edge. Take the longest run of content through
              the middle of the image: the card measures ~1420px across, the
              crescent ~18px, so the card wins and the crescent is dropped.
    """
    if numpy is None:
        return image

    rgba = image.convert('RGBA')
    content = content_mask(rgba)
    height, width = content.shape

    if layout == 'card':
        def longest_run(mask):
            best = (0, 0, -1)                       # length, start, end
            start = None
            for index, filled in enumerate(mask):
                if filled and start is None:
                    start = index
                elif not filled and start is not None:
                    if index - start > best[0]:
                        best = (index - start, start, index - 1)
                    start = None
            if start is not None and len(mask) - start > best[0]:
                best = (len(mask) - start, start, len(mask) - 1)
            return best

        _, x0, x1 = longest_run(content[height // 2])
        _, y0, y1 = longest_run(content[:, width // 2])
    else:
        rows = numpy.where(content.any(axis=1))[0]
        columns = numpy.where(content.any(axis=0))[0]
        if len(rows) == 0 or len(columns) == 0:
            return image
        x0, x1 = int(columns[0]), int(columns[-1])
        y0, y1 = int(rows[0]), int(rows[-1])

    if x1 - x0 < width * 0.4 or y1 - y0 < height * 0.4:
        return image                                # not a disc — leave it

    diameter = min(x1 - x0, y1 - y0) + 1
    cx, cy = (x0 + x1) // 2, (y0 + y1) // 2
    box = (cx - diameter // 2, cy - diameter // 2,
           cx - diameter // 2 + diameter, cy - diameter // 2 + diameter)
    disc = rgba.crop(box)

    scale = 4                                               # smooth rim
    inset = max(2, round(diameter * 0.004)) * scale
    mask = Image.new('L', (diameter * scale, diameter * scale), 0)
    ImageDraw.Draw(mask).ellipse(
        (inset, inset, diameter * scale - 1 - inset, diameter * scale - 1 - inset),
        fill=255)
    mask = mask.resize((diameter, diameter), Image.LANCZOS)

    existing = disc.getchannel('A')
    mask = Image.fromarray(
        numpy.minimum(numpy.array(mask), numpy.array(existing)).astype('uint8'))
    disc.putalpha(mask)
    return disc


# For things that are not discs — a sculpted element, a food token seen in
# perspective — just drop the flat studio backdrop and crop to what is left.
def knockout(image):
    if numpy is None:
        return image
    rgba = image.convert('RGBA')
    content = content_mask(rgba)
    if not content.any():
        return image
    array = numpy.array(rgba)
    array[:, :, 3] = numpy.where(content, array[:, :, 3], 0)
    result = Image.fromarray(array)
    rows, cols = numpy.where(content)
    return result.crop((cols.min(), rows.min(), cols.max() + 1, rows.max() + 1))


# The sculpted elements ship three views to a strip (side, top, angled), each
# already cut out. Split on the empty columns between them and take one.
def extract_view(image, index):
    if numpy is None:
        return image
    rgba = image.convert('RGBA')
    content = content_mask(rgba)
    occupied = content.any(axis=0)

    runs, start = [], None
    for column, filled in enumerate(occupied):
        if filled and start is None:
            start = column
        elif not filled and start is not None:
            runs.append((start, column - 1))
            start = None
    if start is not None:
        runs.append((start, len(occupied) - 1))
    if index >= len(runs):
        return knockout(image)

    x0, x1 = runs[index]
    rows = numpy.where(content[:, x0:x1 + 1].any(axis=1))[0]
    array = numpy.array(rgba)
    array[:, :, 3] = numpy.where(content, array[:, :, 3], 0)
    return Image.fromarray(array).crop((x0, rows.min(), x1 + 1, rows.max() + 1))


def has_transparency(image):
    if image.mode not in ('RGBA', 'LA', 'P'):
        return False
    if image.mode == 'P':
        return 'transparency' in image.info
    return image.getchannel('A').getextrema()[0] < 255


def convert(source, dest, longest, mode=None):
    image = Image.open(source)
    if mode == 'circle':
        image = trim_to_circle(image)
    elif mode == 'card':
        image = trim_to_circle(image, layout='card')
    elif mode == 'knockout':
        image = knockout(image)
    elif isinstance(mode, tuple) and mode[0] == 'view':
        image = extract_view(image, mode[1])
    image.thumbnail((longest, longest), Image.LANCZOS)

    if dest.suffix == '.png':
        if has_transparency(image):
            # Flat, poster-like art: a 256-colour palette is visually
            # indistinguishable here and about a quarter of the bytes. The
            # palette covers alpha too, which leaves ~40 levels of it —
            # plenty for a smooth rim now that the disc is inset off the
            # white surround it was cut from.
            image = image.convert('RGBA').quantize(
                colors=256, method=Image.FASTOCTREE, dither=Image.NONE)
        elif image.mode not in ('RGB', 'P'):
            image = image.convert('RGB')
        image.save(dest, 'PNG', optimize=True)
    else:
        if image.mode in ('RGBA', 'LA', 'P'):
            # flatten onto white so the JPEG doesn't get a black halo
            flat = Image.new('RGB', image.size, (255, 255, 255))
            rgba = image.convert('RGBA')
            flat.paste(rgba, mask=rgba.getchannel('A'))
            image = flat
        elif image.mode != 'RGB':
            image = image.convert('RGB')
        image.save(dest, 'JPEG', quality=JPEG_QUALITY, optimize=True,
                   progressive=True)
    return image.size


# The page header wants a wide, dark backdrop, matching how the Sol pages use
# bkg_solworld_*.jpg. Crop a band out of the box art and dim it so white
# headline text stays legible.
def build_backdrop(force):
    source = SOURCE / 'clip_assets' / 'box_top.png'
    dest = DEST / 'bkg-organism.jpg'
    if not source.is_file():
        return None
    if not force and dest.is_file() and dest.stat().st_mtime >= source.stat().st_mtime:
        return None

    image = Image.open(source).convert('RGB')
    width, height = image.size
    # The cover carries the title along the top and the credits along the
    # bottom. This band starts just below "RYAN SPANGLER" and takes the
    # richest stretch of the painting — the teal disc, the coral and purple
    # blobs, the green fronds and the big yellow sweep.
    #
    # Deliberately NOT darkened. The art is the identity and it should read at
    # full strength; the header's text contrast is a scrim in styles.css,
    # which can be tuned without rebuilding the image.
    top = int(height * 0.24)
    band = int(width * 0.42)
    image = image.crop((0, top, width, top + band))
    image.thumbnail((2500, 2500), Image.LANCZOS)
    image.save(dest, 'JPEG', quality=86, optimize=True, progressive=True)
    return dest


# The site's masthead slot is a wide banner. ORGANISM has no standalone
# wordmark file — the title lives on the box — so lift the title band off the
# cover art rather than squashing the square cover into a 3.7:1 box.
def build_masthead(force):
    source = SOURCE / 'clip_assets' / 'box_top.png'
    dest = DEST / 'masthead.jpg'
    if not source.is_file():
        return None
    if not force and dest.is_file() and dest.stat().st_mtime >= source.stat().st_mtime:
        return None

    image = Image.open(source).convert('RGB')
    width, height = image.size
    image = image.crop((0, int(height * 0.02), width, int(height * 0.30)))
    image.thumbnail((1400, 1400), Image.LANCZOS)
    image.save(dest, 'JPEG', quality=90, optimize=True, progressive=True)
    return dest


# ── Print masters, photographs and the rule clips ────────────────────────────

# Straight off the print files, so the site shows the component that was
# actually printed rather than an earlier intermediate render.
PRINT_MASTERS = [
    ('02_CARDSTOCK/player_aid_190mm_print5.pdf', 'assets/player-aid.png',     1400, 1),
    ('01_MOUNT_chipboard/main_board_HEX_540mm_FULL.pdf',
                                               'assets/board-hex.png',        1600, 1),
    ('01_MOUNT_chipboard/main_board_PENT_540mm_FULL.pdf',
                                               'assets/board-pent.png',       1600, 1),
    ('01_MOUNT_chipboard/power_score_board_165mm.pdf',
                                               'assets/power-board.png',      1200, 1),
    ('03_BOX/box_top_VECTOR.pdf',              'box-cover.jpg',               1400, 1),
    ('03_BOX/box_top_VECTOR.pdf',              'box-cover-500.jpg',            500, 1),
    ('03_BOX/box_wrap_457mm.pdf',              'box-wrap.jpg',                1600, 1),
]

# Photographs of the built game. These beat any render for showing what the
# thing actually is, so they lead the pages.
PHOTOGRAPHS = [
    ('organism-closeup-xlarge.jpg',                 'photo-closeup.jpg',  1400),
    ('26c716cd7401a0ff746729376ee39c5b-xlarge.jpg', 'photo-table.jpg',    1200),
    ('ce9c89c2272471bfccb24ed4c36595b2-xlarge.jpg', 'photo-ryan.jpg',     1200),
]

# The EAT / GROW / MOVE glyphs, white on transparency. The site recolours them
# per action with a CSS mask, so one file serves every colour.
SYMBOLS = ['eat', 'grow', 'move']

# The rule clips, in the order a rulebook would teach them.
RULE_CLIPS = [
    ('clip_board',      'The board',    'Concentric rings of hexes, from the contested centre to the rim.'),
    ('clip_eat',        'EAT',          'Draw food in from the space an element occupies.'),
    ('clip_grow',       'GROW',         'Spend food to add an element to the organism.'),
    ('clip_move',       'MOVE',         'Carry an element into a neighbouring space.'),
    ('clip_circulate',  'CIRCULATE',    'Pass food between the elements of one organism.'),
    ('clip_two_org',    'Two organisms','Split, and each half acts on its own.'),
    ('clip_three_org',  'Three organisms', 'And again — the shape you make is the game.'),
    ('clip_conflict',   'Conflict',     'What happens when rival elements of a type meet.'),
    ('clip_perish',     'Integrity',    'An organism that cannot hold together comes apart.'),
    ('clip_power',      'Power',        'Holding the centre pays, and the power board tracks it.'),
]


def build_print_masters(force):
    made = []
    for source_rel, dest_name, width, page in PRINT_MASTERS:
        source = PRINT_READY / source_rel
        dest = DEST / dest_name
        if not source.is_file():
            print(f'  missing print master: {source_rel}', file=sys.stderr)
            continue
        if not force and dest.is_file() and dest.stat().st_mtime >= source.stat().st_mtime:
            continue
        dest.parent.mkdir(parents=True, exist_ok=True)
        scratch = dest.with_suffix('.render.png')
        render_pdf(source, scratch, width, page)
        image = Image.open(scratch)
        if dest.suffix == '.png':
            image = trim_to_circle(image) if 'board' in dest_name else image
            image = image.convert('RGBA')
            image.thumbnail((width, width), Image.LANCZOS)
            image.quantize(colors=256, method=Image.FASTOCTREE,
                           dither=Image.NONE).save(dest, 'PNG', optimize=True)
        else:
            image.convert('RGB').save(dest, 'JPEG', quality=JPEG_QUALITY,
                                      optimize=True, progressive=True)
        scratch.unlink(missing_ok=True)
        made.append(dest)
        print(f'  {dest_name:<34} from print master  {dest.stat().st_size/1024:.0f} KB')
    return made


def build_photographs(force):
    made = []
    for source_name, dest_name, width in PHOTOGRAPHS:
        source = PHOTOS / source_name
        dest = DEST / dest_name
        if not source.is_file():
            print(f'  photo not found: {source}', file=sys.stderr)
            continue
        if not force and dest.is_file() and dest.stat().st_mtime >= source.stat().st_mtime:
            continue
        image = Image.open(source).convert('RGB')
        image.thumbnail((width, width), Image.LANCZOS)
        image.save(dest, 'JPEG', quality=JPEG_QUALITY, optimize=True, progressive=True)
        made.append(dest)
        print(f'  {dest_name:<34} photo {image.size[0]}x{image.size[1]}  '
              f'{dest.stat().st_size/1024:.0f} KB')
    return made


def build_symbols(force):
    made = []
    for name in SYMBOLS:
        source = SOURCE / 'inputs' / 'sym_png' / f'{name}.png'
        dest = CUTOUTS / f'symbol-{name}.png'
        if not source.is_file():
            continue
        if not force and dest.is_file() and dest.stat().st_mtime >= source.stat().st_mtime:
            continue
        image = Image.open(source).convert('RGBA')
        image.thumbnail((256, 256), Image.LANCZOS)
        image.save(dest, 'PNG', optimize=True)
        made.append(dest)
        print(f'  {dest.name:<34} glyph {image.size[0]}x{image.size[1]}  '
              f'{dest.stat().st_size/1024:.0f} KB')
    return made


# The clips are already small H.264; copy them through rather than re-encoding,
# and lift a poster frame so the page has something to show before play.
def build_clips(force):
    made = []
    videos = DEST / 'video'
    videos.mkdir(parents=True, exist_ok=True)
    for name, _, _ in RULE_CLIPS:
        source = SOURCE / 'scene' / f'{name}.mp4'
        dest = videos / f'{name}.mp4'
        poster = videos / f'{name}.jpg'
        if not source.is_file():
            print(f'  clip not found: {name}.mp4', file=sys.stderr)
            continue
        if force or not dest.is_file() or dest.stat().st_mtime < source.stat().st_mtime:
            shutil.copyfile(source, dest)
            made.append(dest)
        if force or not poster.is_file():
            cmd = ' '.join(shlex.quote(str(a)) for a in [
                'ffmpeg', '-v', 'error', '-y', '-i', str(source),
                '-frames:v', '1', '-q:v', '4', str(poster)])
            os.system(cmd)
    if made:
        total = sum(d.stat().st_size for d in made) / (1024 * 1024)
        print(f'  {len(made)} rule clips copied into img/organism/video/ ({total:.1f} MB)')
    return made


def main():
    parser = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--force', action='store_true', help='rebuild everything')
    parser.add_argument('--list', action='store_true', help='show the manifest only')
    args = parser.parse_args()

    items = manifest()

    if args.list:
        for source_rel, dest_name, longest, mode in items:
            note = {'circle': ' (trimmed to its disc)',
                    'card': ' (card lifted off the print sheet)',
                    'knockout': ' (backdrop removed)'}.get(mode, '')
            if isinstance(mode, tuple):
                note = f' (view {mode[1]} of the strip)'
            print(f'{longest:>5}px  {dest_name:<34} <- {source_rel}{note}')
        print(f'\n{len(items)} images + 3 derived (backdrop, masthead, turn aid)')
        return

    if not SOURCE.is_dir():
        raise SystemExit(
            f'source art not found: {SOURCE}\n'
            'Set $ORGANISM_PIECES to the organism repo\'s pieces directory.')

    DEST.mkdir(parents=True, exist_ok=True)
    THUMBS.mkdir(parents=True, exist_ok=True)

    built = skipped = 0
    missing = []
    total = 0

    for source_rel, dest_name, longest, mode in items:
        source = SOURCE / source_rel
        dest = DEST / dest_name

        if not source.is_file():
            missing.append(source_rel)
            continue
        if not args.force and dest.is_file() and dest.stat().st_mtime >= source.stat().st_mtime:
            skipped += 1
            total += dest.stat().st_size
            continue

        size = convert(source, dest, longest, mode)
        built += 1
        total += dest.stat().st_size
        print(f'  {dest_name:<34} {size[0]}x{size[1]}  {dest.stat().st_size/1024:.0f} KB')

    built += len(build_print_masters(args.force))
    built += len(build_photographs(args.force))
    built += len(build_symbols(args.force))
    built += len(build_clips(args.force))

    for build, note in ((build_backdrop, 'page backdrop'),
                        (build_masthead, 'masthead banner')):
        made = build(args.force)
        if made:
            built += 1
            total += made.stat().st_size
            print(f'  {made.name:<34} {note}  {made.stat().st_size/1024:.0f} KB')

    print(f'\n{built} built, {skipped} already current -> {DEST}')
    print(f'circular cut-outs (transparent PNG) live in {CUTOUTS.relative_to(REPO_ROOT)}/')
    print(f'total {total/1024/1024:.1f} MB on disk')

    if missing:
        print(f'\nMISSING from {SOURCE} ({len(missing)}):', file=sys.stderr)
        for name in missing:
            print(f'  {name}', file=sys.stderr)
        print('The site will 404 on these — check the pieces repo.', file=sys.stderr)


if __name__ == '__main__':
    main()
