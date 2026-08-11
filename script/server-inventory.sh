#!/usr/bin/env bash
#
# Inventory an existing server so it can be reproduced elsewhere.
# Run on the OLD server. Read-only — never modifies anything.
#
# Usage:
#   sudo ./server-inventory.sh [output-dir]
#
# Default output dir: ~/server-inventory-YYYYMMDD
#
# When done, tar it up and scp it to your laptop:
#   tar czf inventory.tgz -C ~ server-inventory-YYYYMMDD
#   # then from your laptop:  scp user@server:inventory.tgz .

set -u  # don't set -e — we want to keep going if individual probes fail

OUT="${1:-$HOME/server-inventory-$(date +%Y%m%d)}"
mkdir -p "$OUT"

# Run a command, write its output (and stderr) to a file, with a header.
# usage:  cap <outfile> <cmd...>
cap() {
  local f="$OUT/$1"; shift
  {
    echo "## $*"
    echo "## $(date -Iseconds)"
    echo "----"
    "$@" 2>&1 || echo "[command failed: $*]"
    echo
  } >> "$f"
}

# Copy a path (file or directory) into the output, preserving structure.
copy_into() {
  local src="$1" dest="$2"
  if [[ -e "$src" ]]; then
    mkdir -p "$OUT/$dest"
    cp -aL "$src" "$OUT/$dest/" 2>/dev/null \
      || cp -a  "$src" "$OUT/$dest/" 2>/dev/null \
      || echo "[copy failed: $src]" >> "$OUT/copy-errors.log"
  fi
}

echo "Writing inventory to: $OUT"
echo

# ---- 1. OS / kernel / hardware ------------------------------------------
echo "[*] OS and hardware"
cap os.txt            uname -a
cap os.txt            cat /etc/os-release
cap os.txt            lsb_release -a
cap os.txt            uptime
cap os.txt            hostnamectl
cap hardware.txt      lscpu
cap hardware.txt      free -h
cap hardware.txt      lsblk
cap hardware.txt      df -hT
cap hardware.txt      cat /proc/meminfo

# ---- 2. Network ---------------------------------------------------------
echo "[*] Network"
cap network.txt       ip addr
cap network.txt       ip route
cap network.txt       cat /etc/resolv.conf
cap network.txt       cat /etc/hosts
# listening sockets — different flags on old systems, try both
cap listening.txt     ss -tulnp
cap listening.txt     netstat -tulnp

# ---- 3. Firewall --------------------------------------------------------
echo "[*] Firewall"
cap firewall.txt      ufw status verbose
cap firewall.txt      iptables -L -n -v
cap firewall.txt      iptables -t nat -L -n -v
cap firewall.txt      nft list ruleset

# ---- 4. Packages --------------------------------------------------------
echo "[*] Packages"
# dpkg --get-selections is too noisy; manually-installed is what we want
cap packages.txt      bash -c 'apt-mark showmanual | sort'
cap packages-all.txt  bash -c 'dpkg -l | awk "NR>5"'
cap repos.txt         bash -c 'ls -la /etc/apt/sources.list /etc/apt/sources.list.d/ 2>/dev/null'
copy_into /etc/apt/sources.list etc-apt
copy_into /etc/apt/sources.list.d etc-apt

# ---- 5. Services --------------------------------------------------------
echo "[*] Services"
cap services.txt      systemctl list-unit-files --state=enabled
cap services.txt      systemctl list-units --type=service --state=running
cap services.txt      systemctl --failed
# anything in /etc/systemd/system is almost certainly custom
copy_into /etc/systemd/system etc-systemd

# ---- 6. Cron ------------------------------------------------------------
echo "[*] Cron jobs"
cap cron.txt          ls -la /etc/cron.d /etc/cron.daily /etc/cron.hourly /etc/cron.weekly /etc/cron.monthly
copy_into /etc/crontab           etc-cron
copy_into /etc/cron.d            etc-cron
copy_into /etc/cron.daily        etc-cron
copy_into /etc/cron.hourly       etc-cron
copy_into /etc/cron.weekly       etc-cron
copy_into /etc/cron.monthly      etc-cron
# per-user crontabs
{
  echo "## per-user crontabs"
  for u in $(cut -d: -f1 /etc/passwd); do
    out="$(crontab -u "$u" -l 2>/dev/null)" || continue
    [[ -z "$out" ]] && continue
    echo "---- crontab for $u ----"
    echo "$out"
    echo
  done
} >> "$OUT/cron.txt"

# ---- 7. Users -----------------------------------------------------------
echo "[*] Users"
cap users.txt         bash -c 'awk -F: "(\$3>=1000 && \$3<65534){print}" /etc/passwd'
cap users.txt         bash -c 'getent group sudo; getent group adm; getent group wheel 2>/dev/null'
cap users.txt         bash -c 'ls -la /home/'
# list each user's home dir top-level (no contents copied)
{
  echo "## /home/* top-level listings"
  for h in /home/*; do
    [[ -d "$h" ]] || continue
    echo "---- $h ----"
    ls -la "$h" 2>/dev/null
    echo
  done
} >> "$OUT/users.txt"

# ---- 8. Web servers -----------------------------------------------------
echo "[*] Web servers"
# nginx
if command -v nginx >/dev/null 2>&1; then
  cap nginx.txt       nginx -v
  cap nginx.txt       nginx -T          # dumps full effective config + included files
  copy_into /etc/nginx etc-web
fi
# apache
if command -v apache2 >/dev/null 2>&1 || command -v httpd >/dev/null 2>&1; then
  cap apache.txt      bash -c 'apache2 -v 2>/dev/null || httpd -v'
  cap apache.txt      bash -c 'apache2ctl -S 2>/dev/null || httpd -S'
  cap apache.txt      bash -c 'apache2ctl -M 2>/dev/null || httpd -M'
  copy_into /etc/apache2 etc-web
  copy_into /etc/httpd   etc-web
fi
# document roots commonly hold static sites
cap www-dirs.txt      bash -c 'ls -la /var/www/ 2>/dev/null'
cap www-dirs.txt      bash -c 'find /var/www -maxdepth 3 -type d 2>/dev/null'
cap www-dirs.txt      bash -c 'du -sh /var/www/* 2>/dev/null'

# ---- 9. TLS / Let's Encrypt --------------------------------------------
echo "[*] TLS certificates"
if command -v certbot >/dev/null 2>&1; then
  cap letsencrypt.txt certbot --version
  cap letsencrypt.txt certbot certificates
fi
# list cert names + expiry without exposing the keys
if [[ -d /etc/letsencrypt/live ]]; then
  {
    echo "## /etc/letsencrypt/live"
    for d in /etc/letsencrypt/live/*/; do
      [[ -d "$d" ]] || continue
      name="$(basename "$d")"
      cert="$d/cert.pem"
      if [[ -r "$cert" ]]; then
        echo "---- $name ----"
        openssl x509 -in "$cert" -noout -subject -issuer -dates -ext subjectAltName 2>&1
        echo
      else
        echo "---- $name (cert.pem not readable) ----"
      fi
    done
  } >> "$OUT/letsencrypt.txt"
  # copy renewal config (no private keys) so we know how each cert is managed
  if [[ -d /etc/letsencrypt/renewal ]]; then
    copy_into /etc/letsencrypt/renewal etc-letsencrypt
  fi
fi

# ---- 10. Databases ------------------------------------------------------
echo "[*] Databases"
# MySQL / MariaDB — list databases only, no contents
if command -v mysql >/dev/null 2>&1; then
  cap databases.txt   bash -c 'mysql -V'
  cap databases.txt   bash -c 'mysql -e "SHOW DATABASES;" 2>&1 || echo "(could not connect — check ~/.my.cnf or run as root)"'
fi
# Postgres
if command -v psql >/dev/null 2>&1; then
  cap databases.txt   bash -c 'psql --version'
  cap databases.txt   bash -c 'sudo -u postgres psql -l 2>&1 || echo "(could not list — postgres not running, or auth required)"'
fi
# Redis
if command -v redis-cli >/dev/null 2>&1; then
  cap databases.txt   bash -c 'redis-cli --version'
  cap databases.txt   bash -c 'redis-cli info server 2>&1 | head -20'
fi

# ---- 11. App / project dirs --------------------------------------------
echo "[*] App and project locations"
cap custom-dirs.txt   bash -c 'ls -la /opt /srv 2>/dev/null'
cap custom-dirs.txt   bash -c 'find /opt /srv -maxdepth 3 -type d 2>/dev/null'
# find any git checkouts that might be a deployed app
cap git-checkouts.txt bash -c '
  find /var/www /opt /srv /home -maxdepth 5 -type d -name .git 2>/dev/null | while read g; do
    repo="$(dirname "$g")"
    url="$(git -C "$repo" remote get-url origin 2>/dev/null || echo "(no origin)")"
    branch="$(git -C "$repo" symbolic-ref --short HEAD 2>/dev/null || echo "?")"
    echo "$repo  [$branch]  $url"
  done
'

# ---- 12. Processes ------------------------------------------------------
echo "[*] Processes"
cap processes.txt     bash -c 'ps auxf'

# ---- 13. Disk usage hot-spots ------------------------------------------
echo "[*] Disk usage"
cap disk-usage.txt    df -hT
cap disk-usage.txt    bash -c 'du -h --max-depth=1 / 2>/dev/null | sort -rh | head -30'
cap disk-usage.txt    bash -c 'du -h --max-depth=2 /var 2>/dev/null | sort -rh | head -20'
cap disk-usage.txt    bash -c 'du -h --max-depth=2 /home 2>/dev/null | sort -rh | head -20'

# ---- 14. SSH config -----------------------------------------------------
echo "[*] SSH"
copy_into /etc/ssh/sshd_config       etc-ssh
copy_into /etc/ssh/sshd_config.d     etc-ssh

# ---- 15. Mail (if anything's configured) -------------------------------
if [[ -f /etc/postfix/main.cf ]] || [[ -d /etc/exim4 ]]; then
  echo "[*] Mail"
  copy_into /etc/postfix etc-mail
  copy_into /etc/exim4   etc-mail
  cap mail.txt          bash -c 'postconf -n 2>/dev/null'
fi

# ---- summary ------------------------------------------------------------
{
  echo "Server inventory captured $(date -Iseconds)"
  echo "Host: $(hostname -f 2>/dev/null || hostname)"
  echo
  echo "OS:"
  grep -E '^(NAME|VERSION)=' /etc/os-release 2>/dev/null | sed 's/^/  /'
  echo
  echo "Output files:"
  ( cd "$OUT" && find . -type f | sort | sed 's/^/  /' )
} > "$OUT/00-summary.txt"

# Package it right here — no reason to make you type the tar command yourself.
TARBALL="$(dirname "$OUT")/inventory.tgz"
if tar czf "$TARBALL" -C "$(dirname "$OUT")" "$(basename "$OUT")"; then
  PACKAGED="$TARBALL  ($(du -h "$TARBALL" 2>/dev/null | cut -f1))"
else
  PACKAGED="(tar failed — package $OUT by hand)"
fi

echo
echo "Done."
echo "Inventory dir:    $OUT"
echo "Packaged tarball: $PACKAGED"
echo
echo "Only remaining step runs FROM YOUR LAPTOP (this box can't push to it):"
echo "  scp <user>@<this-host>:$TARBALL ."
