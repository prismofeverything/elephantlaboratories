#!/usr/bin/env bash
#
# deploy-and-verify.sh  —  RUN THIS ON YOUR LAPTOP
#
# Migration step 6, after 03-restore-data.sh. Builds + ships the app jars to the
# new box and verifies every site over TLS *before* the DNS flip. Replaces the
# scp / ssh / curl blocks that 02 and 03 used to print at the end.
#
# elephantlaboratories + prismofeverything are one jar, already fully automated by
# release.py (build -> scp -> systemctl restart). organism lives in a separate
# repo, so point ORGANISM_REPO at it to have its deploy.sh run too.
#
# Deploy runs as the app user 'prism' (created by 02) — it owns the jars and holds
# the scoped NOPASSWD sudoers to restart the systemd services.
#
# Usage:
#   NEW=prism@tetrahedron.world ./deploy-and-verify.sh
#   NEW=prism@tetrahedron.world ORGANISM_REPO=~/code/organism ./deploy-and-verify.sh
#   NEW=tetrahedron.world ./deploy-and-verify.sh --verify-only     # re-check only, no deploy
#
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$HERE/../.." && pwd)"

VERIFY_ONLY=""
if [ "${1:-}" = "--verify-only" ]; then VERIFY_ONLY=1; fi

NEW="${NEW:?set NEW=prism@tetrahedron.world (deploy) or NEW=tetrahedron.world (--verify-only)}"
NEW_HOST="${NEW##*@}"      # bare host/IP for curl --resolve (strips any user@)

if [ -z "$VERIFY_ONLY" ]; then
  echo "==> deploy elephantlaboratories + prismofeverything  (release.py build)"
  DEPLOY_HOST="$NEW" python3 "$REPO_ROOT/script/release.py" build

  if [ -n "${ORGANISM_REPO:-}" ]; then
    if [ -x "$ORGANISM_REPO/deploy.sh" ]; then
      echo "==> deploy organism  (deploy.sh ${ORGANISM_DEPLOY_CMD:-ship})"
      ( cd "$ORGANISM_REPO" && DEPLOY_HOST="$NEW" ./deploy.sh "${ORGANISM_DEPLOY_CMD:-ship}" )
    else
      echo "  [!] $ORGANISM_REPO/deploy.sh not found or not executable — organism NOT deployed"
    fi
  else
    echo "  (organism not deployed — set ORGANISM_REPO=/path/to/organism to include it)"
  fi
fi

echo
echo "==> verify sites on $NEW_HOST  (curl --resolve, independent of DNS)"
fail=0
check() {   # <domain> <path>
  local dom="$1" path="$2" code
  # curl's %{http_code} is already 000 on a failed connection; don't add a second
  # fallback or it prints "000000". || true just keeps set -e from aborting.
  code="$(curl -sS --max-time 15 --resolve "$dom:443:$NEW_HOST" \
            -o /dev/null -w '%{http_code}' "https://$dom$path" 2>/dev/null)" || true
  code="${code:-000}"
  printf '  %-40s %s\n' "$dom$path" "$code"
  case "$code" in 2*|3*) ;; *) fail=1 ;; esac
}
check elephantlaboratories.com /
check prismofeverything.com     /api/tracks
check playorganism.io          /

echo
if [ "$fail" -eq 0 ]; then
  echo "All sites answered 2xx/3xx. Safe to flip the DNS A-records to $NEW_HOST."
  echo "After DNS propagates, re-check:  NEW=$NEW_HOST script/migration/deploy-and-verify.sh --verify-only"
  echo "and confirm renewals:            ssh root@$NEW_HOST 'certbot renew --dry-run'"
else
  echo "[!] a site did NOT return 2xx/3xx — check 'journalctl -u elephantlaboratories -u organism'"
  echo "    on the new box before flipping DNS."
  exit 1
fi
