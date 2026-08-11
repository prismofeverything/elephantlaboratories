#!/usr/bin/env bash
#
# pull-to-laptop.sh  —  RUN THIS ON YOUR LAPTOP
#
# Migration step 2, right after 01-pull-from-old-server.sh has staged the bundle
# on the OLD server. Pulls that bundle AND the big media dirs down to a local
# staging dir. This *is* the set of rsync commands 01 used to print at the end —
# now a script you can just run (and re-run).
#
# Idempotent: rsync transfers only deltas, so re-run freely — e.g. to pick up
# tracks added between the first pull and the DNS cutover.
#
# Usage:
#   OLD=ryan@elephantlaboratories.com ./pull-to-laptop.sh
#   OLD=ryan@138.197.213.77 DEST=~/elabs-migration ./pull-to-laptop.sh
#   DRY_RUN=1 ./pull-to-laptop.sh            # preview transfers, write nothing
#   SKIP_PERIPHERALS=1 ./pull-to-laptop.sh   # bundle + prism tracks only
#
set -euo pipefail

OLD="${OLD:-ryan@elephantlaboratories.com}"   # pre-cutover the .com still resolves to the old box
DEST="${DEST:-$HOME/elabs-migration}"

RSYNC_OPTS=(-avz --human-readable)
if [ -n "${DRY_RUN:-}" ]; then
  RSYNC_OPTS+=(--dry-run)
  echo "[dry-run] no files will be written"
fi

command -v rsync >/dev/null 2>&1 || { echo "rsync not found on this machine"; exit 1; }

echo "==> pull  $OLD  ->  $DEST"
mkdir -p "$DEST"

# Remote paths are relative to the SSH login's home dir (log in as the data
# owner), so nothing hardcodes /home/ryan. rsync exit 24 = "some source files
# vanished mid-sync" on the live tracks dir — harmless, treat it as success.
rsync_to() {
  local rc=0
  mkdir -p "$2"                      # rsync won't create nested dest parents (media/) itself
  rsync "${RSYNC_OPTS[@]}" "$1" "$2" || rc=$?
  [ "$rc" -eq 0 ] || [ "$rc" -eq 24 ]
}
pull()     { echo "  --> $1"; rsync_to "$OLD:$1/" "$DEST/$2/" || { echo "      FAILED: $1"; exit 1; }; }
pull_opt() { echo "  --> $1 (optional)"; rsync_to "$OLD:$1/" "$DEST/$2/" || echo "      skipped ($1 not present on $OLD)"; }

pull migrate-out       bundle                    # config / secrets / db bundle  (critical)
pull prismofeverything media/prismofeverything   # track library ~2.7 GB         (critical)

if [ -z "${SKIP_PERIPHERALS:-}" ]; then
  # ryanspangler + its music archive, and the HTTP-only static sites. Pulling
  # them loses nothing; 03-restore-data.sh only serves the ones you actually keep.
  pull_opt music            media/music
  pull_opt ryanspangler     media/ryanspangler
  pull_opt metamagicreality media/metamagicreality
  pull_opt repressilator    media/repressilator
  pull_opt lisamarahrens    media/lisamarahrens
else
  echo "  (peripherals skipped: SKIP_PERIPHERALS set)"
fi

echo
echo "==> $DEST now holds $(du -sh "$DEST" 2>/dev/null | cut -f1)  (bundle/ + media/)"
echo
echo "Next:"
echo "  1) provision the new droplet:   (on NEW box)  ssh root@tetrahedron.world 'bash ~/02-provision-new-server.sh'"
echo "  2) push this bundle up to it:    (on laptop)   NEW=root@tetrahedron.world script/migration/push-to-new-server.sh"
