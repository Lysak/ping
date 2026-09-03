# PING — Architecture

Status: **draft for review.** Nothing is built yet. This describes the target
shape of the app so implementation is a straight line.

## Goals

- **Native, tiny, beautiful.** Kotlin + Jetpack Compose + Material 3 Expressive.
- **No overhead.** Minimal dependency list, R8 + resource shrinking, no service,
  no analytics, no reflection-heavy DI.
- **Wide device coverage.** As low a `minSdk` as the modern toolchain allows.
- **Correct ICMP ping**, with a graceful fallback when raw ICMP is unavailable.

## Tech stack (2024–2026 current)

| Concern            | Choice                                                             |
|--------------------|-------------------------------------------------------------------|
| Language           | Kotlin `2.x`                                                       |
| UI                 | Jetpack Compose (BOM) + **Material 3 Expressive** (`MaterialExpressiveTheme`) |
| Build              | Gradle Kotlin DSL + version catalog (`gradle/libs.versions.toml`)  |
| Async              | Kotlin coroutines + `Flow` (bundled with lifecycle libs)           |
| State holder       | `androidx.lifecycle` `ViewModel` + `lifecycle-viewmodel-compose`   |
| Persistence        | `androidx.datastore:datastore-preferences`                         |
| Launch UX          | `androidx.core:core-splashscreen`                                  |
| DI                 | **None.** Manual wiring via the `Application` object.              |
| Navigation         | **None.** One screen; the host picker is a modal bottom sheet.     |
| Icon set           | `material-icons-core` only (skip `-extended`, it is large)         |
| Font               | Inter variable (bundled `.ttf`), like metiq                        |

### Full dependency list (intentionally short)

```
androidx.core:core-ktx
androidx.activity:activity-compose
androidx.lifecycle:lifecycle-runtime-compose
androidx.lifecycle:lifecycle-viewmodel-compose
androidx.compose:compose-bom            (platform)
androidx.compose.ui:ui
androidx.compose.ui:ui-graphics
androidx.compose.material3:material3    (Material 3 Expressive — same artifact, use MaterialExpressiveTheme)
androidx.compose.material:material-icons-core
androidx.datastore:datastore-preferences
androidx.core:core-splashscreen
debugImplementation androidx.compose.ui:ui-tooling
```

No Retrofit / OkHttp / Hilt / Room / WorkManager / Firebase / Play Services.

## SDK levels

- **`minSdk = 24` (Android 7.0).** Compose's own floor is 21, but two required
  libraries push it up: `androidx.navigationevent` needs 23, and
  `androidx.compose.material3:material3` **1.5.0-alpha** (the first version where
  `MaterialExpressiveTheme` / `MotionScheme` are a public API — Expressive is an
  `AGENTS.md` non-negotiable) needs 24. API 24 still covers **~97%** of active
  devices. Drop back toward 23/21 only if the Expressive requirement is lifted.
- `compileSdk = 37`, `targetSdk = 37` (keep current for Play Store compliance).
- `enableEdgeToEdge()` + explicit `WindowInsets` handling everywhere. The add-host
  text field uses `imePadding()` + `navigationBarsPadding()`. (This is the exact
  bug that broke the reference app on Android 15 — do not repeat it.)

## The ping mechanism — "done properly"

This is the part worth bragging about in the store description. We do **not**
shell out to the `ping` binary and scrape its text as the primary method. We
send real ICMP echo packets ourselves.

### Primary: unprivileged ICMP datagram socket, pure Kotlin

Since Android 4.4, AOSP `init` sets `/proc/sys/net/ipv4/ping_group_range` to
`0 2147483647`, i.e. **every app may open a `SOCK_DGRAM` / `IPPROTO_ICMP`
socket** — no root, no `SOCK_RAW`, no NDK. (This is the same mechanism the
platform's own `InetAddress.isReachable` uses.) IPv6 works the same via
`IPPROTO_ICMPV6`.

`Pinger` uses `android.system.Os` directly:

- `Os.socket(AF_INET|AF_INET6, SOCK_DGRAM, IPPROTO_ICMP|IPPROTO_ICMPV6)`
- build the 8-byte ICMP echo header (type 8 / 128, code 0, our `icmp_seq`,
  a small timestamped payload); the kernel owns the identifier and checksum
- `Os.connect` to the resolved address, `Os.sendto`, then `Os.poll` with the
  per-packet timeout, then `Os.recvfrom`
- RTT = `System.nanoTime()` delta around send/receive, reported to **0.01 ms**
- match replies by `icmp_seq`; late replies after the timeout are counted as
  received-late, unmatched ones as loss (exactly like BSD `ping`)
- **TTL** of the reply: read via `Os.recvmsg` + `IP_RECVTTL` / `IPV6_RECVHOPLIMIT`
  on API 33+; on older devices TTL is shown as `—` (cosmetic only)

Pure Kotlin, testable off-device for the packet encode/decode and the stats
math. Only the socket calls touch the platform.

### Fallback 1: exec the system `ping`

If the datagram socket can't be created (a locked-down ROM that narrowed
`ping_group_range`), fall back to
`Runtime.exec(arrayOf("ping", "-n", "-c", "1", "-W", "<sec>", host))`, parse the
`icmp_seq= … ttl= … time= … ms` line. Still real ICMP, just less precise timing
and control.

### Fallback 2: TCP connect latency

If ICMP is blocked entirely (captive / enterprise Wi-Fi), measure
`Socket().connect(InetSocketAddress(host, 443), timeout)` wall time. The UI
tags these samples **TCP** so the number is never mistaken for ICMP RTT. Probe
port default 443.

### Result type

```
sealed interface PingSample {
    data class Reply(
        val seq: Int,
        val rttMs: Double,
        val ttl: Int?,           // null when unavailable
        val via: Method,         // ICMP | ICMP_EXEC | TCP
        val bytes: Int,
    ) : PingSample
    data class Lost(val seq: Int, val reason: Reason) : PingSample
    // Reason: TIMEOUT, UNREACHABLE, UNRESOLVED, NETWORK_DOWN, ERROR
}

suspend fun Pinger.probe(target: ResolvedTarget, seq: Int, timeoutMs: Int): PingSample
```

### Continuous mode + statistics

`PingViewModel` runs `while (running) { probe(seq++); delay(interval) }` in
`viewModelScope` (default interval **1000 ms**). It keeps two things:

1. **Cumulative run stats** — like real `ping`: `transmitted`, `received`,
   `lossPct`, and `min / avg / max / stddev` of RTT computed online with
   **Welford's algorithm** (no growing array; stddev is the "geek" number the
   reference app doesn't even show).
2. **A rolling window** — the last **N = 60** samples (the *history depth*),
   used only to draw the sparkline / bar strip. 60 samples ≈ the last minute at
   1 s interval. Purely visual; the stats above are for the whole run.

Both are exposed as one `StateFlow<PingUiState>`. The loop stops on: button
toggle, `ViewModel` cleared, or `Lifecycle` below `STARTED`
(`repeatOnLifecycle`). **No foreground service** — background pinging is out of
scope. `// ponytail: screen-scoped only; add a foreground service if background
monitoring is ever wanted.`

### The one real risk — verify first (spike)

Before building the UI, confirm on a real device + an emulator that
`Os.socket(AF_INET, SOCK_DGRAM, IPPROTO_ICMP)` succeeds and a Google echo reply
comes back. If some target API/OEM blocks it, the exec fallback becomes primary.
Cheap to check, decides the core.

## Data / persistence

`HostsRepository` over DataStore Preferences (mirrors metiq's `SettingsRepository`
encoding style).

- `hosts` — one `String` key, newline-separated `label|host` lines (label
  sanitized of `|` and `\n` on save). Only **custom** hosts are stored; the two
  defaults are constants in code and always shown first.
- `selectedHost` — a `String` key holding the chosen host value; falls back to
  the first default if unset or dangling.
- `theme` — `SYSTEM | LIGHT | DARK`. `dynamicColor` — `Boolean` (Android 12+).

Host validation on add: non-empty, no whitespace, matches a hostname or
IPv4/IPv6 literal; label trimmed, max ~24 chars, non-empty (default to the host
string if blank).

## Module / package layout

Single Gradle module `:app`, package `com.lysak.ping`:

```
com/lysak/ping/
  PingApp.kt              Application; builds HostsRepository, Pinger
  MainActivity.kt         edge-to-edge, splash, theme; one Composable: PingScreen
  net/
    Pinger.kt             ICMP datagram socket + exec + TCP fallbacks; testable
    IcmpPacket.kt         echo header encode / reply decode (pure, tested)
    PingStats.kt          Welford online min/avg/max/stddev/loss (pure, tested)
    PingSample.kt         sealed result + enums
  data/
    HostsRepository.kt    DataStore; hosts + selection + theme prefs
    Host.kt               data class Host(label, value, deletable)
  ui/
    PingScreen.kt         the single screen
    PingViewModel.kt      run loop, rolling window, cumulative stats
    HostPickerSheet.kt    modal bottom sheet: select / add / delete
    Hero.kt               big latency number + wave pulse + min/avg/max/loss/stddev
    Console.kt            the "geek" terminal-style live log + stats block
    WavePulse.kt          Canvas wave-ring animation around the button
    theme/
      Color.kt            metiq token set, copied values (dark default) + dynamic
      Type.kt             Inter variable font -> Material 3 Typography
      Theme.kt            PingTheme(darkTheme, dynamicColor)
```

Target: **well under 1000 lines** of Kotlin total.

## UI / visual design

Two layers on one screen: a **beautiful hero** (metiq aesthetic) on top, a
**geek console** below. Both visible at once on a normal phone; the console
scrolls.

### Design system — Material 3 Expressive

PING's UI is built on **Material 3 Expressive**: wrap the app in
`MaterialExpressiveTheme` (not the plain `MaterialTheme`), and prefer the
expressive components, shapes, and `MotionScheme` where they fit — expressive
buttons, the expanding shape/motion vocabulary, spring-based motion.

**metiq is a visual reference, not a technical one.** metiq predates M3
Expressive and uses plain Material 3. We borrow metiq's *look and feel* — its
palette, layout, the hero + console split, the wave-ring pulse, the monochrome
latency treatment — but implement it with M3 Expressive primitives. Where metiq
hand-rolls something that M3 Expressive now provides natively, use the native
version.

### Palette — same as metiq

Use metiq's exact token values (they are GPL as *code*, but these are short
color constants / factual values, re-keyed into our own `Color.kt`):

- dark: bg `#111010`, foreground `#222121`, cell `#2E2C2D`, text `#FFFFFF` /
  50%, divider white 8%, accent (logo) `#DBF1B3` (pale lime)
- light: bg `#E5E7EB`, foreground `#F5F7FA`, cell `#ECEEF3`, text `#111827`,
  accent `#ADC08B`
- Dark by default. Light + System options. Optional Material You dynamic color
  on Android 12+ (off by default), same fallback structure as metiq.
- Latency is shown **monochrome** — no green/amber/red. It is a pro tool; the
  number and the sparkline carry the meaning. (A subtle latency tint could be a
  later opt-in setting.)

### Hero (top)

- **Inter** variable font across the Material 3 type scale.
- Target chip: `Google · 8.8.8.8` — tap opens the host picker sheet.
- Big rounded **PING** button, ~94.dp tall (metiq's `BUTTON_HEIGHT`). Tap =
  start / stop.
- **Wave-ring pulse** emanating from the button on every reply — reimplemented
  fresh from metiq's `ActiveAnimations.kt` approach (frame-driven `Canvas`,
  expanding rings fading out). Static ring when system animations are off.
- One large latency number (`animateFloatAsState`) + a compact
  `min · avg · max · stddev · loss%` row + a thin sparkline of the rolling
  window.

### Console (below) — the geeky part

A terminal-styled panel, monospace, metiq colors, that prints exactly what
`ping` prints, line per reply:

```
64 bytes from 1.1.1.1: icmp_seq=0 ttl=56 time=11.16 ms
64 bytes from 1.1.1.1: icmp_seq=1 ttl=56 time=6.95 ms
Request timeout for icmp_seq 2
```

On stop it appends the standard statistics block:

```
--- 1.1.1.1 ping statistics ---
7 packets transmitted, 7 received, 0.0% packet loss
round-trip min/avg/max/stddev = 6.95/9.24/11.16/1.52 ms
```

- Auto-scrolls; long-press / a button to **copy all** or **share** the transcript
  (plain text — the thing a geek actually wants).
- This is the "made the way it should be" story: identical to BSD/Linux `ping`
  output, plus `stddev`, which the reference app omits.

- Splash screen via `core-splashscreen`, minimal.

## Licensing

**GPL-3.0-or-later** — same as metiq. Matches the "open forever, no closed
adware fork" intent.

- `LICENSE` = the full GPL-3.0 text (identical file to metiq's, 674 lines).
- `README` gets the short GPLv3 notice block with
  `Copyright (C) 2026 Lysak`.
- Because both projects are GPL-3.0, **code may be reused from metiq**
  (theme tokens, `WavePulse` from `ActiveAnimations.kt`, DataStore encoding
  patterns) — keep it GPL, and credit metiq in the README / an `ACKNOWLEDGEMENTS`
  note. Prefer rewriting where it's cleaner, copy where it's genuinely the same.
- Add `// SPDX-License-Identifier: GPL-3.0-or-later` headers to our `.kt` files
  (metiq itself doesn't, but it's cheap and correct).
- GPL-3.0 apps are fine on both Google Play and F-Droid (metiq ships on both).

## Permissions

`android.permission.INTERNET` only. Nothing else. No `ACCESS_NETWORK_STATE`,
no location, no notifications.

## Forward compatibility (staying unbroken on future Android)

There is **no `maxSdkVersion` for apps** and we must never set one — it would
make the app vanish from newer devices. "Keeps working on the next Android" is a
small yearly habit, not magic. The reference app's whole changelog *is* this
habit ("compatibility with Android 16 / API 36").

- **`targetSdk` = latest stable**, bumped once a year after a pass against the
  Developer Preview / Beta.
- Use **only AndroidX + Compose** — Google backports behaviour changes there.
- `enableEdgeToEdge()` + honest `WindowInsets` (`statusBars`, `navigationBars`,
  `ime`). Edge-to-edge is the single most common thing that breaks apps each
  release — and the exact bug that hit the reference app on Android 15.
- No hidden / greylisted framework APIs, no reflection into the platform.
- Every `@RequiresApi` branch has a real fallback (see TTL, dynamic color).
- Treat **lint deprecation warnings as errors** in CI, so drift is caught early.
- Predictive back: `android:enableOnBackInvokedCallback="true"`.
- Themed launcher icon (adaptive icon + `monochrome` layer).
- A **dependency bot** (Renovate or Dependabot) opens weekly PRs bumping
  AGP / Kotlin / Compose BOM / AndroidX; CI must be green to merge. This is the
  closest thing to "automatic".

## Repo tooling (first setup commit)

- `.editorconfig` — Kotlin official style, 4-space, 100 col (Android Studio and
  ktlint both honour it).
- `.gitignore` — Android + Gradle + IDEA.
- `gradle/libs.versions.toml` version catalog; committed Gradle wrapper with a
  pinned version + `-sha256` in `gradle-wrapper.properties`.
- **Spotless + ktlint** — `spotlessCheck` in CI, `spotlessApply` locally.
  (Lighter than detekt; enough for one module.)
- **Android Lint** with `lint { warningsAsErrors = true }`, run in CI.
- **GitHub Actions** — one workflow on push / PR:
  `assembleDebug`, `testDebugUnitTest`, `lint`, `spotlessCheck`.
- **Renovate/Dependabot** config for `gradle` and `github-actions`.
- `keystore.properties` git-ignored; release signing wired like metiq.
- Later, at publish time: `fastlane/metadata/android/` for the Play listing text
  in-repo (metiq does this). Defer until v1 is ready to ship.

## Build / release

- `gradlew :app:bundleRelease` → AAB, R8 full mode + `shrinkResources`.
- `keystore.properties` (git-ignored) for signing, like metiq.
- Expected download size: **~2–3.5 MB**. The `.aab` on disk is ~3.9 MB, but that
  includes the R8 mapping file in `BUNDLE-METADATA/` (~31 MB uncompressed) which
  is **not** delivered to devices. The shipped payload is `classes.dex` (~2.3 MB)
  + `inter_variable.ttf` (~0.9 MB) + resources + two tiny `.so` stubs; after
  Play's per-device ABI/density splits and compression the download is well
  under target. Confirm the real number in Play Console after the first upload.
- Optional later: F-Droid flavor (metiq has one) — trivial since there are no
  proprietary deps. Out of scope for v1.

**Play Store / Android compliance** — full checklist in `docs/COMPLIANCE.md`.
Highlights: `targetSdk` bumped yearly; edge-to-edge + predictive back +
themed icon (already in the plan); the only native code is two tiny 16 KB-safe
AndroidX stubs (see `COMPLIANCE.md`); Data Safety form = "collects nothing";
privacy policy = `PRIVACY.md`.
**Android developer verification (deadline 2026-09-30):** an account-level task —
verify developer identity and register `com.lysak.ping` + its signing keys in
Play Console. Enrol in Play App Signing; keep the upload keystore off git and
backed up. Not a code change.

## Testing

- `PingerParseTest.kt` — feed captured `ping` stdout samples (Android toybox,
  Linux iputils, error cases) and assert parsed RTT / failure reason.
- `HostValidationTest.kt` — valid/invalid hostnames and IPs.
- `HostsRepositoryTest.kt` (optional) — encode/decode round-trip of the
  `label|host` lines.
- No UI test framework for v1. `@Preview` composables for visual checks.

## Decisions (locked)

- **Package / `applicationId`:** `com.lysak.ping`.
- **Design system:** Material 3 Expressive (`MaterialExpressiveTheme`). metiq is
  a look-and-feel reference only; it uses plain M3.
- **Palette:** metiq's exact tokens, monochrome latency.
- **Ping interval:** 1000 ms.
- **History depth:** 60 samples for the sparkline; cumulative stats for the full
  run (Welford).
- **License:** GPL-3.0-or-later (same as metiq). Reuse of metiq code is allowed.
- **TCP fallback port:** 443.
- **Console:** always visible in v1 (hero + console both on screen). Small-screen
  collapse (< 560.dp) is deferred until a real device shows it's needed.

## Still open

- Small-screen console collapse (< 560.dp) — deferred, not yet needed on tested
  hardware.
