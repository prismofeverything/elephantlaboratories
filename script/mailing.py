#!/usr/bin/env python3
"""Mailing list sync for elephantlaboratories.

Pulls the site's signups out of the production MongoDB and turns them into
CSV files MailerLite can import.

Subcommands:

    sync
        pull + export. The one you usually want.

    pull
        ssh to the server, mongoexport the mailing-list collection into
        dump/mailing-list.json (plus a dated archive copy). Read-only on
        the server; nothing is written there and no service is touched.

    export
        turn a local snapshot into MailerLite CSVs. No network.

    stats
        summarize a local snapshot without writing anything.

Everything lands in dump/, which is gitignored — subscriber emails never
enter version control.

A sync writes:

    dump/mailerlite-<date>.csv           every valid subscriber, deduped
    dump/mailerlite-new-<date>.csv       only those who first signed up
                                         since the last sync (skipped on
                                         a first run — nothing to diff)
    dump/mailerlite-rejected-<date>.csv  rows not fit to import, with why
    dump/mailing-state.json              watermark for the next delta

To import: MailerLite -> Subscribers -> Import -> upload the CSV. The
headers are already named after MailerLite's own fields (email, name,
last_name), so its column mapper picks them up; the first time through,
tell it to create signed_up and the interest_* columns as new custom
fields. MailerLite dedupes on email, so re-importing is safe.

Deploy host comes from $DEPLOY_HOST, same convention as release.py.
"""

import argparse
import csv
import datetime
import json
import os
import re
import shlex
import sys

from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parent.parent
DUMP_DIR = REPO_ROOT / 'dump'

# Same target as release.py: the app user on the new box, at the domain
# (correct post-DNS-cutover). Override for a different box:
#   DEPLOY_HOST=prism@tetrahedron.world ./script/mailing.py sync
REMOTE_HOST = os.environ.get('DEPLOY_HOST', 'prism@elephantlaboratories.com')
MONGO_DB = 'elephantlaboratories'
MONGO_COLLECTION = 'mailing-list'

SNAPSHOT = DUMP_DIR / 'mailing-list.json'
STATE_FILE = DUMP_DIR / 'mailing-state.json'

# The three interest checkboxes on /sol/sign-up. Mongo key -> CSV column,
# named after what the form actually says next to the box.
INTERESTS = {
    'reprint': 'interest_sol_journey',
    'organism': 'interest_organism',
    'campaign': 'interest_beam_of_light'}

CSV_COLUMNS = (
    ['email', 'name', 'last_name', 'signed_up']
    + list(INTERESTS.values())
    + ['comments', 'signup_count'])

# Deliberately strict. Anything this rejects goes to the rejected CSV with a
# reason rather than being silently dropped — the old Clojure exporter let
# junk through (a name typed into the email box, an address with a comma in
# it that then broke the column count) and it ended up in Mailchimp.
EMAIL_RE = re.compile(r'^[^\s@,;:<>"\'()\[\]\\]+@[^\s@,;:<>"\'()\[\]\\]+\.[A-Za-z]{2,}$')

TRUTHY = {True, 1, 'true', 'True', 'on', 'yes', '1', 'checked'}


# Bypass CPython's subprocess module — go through libc system(3), matching
# release.py. Output is redirected to a file rather than captured in-process.
def run_to_file(cmd, out_path):
    quoted = ' '.join(shlex.quote(str(a)) for a in cmd)
    print('$', quoted, '>', out_path, file=sys.stderr)
    rc = os.system(f'{quoted} > {shlex.quote(str(out_path))}')
    if rc != 0:
        raise SystemExit(f'command failed (status={rc}): {cmd[0]}')


def shell_quote(s):
    return "'" + s.replace("'", "'\\''") + "'"


# ── Pull ─────────────────────────────────────────────────────────────────────

# mongoexport ships with the mongodb-org metapackage the migration's 02 script
# installs, but fall back to mongosh's EJSON so this still works on a box that
# only has the shell. Both emit {"$oid": ...} for _id, which is what we want:
# the ObjectId carries the signup time.
#
# Deliberately NOT --quiet: mongoexport exits 0 even when it connects to
# nothing and exports nothing, so its stderr ("exported N records") is the
# only confirmation that the pull actually hit the database. The count guards
# in cmd_pull are the backstop.
def remote_export_script():
    return f'''
set -e
if command -v mongoexport >/dev/null 2>&1; then
  mongoexport --db={MONGO_DB} --collection={MONGO_COLLECTION} --jsonArray
elif command -v mongosh >/dev/null 2>&1; then
  mongosh --quiet --eval 'EJSON.stringify(db.getSiblingDB("{MONGO_DB}").getCollection("{MONGO_COLLECTION}").find().toArray())'
else
  echo "neither mongoexport nor mongosh found on the server" >&2
  exit 1
fi
'''


def cmd_pull(args):
    DUMP_DIR.mkdir(parents=True, exist_ok=True)
    host = args.host

    print(f'=== Pulling {MONGO_DB}.{MONGO_COLLECTION} from {host} ===', file=sys.stderr)
    staging = DUMP_DIR / '.mailing-list.json.part'
    run_to_file(
        ['ssh', '-o', 'ConnectTimeout=15', host,
         f'bash -lc {shell_quote(remote_export_script())}'],
        staging)

    records = parse_snapshot(staging)
    if not records:
        staging.unlink(missing_ok=True)
        raise SystemExit(
            'server returned no records — refusing to overwrite the snapshot.\n'
            'mongoexport exits 0 even when it reaches nothing, so check the\n'
            'lines above: is mongod up, and is the collection name right?')

    previous = read_state().get('signups')
    if previous and len(records) < previous and not args.force:
        staging.unlink(missing_ok=True)
        raise SystemExit(
            f'server returned {len(records)} records but the last sync saw '
            f'{previous}.\nThe list only grows, so this is probably a bad pull '
            f'— refusing to overwrite\n{SNAPSHOT}. Re-run with --force if the '
            f'shrink is real (deletions, cleanup).')

    staging.replace(SNAPSHOT)
    archive = DUMP_DIR / f'mailing-list-{today()}.json'
    archive.write_bytes(SNAPSHOT.read_bytes())

    print(f'pulled {len(records)} records -> {SNAPSHOT}', file=sys.stderr)
    print(f'archived            -> {archive}', file=sys.stderr)
    return records


# ── Snapshot parsing ─────────────────────────────────────────────────────────

def parse_snapshot(path):
    """Read mongoexport output: a JSON array, or one JSON doc per line."""
    text = Path(path).read_text(encoding='utf-8', errors='replace').strip()
    if not text:
        return []

    try:
        loaded = json.loads(text)
        return loaded if isinstance(loaded, list) else [loaded]
    except json.JSONDecodeError:
        pass

    records = []
    for number, line in enumerate(text.splitlines(), start=1):
        line = line.strip().rstrip(',')
        if not line or line in ('[', ']'):
            continue
        try:
            records.append(json.loads(line))
        except json.JSONDecodeError:
            print(f'  skipping unparseable line {number}', file=sys.stderr)
    return records


def object_id(record):
    raw = record.get('_id')
    if isinstance(raw, dict):
        raw = raw.get('$oid')
    return raw if isinstance(raw, str) else None


# The first 4 bytes of an ObjectId are the creation time — that is the only
# record we have of when someone signed up, since the form stores no date.
def signup_time(record):
    oid = object_id(record)
    if not oid or len(oid) < 8:
        return None
    try:
        seconds = int(oid[:8], 16)
    except ValueError:
        return None
    return datetime.datetime.fromtimestamp(seconds, tz=datetime.timezone.utc)


def clean_text(value):
    if value is None or isinstance(value, bool):
        return ''
    return re.sub(r'\s+', ' ', str(value)).strip()


def split_name(full_name, style):
    """MailerLite personalizes with {$name}, so the default puts only the
    first token there — 'Hi Aaron' reads better than 'Hi Aaron M'. The
    'last' style reproduces what the old Clojure exporter sent Mailchimp
    (everything but the final token as the first name)."""
    tokens = full_name.split()
    if not tokens:
        return '', ''
    if len(tokens) == 1:
        return tokens[0], ''
    if style == 'last':
        return ' '.join(tokens[:-1]), tokens[-1]
    return tokens[0], ' '.join(tokens[1:])


def truthy(value):
    if isinstance(value, str):
        return value.strip().lower() in {'true', 'on', 'yes', '1', 'checked'}
    return value in TRUTHY


# ── Normalize + merge ────────────────────────────────────────────────────────

def collect(records, name_style):
    """Fold raw signups into one entry per email address.

    Someone who signed up three times over three campaigns is one
    subscriber: earliest signup wins as the date, the most recent non-empty
    name wins, interests are unioned (a later 'yes' is never undone by an
    earlier blank), and distinct comments are kept."""
    subscribers = {}
    rejected = []

    ordered = sorted(records, key=lambda r: object_id(r) or '')

    for record in ordered:
        email = clean_text(record.get('email')).lower()
        full_name = clean_text(record.get('name'))
        when = signup_time(record)

        if not email:
            rejected.append(reject(record, when, 'no email address'))
            continue
        if not EMAIL_RE.match(email):
            rejected.append(reject(record, when, why_invalid(email)))
            continue

        first_name, last_name = split_name(full_name, name_style)
        comment = clean_text(record.get('comments'))

        entry = subscribers.get(email)
        if entry is None:
            entry = {
                'email': email,
                'name': first_name,
                'last_name': last_name,
                'signed_up': when,
                'last_signup': when,
                'comments': [],
                'signup_count': 0}
            for column in INTERESTS.values():
                entry[column] = False
            subscribers[email] = entry

        # Records arrive oldest-first, so a later non-empty name overwrites.
        if first_name or last_name:
            entry['name'] = first_name
            entry['last_name'] = last_name
        if when and (entry['signed_up'] is None or when < entry['signed_up']):
            entry['signed_up'] = when
        if when and (entry['last_signup'] is None or when > entry['last_signup']):
            entry['last_signup'] = when
        for key, column in INTERESTS.items():
            entry[column] = entry[column] or truthy(record.get(key))
        if comment and comment not in entry['comments']:
            entry['comments'].append(comment)
        entry['signup_count'] += 1

    return subscribers, rejected


def why_invalid(email):
    if ',' in email:
        return f"malformed email (comma — likely a typo'd dot): {email}"
    if '@' not in email:
        return f'not an email address: {email}'
    return f'malformed email: {email}'


def reject(record, when, reason):
    return {
        'email': clean_text(record.get('email')),
        'name': clean_text(record.get('name')),
        'signed_up': format_date(when),
        'reason': reason}


def format_date(when):
    return when.date().isoformat() if when else ''


def as_row(entry):
    row = {
        'email': entry['email'],
        'name': entry['name'],
        'last_name': entry['last_name'],
        'signed_up': format_date(entry['signed_up']),
        'comments': ' | '.join(entry['comments']),
        'signup_count': entry['signup_count']}
    for column in INTERESTS.values():
        row[column] = 'yes' if entry[column] else 'no'
    return row


def write_csv(path, columns, rows):
    with open(path, 'w', encoding='utf-8', newline='') as handle:
        writer = csv.DictWriter(handle, fieldnames=columns, lineterminator='\n')
        writer.writeheader()
        writer.writerows(rows)
    return path


# ── State ────────────────────────────────────────────────────────────────────

def read_state():
    if not STATE_FILE.is_file():
        return {}
    try:
        return json.loads(STATE_FILE.read_text(encoding='utf-8'))
    except json.JSONDecodeError:
        print(f'warning: {STATE_FILE} is unreadable — treating this as a first run',
              file=sys.stderr)
        return {}


def write_state(state):
    STATE_FILE.write_text(json.dumps(state, indent=2) + '\n', encoding='utf-8')


def parse_since(text):
    when = datetime.datetime.strptime(text, '%Y-%m-%d')
    return when.replace(tzinfo=datetime.timezone.utc)


def today():
    return datetime.date.today().isoformat()


# ── Export ───────────────────────────────────────────────────────────────────

def cmd_export(args, records=None):
    DUMP_DIR.mkdir(parents=True, exist_ok=True)

    source = Path(args.input).expanduser() if args.input else SNAPSHOT
    if records is None:
        if not source.is_file():
            raise SystemExit(
                f'no snapshot at {source} — run `./script/mailing.py pull` first')
        records = parse_snapshot(source)

    out_dir = Path(args.out_dir).expanduser() if args.out_dir else DUMP_DIR
    out_dir.mkdir(parents=True, exist_ok=True)

    subscribers, rejected = collect(records, args.name_split)
    entries = sorted(subscribers.values(), key=lambda e: (e['signed_up'] is None,
                                                          e['signed_up'],
                                                          e['email']))

    state = read_state()
    since = None
    since_label = None
    if args.since:
        since = parse_since(args.since)
        since_label = f'--since {args.since}'
    elif state.get('last_sync') and not args.all:
        since = datetime.datetime.fromisoformat(state['last_sync'])
        since_label = f"last sync ({state['last_sync'][:10]})"

    stamp = today()
    full_path = write_csv(
        out_dir / f'mailerlite-{stamp}.csv',
        CSV_COLUMNS,
        [as_row(entry) for entry in entries])

    print(f'\n{len(records)} signups -> {len(entries)} subscribers '
          f'({len(records) - len(entries) - len(rejected)} duplicate, '
          f'{len(rejected)} rejected)', file=sys.stderr)
    print(f'  all      {full_path}', file=sys.stderr)

    new_path = None
    if since is not None:
        fresh = [entry for entry in entries
                 if entry['last_signup'] and entry['last_signup'] > since]
        first_timers = sum(1 for entry in fresh if entry['signed_up'] > since)
        new_path = write_csv(
            out_dir / f'mailerlite-new-{stamp}.csv',
            CSV_COLUMNS,
            [as_row(entry) for entry in fresh])
        print(f'  new      {new_path}  ({len(fresh)} since {since_label}: '
              f'{first_timers} new, {len(fresh) - first_timers} re-signed '
              f'with updated answers)', file=sys.stderr)
    else:
        print('  new      (skipped — no previous sync to diff against; '
              'import the full file, then later runs produce a delta)',
              file=sys.stderr)

    if rejected:
        rejected_path = write_csv(
            out_dir / f'mailerlite-rejected-{stamp}.csv',
            ['email', 'name', 'signed_up', 'reason'],
            rejected)
        print(f'  rejected {rejected_path}  ({len(rejected)} rows — '
              f'eyeball these, some are fixable typos)', file=sys.stderr)

    if not args.no_state and out_dir == DUMP_DIR:
        latest = max((e['signed_up'] for e in entries if e['signed_up']),
                     default=None)
        write_state({
            'last_sync': datetime.datetime.now(datetime.timezone.utc).isoformat(),
            'latest_signup': latest.isoformat() if latest else None,
            'signups': len(records),
            'subscribers': len(entries),
            'rejected': len(rejected),
            'last_export': str(full_path)})

    return full_path, new_path


def cmd_sync(args):
    records = cmd_pull(args)
    cmd_export(args, records=records)


def cmd_stats(args):
    source = Path(args.input).expanduser() if args.input else SNAPSHOT
    if not source.is_file():
        raise SystemExit(f'no snapshot at {source} — run `./script/mailing.py pull` first')

    records = parse_snapshot(source)
    subscribers, rejected = collect(records, args.name_split)

    dated = [e['signed_up'] for e in subscribers.values() if e['signed_up']]
    print(f'snapshot     {source}')
    print(f'signups      {len(records)}')
    print(f'subscribers  {len(subscribers)}')
    print(f'rejected     {len(rejected)}')
    if dated:
        print(f'first signup {min(dated).date()}')
        print(f'last signup  {max(dated).date()}')

    by_year = {}
    for when in dated:
        by_year[when.year] = by_year.get(when.year, 0) + 1
    for year in sorted(by_year):
        print(f'  {year}  {by_year[year]:5d}')

    for key, column in INTERESTS.items():
        count = sum(1 for e in subscribers.values() if e[column])
        print(f'{column:<26} {count}')

    state = read_state()
    if state.get('last_sync'):
        print(f"last sync    {state['last_sync']}")


# ── CLI ──────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest='cmd', required=True)

    def add_export_args(p):
        p.add_argument('--since', metavar='YYYY-MM-DD',
                       help='delta cutoff; defaults to the last sync')
        p.add_argument('--all', action='store_true',
                       help='full list only, no delta file')
        p.add_argument('--name-split', choices=['first', 'last'], default='first',
                       help="'first': first token is the first name (default, "
                            "reads better in {$name}); 'last': everything but "
                            "the final token, matching the old Mailchimp export")
        p.add_argument('--out-dir', help='write CSVs somewhere other than dump/')
        p.add_argument('--no-state', action='store_true',
                       help='do not update dump/mailing-state.json')

    p_sync = sub.add_parser('sync', help='pull from the server, then export (usual)')
    p_sync.add_argument('--host', default=REMOTE_HOST, help=f'default: {REMOTE_HOST}')
    p_sync.add_argument('--force', action='store_true',
                        help='accept a snapshot with fewer records than last time')
    p_sync.add_argument('--input', help=argparse.SUPPRESS)
    add_export_args(p_sync)
    p_sync.set_defaults(func=cmd_sync)

    p_pull = sub.add_parser('pull', help='pull the collection into dump/, no export')
    p_pull.add_argument('--host', default=REMOTE_HOST, help=f'default: {REMOTE_HOST}')
    p_pull.add_argument('--force', action='store_true',
                        help='accept a snapshot with fewer records than last time')
    p_pull.set_defaults(func=cmd_pull)

    p_export = sub.add_parser('export', help='snapshot -> MailerLite CSVs, no network')
    p_export.add_argument('--input', help='snapshot to read (default dump/mailing-list.json)')
    add_export_args(p_export)
    p_export.set_defaults(func=cmd_export)

    p_stats = sub.add_parser('stats', help='summarize the local snapshot')
    p_stats.add_argument('--input', help='snapshot to read (default dump/mailing-list.json)')
    p_stats.add_argument('--name-split', choices=['first', 'last'], default='first',
                         help=argparse.SUPPRESS)
    p_stats.set_defaults(func=cmd_stats)

    args = parser.parse_args()
    args.func(args)


if __name__ == '__main__':
    main()
