# PING — Code Quality Toolchain

Status: **wired and in use.** The tools below are configured in Gradle and run
via `make`. A `Stop` hook runs them automatically for AI agents. This doc is
both the rationale and the runbook.

Scope: this project only — single-module Android app, Kotlin `2.4`, Jetpack
Compose + Material 3 Expressive, Gradle Kotlin DSL + version catalog. No multi-module, no
DI framework, no server. Tools that only pay off at larger scale are
deliberately left out and marked as such (§2, §5).

---

## 1. Runbook — how an agent runs the tools

### 1.1 The mental map (PHP → Gradle)

On PHP you have `composer.json` scripts calling standalone binaries
(`pint`, `phpstan`, …). Here:

| PHP world | Here |
|---|---|
| `composer` | Gradle (`./gradlew`) — the build system *and* the task runner |
| a `composer` script | a **Gradle task** from a plugin (`spotlessApply`, `detekt`, `testDebugUnitTest`, `lintDebug`) |
| `composer.lock` | `gradle/libs.versions.toml` (version catalog) |
| `composer quality` | **`make`** — a thin wrapper that chains the Gradle tasks |

**The agent calls `make` targets, never raw `./gradlew`.** The `make` layer is
the stable command surface; it also auto-detects a JDK 17 when the shell has
none (agent hooks run with a bare environment).

### 1.2 Commands

| Command | Runs | Auto-fixes? | Use when |
|---|---|---|---|
| `make format` | `spotlessApply` (ktlint) | **yes** | first thing, on any task touching `.kt` |
| `make gate` | `spotlessApply` → `detekt` → `testDebugUnitTest` (no Konsist) | formatting only | **the main loop** — run repeatedly until clean |
| `make check` | alias for `make gate` | — | same as `gate` |
| `make verify` | `spotlessCheck` → `detekt` → `testDebugUnitTest` (`-PwithArchTest`) → `lintDebug` | **no** | once, before declaring the task done |
| `make test` | `testDebugUnitTest -PwithArchTest` (incl. Konsist architecture guards) | no | while iterating on logic |
| `make detekt` | `detekt` only | no | to re-read just the static-analysis findings |
| `make lint-android` | `lintDebug` only | no | after UI / manifest / resource changes |
| `make itest` | `connectedDebugAndroidTest` | no | instrumented/UI changes; needs a device **awake + unlocked** (`adb shell svc power stayon true`) or the Compose tests fail with "No compose hierarchies found" |

### 1.3 The loop (end every coding task with this)

```
1. make gate
2. read the output:
   - detekt findings  -> console, and app/build/reports/detekt/detekt.md
   - test failures    -> console, and app/build/reports/tests/testDebugUnitTest/index.html
   - formatting       -> already auto-applied by step 1; just re-stage the files
3. fix EVERY finding and failure in the code (not by suppressing)
4. go to 1
5. when gate is clean: run `make verify` once (adds Android Lint) and confirm green
```

Exit codes: `make gate` / `make verify` exit non-zero if anything fails. The
`Stop` hook (§6) turns that into feedback the agent must act on.

### 1.4 Suppressions

`@Suppress(...)`, a detekt baseline, `// ktlint-disable` — allowed **only** with
a one-line reason visible in the same diff. Silent blanket suppression is a
review failure. A `detekt` complexity finding is a signal to split the
function, not to raise the threshold.

---

## 2. The toolchain — what maps to what

| PHP tool | Does | Here | Why |
|---|---|---|---|
| **Laravel Pint** | opinionated auto-format | **Spotless + ktlint** `1.5.0` | ktlint is the de-facto Kotlin style standard; `ktlint_official` code style, 100-col, config in `.editorconfig` + `spotless {}` |
| **PHPStan** | type-level static analysis | **detekt** + **Android Lint** + the Kotlin compiler | detekt = generic Kotlin smells/bugs; Lint = Android-specific; the compiler's null-safety already covers PHPStan's headline feature |
| **PHPMD** | mess detector (complexity, naming) | **detekt** (`complexity`, `naming`, `style`, `potential-bugs` rule sets) | one tool covers PHPStan + PHPMD |
| **PHPMND** | magic number / string | **detekt** `MagicNumber` (tuned, see §3.2) | built in |
| **Deptrac** | enforce architecture layers | **Konsist** | Kotlin-native; rules are plain JUnit tests, run via `-PwithArchTest` in `make test` / `make verify` |
| **Rector** | automated refactors / upgrades | **no standing tool** — see §5 | `spotlessApply` does the mechanical style part; big migrations are one-off OpenRewrite runs |

### Deliberately NOT included

- **Dependency Analysis Gradle Plugin** — the dependency list is ~15 lines,
  hand-curated in `ARCHITECTURE.md`. Eyeballing beats a plugin.
- **Kotest** as a test runner — second framework. Take its matchers only if
  ever needed; runner stays JUnit.
- **ktfmt / google-java-format** — ktlint already owns formatting.
- **A standing OpenRewrite/Rector plugin** — see §5.
- **JaCoCo** — if we ever want coverage numbers, Kover
  (`org.jetbrains.kotlinx.kover`) is the Kotlin-native pick.

---

## 3. Tool-by-tool

### 3.1 Spotless + ktlint — formatting

- **Command:** `make format` (apply) · `make lint` (check only)
- **Config:** `app/build.gradle.kts` `spotless {}` + `.editorconfig`. ktlint
  `1.5.0`, `ktlint_official` style, 100-col, GPL license header enforced.
- **Compose carve-out:** `ktlint_function_naming_ignore_when_annotated_with =
  Composable, Preview` — `@Composable fun HomeScreen()` is PascalCase by design.
- **Clean means:** `spotlessCheck` exits 0.
- **Agent workflow:** run `make format` first, always. Never hand-format.

### 3.2 detekt — static analysis (PHPStan + PHPMD + PHPMND)

- **Command:** part of `make gate` / `make verify`; standalone `make detekt`
- **Version:** **`2.0.0-alpha.6`**, plugin id **`dev.detekt`**. This project
  forces the alpha: detekt `1.23.x` bundles a Kotlin 1.9/2.0 analyzer that
  throws on Kotlin `2.4` sources and cannot run on JDK 25
  (`JavaVersion.parse("25.0.3")` blows up). 2.0-alpha is built against Kotlin
  2.4 / AGP 9.3 / JDK 25 — this exact toolchain. A running alpha beats a stable
  that crashes; revisit when 2.0 is stable.
- **Config:** `config/detekt/detekt.yml` — hand-written **deltas only**
  (`buildUponDefaultConfig = true`). `autoCorrect = false` — formatting fixes
  are ktlint's job; detekt only reports.
- **Report to read:** `app/build/reports/detekt/detekt.md` (also `.html`,
  `.sarif`, `.xml`). Console output has the same findings.
- **Rule tuning (in `detekt.yml`):**
  - `MagicNumber`: ignores property/companion/annotation/named-arg/enum
    literals; excludes `**/ui/theme/**` (dp/sp design tokens), `**/net/**` (ICMP
    byte offsets — documented in comments), `**/test/**`, `**/androidTest/**`.
    Real logic numbers (timeouts, retry counts) still must be named constants.
  - `FunctionNaming` / `TopLevelPropertyNaming`: ignore `@Composable` / `@Preview`.
  - `LongMethod` / `LongParameterList` / `TooManyFunctions`: ignore
    `@Composable` (slot lambdas + `Modifier` run long legitimately).
  - `ReturnCount`: `max: 3`, `excludeGuardClauses: true`.
- **`detekt-compose` community rules — wired.** `io.nlopez.compose.rules:detekt`
  (`0.6.6`, catalog `composeRules`) is on the detekt classpath
  (`detektPlugins(libs.detekt.compose)` in `app/build.gradle.kts`). It catches
  Compose perf/correctness traps (multiple emitters, `mutableStateOf` autoboxing,
  unstable params, `CompositionLocal` sprawl). Config lives under the `Compose:`
  key in `config/detekt/detekt.yml` — currently just an allowlist entry for
  `LocalPingColors` (the deliberate token layer). Loads fine on detekt 2.0-alpha.
- **Baseline:** none. Start at zero, stay at zero.
- **Agent workflow:** every entry is fixed in code or suppressed-with-reason in
  the same diff.

### 3.3 Android Lint — Android-specific correctness

- **Command:** `make lint-android` · part of `make verify`
- **Config:** `app/build.gradle.kts` — `lint { warningsAsErrors = true; disable
  += "GradleDependency" }`.
- **Why separate from detekt:** Lint knows the Android SDK — window insets,
  battery (`WakeLock`, wrong `Handler`), accessibility
  (`contentDescription`), `minSdk` API misuse, resource shrinking, `targetSdk`
  behaviour changes. detekt does not. Both run; they do not overlap.
- **Clean means:** `lintDebug` exits 0 (warnings are errors).
- **Agent workflow:** every Lint error is blocking. The reference app's Android
  15 inset bug (`docs/IDEA.md`) is exactly what Lint catches — never disable
  insets/a11y checks.

### 3.4 Konsist — architecture rules (Deptrac)

- **Command:** runs inside `make test` (it *is* a unit test)
- **Version:** Konsist `0.17.3`
- **Location:** `app/src/test/java/com/lysak/ping/architecture/ArchitectureTest.kt`
- **Layers** (inner may not know outer): `core` (pure domain) → `net` (sockets)
  → `data` (persistence) → `presentation` (ViewModel) → `ui` (Compose).
- **Rules currently encoded:**
  - `core..` imports nothing from `net` / `data` / `presentation` / `ui` /
    `androidx.` / `android.` — it stays pure and JVM-testable.
  - `net..` does not import `presentation` / `ui` / `androidx.compose`.
  - No `@Composable` function references ping internals directly (`Pinger`,
    `IcmpPacket`, `PingProbe`, `HostsRepository`, …) — UI goes through the
    `ViewModel`.
  - No `println(` in production code.
  - *(Deferred)* a Log-wrapper rule — wait for a logging wrapper to exist.
- **Agent workflow:** a failing architecture test means the *design* is wrong —
  route the dependency through the right layer. Edit `ArchitectureTest.kt` only
  when the layering is intentionally changing, and say so in the diff.

### 3.5 Tests — framework & libraries

**`src/test` (JVM): JUnit 5 (Jupiter). `src/androidTest` (instrumented): JUnit 4.**

- **`src/androidTest`** — JUnit 4, no choice. `AndroidJUnitRunner`,
  `createComposeRule()` and every AndroidX `TestRule` are JUnit 4 API; there is
  no official JUnit 5 instrumentation runner.
- **`src/test`** — JUnit 5. Most tests for a one-screen app live here. Benefit:
  `@ParameterizedTest` (table-driven checksum/stats tests), `@Nested`,
  `@DisplayName`, `assertAll`, `MockKExtension`.

**Wiring** (`app/build.gradle.kts`):

```
testImplementation(libs.junit.jupiter)          // Jupiter API + params
testRuntimeOnly(libs.junit.jupiter.engine)
testImplementation(libs.junit)                  // JUnit 4 API — for not-yet-migrated tests
testRuntimeOnly(libs.junit.vintage.engine)      // runs JUnit 4 tests on the JUnit 5 platform
testRuntimeOnly(libs.junit.platform.launcher)   // required explicitly under AGP 9
```

plus the `de.mannodermaus.android-junit5` plugin (`2.0.1`), which flips
`useJUnitPlatform()` on the unit-test tasks.

**Migration policy:** new tests are Jupiter. Existing JUnit 4 tests keep running
via the vintage engine; migrate a file (`org.junit.Test` → `org.junit.jupiter`,
`org.junit.Assert` → Truth, `@Rule TemporaryFolder` → `@TempDir`) when you next
touch it — not in a big bang.

| Need | Library | Version | Notes |
|---|---|---|---|
| unit engine (`src/test`) | JUnit 5 / Jupiter | `5.13.2` | via `mannodermaus` `2.0.1` |
| legacy unit engine | JUnit Vintage | `5.13.2` | runs old JUnit 4 tests, transitional |
| instrumented engine | JUnit 4 | `4.13.2` | forced by AndroidX Test |
| assertions | Google Truth | `1.4.4` | `assertThat(x).isEqualTo(y)`; `isWithin(d).of(v)` for doubles |
| mocking | MockK | `1.14.5` | Kotlin-native standard |
| coroutines | kotlinx-coroutines-test | `1.9.0` | `runTest`; **must equal the app's coroutines-core** (BOM pins 1.9.0 via AGP consistent resolution) or instrumented `runTest` throws `NoSuchMethodError` |
| Flow testing | Turbine | `1.2.1` | `flow.test { awaitItem() }` |
| Compose UI | compose-ui-test-junit4 | via BOM | `src/androidTest`; use `createComposeRule()` — `createAndroidComposeRule` was flaky here |
| instrumented runner libs | androidx.test | `ext:junit 1.3.0`, `runner 1.7.0`, `core 1.7.0`, `espresso-core 3.7.0` | kept current for compose-ui-test 1.12; older versions → "No compose hierarchies found" |
| Android framework in JVM tests | Robolectric | — | **not added.** Keep `net` free of Android types; add only if `Context`/`DataStore` code truly can't be abstracted |

- **Command:** `make test` (JVM) · `make itest` (instrumented, needs device)
- **Coverage:** no hard gate. Aim for "every non-trivial piece of logic has a
  test", not a percentage.
- **Agent workflow:** TDD (`superpowers:test-driven-development`) — test first
  for every logic change. Not done while `make test` is red or new logic landed
  untested.

---

## 4. `make verify` / `make gate` — exact chains

| Target | Chain | Auto-fix |
|---|---|---|
| `make gate` (= `make check`) | `spotlessApply` `detekt` `testDebugUnitTest` | formatting only |
| `make verify` | `spotlessCheck` `detekt` `testDebugUnitTest` `lintDebug` | none |

`gate` is the fast inner loop (no Android Lint — that needs a full debug
variant). `verify` is the pre-done / pre-commit full check.

---

## 5. "Rector" / staying current over the years — separate task

`docs/IDEA.md` wants the app to "stay fresh year to year with minimal
intervention". That is **CI/automation**, tracked here, not part of this
toolchain:

- **Dependency updates:** Renovate (understands `libs.versions.toml`, groups
  updates, auto-merges patch bumps) or GitHub Dependabot. Runs on GitHub.
- **New Android release cadence:** a scheduled job that bumps `compileSdk` /
  `targetSdk` / AGP and opens a PR when a new stable API level ships, with
  `make verify` proving it still builds.
- **One-off code migrations** (e.g. a Compose API deprecation sweep):
  **OpenRewrite** (`org.openrewrite.rewrite`, now supports Kotlin + Gradle
  Kotlin DSL) run ad hoc, then removed — the closest thing to Rector for
  Kotlin, but a pick-up-and-put-down tool, not a standing gate.

Gets its own doc when built.

---

## 6. The agent `Stop` hook — enforcement

The quality loop is enforced by a **shared hook** so it does not depend on the
agent remembering.

- **Script:** `.dev/hooks/quality-gate.sh` — one file, used by both agents.
  Reads the hook payload on stdin, and:
  1. exits 0 immediately if no `.kt` / `gradle/` / `config/detekt/` files
     changed (nothing to check);
  2. otherwise runs `make gate`;
  3. on success, exits 0;
  4. on failure, exits **2** with the findings on **stderr** — both Claude Code
     and Codex feed a blocked `Stop` hook's stderr back to the model as a
     continuation prompt, so the agent fixes the findings and finishes again;
  5. loop guard: if the gate is still failing on a tree byte-identical to the
     last time it blocked, it exits 0 (the agent already tried; a human
     decides) — and it honours Claude Code's `stop_hook_active` flag.
- **Claude Code:** `.claude/settings.json` → `hooks.Stop`, `timeout: 600`,
  command `"$CLAUDE_PROJECT_DIR"/.dev/hooks/quality-gate.sh`. Fires at the end
  of every turn. Prompts once to trust the hook (project settings).
- **Codex CLI:** `.codex/hooks.json` → `hooks.Stop`, `timeout: 600`. Codex sets
  no project-dir env var and runs hooks from the *session* cwd, so the command
  is `bash -c 'cd "$(git rev-parse --show-toplevel)" && exec
  ./.dev/hooks/quality-gate.sh'`. Same Stop contract (exit 2 + stderr, or
  `{"decision":"block","reason":…}` on stdout — the script uses exit 2, which
  both accept). Codex runs project-local hooks only once the `.codex/` layer is
  **trusted** — approve it on first use.
  - **Worktree caveat:** Codex issue #27133 — project-level `.codex/hooks.json`
    is currently ignored when Codex runs inside a git *worktree*. For worktree
    sessions, put the same `Stop` entry in `~/.codex/hooks.json` until that is
    fixed.
- **Instruction source:** `AGENTS.md` (canonical) + `CLAUDE.md` (`@AGENTS.md`
  import). Both agents read `AGENTS.md`; the quality loop is spelled out there
  too, so the hook is a backstop, not the only signal.
- **Pre-commit:** `.dev/hooks/pre-commit` runs the full `make verify` before a
  commit lands. Enable once per clone:

  ```
  git config core.hooksPath .dev/hooks
  ```

  Skip for one commit with `git commit --no-verify` or `SKIP_GATE=1 git commit`.

---

## 7. Status

**Wired & verified locally:**

- `make test` green — Jupiter + vintage + platform-launcher resolve; migrated
  (`IcmpPacketTest`, `PingStatsTest`) and legacy JUnit 4 tests both run; Konsist
  `ArchitectureTest` passes.
- `make detekt` runs; tuned config takes (83 raw findings → 5 genuine).
- `spotlessCheck` clean.
- `Stop` hook verified end-to-end (Claude Code fired it this session; Makefile
  resolves a JDK from a bare env).
- **Not run in the dev sandbox:** `lintDebug` (part of `make verify`) — run it
  locally.

**Open follow-ups (belong to whoever owns that code):**

- detekt (incl. `detekt-compose`) is clean as of the 2026-09-03 refinement pass.
- All `src/test` files are on JUnit 5 Jupiter. The vintage engine +
  `testImplementation(libs.junit)` for `src/test` are now unused (kept only as a
  safety net for a stray future JUnit 4 test); drop them on the next cleanup.
- `git config core.hooksPath .dev/hooks` — manual, once per clone.
