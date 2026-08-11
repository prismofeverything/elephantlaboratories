#!/usr/bin/env bash
#
# push-to-new-server.sh  —  RUN THIS ON YOUR LAPTOP
#
# Migration step 4, after 02-provision-new-server.sh has provisioned the new box.
# Pushes the locally-staged bundle+media up to the new server, where
# 03-restore-data.sh consumes it. This is the rsync line 02 used to print — now
# a script you can just run (and re-run; rsync only sends deltas).
#
# Transfers run as root — the fresh box's only login, and 03 (run as root) restores
# from root's home by default. The app user 'prism' is created by 02 and has no sudo
# password, so it's not used for the transfer.
#
# Usage:
#   NEW=root@tetrahedron.world ./push-to-new-server.sh
#   NEW=root@tetrahedron.world DEST=~/elabs-migration DRY_RUN=1 ./push-to-new-server.sh
#
set -euo pipefail

NEW="${NEW:?set NEW=root@tetrahedron.world  (the new box; transfers run as root)}"
DEST="${DEST:-$HOME/elabs-migration}"
REMOTE_DIR="${REMOTE_DIR:-elabs-migration}"    # lands at ~/elabs-migration on NEW = 03's default ROOT

RSYNC_OPTS=(-avz --human-readable)
if [ -n "${DRY_RUN:-}" ]; then
  RSYNC_OPTS+=(--dry-run)
  echo "[dry-run] no files will be written"
fi

[ -d "$DEST" ] || { echo "no local staging dir at $DEST — run ./pull-to-laptop.sh first"; exit 1; }
command -v rsync >/dev/null 2>&1 || { echo "rsync not found on this machine"; exit 1; }

echo "==> push  $DEST ($(du -sh "$DEST" 2>/dev/null | cut -f1))  ->  $NEW:~/$REMOTE_DIR"
rsync "${RSYNC_OPTS[@]}" "$DEST"/ "$NEW:$REMOTE_DIR/"

echo
echo "Next, on the NEW box (you're root there):"
echo "    bash ~/03-restore-data.sh ~/$REMOTE_DIR"
echo "then, back on your laptop, deploy as the app user 'prism':"
echo "    NEW=prism@${NEW#*@} script/migration/deploy-and-verify.sh"
