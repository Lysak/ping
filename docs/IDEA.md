# PING — Idea

## One line

A tiny, beautiful, native Android app that does one thing: ping a host and show
the latency. No ads, no trackers, no accounts, no Firebase, no push. Open source.

## Why it exists

The app I use today (`com.hsteen.ping`, "p!ng") works fine but is named with a
`!` so it is unfindable in Play Store search. I want a clean replacement called
**PING** that is easy to find, pretty, and does exactly as much as it needs to.

## Reference app analysis — `com.hsteen.ping`

Pulled from the Play Store listing (full description + metadata):

- **Store name:** "Ping" · tagline "Simple ping for android" · category *Tools* ·
  "Rated for 3+".
- **What it does (developer's words):** "Ping is a network utility used to test
  reachability of an IP address or a host. Ping uses ICMP for request packets
  and waits for an ICMP response. Ping is useful to check a server's
  availability."
- **Mechanism:** real **ICMP** echo request/reply (not HTTP). On Android this is
  done by shelling out to the system `ping` binary and parsing `time=… ms`.
- **Latest changelog:** "Enhanced compatibility for the latest Android versions
  (Android 16 / API 36). General performance optimizations and minor bug fixes."
- **Known weak spot (from a user review):** after upgrading to Android 15 the
  host input bar rendered *under* the system navigation bar and became unusable
  — an edge-to-edge / window-insets regression. **We must get insets right.**
- **UX shape:** one screen, a host input field, a start control, a running
  readout. It keeps pinging until you stop it.

## What PING does (v1)

- One screen, two layers: a clean **hero** (big latency number, wave pulse,
  `min · avg · max · stddev · loss%`, sparkline) and a **geek console** below
  that prints real `ping`-style lines and the standard statistics block.
- One big **PING** button. Tap to start, tap to stop.
- A **target selector**: a chip showing the current host; tap it to open a
  picker.
- **Default hosts (not deletable):** `Google — 8.8.8.8`, `Cloudflare — 1.1.1.1`.
- **Add your own host:** label + hostname/IP, validated, saved on the device.
  Custom hosts can be deleted. No cloud sync — device-local only.
- Copy / share the full transcript as plain text.
- Pinging runs only while the app is on screen. Closing or stopping ends it.

## How it's actually built (the story for the store description)

Real ICMP echo, done the right way: PING opens an **unprivileged ICMP datagram
socket** (`SOCK_DGRAM` / `IPPROTO_ICMP`) and sends the echo packets itself — no
root, no `su`, no shelling out to the `ping` binary, no bundled native blob.
Round-trip time is measured with `System.nanoTime()` and reported to 1/100 ms,
with live **jitter / standard deviation** that the app it replaces doesn't even
show. Output matches BSD/Linux `ping` line for line. Falls back to the system
`ping` binary, then to TCP-connect timing, only when a network blocks ICMP.
Package: `com.lysak.ping`. Palette and typography match **metiq**.

## Explicitly NOT in scope

- No iOS, no desktop, no web / Telegram mini-app. Android only.
  (A browser cannot do ICMP at all — only `fetch()` timing — so a web port would
  be a different, worse app.)
- No accounts, no analytics, no crash reporting SDK, no ads, no push, no
  background service, no widgets, no notifications.

## Design direction

Match the **look and feel** of **metiq** (`github.com/metiq-xyz/android-app`):
Inter variable font, a small hand-rolled color-token system, optional dynamic
color, tasteful Canvas animations (wave rings), dark theme by default. **Same
colour palette as metiq** (its exact tokens).

The **design system is Material 3 Expressive** (`MaterialExpressiveTheme`) —
metiq itself is on plain Material 3, so it is a visual reference only, not a
technical one. See `ARCHITECTURE.md`.

## Play Store

- Developer account: already owned.
- Title: `Ping — No Ads` (launcher label stays just `PING`).
- Fully open source; repo linked from the listing and an in-app "Source code"
  link.
- **License: GPL-3.0-or-later**, same as metiq — no closed, ad-supported fork
  possible, and metiq code can be reused with credit.
