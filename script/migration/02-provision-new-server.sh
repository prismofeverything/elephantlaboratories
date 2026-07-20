#!/usr/bin/env bash
#
# 02-provision-new-server.sh
#
# Run this ON THE NEW SERVER (fresh Ubuntu 24.04 LTS), as root or with sudo:
#
#     sudo bash 02-provision-new-server.sh
#
# Installs the whole stack (nginx, OpenJDK 17, MongoDB 8.0, ffmpeg, certbot,
# git, ufw), creates swap, the ryan/git users, app dirs, systemd units, the
# nginx site configs, and the firewall. Idempotent — safe to re-run.
#
# It leaves nginx serving a plain HTTP holding page. The real TLS vhosts are
# written to sites-available but NOT enabled yet — 03-restore-data.sh restores
# the certs and enables them (nginx never references a cert that isn't there).
#
set -euo pipefail

# ── config — edit if your names differ ───────────────────────────────────────
APP_USER=ryan
JAVA_PKG=openjdk-17-jre-headless
MONGO_MAJOR=8.0
SWAP_GB=2
UBUNTU_CODENAME="$(. /etc/os-release; echo "$VERSION_CODENAME")"   # noble

msg() { echo -e "\n\033[1;32m==>\033[0m $*"; }

[ "$(id -u)" -eq 0 ] || { echo "run as root / with sudo"; exit 1; }

# ── 1. base packages ─────────────────────────────────────────────────────────
msg "installing base packages"
export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y \
  nginx "$JAVA_PKG" ffmpeg git ufw fail2ban rsync curl gnupg ca-certificates \
  certbot python3-certbot-nginx unattended-upgrades

# ── 2. MongoDB 8.0 (official repo) ───────────────────────────────────────────
msg "installing MongoDB $MONGO_MAJOR"
if ! command -v mongod >/dev/null 2>&1; then
  curl -fsSL "https://www.mongodb.org/static/pgp/server-${MONGO_MAJOR}.asc" \
    | gpg --dearmor -o "/usr/share/keyrings/mongodb-server-${MONGO_MAJOR}.gpg"
  echo "deb [ signed-by=/usr/share/keyrings/mongodb-server-${MONGO_MAJOR}.gpg ] https://repo.mongodb.org/apt/ubuntu ${UBUNTU_CODENAME}/mongodb-org/${MONGO_MAJOR} multiverse" \
    > "/etc/apt/sources.list.d/mongodb-org-${MONGO_MAJOR}.list"
  apt-get update -y
  apt-get install -y mongodb-org
fi
systemctl enable --now mongod
# MongoDB binds 127.0.0.1 only by default on this package — leave it that way.

# ── 3. swap (the old box had none — a real liability at low RAM) ──────────────
msg "ensuring ${SWAP_GB}G swap"
if ! swapon --show | grep -q '/swapfile'; then
  fallocate -l "${SWAP_GB}G" /swapfile || dd if=/dev/zero of=/swapfile bs=1M count=$((SWAP_GB*1024))
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  grep -q '/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
  sysctl -w vm.swappiness=10 >/dev/null
  grep -q vm.swappiness /etc/sysctl.conf || echo 'vm.swappiness=10' >> /etc/sysctl.conf
fi

# ── 4. users ─────────────────────────────────────────────────────────────────
msg "creating users: $APP_USER, git"
if ! id "$APP_USER" >/dev/null 2>&1; then
  adduser --disabled-password --gecos "" "$APP_USER"
  usermod -aG sudo "$APP_USER"
fi
# keep your SSH access: copy root's authorized_keys to the app user if present
if [ -f /root/.ssh/authorized_keys ]; then
  install -d -m700 -o "$APP_USER" -g "$APP_USER" "/home/$APP_USER/.ssh"
  install -m600 -o "$APP_USER" -g "$APP_USER" /root/.ssh/authorized_keys "/home/$APP_USER/.ssh/authorized_keys"
fi
# git user with git-shell for the /srv/git bare repos (03 restores the repos)
if ! id git >/dev/null 2>&1; then
  adduser --disabled-password --gecos "" --shell /usr/bin/git-shell git
fi
install -d -m755 -o git -g git /srv/git

# ── 5. app directories ───────────────────────────────────────────────────────
msg "creating app directories under /home/$APP_USER"
for d in elephantlaboratories organism prismofeverything \
         ryanspangler metamagicreality repressilator lisamarahrens; do
  install -d -o "$APP_USER" -g "$APP_USER" "/home/$APP_USER/$d"
done

# ── 6. systemd units (replace the old nohup + pidfile hack) ───────────────────
msg "installing systemd units"
cat > /etc/systemd/system/elephantlaboratories.service <<UNIT
[Unit]
Description=elephantlaboratories.com + prismofeverything.com (Clojure/JVM)
After=network-online.target mongod.service
Wants=network-online.target mongod.service

[Service]
Type=simple
User=$APP_USER
WorkingDirectory=/home/$APP_USER/elephantlaboratories
ExecStart=/usr/bin/java -Dclojure.main.report=stderr -cp /home/$APP_USER/elephantlaboratories/elephantlaboratories.jar clojure.main -m elephantlaboratories.core
Restart=on-failure
RestartSec=5
SuccessExitStatus=143
# modest hardening
NoNewPrivileges=true
ProtectSystem=full

[Install]
WantedBy=multi-user.target
UNIT

cat > /etc/systemd/system/organism.service <<UNIT
[Unit]
Description=playorganism.io game server (Clojure/JVM, HTTP + WebSocket)
After=network-online.target mongod.service
Wants=network-online.target mongod.service

[Service]
Type=simple
User=$APP_USER
WorkingDirectory=/home/$APP_USER/organism
ExecStart=/usr/bin/java -jar /home/$APP_USER/organism/organism.jar
Restart=on-failure
RestartSec=5
SuccessExitStatus=143
NoNewPrivileges=true
ProtectSystem=full

[Install]
WantedBy=multi-user.target
UNIT

systemctl daemon-reload
# enable so they start on boot; they'll only run once the jars are deployed
systemctl enable elephantlaboratories.service organism.service || true

# Let the app user restart its services (and read their journals) over SSH with
# no password, so release.py / deploy.sh can deploy non-interactively. Scoped to
# exactly these units + verbs — not blanket root.
cat > /etc/sudoers.d/deploy <<SUDO
Cmnd_Alias DEPLOY_SVC = /usr/bin/systemctl restart elephantlaboratories, /usr/bin/systemctl start elephantlaboratories, /usr/bin/systemctl stop elephantlaboratories, /usr/bin/systemctl restart organism, /usr/bin/systemctl start organism, /usr/bin/systemctl stop organism
$APP_USER ALL=(root) NOPASSWD: DEPLOY_SVC
SUDO
chmod 440 /etc/sudoers.d/deploy
visudo -cf /etc/sudoers.d/deploy          # validate; abort provisioning if malformed
usermod -aG systemd-journal "$APP_USER"   # `journalctl -u <svc>` without sudo

# ── 7. nginx — holding page now; real TLS vhosts written but not enabled ──────
msg "writing nginx configs"
install -d -m755 /var/www/html
echo "migrating…" > /var/www/html/index.html

# HTTP default: serves the holding page + ACME challenges for any host
cat > /etc/nginx/sites-available/000-holding <<'NG'
server {
    listen 80 default_server;
    listen [::]:80 default_server;
    server_name _;
    root /var/www/html;
    location /.well-known/acme-challenge/ { root /var/www/html; }
    location / { try_files $uri $uri/ =200; }
}
NG
ln -sf /etc/nginx/sites-available/000-holding /etc/nginx/sites-enabled/000-holding
rm -f /etc/nginx/sites-enabled/default

# shared TLS settings (self-contained; no dependency on certbot include files)
cat > /etc/nginx/snippets/tls.conf <<'NG'
ssl_protocols TLSv1.2 TLSv1.3;
ssl_prefer_server_ciphers off;
ssl_session_cache shared:SSL:10m;
ssl_session_timeout 1d;
add_header Strict-Transport-Security "max-age=31536000" always;
NG

# the "static-first, then proxy to the app" location block, shared by el + prism
_proxy_block() { cat <<'NG'
    location / {
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Host $http_host;
        proxy_redirect off;
        if ($request_method != GET) { proxy_pass http://__UPSTREAM__; break; }
        if (-f $request_filename)        { break; }
        if (-f $request_filename.html)   { rewrite (.*) $1.html break; }
        if (!-f $request_filename)       { proxy_pass http://__UPSTREAM__; break; }
    }
    error_page 500 502 503 504 /50x.html;
    location = /50x.html { root html; }
NG
}

# elephantlaboratories.com  -> 127.0.0.1:21112
{
  echo 'upstream elephantlaboratories { server 127.0.0.1:21112; }'
  echo 'server { server_name www.elephantlaboratories.com; listen 80; return 301 https://elephantlaboratories.com$request_uri; }'
  echo 'server { server_name elephantlaboratories.com; listen 80; return 301 https://elephantlaboratories.com$request_uri; }'
  echo 'server {'
  echo '    listen 443 ssl; listen [::]:443 ssl;'
  echo '    server_name elephantlaboratories.com;'
  echo '    ssl_certificate /etc/letsencrypt/live/elephantlaboratories.com/fullchain.pem;'
  echo '    ssl_certificate_key /etc/letsencrypt/live/elephantlaboratories.com/privkey.pem;'
  echo '    include snippets/tls.conf;'
  echo '    root /home/'"$APP_USER"'/elephantlaboratories/resources/communal;'
  echo '    index index.html;'
  _proxy_block | sed 's/__UPSTREAM__/elephantlaboratories/g'
  echo '}'
} > /etc/nginx/sites-available/elephantlaboratories.com

# prismofeverything.com  -> 127.0.0.1:21113  (same JVM, prism-server)
{
  echo 'upstream prismofeverything { server 127.0.0.1:21113; }'
  echo 'server { server_name www.prismofeverything.com; listen 80; return 301 https://prismofeverything.com$request_uri; }'
  echo 'server { server_name prismofeverything.com; listen 80; return 301 https://prismofeverything.com$request_uri; }'
  echo 'server {'
  echo '    listen 443 ssl; listen [::]:443 ssl;'
  echo '    server_name prismofeverything.com;'
  echo '    ssl_certificate /etc/letsencrypt/live/prismofeverything.com/fullchain.pem;'
  echo '    ssl_certificate_key /etc/letsencrypt/live/prismofeverything.com/privkey.pem;'
  echo '    include snippets/tls.conf;'
  echo '    root /home/'"$APP_USER"'/elephantlaboratories/resources/public;'
  echo '    index index.html;'
  _proxy_block | sed 's/__UPSTREAM__/prismofeverything/g'
  echo '}'
} > /etc/nginx/sites-available/prismofeverything.com

# playorganism.io  -> 127.0.0.1:11551  (HTTP + WebSocket on /ws)
cat > /etc/nginx/sites-available/playorganism.io <<NG
upstream playorganism { server 127.0.0.1:11551; }
server { server_name www.playorganism.io playorganism.io; listen 80;
         return 301 https://playorganism.io\$request_uri; }
server {
    listen 443 ssl; listen [::]:443 ssl;
    server_name playorganism.io;
    ssl_certificate /etc/letsencrypt/live/playorganism.io/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/playorganism.io/privkey.pem;
    include snippets/tls.conf;
    root /home/$APP_USER/organism/resources/public;
    index index.html;

    location /ws {
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection "Upgrade";
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_read_timeout 86400s;
        proxy_send_timeout 86400s;
        proxy_pass http://playorganism;
    }
    location / {
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        if (\$request_method != GET)  { proxy_pass http://playorganism; break; }
        if (-f \$request_filename)      { break; }
        if (-f \$request_filename.html) { rewrite (.*) \$1.html break; }
        if (!-f \$request_filename)     { proxy_pass http://playorganism; break; }
    }
    error_page 500 502 503 504 /50x.html;
    location = /50x.html { root html; }
}
NG

# OPTIONAL static-only sites (HTTP). Enable in 03 only if you kept them.
for s in ryanspangler.com:ryanspangler/resources/public \
         metamagicreality.com:metamagicreality/resources/public \
         repressilator.com:repressilator \
         lisamarahrens.com:lisamarahrens; do
  name="${s%%:*}"; path="${s##*:}"
  # ryanspangler.com additionally exposes an autoindexed /music archive
  music=""
  [ "$name" = "ryanspangler.com" ] && music='    location /music { autoindex on; }'
  cat > "/etc/nginx/sites-available/$name" <<NG
server {
    listen 80;
    server_name $name *.$name;
    root /home/$APP_USER/$path;
    index index.html;
    location / { try_files \$uri \$uri/ \$uri/index.html \$uri.html =404; }
$music
}
NG
done

nginx -t && systemctl reload nginx
msg "nginx up with holding page; TLS vhosts written to sites-available (enabled by 03)"

# ── 8. firewall (only 22/80/443 public; app ports stay localhost) ─────────────
msg "configuring ufw"
ufw allow OpenSSH
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable
systemctl enable --now fail2ban

cat <<EOF

============================================================================
  Provisioned. Stack: nginx + OpenJDK 17 + MongoDB 8.0 + certbot + git + ufw
============================================================================
Next:
  1) rsync your laptop's ~/elabs-migration bundle up to this box, e.g.:
        rsync -avz ~/elabs-migration/  $APP_USER@THIS_IP:~/elabs-migration/
  2) sudo bash 03-restore-data.sh ~/elabs-migration     # restores mongo/tracks/git/certs, enables TLS sites
  3) deploy the jars from your laptop:
        # elephantlaboratories repo:  lein uberjar
        scp target/uberjar/elephantlaboratories.jar $APP_USER@THIS_IP:~/elephantlaboratories/
        # organism repo:              ./deploy.sh build   (or scp organism.jar + *.json)
        scp target/uberjar/organism.jar organism/game_library.json organism/lineage.json $APP_USER@THIS_IP:~/organism/
        ssh $APP_USER@THIS_IP 'sudo systemctl restart elephantlaboratories organism'
  4) verify before DNS flip (certs are the restored ones, valid already):
        curl --resolve elephantlaboratories.com:443:THIS_IP https://elephantlaboratories.com -I
  5) flip DNS A records to THIS_IP, then:  sudo certbot renew --dry-run
============================================================================
EOF
