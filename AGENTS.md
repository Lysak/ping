# PING — instructions for AI coding agents

Shared by every agent (Claude Code, Codex CLI, …). Keep it short; the detail
lives in `docs/`.

## Read first

- `docs/IDEA.md` — what the app is and is not.
- `docs/ARCHITECTURE.md` — target shape, dependency list (intentionally short),
  SDK levels, the ICMP mechanism, the edge-to-edge insets requirement.
- `docs/tools.md` — the code-quality toolchain and how you are expected to use
  it.

## Non-negotiables

- **Native, tiny, beautiful.** Kotlin + Compose + Material 3 Expressive
  (`MaterialExpressiveTheme`). No new
  dependency without a line in `docs/ARCHITECTURE.md` justifying it.
- **No overhead in the shipped app:** no jank, no wasted recomposition, no
  wakelocks, no background services, no analytics, no reflection-heavy DI.
- **Get window insets right** (edge-to-edge, `imePadding()` on the host field).
  This is the exact bug that broke the reference app.

## The quality loop — end every coding task with this

1. Run `make gate` (applies formatting, then runs detekt + unit tests).
2. Read the output. Fix **every** finding and failure — in the code, not by
   suppressing. A suppression is allowed only with its one-line reason visible
   in the same diff.
3. Repeat until `make gate` is clean.
4. Run `make verify` once (adds Android Lint, no auto-fix) and confirm it is
   green before you say the task is done.

A `Stop` hook runs `make gate` automatically and will hand failures back to
you — but do not rely on it; run the loop yourself.

## Tests

- TDD: write the test first for every logic change.
- `src/test` (JVM): write **new** tests in **JUnit 5** (Jupiter) + Truth + MockK
  + Turbine. Existing JUnit 4 tests still run (vintage engine); migrate a file to
  Jupiter when you touch it, not in a big bang.
- `src/androidTest` (instrumented / Compose UI) uses **JUnit 4** — required by
  AndroidX Test.
- Architecture rules live in `ArchitectureTest.kt` (Konsist). If a change makes
  them fail, the design is wrong — route the dependency through the right
  layer; do not edit the rules unless the layering is intentionally changing.

## Commits

Do not run `git commit`. Produce a commit message for the human to apply.
