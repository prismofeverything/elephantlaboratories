# Server migration plan

Moving off the old DigitalOcean droplet (`138.197.213.77`, "elephant-laboratories")
onto a fresh box, unifying the three live sites and shedding the cruft.

Everything here is derived from the read-only inventory you already ran
(`script/server-inventory.sh` → `script/inventory.tgz`) plus the app repos.

## Decisions (locked 2026-07-06)

- **Architecture:** unify the 3 live sites on ONE new droplet now; tetrahedron gets
  its own server later, when it's actually built.
- **Host:** DigitalOcean **Premium droplet, 2 vCPU / 4 GB / ~80 GB NVMe, NYC**,
  Ubuntu 24.04 LTS, + 2 GB swap + weekly snapshots.
- **Carry the peripherals too:** ryanspangler.com (+ /music), metamagicreality.com,
  repressilator.com, lisamarahrens.com — all kept and re-served.
- **Drop Postgres:** verified no site uses it (both apps are MongoDB-only; the static
  sites have no backend; only user `zyuu`'s dead DB lives there). `01` dumps `zyuu` to
  `postgres/zyuu.sql.gz` for cold storage; `02` never installs Postgres.

---

## 1. What's actually on the old server

**The box:** DigitalOcean droplet, NYC (`138.197.208.0/20`). 2 vCPU (Xeon E5-2650 v4),
**2 GB RAM, 0 swap**, 40 GB disk (21 GB used / 54 %). **Ubuntu 16.04.6** — end-of-life
since April 2021, kernel from 2018, **uptime 3052 days** (never patched, never rebooted).
This is the single biggest reason to move: it is one power-cycle or one exploit away from
being unrecoverable, and it cannot be safely upgraded in place across 5 Ubuntu releases.

**The three live sites and how they run:**

| Site | Backend | How it runs today | Data |
|---|---|---|---|
| **elephantlaboratories.com** | `elephantlaboratories.jar` JVM, port **21112** | bare `nohup java …`, no supervisor | MongoDB (site content, mailing list) |
| **prismofeverything.com** | *same* JVM, port **21113** (`prism-server`) | *same process as above* | track library in `/home/ryan/prismofeverything` (**2.7 GB, not in git**) + MongoDB |
| **playorganism.io** | `organism.jar` JVM, port **11551** (HTTP+WS) | bare `nohup java …`, no supervisor | MongoDB db `organism`, + committed `game_library.json` / `lineage.json` |

Both JVMs are started by hand (`release.py` / `deploy.sh` → `kill $(cat …pid); nohup java …`).
There is **no systemd, no auto-restart, no swap** — if either process or the box dies, the
sites stay down until you SSH in. Fixing this is part of the migration.

**Shared services:** nginx (reverse proxy + TLS), **MongoDB 3.6** (3.6 GB, the real
database for all three), **Postgres 9.5** (only holds user `zyuu`'s abandoned db — dead weight),
and a `/srv/git` set of bare repos (`tetrahedron`, `prism`, `monad`, `totality`, `coda`,
`parsimony`) served over `git-shell` — you're **already self-hosting git**.

**TLS is quietly broken:** `certbot.service` is in a *failed* state (certbot 0.31, from 2019).
Renewals aren't happening cleanly. The new box gets a modern certbot.

**Also present but peripheral:** `ryanspangler.com` (+ a `/music` archive, ~3 GB under
`/home/ryan/music`), three HTTP-only static sites (`lisamarahrens.com`,
`metamagicreality.com`, `repressilator.com`), the `abby` user account (idle since 2017),
and `elabs-legacy` (2.1 GB, the old sold-out shop).

---

## 2. One server or many? — recommendation

**Unify the three live sites onto ONE new droplet. Keep `tetrahedron` on its own box, later.**

Why unify the three: they already share nginx, a JVM, and one MongoDB; their combined
traffic is tiny (elephantlaboratories is near-static; prismofeverything is audio files served
off disk; organism's websockets are low-volume today). One box means one OS to patch, one
TLS setup, one backup, one bill. Splitting them would triple the ops work for no benefit at
today's load.

Why keep **tetrahedron separate**: it is the wildcard. It's currently an empty stub, but its
*intended* workload is the exact opposite of the calm company sites —

- **interactive streaming audio**: prism/synth already prototypes this (hand-rolled WebSocket
  pushing **uncompressed PCM, ~1.5 Mbit/s per listener**; CPU scales with concurrent live
  synth engines, each single-threaded). Bandwidth- and CPU-spiky.
- **git hosting**: cheap (you already do it), can live anywhere.
- **"maybe lightweight video conferencing"**: this does **not exist yet** and has no code —
  it means WebRTC + a TURN/SFU server, which is the single most bandwidth/CPU-hungry thing
  on your whole roadmap.

You do **not** want an experimental audio/video engine sharing a 2-4 GB box with
elephantlaboratories.com (your public face). A runaway synth session or a video call should
never be able to take the company site down. So: **tetrahedron gets its own server when it's
real** — sized for audio egress and (if you build video) a TURN server. Until then it costs
nothing.

Middle ground if you insist on one box: run everything on a bigger droplet and fence
tetrahedron off with systemd `MemoryMax=`/`CPUQuota=` or a container. Workable, but you've
added blast-radius risk to save ~$15/mo. Not worth it for the company site.

**Net:** Server A = "the calm cluster" (elephantlaboratories + prismofeverything + organism),
build now. Server B = tetrahedron, build when you start building tetrahedron. Git hosting
rides on whichever; it's featherweight.

---

## 3. Where to host

You're on DigitalOcean and there's no strong reason to leave. Options, honestly compared:

| Option | ~Cost/mo | For / against |
|---|---|---|
| **DigitalOcean Premium droplet** — 2 vCPU / **4 GB** / ~80 GB NVMe, NYC *(recommended for Server A)* | ~$24–28 | You already know DO; NYC latency is good for a US audience; per-second billing; snapshots/backups one click. 2× the RAM & disk of today with room for Mongo + both JVMs + tracks. |
| **Hetzner Cloud (Ashburn US)** — CPX31-ish, 4 vCPU / 8 GB / 160 GB | ~$16–22 | More hardware per dollar even after Hetzner's **June 2026 US price hike**; but US regions now have **smaller traffic allowances (1–8 TB)** and it's a new provider to learn. Great value, especially for the future tetrahedron audio box. |
| **Stay put / resize old droplet** | — | ❌ Not an option — the OS is EOL and can't be upgraded in place across 5 releases. A fresh box is mandatory regardless. |
| Managed Mongo (DO/Atlas) | +$15+ | Not worth it — your Mongo is 3.6 GB, no auth, single-node. Self-host it; snapshot the droplet. |

**Recommendation:** DigitalOcean **Premium AMD/Intel, 2 vCPU / 4 GB / 80 GB, NYC**, plus a
**2 GB swap file** (today's box has none — a real liability at 2 GB RAM), plus DO's weekly
snapshot backups ($1–2/mo). When tetrahedron is real, put it on a **Hetzner Ashburn** box
sized for audio/video — that's where Hetzner's price/performance shines.

**OS:** **Ubuntu 24.04 LTS (Noble)** — mature, every third-party repo (MongoDB, certbot)
supports it today, supported to 2029 (2034 ESM). 26.04 LTS exists (April 2026) but is only
months old and some apt repos lag; not worth the bleeding edge for a set-and-forget host.

---

## 4. Target architecture (Server A)

```
                         nginx  :80 → 301 → :443 (TLS, Let's Encrypt)
  ┌──────────────────────────────────────────────────────────────────┐
  │  elephantlaboratories.com  ──proxy──▶ 127.0.0.1:21112  ┐          │
  │  prismofeverything.com     ──proxy──▶ 127.0.0.1:21113  ├─ ONE JVM │  systemd:
  │      (static tracks from /home/ryan/prismofeverything) ┘  (el.jar)│  elephantlaboratories.service
  │  playorganism.io    ──proxy + /ws──▶ 127.0.0.1:11551  ── JVM      │  organism.service
  │  (+ optional: ryanspangler.com, the 3 static sites)              │
  └──────────────────────────────────────────────────────────────────┘
        MongoDB (localhost:27017)  ◀── all JVMs      /srv/git (bare repos, git-shell)
```

Changes vs. today, all improvements:
- **systemd units** replace the `nohup`+pidfile hack → auto-restart on crash/reboot, proper logs.
- **swap** added; **ufw** allows only 22/80/443 (the app ports stay localhost-only — today
  11551/21112/21113 are needlessly world-listening).
- **Postgres dropped** (dump `zyuu` to cold storage first, then gone).
- **certbot** modernized; renewals actually work.
- Unified paths: organism's nginx currently points `root` at `/home/cosmos/organism` while the
  jar deploys to `/home/ryan/organism` — reconciled to `/home/ryan/organism` on the new box.

---

## 5. What to carry vs. shed

**Carry (data that isn't in git and can't be rebuilt):**
- MongoDB — full dump/restore (all dbs; `organism` + elephantlaboratories').
- `/home/ryan/prismofeverything` — the track library (2.7 GB). **Critical.**
- `/etc/letsencrypt` — certs + account, so the new box serves TLS the instant DNS flips.
- App secrets/config: `~/.keys`, any server-side `dev-config.edn` / prod conf, `~git/.ssh`.
- `/srv/git` bare repos + the `git` user setup.
- `~/.ssh/authorized_keys` (so you can log in).

**Rebuild (don't copy — comes from git / a fresh build):**
- Both jars → `lein uberjar` / `deploy.sh build` from the repos on your laptop.
- ClojureScript, node_modules, `~/.m2`, `~/.nvm`, target dirs — all regenerated.

**Shed (consciously drop):**
- Ubuntu 16.04, Postgres 9.5, the `abby` account, `elabs-legacy`, `oldprismofeverything`,
  old inventory tarballs, `~/.weechat`, etc.
- **Decide:** `ryanspangler.com` + its 3 GB music archive, and the three HTTP-only static
  sites (`lisamarahrens`, `metamagicreality`, `repressilator`). Keeping the ones that are
  *yours* is nearly free (static files + one nginx block each); `lisamarahrens.com` is
  someone else's — carry only if you still host for them.

The pull step (`01-pull-from-old-server.sh` + `pull-to-laptop.sh`) copies **all** the "carry"
items and rsyncs the static sites too, so nothing is lost; you choose at provision time which
nginx blocks to actually enable.

---

## 6. Runbook

Six scripts in this directory, run in order. Each runs on a specific machine and ends by
naming the next one — **none of them dump commands for you to copy-paste and run.** Read each
before running; they're commented. The three server-side steps are numbered (`01`/`02`/`03`);
the three laptop-side "bridge" steps that move data between machines are verb-named.

| # | Script | Run on | Does |
|---|---|---|---|
| 1 | `01-pull-from-old-server.sh` | **old server** (`sudo`) | dumps Mongo + Postgres(`zyuu`), copies certs / secrets / `/srv/git`, stages `~/migrate-out/`. Read-only w.r.t. running services. |
| 2 | `pull-to-laptop.sh` | **laptop** | rsyncs the bundle **and** the big media dirs down to `~/elabs-migration`. Idempotent — re-run to catch late track additions. |
| 3 | `02-provision-new-server.sh` | **new box** (as root) | nginx + OpenJDK 17 + MongoDB 8.0 + ffmpeg + certbot + git + ufw, swap, creates the `prism`/`git` users (copies root's SSH key to `prism`), dirs, **systemd units**, nginx vhosts, firewall. Idempotent. |
| 4 | `push-to-new-server.sh` | **laptop** | rsyncs `~/elabs-migration` up to the new box. Idempotent. |
| 5 | `03-restore-data.sh [dir]` | **new box** (as root) | restores Mongo, tracks, `/srv/git`, secrets, `/etc/letsencrypt` into `/home/prism`; enables each TLS vhost whose cert is present. Idempotent. |
| 6 | `deploy-and-verify.sh` | **laptop** | builds + ships the jars (elephantlaboratories/prism via `release.py`; organism via `ORGANISM_REPO=… ./deploy.sh`) and curl-verifies all three sites over TLS with `--resolve`, before any DNS change. |

Host is passed by env var, so nothing is pinned to a machine:
- `OLD=ryan@<old-ip-or-.com>` for step 2 (defaults to `ryan@elephantlaboratories.com` — still
  the old box, user `ryan`, pre-cutover). `DRY_RUN=1` previews; `SKIP_PERIPHERALS=1` pulls only
  the critical dirs.
- The new box starts with only `root`; `02` creates the app user **`prism`** (which has no sudo
  password by design). So run `02`/`03` and the **push as root**, and the app **deploy as `prism`**:
  `NEW=root@tetrahedron.world` for the push (step 4), `NEW=prism@tetrahedron.world` for
  deploy-and-verify (step 6). Because `02` copies root's `authorized_keys` to `prism`, `prism@…`
  works the moment provisioning finishes.

`02` installs a scoped `/etc/sudoers.d/deploy` and adds `prism` to `systemd-journal`, so
`release.py` / `deploy.sh` can `systemctl restart` and read journals over SSH with no password.
organism's `game_library.json` / `lineage.json` land once via `03`; re-scp them only if you
change the seed data.

7. **Cutover** — the one genuinely manual step, because it happens at your DNS registrar, not
   on a box we control. Run `deploy-and-verify.sh` until every check is green (it tests each
   site via `--resolve` while DNS still points at the old box), then change **only these
   A-records** from the old IP (`138.197.213.77`) to the new one: `elephantlaboratories.com`,
   `www.elephantlaboratories.com`, `prismofeverything.com`, `www.prismofeverything.com`,
   `playorganism.io`, `www.playorganism.io`, and any static sites you kept. Re-check with
   `deploy-and-verify.sh --verify-only`.

   ⚠️ **Do NOT touch `shop.elephantlaboratories.com`** — it's a `CNAME` to `shops.myshopify.com`
   (the Shopify store, hosted by Shopify, not the droplet). It is *not* an A-record and is *not*
   in the list above; changing the apex A-record does not affect this independent subdomain.
   Likewise leave `MX` / `TXT` (SPF, DKIM, domain-verification) / `CAA` records alone — the IP
   flip concerns only the A-records above, so Shopify's store, Buy links, and order email are
   untouched.

   After 48 h of clean logs and a successful `certbot renew --dry-run`, snapshot and destroy the
   old droplet.

## 7. Risks / watch-items
- **Mongo 3.6 → 8.0 is a 5-major jump.** `mongodump`/`mongorestore` of plain BSON documents
  handles this fine (the data is small and auth-free), but eyeball the collection counts after
  restore (`03` prints them). If an old index type warns, it's cosmetic — rebuild it.
- **DNS TTL:** lower the A-record TTL to 300 s a day *before* cutover so the flip is fast and
  reversible.
- **The tracks dir is the source of truth for prismofeverything** (the app rescans it live) —
  make sure the rsync completes before cutover, and re-run `release.py sync` to the new host
  afterward for anything added in between.
- Keep the old droplet powered on (not destroyed) for at least a few days post-cutover as a
  rollback.
