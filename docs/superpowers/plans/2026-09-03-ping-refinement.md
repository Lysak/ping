# PING — Refinement plan (2026-09-03)

Tasks 1–15 of the build plan are done: the app pings for real on hardware,
`make gate` + `make verify` are green, the release bundle builds. This plan is
the **refinement pass** — close the gaps between "works" and "matches the
non-negotiables in `AGENTS.md`", then re-run the quality loop.

Each phase ends with `make gate` clean, then `make verify` green. No commits —
the human commits once at the end.

---

## Gap analysis (what's not right yet)

| # | Gap | Source of the rule | Severity |
|---|-----|--------------------|----------|
| G1 | Theme uses `MaterialTheme`, not `MaterialExpressiveTheme`; no `MotionScheme` | `AGENTS.md` non-negotiable: "Material 3 Expressive (`MaterialExpressiveTheme`)" | **blocking** |
| G2 | No `detekt-compose` rules, though 5 real `@Composable` files now exist | `docs/tools.md` §3.2 ponytail marker | medium |
| G3 | Zero Compose UI tests; `androidTest` has only the ICMP smoke test | `AGENTS.md` Tests §; plan Self-Review note | medium |
| G4 | 4 unit-test files still JUnit 4 (`PingViewModelTest`, `PingerParseTest`, `HostValidationTest`, `HostsRepositoryTest`) | `AGENTS.md`: migrate on touch; `tools.md` §7 | low |
| G5 | `LocalClipboardManager` is deprecated (compile warning) | compiler | low |
| G6 | Console lines read `24 bytes from …` (16-byte payload) vs BSD `ping`'s `64 bytes` (56-byte payload) | `docs/IDEA.md`: "Output matches BSD/Linux `ping` line for line" | low |
| G7 | Reply lines never show TTL (`readTtl()` stub returns `null`) | plan Task 6b; spec-sanctioned as cosmetic | optional |
| G8 | Release `.aab` is 3.9 MB; plan target ≤ ~3.5 MB | plan Task 14 Step 3 | investigate |
| G9 | Sparkline sits visually detached from the PING button | manual UI review | polish |
| G10 | Edge-to-edge / `imePadding` only re-verified once; AGENTS.md calls it "the exact bug that broke the reference app" | `AGENTS.md` non-negotiable | re-verify |

---

## Status (2026-09-03) — COMPLETE

All phases done, G7 (TTL) included. `make gate`, `make verify`,
`make itest` all green. 29 unit tests + Konsist + 5 instrumented tests (2 real
ICMP smoke + 3 `PingScreenTest`) pass on the motorola razr plus 2024.

On-device verified: Expressive theme renders identically (dark #111010, green
button); real ICMP with `PING 8.8.8.8: 56 data bytes` / `64 bytes from …`
(G6); stats block on Stop; sparkline flush to the button (G9); host sheet opens
with the two default hosts + Label/Hostname fields + disabled Add button.

### Dependency changes forced by this pass
- `androidx.compose.material3:material3` → `1.5.0-alpha27` (Expressive public API).
- `minSdk` 23 → 24 (material3 1.5.0-alpha floor).
- `io.nlopez.compose.rules:detekt` `0.6.6` added (detekt-compose).
- `kotlinx-coroutines-test` 1.11.0 → **1.9.0** — must equal the app's
  coroutines-core (pinned 1.9.0 by the Compose BOM via AGP consistent
  resolution); a newer test artifact hit `NoSuchMethodError` at runtime.
- `androidx.test`: `ext:junit` 1.2.1→1.3.0, `runner` 1.6.2→1.7.0, added
  `core` 1.7.0 + `espresso-core` 3.7.0 — the old versions made
  `createAndroidComposeRule` fail to attach the composition
  ("No compose hierarchies found"). `PingScreenTest` uses the plain
  `createComposeRule()`, which proved reliable where the activity-based rule
  was flaky.

---

## Phase 1 — Material 3 Expressive (G1)

**Files:** `app/src/main/java/com/lysak/ping/ui/theme/Theme.kt`, maybe
`MainActivity.kt`, `docs/tools.md`, `docs/ARCHITECTURE.md`.

**Done differently than expected:** in material3 **1.4.0** (BOM default) the
Expressive APIs are `internal` — won't compile. They are public only from
**1.5.0-alpha**, which requires **minSdk 24**. So: pinned
`androidx.compose.material3:material3` to `1.5.0-alpha27` in the catalog (with a
justifying comment) and bumped `minSdk 23 → 24` (user-approved). `@OptIn` still
needed. Documented in `docs/ARCHITECTURE.md` SDK-levels section.

- [x] Swap `MaterialTheme(colorScheme, typography, content)` →
      `MaterialExpressiveTheme(colorScheme = …, typography = …, motionScheme = MotionScheme.expressive(), content = …)`.
      Keep the existing `PingColors` token layer and `LocalPingColors` provider
      untouched.
- [x] Add the `@OptIn` with a one-line reason comment (allowed suppression form).
- [x] Confirm the hero button + wave pulse still animate; the expressive motion
      scheme changes spring feel, not layout.
- [x] `docs/tools.md` §7 + scope line: drop "the app is ~one theme file of
      Compose"; `docs/ARCHITECTURE.md`: tick Expressive as done.
- [x] `make gate` → `make verify` — green.
- [x] Rebuilt, installed, screenshotted — no visual regression; Expressive motion only.

### Phase 1b — visible Expressive in the hero (follow-up, user-requested)

The bare theme swap had no visible effect. Added three restrained touches, all
in `Hero.kt` (`@OptIn(ExperimentalMaterial3ExpressiveApi::class)`):

- [x] **Ping button** — `Button(shapes = ButtonDefaults.shapes(shape, pressedShape))`:
      corner radius squishes to 48.dp while pressed, and springs between idle
      (30.dp pill) and running (14.dp blocky) via
      `motionScheme.defaultSpatialSpec()` on an `animateDpAsState`.
- [x] **Latency number** — `animateFloatAsState` now uses
      `MaterialTheme.motionScheme.slowSpatialSpec()` for a subtle expressive
      overshoot on each update.
- [x] **`LoadingIndicator`** (expressive morphing polygon) replaces `—` while
      `running && lastRttMs == null`. Fixed-height row so no layout shift.
- [x] `make gate` + `make verify` + `make itest` green; verified all states on device.

### Phase 1c — animated splash mark (user-requested)

- [x] **`res/drawable/ic_launcher_animated.xml`** — `AnimatedVectorDrawable` of the
      launcher mark: inner then outer ring scale in with `overshoot`, the centre
      dot fades in, and one `pulse` ring ripples out and fades (~840 ms total).
- [x] `themes.xml` `Theme.Ping.Splash` → `windowSplashScreenAnimatedIcon` points
      at it, `windowSplashScreenAnimationDuration` = 840.
- [x] `MainActivity` holds the splash via `setKeepOnScreenCondition` for 840 ms so
      the animation actually plays out (cold start only).
- [x] Verified on device — the mark draws itself outward on launch.

*The in-app "waiting for first reply" indicator stays the M3 `LoadingIndicator`
(the user likes it). A parallel experiment that made `WavePulse` do a continuous
"sonar" sweep there was reverted; `WavePulse.kt` is back to its per-`pulseKey`
form and the throwaway `PulseGlyph.kt` was removed.*

*Still skipped: `ButtonGroup`, sparkline bar animations, stat-row transitions —
excessive for a one-screen utility.*

### Phase 1d — motion polish (2026-09-03, user-requested)

Three follow-ups after on-device use:

- [x] **Ping button jitter / "ривками"** — the corner radius had two animations
      fighting (`animateDpAsState` + `ButtonDefaults.shapes` press morph) and a
      mid-flight reversal. Now a single `Animatable<Dp>` driven by
      `snapshotFlow { pressed }.collectLatest`: on release it plays **two beats in
      strict sequence** — finish the press-in, *then* settle to the resting
      (idle/running) corner — each `tween(PING_CORNER_BEAT_MS = 380,
      FastOutSlowInEasing)`. A second `LaunchedEffect(restingCorner)` eases
      non-press state changes. Button takes plain `shape =` + its own
      `MutableInteractionSource`.
- [ ] **Splash pulse** — tried looping / multi-ripple pulse in the system AVD;
      it rendered unpredictably on the Moto foldable (icon sometimes absent,
      sometimes janky) and can't be screenshotted for tuning. **Reverted** to the
      original one-shot mark (840 ms, single ripple). A looping preloader, if
      still wanted, should be done in-app in Compose, not the system SplashScreen.
- [x] **Loader visibility** — `PingUiState.awaitingReply` (set before each
      `probe.probe`, cleared in `fold`/`stop`). `Hero` shows the 32 dp
      `LoadingIndicator` when `awaitingReply && lastRttMs == null` (immediately
      after a tap, as before) **or** once `awaitingReply` has held 250 ms
      (later lag). The 250-ms-only gate alone hid it for fast hosts like 8.8.8.8.
- [x] New unit test `awaitingReplyIsTrueWhileProbeSuspendsAndClearsAfterReplyAndStop`.
      `make gate` + `make verify` green.

## Phase 2 — detekt-compose rules (G2)

**Files:** `app/build.gradle.kts`, `gradle/libs.versions.toml`,
`config/detekt/detekt.yml`, any `@Composable` file the rules flag.

- [x] Add `io.nlopez.compose.rules:detekt` (latest) as `detektPlugins(...)`,
      version in the catalog. This is a build-classpath tool, not an app dep —
      note it in `docs/tools.md`, not `ARCHITECTURE.md`.
- [x] Run `make detekt`, read `app/build/reports/detekt/detekt.md`.
- [x] Fix every finding in code (common ones: missing `Modifier` param, `Modifier`
      not first optional param, unstable lambda in a loop, `Color` hard-coded).
      Suppress only with a visible reason.
- [x] Remove the ponytail marker comment in `app/build.gradle.kts`.
- [x] `make gate` → `make verify` — green (3 findings fixed: MultipleEmitters, MutableStateAutoboxing, CompositionLocalAllowlist).

## Phase 3 — Compose UI tests (G3)

**Files:** new `app/src/androidTest/java/com/lysak/ping/ui/PingScreenTest.kt`
(JUnit 4 + `createAndroidComposeRule`), maybe a fake `PingProbe`.

TDD is awkward retroactively; write these as characterization tests for
behaviour that must not regress:

- [x] `PingScreenTest.kt` — 3 tests (label toggle, chip opens sheet, invalid host
      keeps Add disabled). Green via `make itest` on device, run 4×, not flaky.

Keep it to one file, ~4 tests, a hand-written `PingProbe` fake (no MockK in
instrumented). Run with `make itest` (needs the device connected).

## Phase 4 — small correctness + polish (G5, G6, G9, G10)

- [x] **G5**: `LocalClipboardManager` → `LocalClipboard` (`getClipEntry`/`setClipEntry`
      or the `Clipboard` suspend API); adjust the `onCopy` lambda. Re-run gate.
- [x] **G6**: ICMP echo payload 16 → 56 bytes (`PAYLOAD_BYTES`); tests already asserted `64 bytes` — now consistent. Originally: bump payload to 56 bytes so lines read `64 bytes
      from …`. One constant + update `IcmpPacketTest` and any formatter test
      that asserts byte counts. Test first.
- [x] **G9**: sparkline + button grouped in a sub-Column (6.dp gap). Originally: tighten `Hero` layout — reduce the gap between the sparkline and
      the button, or move the sparkline directly above the button inside the
      same spacing group.
- [x] **G10**: host sheet verified on device — opens above the nav bar, scrim
      dims content; `imePadding()` + `navigationBarsPadding()` on the sheet
      Column (unchanged from the earlier session's on-device IME check).
- [x] `make gate` → `make verify` green. Device screenshot pending.

## Phase 5 — JUnit 5 migration (G4)

**Files:** `PingViewModelTest.kt`, `PingerParseTest.kt`,
`HostValidationTest.kt`, `HostsRepositoryTest.kt`.

- [x] **Already done** — audit found all 7 `src/test` files are already on JUnit 5
      Jupiter + Truth (`@TempDir` included). The `tools.md` §7 list was stale.
- [x] `docs/tools.md` §7: replaced the migration list with a note that the
      vintage engine + `src/test` JUnit 4 dep are now unused, drop next cleanup.
- [x] `make test -PwithArchTest` → `make gate` → `make verify` — all green.

## Phase 6 — release size + docs (G8, G7)

- [x] **G8**: `./gradlew :app:bundleRelease` then inspect the APKs inside the
      `.aab` (`bundletool build-apks` or unzip `base/`): find what's big. Likely
      the Inter variable font. Options, cheapest first: (a) accept and update the
      plan's target to ~4 MB with a one-line reason; (b) subset the font to
      Latin + the glyphs actually used; (c) drop to a static weight or two.
      Decide, document in `ARCHITECTURE.md`.
- [x] **G7 done**: `receiveReplyWithTtl` (API 33+) sets `IP_RECVTTL` /
      `IPV6_RECVHOPLIMIT`, reads via `Os.recvmsg`, parses the `IP_TTL` /
      `IPV6_HOPLIMIT` cmsg. Verified on device: primary ICMP lines now show
      `ttl=114` (no `(exec)` fallback). Fallbacks: `Os.read` if `recvmsg`
      throws, `SO_RCVTIMEO` (API 29+) so a consumed datagram can't hang the
      loop, plain `poll`+`read` below API 33. `IcmpSocketSmokeTest` asserts a
      sane TTL on the ICMP path.
- [x] **Console**: `ttl=` made lines wrap two-high; switched the console to
      no-wrap + horizontal scroll (`softWrap=false`, `horizontalScroll`) — one
      ping per row, proper terminal feel.
- [x] Final `make gate` + `make verify` + `make itest` green; device screenshots taken., update this file's checkboxes.

---

## Done when

- `MaterialExpressiveTheme` is the theme; `AGENTS.md` non-negotiables all hold.
- `detekt-compose` rules run clean in the gate.
- At least one Compose UI test file exists and passes on device.
- `make gate` and `make verify` both green.
- The app is re-verified on the motorola razr plus 2024 with a fresh screenshot.
- No commits made by the agent.
