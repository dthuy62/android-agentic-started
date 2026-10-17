# Sub-project 2: Agent instruction layer

Date: 2026-10-15
Status: draft, waiting for review
Parent effort: `android-agentic-starter`, an open-source Android starter built for AI-assisted development.
Depends on: sub-project 1 (identity and setup), done.

## 1. Goal

Coding agents (Claude Code, Codex, OpenCode, Cursor) working in the starter get the few rules they must never break
up front, and everything else only when the task needs it. Rules a machine can check are enforced by hooks and tests,
not by prose.

Success means:

- `AGENTS.md` stays short (hard cap 60 lines, tested) and every pointer in it resolves to a real file.
- Skills and path-scoped rules carry the detail; one copy of each file serves every agent.
- Edits to generated files are blocked; oversized Kotlin files are flagged right after the edit.
- Architecture "why" lives in ADRs that agents can read before proposing changes.

## 2. Principles (from the discussion)

1. **Short always-on context.** Agents follow instructions eagerly; extra instructions add steps and cost without
   improving results. `AGENTS.md` holds only commands, never-break rules, key paths and a "when you need more" table.
2. **Progressive disclosure.** Detail lives in skills (`SKILL.md` plus `references/`) and path-scoped rules that load
   only when the agent works on matching files.
3. **One source, many agents.** No copied instruction text. `CLAUDE.md` imports `AGENTS.md`; Codex and OpenCode read
   `AGENTS.md` natively (no `opencode.json` needed, which would load it twice) and the Claude skills directory
   through symlinks.
4. **Machines enforce, prose explains.** Hooks and tests replace instructions wherever a check is possible.
5. **Clean template.** Nothing personal or session-specific is committed (no daily logs, no author preferences).

## 3. What is borrowed from `flutter-agentic-starter`, and what is not

Borrowed: a scoped stack skill with a review checklist; minimal-edit, never-edit-generated, avoid-primitive-obsession
and library-first rules; adaptive and accessible UI rules (translated to Compose); multi-agent support; a hook with
tests.

Not borrowed: duplicated `CLAUDE.md`/`AGENTS.md` text; ClaudeKit leftovers (privacy hook, Python venv notes, shouted
"MANDATORY" lines); the `caveman` style skill; the third-party `frontend-design` skill; the "download images from the
internet" rule (license risk); `okf-memory` daily logs (replaced by ADRs).

## 4. File layout

```
AGENTS.md                         shared instructions, <= 60 lines
CLAUDE.md                         "@AGENTS.md" + a short "Claude Code only" section (replaces the symlink)
.claude/
├── settings.json                 existing plugin wiring + hooks (section 7)
├── rules/
│   ├── compose-ui.md             paths: app/src/main/**/ui/**
│   └── testing.md                paths: app/src/test/**, app/src/androidTest/**
└── skills/
    ├── android-agentic-starter/  SKILL.md + references/{presentation,data-and-errors,review-checklist}.md
    ├── new-feature/              SKILL.md
    ├── setup-starter/            SKILL.md
    └── adr/                      SKILL.md + references/template.md
.agents/skills   -> ../.claude/skills      (symlink, Codex)
.opencode/skills -> ../.claude/skills      (symlink, OpenCode)
.cursor/rules/android.mdc         alwaysApply: true; points to AGENTS.md
.codex/hooks.json                 same hook scripts for Codex (section 7)
docs/decisions/                   ADRs (section 8)
tools/agents/
├── hooks/guard_generated.py
├── hooks/file_length.py
├── hooks/_payload.py             shared stdin/JSON + path extraction
└── tests/                        unittest suite (section 9)
```

All new text is written from scratch for this repo (MIT-compatible). No third-party skill text is copied.

## 5. `AGENTS.md` and `CLAUDE.md`

### 5.1 `AGENTS.md` target content

It replaces today's 67-line file. "Presentation patterns" and most of "Code rules" move into the
`android-agentic-starter` skill. Target text (wording may be tightened, structure and limits may not):

```markdown
# AGENTS.md

Android starter: one `:app` module, Jetpack Compose, Hilt, Room, DataStore, Navigation 3.
Package root = `namespace` in `app/build.gradle.kts`. Layers: `ui/` → `domain/` ← `data/`, wired in `di/`.

## Verify (run before saying a task is done)

./gradlew assembleDebug testDebugUnitTest lintDebug

## Never

- Import `data/` from `ui/`, or Android/Room/DataStore types from `domain/`.
- Import one `ui/<feature>` from another; navigation goes through `navigation/NavGraph.kt` callbacks.
- Edit generated files (`build/`, `.gradle/`, `app/schemas/`); a hook blocks it.
- Change package, applicationId or app name by hand; use the `mobile-app-config` plugin.
- Put dependency versions anywhere but `gradle/libs.versions.toml`.
- Mix MVVM-lite and MVI in one screen, or add MVC/MVP.

## When you need more

| Task | Read first |
|---|---|
| Write ViewModel, use case, repository or DI code | skill `android-agentic-starter` |
| Add a screen | skill `new-feature` |
| Change Compose UI | `.claude/rules/compose-ui.md` |
| Write or fix tests | `.claude/rules/testing.md` |
| Make or question an architecture decision | `docs/decisions/` and skill `adr` |
| First-time setup, rename, flavors, Firebase | skill `setup-starter` |
```

### 5.2 `CLAUDE.md`

Replace the symlink with a regular file:

```markdown
@AGENTS.md

## Claude Code only

- Skills are in `.claude/skills/`; the identity plugin runs as `/mobile-app-config:android`.
- Hooks in `.claude/settings.json` block edits to generated files and flag Kotlin files over 400 lines.
```

The import works on every Claude Code version in use (the user runs 2.1.138; reading `AGENTS.md` without a
`CLAUDE.md` needs 2.1.277+), and it works on Windows where symlinks may not.

## 6. Skills and rules (content outline)

### 6.1 `android-agentic-starter`

- `description`: use when writing or changing app code under `app/src` (ViewModels, use cases, repositories, DI,
  navigation) or its tests; not for docs, build scripts or agent configuration.
- `SKILL.md` (short): layer map, the choice MVVM-lite vs MVI with the decision rule from `docs/ARCHITECTURE.md`,
  pointers to references.
- `references/presentation.md`: `XScreen` + `XContent`, immutable `UiState` / sealed states, no parallel booleans,
  `ImmutableList`, `collectAsStateWithLifecycle`, `CollectEffects` for one-shot events, `MviViewModel` usage.
- `references/data-and-errors.md`: `Outcome<T>` and `AppError`, mapping in `data/`, never swallow
  `CancellationException`, inject `DispatcherProvider`, use cases only with real logic, avoid primitive obsession
  (value classes, enums, sealed types), prefer Jetpack/maintained libraries over custom helpers.
- `references/review-checklist.md`: the questions a reviewer asks before accepting a change.

### 6.2 `new-feature`

Steps: run `scripts/new-feature.sh <name>`; add `entry<XRoute>` in `NavGraph.kt` with callbacks; keep MVVM-lite
unless the MVI rule applies; add a ViewModel test using fakes; run the verify command.

### 6.3 `setup-starter`

First-run wizard: ask app name, applicationId, root project name, whether to add `dev`/`stg`/`prod` flavors and
Firebase; run the matching `mobile-app-config` commands (plugin skill or CLI); run the verify command; offer to
write ADR 0005 if the user changes a default decision. Never edits identity files by hand.

### 6.4 `adr`

When an ADR is needed (significant, hard to reverse, needs a "why" later) and when not (style choices, linter
territory, temporary workarounds); location `docs/decisions/`; file name `NNNN-<present-tense-verb-phrase>.md`,
four digits, next free number; Nygard template in `references/template.md` (Title, Status with date, Context,
Decision, Consequences); one decision per file; accepted ADRs are not edited — supersede with a new one and update
the old one's Status line to `Superseded by NNNN`.

### 6.5 Rules

- `compose-ui.md` (`paths: app/src/main/**/ui/**`): adaptive layouts by window size class
  (`NavigationSuiteScaffold`, list-detail where it fits), no orientation or device checks for layout, semantics and
  content descriptions, touch targets, strings from `strings.xml`, spacing from `Spacing`, a `@Preview` for each
  `XContent`, lazy lists for long content.
- `testing.md` (`paths: app/src/test/**, app/src/androidTest/**`): fakes of domain interfaces over mocks,
  `MainDispatcherRule`, assert on `state.value` and collected effects, one behaviour per test, test names describe
  behaviour.

Each rule file stays under 40 lines.

## 7. Hooks

Python 3, standard library only, in `tools/agents/hooks/`. Each script reads the hook payload JSON from stdin and
never crashes the agent: unexpected input means "allow, no output".

### 7.1 Path extraction (`_payload.py`)

Collect target paths from `tool_input.file_path`, `tool_input.path`, `tool_input.filePath`, and from an
`apply_patch`-style `tool_input.command` (`*** Add File:` / `*** Update File:` / `*** Delete File:` headers).
Resolve relative paths against `cwd` from the payload; compare against the project root.

### 7.2 `guard_generated.py` (PreToolUse)

Blocks when any target path, relative to the project root, matches:
`**/build/**`, `.gradle/**`, `.kotlin/**`, `app/schemas/**`, `gradle/wrapper/gradle-wrapper.jar`.

Block response (Claude Code): exit 0 with

```json
{"hookSpecificOutput": {"hookEventName": "PreToolUse", "permissionDecision": "deny",
  "permissionDecisionReason": "<path> is generated. Edit the source instead: <hint>"}}
```

Hints: `app/schemas/` → "Room exports this from @Database classes; change the entity/DAO and rebuild";
`build/` → "build output; change the source file that produces it". Non-matching paths produce no output.

### 7.3 `file_length.py` (PostToolUse)

For each edited `.kt` / `.kts` file outside generated paths with more than 400 lines, emit

```json
{"hookSpecificOutput": {"hookEventName": "PostToolUse",
  "additionalContext": "<path> has N lines (limit 400). Split it: extract a use case or sub-composables."}}
```

### 7.4 Wiring

- `.claude/settings.json` gains a `hooks` block, matcher `Write|Edit|MultiEdit` for both events (exec form with
  `${CLAUDE_PROJECT_DIR}`), keeping the existing `extraKnownMarketplaces` and `enabledPlugins` keys.
- `.codex/hooks.json` registers the same scripts with matcher `apply_patch|Edit|Write`. During implementation, check the current Codex hook docs: if Codex
  does not support blocking from a pre-tool hook, register only `file_length.py` for Codex and say so in
  `AGENTS.md`'s "Never" line wording ("a hook blocks it in Claude Code").

## 8. ADRs

Create `docs/decisions/` with:

| File | Decision | Source |
|---|---|---|
| `0001-use-a-single-app-module.md` | one `:app` module, package-organized | ARCHITECTURE §1 |
| `0002-default-to-mvvm-lite-and-use-mvi-when-needed.md` | MVVM-lite default, MVI rule, no MVC/MVP | ARCHITECTURE §3 "Why not the others" |
| `0003-return-outcome-for-expected-failures.md` | `Outcome<T>` + `AppError`, mapping in `data/` | ARCHITECTURE §4 |
| `0004-change-identity-via-mobile-app-config.md` | identity changes through the plugin, neutral class names | sub-project 1 spec |

All dated 2026-10-15, Status `Accepted`. `docs/ARCHITECTURE.md` keeps the "how" and replaces the moved "why"
paragraphs with links to the ADRs; §7 "Working with AI agents" becomes two lines pointing to `AGENTS.md` and the
skills.

## 9. Tests for the agent layer

`python3 -m unittest discover -s tools/agents` must pass. It is not part of the app verify command; CI runs it in
sub-project 5.

Hook tests (run the scripts as subprocesses with crafted payloads):

- `guard_generated`: denies `app/build/x.kt`, `app/schemas/a/1.json`, `.gradle/x`; allows
  `app/src/main/kotlin/.../Foo.kt`; handles absolute paths, relative paths with `cwd`, and `apply_patch` commands;
  empty or invalid stdin → exit 0, no output.
- `file_length`: 401-line `.kt` → context message naming the file and count; 400 lines → nothing; non-Kotlin file →
  nothing; file under `build/` → nothing.

Structure tests:

- `AGENTS.md` has at most 60 lines.
- Every path or skill named in the "When you need more" table exists (`.claude/skills/<name>/SKILL.md` for skills).
- Every `SKILL.md` has YAML frontmatter with exactly `name` (matching its directory) and `description`.
- Every file under `.claude/rules/` has a `paths:` frontmatter list.
- `.agents/skills` and `.opencode/skills` are symlinks resolving to `.claude/skills`.
- `CLAUDE.md` is a regular file whose first non-empty line is `@AGENTS.md`.
- `.claude/settings.json` and `.codex/hooks.json` parse as JSON and reference existing hook scripts.
- Every file in `docs/decisions/` matches `NNNN-<kebab-case>.md` and has `Status:` and the three Nygard sections.

## 10. Docs touched

- `README.md`: a short "Working with AI agents" section listing the skills, the ADR folder, the hook test command,
  and a Windows note (enable `core.symlinks` / Developer Mode, or Codex/OpenCode will not see the skills).
- `docs/ARCHITECTURE.md`: as in section 8.

## 11. Out of scope

Konsist/detekt/ktlint and a single verify task (sub-project 3); Roborazzi screenshot tests (sub-project 4); CI,
Renovate, templates, license (sub-project 5); AI runtime features of the app.

## 12. Acceptance criteria

- `python3 -m unittest discover -s tools/agents` passes.
- `./gradlew assembleDebug testDebugUnitTest lintDebug` still passes (no app code changes expected).
- In a Claude Code session in the repo: asking to edit `app/schemas/.../1.json` is denied with the hint; writing a
  450-line Kotlin file produces the length warning.
- `wc -l AGENTS.md` ≤ 60.
- No text in the new files is copied from third-party skills or from `flutter-agentic-starter`.

## 13. Risks

- Path-scoped rules are Claude Code-only; other agents reach them through the `AGENTS.md` table. Accepted.
- Symlinked skill folders do not work on Windows without symlink support; documented in README.
- Hooks need `python3` on PATH; if missing, the hook fails open (agent continues) and README states the requirement.
- Codex hook support may differ from Claude Code's; handled in 7.4.
