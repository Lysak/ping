#!/usr/bin/env bash
# Shared code-quality gate for AI coding agents (Claude Code + Codex CLI).
#
# Wired as a "Stop" hook in both .claude/settings.json and .codex/hooks.json.
# When the agent tries to finish a turn, this runs `make gate` (format + detekt
# + unit tests). On failure it exits 2 with the output on stderr — both agents
# feed that back into the model as a continuation prompt, so the agent fixes
# the findings and tries again. See docs/tools.md.
#
# Contract (identical for both agents):
#   exit 0  -> let the agent stop
#   exit 2  -> block; stderr is shown to the agent as feedback
#
# Loop guard: if the working tree is unchanged since the last time we blocked
# on it, we let the agent stop (it already tried and could not fix it) instead
# of looping forever. A human then decides.

set -uo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root" || exit 0

marker="$repo_root/.dev/hooks/.last-blocked-hash"

# --- honour the agent's explicit loop flag (Claude Code + Codex both send
#     stop_hook_active on the Stop payload's stdin JSON) ---
if [ ! -t 0 ]; then
  stdin_json="$(cat 2>/dev/null || true)"
  case "$stdin_json" in
    *'"stop_hook_active":true'*|*'"stop_hook_active": true'*) exit 0 ;;
  esac
fi

# --- only run when Kotlin / Gradle sources actually changed ---
changed="$(git status --porcelain -- '*.kt' '*.kts' 'gradle/' 'config/detekt/' 2>/dev/null)"
if [ -z "$changed" ]; then
  rm -f "$marker"
  exit 0
fi

# Content hash of every changed/untracked Kotlin & Gradle file (works before the
# tree is committed, unlike `git diff HEAD`).
tree_hash="$(git ls-files -mo --exclude-standard -- '*.kt' '*.kts' 'gradle/' 'config/detekt/' 2>/dev/null \
  | sort | xargs -r shasum 2>/dev/null | shasum | awk '{print $1}')"

# --- run the gate ---
if output="$(make gate 2>&1)"; then
  rm -f "$marker"
  exit 0
fi

# Gate failed. If we already blocked on this exact tree, stop looping.
if [ -f "$marker" ] && [ "$(cat "$marker" 2>/dev/null)" = "$tree_hash" ]; then
  echo "quality-gate: still failing on an unchanged tree — letting the turn end so a human can look." >&2
  exit 0
fi

echo "$tree_hash" > "$marker"

# Strip Gradle/AGP boilerplate so the agent sees the findings, not the noise.
signal="$(printf '%s\n' "$output" | grep -vE \
  '^(WARNING:|w: |> Configure |> Task .*(UP-TO-DATE|SKIPPED|NO-SOURCE|FROM-CACHE)|Add android\.sync|For more information|To determine what|It will be removed|The (legacy|current)|android\.newDsl|Deprecated Gradle|You can use|\[Incubating\]|Consider enabling|Starting a Gradle|Welcome to Gradle|Daemon will|See https|https?://)' \
  | grep -vE '^\s*$' | tail -n 100)"

{
  echo "Code-quality gate failed (\`make gate\`). Fix every finding below per docs/tools.md §1.3, then finish again."
  echo "----------------------------------------------------------------------"
  printf '%s\n' "$signal"
} >&2
exit 2
