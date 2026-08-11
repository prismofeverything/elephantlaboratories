#!/usr/bin/env bash
#
# 03-restore-data.sh
#
# Run this ON THE NEW SERVER (after 02-provision-new-server.sh), as root/sudo,
# once you've rsynced the migration bundle up from your laptop:
#
#     sudo bash 03-restore-data.sh [~/elabs-migration]
#
# Expects the layout produced by 01 -> pull-to-laptop.sh -> push-to-new-server.sh:
#     <root>/bundle/   = contents of the old server's ~/migrate-out
#     <root>/media/    = prismofeverything/, music/, ryanspangler/, static sites
#
# Restores MongoDB, the track library, /srv/git, secrets and /etc/letsencrypt,
# then enables the TLS vhosts (only those whose certs are present) and reloads
# nginx. Idempotent.
#
set -euo pipefail
[ "$(id -u)" -eq 0 ] || { echo "run as root / with sudo"; exit 1; }

ROOT="${1:-$HOME/elabs-migration}"
BUNDLE="$ROOT/bundle"
MEDIA="$ROOT/media"
APP_USER=prism          # must match APP_USER in 02-provision-new-server.sh
msg()  { echo -e "\n\033[1;32m==>\033[0m $*"; }
warn() { echo -e "\033[1;33m[!]\033[0m $*" >&2; }
[ -d "$BUNDLE" ] || { echo "no bundle at $BUNDLE — rsync it up first"; exit 1; }

# ── 1. MongoDB ───────────────────────────────────────────────────────────────
msg "restoring MongoDB"
if [ -d "$BUNDLE/mongodump" ]; then
  GZ=""
  find "$BUNDLE/mongodump" -name '*.bson.gz' -print -quit | grep -q . && GZ="--gzip"
  mongorestore --drop $GZ "$BUNDLE/mongodump"
  msg "mongo restored — collection counts:"
  mongosh --quiet --eval '
    db.getMongo().getDBNames().forEach(function(n){
      if (["admin","config","local"].indexOf(n)>=0) return;
      var d=db.getSiblingDB(n);
      d.getCollectionNames().forEach(function(c){
        print("    "+n+"."+c+" = "+d.getCollection(c).countDocuments({}));
      });
    });' 2>/dev/null || true
  [ -f "$BUNDLE/meta/mongo-counts.txt" ] && { echo "  (old server had:)"; sed 's/^/    /' "$BUNDLE/meta/mongo-counts.txt"; }
else
  warn "no mongodump in bundle — skipping"
fi

# ── 2. media: track library (critical) + optional archives/static sites ──────
restore_dir() {  # <src-under-media> <dest-abs>
  local src="$MEDIA/$1" dst="$2"
  if [ -d "$src" ] && [ -n "$(ls -A "$src" 2>/dev/null)" ]; then
    install -d -o "$APP_USER" -g "$APP_USER" "$dst"
    rsync -a "$src"/ "$dst"/
    chown -R "$APP_USER":"$APP_USER" "$dst"
    msg "restored $1 -> $dst ($(du -sh "$dst" | cut -f1))"
  fi
}
restore_dir prismofeverything "/home/$APP_USER/prismofeverything"   # <-- critical
restore_dir music             "/home/$APP_USER/music"
restore_dir ryanspangler      "/home/$APP_USER/ryanspangler"
restore_dir metamagicreality  "/home/$APP_USER/metamagicreality"
restore_dir repressilator     "/home/$APP_USER/repressilator"

# ── 3. self-hosted git bare repos + git user's keys ──────────────────────────
msg "restoring /srv/git"
if [ -d "$BUNDLE/srv-git" ]; then
  rsync -a "$BUNDLE/srv-git"/ /srv/git/
  chown -R git:git /srv/git
fi
if [ -d "$BUNDLE/secrets/git-user-ssh" ]; then
  install -d -m700 -o git -g git /home/git/.ssh
  cp -a "$BUNDLE/secrets/git-user-ssh"/. /home/git/.ssh/ 2>/dev/null || true
  chown -R git:git /home/git/.ssh; chmod 600 /home/git/.ssh/* 2>/dev/null || true
fi

# ── 4. secrets & app config ──────────────────────────────────────────────────
msg "restoring secrets / app config"
u="/home/$APP_USER"
[ -f "$BUNDLE/secrets/.keys" ]      && install -m600 -o "$APP_USER" -g "$APP_USER" "$BUNDLE/secrets/.keys" "$u/.keys"
[ -f "$BUNDLE/secrets/.gitconfig" ] && install -m644 -o "$APP_USER" -g "$APP_USER" "$BUNDLE/secrets/.gitconfig" "$u/.gitconfig"
# app-local edn config / server-side data files, if any were captured
for app in elephantlaboratories organism; do
  if [ -d "$BUNDLE/secrets/$app" ]; then
    cp -a "$BUNDLE/secrets/$app"/. "$u/$app/" 2>/dev/null || true
    chown -R "$APP_USER":"$APP_USER" "$u/$app"
  fi
done
# merge old ryan authorized_keys (don't clobber the key 02 set up)
if [ -f "$BUNDLE/secrets/ryan-ssh/authorized_keys" ]; then
  install -d -m700 -o "$APP_USER" -g "$APP_USER" "$u/.ssh"
  touch "$u/.ssh/authorized_keys"
  cat "$BUNDLE/secrets/ryan-ssh/authorized_keys" >> "$u/.ssh/authorized_keys"
  sort -u "$u/.ssh/authorized_keys" -o "$u/.ssh/authorized_keys"
  chown "$APP_USER":"$APP_USER" "$u/.ssh/authorized_keys"; chmod 600 "$u/.ssh/authorized_keys"
fi

# ── 5. TLS certs — gives working HTTPS before the DNS flip ────────────────────
msg "restoring /etc/letsencrypt"
if [ -d "$BUNDLE/etc-letsencrypt/letsencrypt" ]; then
  cp -a "$BUNDLE/etc-letsencrypt/letsencrypt"/. /etc/letsencrypt/
elif [ -d "$BUNDLE/etc-letsencrypt" ]; then
  cp -a "$BUNDLE/etc-letsencrypt"/. /etc/letsencrypt/ 2>/dev/null || true
fi

# ── 6. enable nginx vhosts whose certs exist; keep static sites optional ─────
msg "enabling nginx vhosts"
enable_tls() {  # enable a TLS vhost only if its cert is present
  local site="$1" dom="$2"
  if [ -f "/etc/letsencrypt/live/$dom/fullchain.pem" ]; then
    ln -sf "/etc/nginx/sites-available/$site" "/etc/nginx/sites-enabled/$site"
    echo "    enabled $site"
  else
    warn "no cert for $dom yet — leaving $site disabled (get one with certbot after DNS flip)"
  fi
}
enable_tls elephantlaboratories.com elephantlaboratories.com
enable_tls prismofeverything.com    prismofeverything.com
enable_tls playorganism.io          playorganism.io
# static HTTP-only sites: enable only if their content was restored
for name in ryanspangler.com metamagicreality.com repressilator.com; do
  dir="${name%%.*}"
  if [ -n "$(ls -A "/home/$APP_USER/$dir" 2>/dev/null)" ]; then
    ln -sf "/etc/nginx/sites-available/$name" "/etc/nginx/sites-enabled/$name"
    echo "    enabled $name (static)"
  fi
done

nginx -t && systemctl reload nginx

THIS_IP="$(hostname -I 2>/dev/null | awk '{print $1}')"
cat <<EOF

============================================================================
  Data restored (mongo, tracks, /srv/git, secrets, TLS certs); TLS vhosts
  enabled for every domain whose cert is present.
============================================================================
NEXT — from your laptop, deploy the apps and verify before flipping DNS:

    NEW=${APP_USER}@${THIS_IP:-THIS_IP} script/migration/deploy-and-verify.sh

That builds + ships the jars (via release.py) and curl-checks all three sites
with --resolve, so you confirm they serve correctly while DNS still points at
the old box. When every check is green, flip the A-records to ${THIS_IP:-THIS_IP},
then re-verify against real DNS:

    NEW=${THIS_IP:-THIS_IP} script/migration/deploy-and-verify.sh --verify-only
============================================================================
EOF
