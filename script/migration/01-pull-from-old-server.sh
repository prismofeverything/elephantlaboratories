#!/usr/bin/env bash
#
# 01-pull-from-old-server.sh
#
# Run this ON THE OLD SERVER (the Ubuntu 16.04 DigitalOcean box), with sudo:
#
#     sudo bash 01-pull-from-old-server.sh
#
# It stages everything that ISN'T in git and can't be rebuilt — Mongo dump,
# TLS certs, app secrets, the git bare repos, static-site data — under
# ~/migrate-out/, then prints the exact rsync commands to pull that bundle
# (and the big media dirs) down to your laptop.
#
# It does NOT stop or modify any running service. Mongo is dumped hot, which is
# fine for this data. The big media dirs (tracks, music) are intentionally NOT
# copied into the bundle (to avoid filling this 54%-full disk) — they're rsynced
# straight from their source paths by the commands printed at the end.
#
set -u

# --- resolve the human user even though we're running under sudo -------------
OWNER="${SUDO_USER:-$(id -un)}"
OWNER_HOME="$(getent passwd "$OWNER" | cut -d: -f6)"
OWNER_HOME="${OWNER_HOME:-/home/ryan}"
STAGE="$OWNER_HOME/migrate-out"

echo "==> staging bundle under: $STAGE"
mkdir -p "$STAGE"/{mongodump,postgres,etc-letsencrypt,nginx,srv-git,secrets,meta}

note()  { echo "[*] $*"; }
warn()  { echo "[!] $*" >&2; }
have()  { command -v "$1" >/dev/null 2>&1; }

# Copy a path if it exists, preserving perms; never fail the script.
grab() {
  local src="$1" dst="$2"
  if [ -e "$src" ]; then
    mkdir -p "$dst"
    cp -a "$src" "$dst"/ 2>/dev/null && note "copied $src" \
      || warn "could not copy $src"
  fi
}

# ── 1. MongoDB — the real database for all three sites ───────────────────────
# (elephantlaboratories content + mailing list, and the `organism` game db)
note "dumping MongoDB (hot dump, all databases)…"
if have mongodump; then
  if ! mongodump --gzip --out "$STAGE/mongodump" >/dev/null 2>"$STAGE/meta/mongodump.log"; then
    warn "gzip dump failed (old mongodump?), retrying without --gzip"
    mongodump --out "$STAGE/mongodump" >/dev/null 2>>"$STAGE/meta/mongodump.log" \
      || warn "mongodump FAILED — see $STAGE/meta/mongodump.log"
  fi
  # record collection counts so we can verify the restore later
  if have mongo; then
    mongo --quiet --eval '
      db.getMongo().getDBNames().forEach(function(n){
        if (["admin","config","local"].indexOf(n)>=0) return;
        var d=db.getSiblingDB(n);
        d.getCollectionNames().forEach(function(c){
          print(n+"."+c+" = "+d.getCollection(c).count());
        });
      });' > "$STAGE/meta/mongo-counts.txt" 2>/dev/null
  fi
  du -sh "$STAGE/mongodump" 2>/dev/null | sed 's/^/    dump size: /'
else
  warn "mongodump not found — is this the right box?"
fi

# ── 2. Postgres — only user `zyuu`'s db; dump for cold storage, then shed ─────
note "dumping Postgres db 'zyuu' (archival only — not redeployed)…"
if have pg_dump; then
  sudo -u postgres pg_dump zyuu 2>/dev/null | gzip > "$STAGE/postgres/zyuu.sql.gz" \
    && note "postgres zyuu dumped" || warn "postgres dump skipped/failed (probably fine)"
fi

# ── 3. TLS — certs + account so the new box serves HTTPS the moment DNS flips ─
note "copying /etc/letsencrypt (certs, accounts, renewal configs)…"
grab /etc/letsencrypt "$STAGE/etc-letsencrypt"
# and a human-readable record of what's issued + expiry (needs root; failed in
# the read-only inventory, works here):
have certbot && certbot certificates > "$STAGE/meta/certbot-certificates.txt" 2>&1 || true

# ── 4. nginx site configs (reference — the new box gets fresh ones from 02) ───
note "copying nginx configs for reference…"
grab /etc/nginx/sites-available "$STAGE/nginx"
grab /etc/nginx/sites-enabled  "$STAGE/nginx"
have nginx && nginx -T > "$STAGE/meta/nginx-T.txt" 2>&1 || true

# ── 5. Self-hosted git — the /srv/git bare repos + git user setup ────────────
note "copying /srv/git bare repos + git user…"
grab /srv/git "$STAGE/srv-git"
grab /home/git/.ssh "$STAGE/secrets/git-user-ssh"
grab "$OWNER_HOME/setup-git-server.sh" "$STAGE/secrets"

# ── 6. App secrets & server-side config (NOT in git) ─────────────────────────
note "copying app secrets / server-side config…"
grab "$OWNER_HOME/.keys"                              "$STAGE/secrets"
grab "$OWNER_HOME/.gitconfig"                         "$STAGE/secrets"
grab "$OWNER_HOME/.ssh/authorized_keys"              "$STAGE/secrets/ryan-ssh"
# any edn config living next to the deployed jars (prod conf / local overrides):
for f in dev-config.edn prod-config.edn config.edn .env; do
  grab "$OWNER_HOME/elephantlaboratories/$f" "$STAGE/secrets/elephantlaboratories"
  grab "$OWNER_HOME/organism/$f"             "$STAGE/secrets/organism"
done
# server-side copies of organism's CWD-relative data files, in case they drifted
grab "$OWNER_HOME/organism/game_library.json" "$STAGE/secrets/organism"
grab "$OWNER_HOME/organism/lineage.json"      "$STAGE/secrets/organism"

# ── 7. Misc metadata worth keeping ───────────────────────────────────────────
note "capturing metadata (crontabs, running processes, versions)…"
{
  echo "## per-user crontabs"
  for u in $(cut -d: -f1 /etc/passwd); do
    out="$(crontab -u "$u" -l 2>/dev/null)" || continue
    [ -z "$out" ] && continue
    echo "---- $u ----"; echo "$out"; echo
  done
} > "$STAGE/meta/crontabs.txt" 2>/dev/null
ps auxww | grep -iE 'java|mongo|nginx|postgres' | grep -v grep > "$STAGE/meta/running.txt" 2>/dev/null
{ java -version; echo; mongod --version | head -3; echo; nginx -v; } > "$STAGE/meta/versions.txt" 2>&1

# ── ownership + summary ──────────────────────────────────────────────────────
chown -R "$OWNER":"$OWNER" "$STAGE" 2>/dev/null || true
BUNDLE_SIZE="$(du -sh "$STAGE" 2>/dev/null | cut -f1)"

cat <<EOF

============================================================================
  Bundle staged at: $STAGE   (total $BUNDLE_SIZE)
============================================================================

Contents:
  mongodump/         full MongoDB dump (all sites' data)      <-- critical
  postgres/          zyuu.sql.gz (archival only)
  etc-letsencrypt/   TLS certs + account + renewal configs    <-- critical
  srv-git/           bare git repos (tetrahedron, prism, ...)
  secrets/           .keys, ssh authorized_keys, app config, git-user ssh
  nginx/, meta/       reference copies + records

NOTE — NOT in this bundle (rebuilt from git, or copied separately below):
  * both uberjars           -> rebuilt on your laptop (lein uberjar / deploy.sh)
  * ~/.m2 ~/.nvm node_modules target/  -> regenerated
  * the big media dirs      -> rsynced straight from source, see below

----------------------------------------------------------------------------
NEXT — run these FROM YOUR LAPTOP to pull everything down:
----------------------------------------------------------------------------

OLD=${OWNER}@$(hostname -I 2>/dev/null | awk '{print $1}')     # or ${OWNER}@elephantlaboratories.com
DEST=~/elabs-migration                                          # laptop staging dir
mkdir -p "\$DEST"

# 1) the config/secrets/db bundle
rsync -avz "\$OLD:$STAGE/" "\$DEST/bundle/"

# 2) the prismofeverything track library (2.7 GB) — SOURCE OF TRUTH, critical
rsync -avz "\$OLD:/home/${OWNER}/prismofeverything/" "\$DEST/media/prismofeverything/"

# 3) ryanspangler.com + its music archive (only if you're keeping that site)
rsync -avz "\$OLD:/home/${OWNER}/music/"         "\$DEST/media/music/"
rsync -avz "\$OLD:/home/${OWNER}/ryanspangler/"  "\$DEST/media/ryanspangler/"

# 4) the small HTTP-only static sites (keep whichever are yours)
rsync -avz "\$OLD:/home/${OWNER}/metamagicreality/" "\$DEST/media/metamagicreality/"
rsync -avz "\$OLD:/home/${OWNER}/repressilator/"    "\$DEST/media/repressilator/"
rsync -avz "\$OLD:/home/${OWNER}/lisamarahrens/"    "\$DEST/media/lisamarahrens/"

Then push \$DEST up to the NEW server after you've run 02-provision-new-server.sh.
============================================================================
EOF
