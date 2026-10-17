# Agent Instruction Layer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give coding agents a short always-on `AGENTS.md`, on-demand skills/rules/ADRs, and hooks that block edits to generated files and flag oversized Kotlin files.

**Architecture:** `AGENTS.md` is the single shared source; `CLAUDE.md` imports it. Detail lives in `.claude/skills/*` and path-scoped `.claude/rules/*`, shared with Codex/OpenCode through symlinks. Two Python hook scripts (stdlib only) serve Claude Code and Codex. A `unittest` suite under `tools/agents/tests/` checks the hooks and the structure of the whole layer.

**Tech Stack:** Markdown, JSON, Python 3 (stdlib `unittest`, `json`, `subprocess`), Claude Code and Codex hook configs.

**Spec:** `docs/superpowers/specs/2026-10-15-agent-instruction-layer-design.md`

## Global Constraints

- Starter repo `/Users/dthuy/Workspace/android-base-2026`: working tree only. No `git add`, `git commit`, branches or other git state changes.
- All new text is written from scratch. Nothing copied from third-party skills or from `flutter-agentic-starter`.
- `AGENTS.md` has at most 60 lines.
- `CLAUDE.md` is a regular file whose first non-empty line is `@AGENTS.md`.
- Hook scripts: Python 3 standard library only, in `tools/agents/hooks/`; on any unexpected input they exit 0 with no output.
- Generated paths blocked: `**/build/**` (a `build` directory not inside `src/`), `.gradle/**`, `.kotlin/**`, `app/schemas/**`, `gradle/wrapper/gradle-wrapper.jar`.
- File-length limit: more than 400 lines in a `.kt`/`.kts` file triggers the warning.
- Claude Code hook matcher `Write|Edit|MultiEdit`; Codex matcher `apply_patch|Edit|Write`. Both deny with `{"hookSpecificOutput": {"hookEventName": "PreToolUse", "permissionDecision": "deny", "permissionDecisionReason": ...}}`.
- Every `.claude/rules/*.md` has `paths:` frontmatter and stays under 40 lines.
- Every `SKILL.md` frontmatter has exactly `name` (equal to its directory) and `description`, each on one line.
- ADR files: `docs/decisions/NNNN-<kebab-case>.md`, Nygard sections, `Status: Accepted (2026-10-15)`.
- Agent-layer test command: `python3 -m unittest discover -s tools/agents -t tools/agents`.
- App verify command (must keep passing): `./gradlew assembleDebug testDebugUnitTest lintDebug`.

## Review Focus

1. A source package that happens to be named `build` (e.g. `app/src/main/kotlin/com/acme/build/Foo.kt`) must not be blocked (Task 1, `test_allows_source_package_named_build`).
2. A Codex `apply_patch` that touches one source file and one generated file must be denied, naming the generated file (Task 1, `test_denies_apply_patch_with_mixed_files`).
3. Paths outside the project (e.g. `/tmp/scratch.kt`) must never be blocked or measured (Task 1 and Task 2 tests).
4. A hook run with empty or non-JSON stdin must not break the agent: exit 0, no output (Task 1 and Task 2 tests).
5. A pointer in the `AGENTS.md` "When you need more" table that names a missing file must fail the suite (Task 7, `test_pointers_resolve`).

---

### Task 1: Shared hook payload module and `guard_generated` hook

**Files:**
- Create: `tools/agents/hooks/_payload.py`
- Create: `tools/agents/hooks/guard_generated.py`
- Create: `tools/agents/tests/__init__.py` (empty)
- Create: `tools/agents/tests/_support.py`
- Test: `tools/agents/tests/test_guard_generated.py`

**Interfaces:**
- Produces (`_payload.py`): `read_payload() -> dict | None`; `target_paths(payload: dict) -> list[Path]` (absolute, resolved); `project_relative(path: Path) -> str | None` (POSIX relative path or `None` when outside the project); `generated_hint(rel: str) -> str | None`; constant `PROJECT_ROOT: Path` (env `AGENT_HOOKS_PROJECT_ROOT` overrides the default repo root).
- Produces (`_support.py`): `REPO_ROOT: Path`, `run_hook(script: str, project_root: Path, payload: dict | None = None, raw: str | None = None) -> subprocess.CompletedProcess[str]`.

- [ ] **Step 1: Write the test support module and failing tests**

`tools/agents/tests/__init__.py`: empty file.

`tools/agents/tests/_support.py`:

```python
"""Helpers shared by the agent-layer tests."""
from __future__ import annotations

import json
import os
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
HOOKS_DIR = REPO_ROOT / "tools" / "agents" / "hooks"


def run_hook(
    script: str,
    project_root: Path,
    payload: dict | None = None,
    raw: str | None = None,
) -> subprocess.CompletedProcess[str]:
    """Run a hook script as the agent would: payload JSON on stdin, project root via env."""
    env = {**os.environ, "AGENT_HOOKS_PROJECT_ROOT": str(project_root)}
    stdin = raw if raw is not None else json.dumps(payload)
    return subprocess.run(
        [sys.executable, str(HOOKS_DIR / script)],
        input=stdin,
        text=True,
        capture_output=True,
        env=env,
        check=False,
    )
```

`tools/agents/tests/test_guard_generated.py`:

```python
from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from ._support import run_hook

SCRIPT = "guard_generated.py"


class GuardGeneratedTest(unittest.TestCase):
    def setUp(self) -> None:
        self._tmp = tempfile.TemporaryDirectory()
        self.root = Path(self._tmp.name).resolve()

    def tearDown(self) -> None:
        self._tmp.cleanup()

    def edit(self, file_path: str) -> dict:
        return {"hook_event_name": "PreToolUse", "tool_name": "Edit", "cwd": str(self.root),
                "tool_input": {"file_path": file_path}}

    def assert_denied(self, result, *fragments: str) -> None:
        self.assertEqual(result.returncode, 0, result.stderr)
        output = json.loads(result.stdout)["hookSpecificOutput"]
        self.assertEqual(output["hookEventName"], "PreToolUse")
        self.assertEqual(output["permissionDecision"], "deny")
        for fragment in fragments:
            self.assertIn(fragment, output["permissionDecisionReason"])

    def assert_allowed(self, result) -> None:
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout.strip(), "")

    def test_denies_build_output_by_absolute_path(self) -> None:
        result = run_hook(SCRIPT, self.root, self.edit(str(self.root / "app/build/generated/X.kt")))
        self.assert_denied(result, "app/build/generated/X.kt", "build output")

    def test_denies_room_schema_by_relative_path(self) -> None:
        result = run_hook(SCRIPT, self.root, self.edit("app/schemas/com.acme.AppDatabase/1.json"))
        self.assert_denied(result, "app/schemas/com.acme.AppDatabase/1.json", "Room")

    def test_denies_gradle_and_kotlin_caches_and_wrapper_jar(self) -> None:
        for path in (".gradle/8.0/x.bin", ".kotlin/sessions/a", "gradle/wrapper/gradle-wrapper.jar"):
            with self.subTest(path=path):
                self.assert_denied(run_hook(SCRIPT, self.root, self.edit(path)), path)

    def test_allows_source_file(self) -> None:
        result = run_hook(SCRIPT, self.root, self.edit("app/src/main/kotlin/com/acme/Foo.kt"))
        self.assert_allowed(result)

    def test_allows_source_package_named_build(self) -> None:
        result = run_hook(SCRIPT, self.root, self.edit("app/src/main/kotlin/com/acme/build/Foo.kt"))
        self.assert_allowed(result)

    def test_allows_path_outside_project(self) -> None:
        result = run_hook(SCRIPT, self.root, self.edit("/tmp/elsewhere/build/Foo.kt"))
        self.assert_allowed(result)

    def test_denies_apply_patch_with_mixed_files(self) -> None:
        patch = (
            "*** Begin Patch\n"
            "*** Update File: app/src/main/kotlin/com/acme/Foo.kt\n@@\n-a\n+b\n"
            "*** Update File: app/build/tmp/Gen.kt\n@@\n-a\n+b\n"
            "*** End Patch\n"
        )
        payload = {"hook_event_name": "PreToolUse", "tool_name": "apply_patch", "cwd": str(self.root),
                   "tool_input": {"command": patch}}
        result = run_hook(SCRIPT, self.root, payload)
        self.assert_denied(result, "app/build/tmp/Gen.kt")
        self.assertNotIn("Foo.kt is generated", result.stdout)

    def test_empty_or_invalid_stdin_is_ignored(self) -> None:
        for raw in ("", "not json", "[1, 2]"):
            with self.subTest(raw=raw):
                self.assert_allowed(run_hook(SCRIPT, self.root, raw=raw))


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `python3 -m unittest discover -s tools/agents -t tools/agents -v`
Expected: FAIL/ERROR in every `GuardGeneratedTest` test (the hook script does not exist yet; `json.loads` on empty stdout raises).

- [ ] **Step 3: Write `_payload.py`**

```python
"""Shared helpers for agent hooks: read the hook payload and locate the files a tool call writes."""
from __future__ import annotations

import json
import os
import re
import sys
from pathlib import Path

PROJECT_ROOT = Path(os.environ.get("AGENT_HOOKS_PROJECT_ROOT") or Path(__file__).resolve().parents[3]).resolve()

_PATCH_FILE_HEADER = re.compile(r"^\*\*\* (?:Add|Update|Delete) File: (.+)$")
_PATH_KEYS = ("file_path", "path", "filePath")


def read_payload() -> dict | None:
    """The hook payload from stdin, or None when stdin is empty or not a JSON object."""
    try:
        data = json.loads(sys.stdin.read() or "null")
    except (json.JSONDecodeError, UnicodeDecodeError):
        return None
    return data if isinstance(data, dict) else None


def target_paths(payload: dict) -> list[Path]:
    """Absolute paths a tool call writes: Claude Code passes file_path, Codex apply_patch passes a patch command."""
    tool_input = payload.get("tool_input")
    if not isinstance(tool_input, dict):
        return []
    raw: list[str] = []
    for key in _PATH_KEYS:
        value = tool_input.get(key)
        if isinstance(value, str) and value.strip():
            raw.append(value.strip())
    command = tool_input.get("command")
    if isinstance(command, str):
        for line in command.splitlines():
            match = _PATCH_FILE_HEADER.match(line.rstrip())
            if match:
                raw.append(match.group(1).strip())
    cwd = Path(payload.get("cwd") or os.getcwd())
    paths: list[Path] = []
    for item in raw:
        path = Path(item)
        resolved = (path if path.is_absolute() else cwd / path).resolve()
        if resolved not in paths:
            paths.append(resolved)
    return paths


def project_relative(path: Path) -> str | None:
    """POSIX path relative to the project root, or None for files outside the project."""
    try:
        return path.relative_to(PROJECT_ROOT).as_posix()
    except ValueError:
        return None


def generated_hint(rel: str) -> str | None:
    """Why a project-relative path is generated and what to edit instead, or None for source files."""
    parts = rel.split("/")
    if rel == "gradle/wrapper/gradle-wrapper.jar":
        return "the Gradle wrapper; change the version with ./gradlew wrapper --gradle-version <version>."
    if parts[0] in (".gradle", ".kotlin"):
        return "a Gradle/Kotlin cache; it is rebuilt automatically."
    if len(parts) > 2 and parts[0] == "app" and parts[1] == "schemas":
        return "Room exports this from @Database classes; change the entity/DAO and rebuild."
    for part in parts[:-1]:
        if part == "src":
            return None
        if part == "build":
            return "build output; change the source file that produces it."
    return None
```

- [ ] **Step 4: Write `guard_generated.py`**

```python
#!/usr/bin/env python3
"""PreToolUse hook: deny edits to generated files and say which source to change instead."""
from __future__ import annotations

import json
import sys

from _payload import generated_hint, project_relative, read_payload, target_paths


def main() -> int:
    payload = read_payload()
    if payload is None:
        return 0
    reasons = []
    for path in target_paths(payload):
        rel = project_relative(path)
        hint = generated_hint(rel) if rel else None
        if hint:
            reasons.append(f"{rel} is generated: {hint} Edit the source instead.")
    if reasons:
        json.dump(
            {
                "hookSpecificOutput": {
                    "hookEventName": "PreToolUse",
                    "permissionDecision": "deny",
                    "permissionDecisionReason": " ".join(reasons),
                }
            },
            sys.stdout,
        )
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception:  # a broken hook must never block the agent
        sys.exit(0)
```

- [ ] **Step 5: Run the tests to see them pass**

Run: `python3 -m unittest discover -s tools/agents -t tools/agents -v`
Expected: all `GuardGeneratedTest` tests pass, no other output.

### Task 2: `file_length` hook

**Files:**
- Create: `tools/agents/hooks/file_length.py`
- Test: `tools/agents/tests/test_file_length.py`

**Interfaces:**
- Consumes: `read_payload`, `target_paths`, `project_relative`, `generated_hint` from `_payload.py`; `run_hook` from `_support.py`.
- Produces: constant `LIMIT = 400` in `file_length.py`.

- [ ] **Step 1: Write the failing tests**

`tools/agents/tests/test_file_length.py`:

```python
from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from ._support import run_hook

SCRIPT = "file_length.py"


class FileLengthTest(unittest.TestCase):
    def setUp(self) -> None:
        self._tmp = tempfile.TemporaryDirectory()
        self.root = Path(self._tmp.name).resolve()

    def tearDown(self) -> None:
        self._tmp.cleanup()

    def write(self, rel: str, lines: int) -> Path:
        path = self.root / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text("".join(f"// line {n}\n" for n in range(lines)))
        return path

    def run_for(self, rel: str):
        payload = {"hook_event_name": "PostToolUse", "tool_name": "Write", "cwd": str(self.root),
                   "tool_input": {"file_path": rel}}
        return run_hook(SCRIPT, self.root, payload)

    def test_warns_for_kotlin_file_over_limit(self) -> None:
        self.write("app/src/main/kotlin/com/acme/Big.kt", 401)
        result = self.run_for("app/src/main/kotlin/com/acme/Big.kt")
        self.assertEqual(result.returncode, 0, result.stderr)
        output = json.loads(result.stdout)["hookSpecificOutput"]
        self.assertEqual(output["hookEventName"], "PostToolUse")
        self.assertIn("app/src/main/kotlin/com/acme/Big.kt has 401 lines", output["additionalContext"])
        self.assertIn("limit 400", output["additionalContext"])

    def test_warns_for_kts_file_over_limit(self) -> None:
        self.write("app/build.gradle.kts", 450)
        self.assertIn("450 lines", self.run_for("app/build.gradle.kts").stdout)

    def test_silent_at_limit(self) -> None:
        self.write("app/src/main/kotlin/com/acme/Ok.kt", 400)
        self.assertEqual(self.run_for("app/src/main/kotlin/com/acme/Ok.kt").stdout.strip(), "")

    def test_silent_for_non_kotlin_file(self) -> None:
        self.write("README.md", 900)
        self.assertEqual(self.run_for("README.md").stdout.strip(), "")

    def test_silent_for_generated_file(self) -> None:
        self.write("app/build/generated/Big.kt", 900)
        self.assertEqual(self.run_for("app/build/generated/Big.kt").stdout.strip(), "")

    def test_silent_for_missing_file_and_outside_project(self) -> None:
        self.assertEqual(self.run_for("app/src/main/kotlin/Missing.kt").stdout.strip(), "")
        self.assertEqual(self.run_for("/tmp/elsewhere/Big.kt").stdout.strip(), "")

    def test_empty_or_invalid_stdin_is_ignored(self) -> None:
        for raw in ("", "{", "42"):
            with self.subTest(raw=raw):
                result = run_hook(SCRIPT, self.root, raw=raw)
                self.assertEqual((result.returncode, result.stdout.strip()), (0, ""))


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run them to see them fail**

Run: `python3 -m unittest discover -s tools/agents -t tools/agents -v`
Expected: `FileLengthTest` tests ERROR (script missing); `GuardGeneratedTest` still passes.

- [ ] **Step 3: Write `file_length.py`**

```python
#!/usr/bin/env python3
"""PostToolUse hook: tell the agent when an edited Kotlin file grows past the size limit."""
from __future__ import annotations

import json
import sys

from _payload import generated_hint, project_relative, read_payload, target_paths

LIMIT = 400
_KOTLIN_SUFFIXES = (".kt", ".kts")


def main() -> int:
    payload = read_payload()
    if payload is None:
        return 0
    messages = []
    for path in target_paths(payload):
        rel = project_relative(path)
        if rel is None or generated_hint(rel) or path.suffix not in _KOTLIN_SUFFIXES or not path.is_file():
            continue
        lines = len(path.read_text(encoding="utf-8", errors="replace").splitlines())
        if lines > LIMIT:
            messages.append(
                f"{rel} has {lines} lines (limit {LIMIT}). Split it: extract a use case or sub-composables."
            )
    if messages:
        json.dump(
            {"hookSpecificOutput": {"hookEventName": "PostToolUse", "additionalContext": " ".join(messages)}},
            sys.stdout,
        )
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception:  # a broken hook must never block the agent
        sys.exit(0)
```

- [ ] **Step 4: Run the tests to see them pass**

Run: `python3 -m unittest discover -s tools/agents -t tools/agents -v`
Expected: all tests pass.

### Task 3: Wire the hooks into Claude Code and Codex

**Files:**
- Modify: `.claude/settings.json` (add `hooks`, keep existing keys)
- Create: `.codex/hooks.json`
- Test: `tools/agents/tests/test_hook_config.py`

**Interfaces:**
- Consumes: hook scripts `tools/agents/hooks/guard_generated.py`, `tools/agents/hooks/file_length.py`.

- [ ] **Step 1: Write the failing test**

`tools/agents/tests/test_hook_config.py`:

```python
from __future__ import annotations

import json
import re
import unittest

from ._support import REPO_ROOT

SCRIPT_REF = re.compile(r"tools/agents/hooks/([a-z_]+\.py)")


def load(rel: str) -> dict:
    return json.loads((REPO_ROOT / rel).read_text())


def commands(config: dict, event: str) -> list[tuple[str, str]]:
    return [(group["matcher"], hook["command"]) for group in config["hooks"][event] for hook in group["hooks"]]


class HookConfigTest(unittest.TestCase):
    def assert_wired(self, config: dict, matcher: str) -> None:
        expected = {"PreToolUse": "guard_generated.py", "PostToolUse": "file_length.py"}
        for event, script in expected.items():
            with self.subTest(event=event):
                entries = commands(config, event)
                self.assertTrue(entries, f"no {event} hooks")
                for group_matcher, command in entries:
                    self.assertEqual(group_matcher, matcher)
                    self.assertEqual(SCRIPT_REF.findall(command), [script])
                    self.assertTrue((REPO_ROOT / "tools/agents/hooks" / script).is_file())

    def test_claude_settings_keep_plugin_and_add_hooks(self) -> None:
        settings = load(".claude/settings.json")
        self.assertIn("mobile-app-config", settings["extraKnownMarketplaces"])
        self.assertTrue(settings["enabledPlugins"]["mobile-app-config@mobile-app-config"])
        self.assert_wired(settings, "Write|Edit|MultiEdit")
        for _, command in commands(settings, "PreToolUse") + commands(settings, "PostToolUse"):
            self.assertIn('"${CLAUDE_PROJECT_DIR}/tools/agents/hooks/', command)

    def test_codex_hooks(self) -> None:
        self.assert_wired(load(".codex/hooks.json"), "apply_patch|Edit|Write")


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run it to see it fail**

Run: `python3 -m unittest discover -s tools/agents -t tools/agents -v`
Expected: `HookConfigTest` fails with `KeyError: 'hooks'` and `FileNotFoundError` for `.codex/hooks.json`.

- [ ] **Step 3: Write the configs**

`.claude/settings.json` (full file; the first two keys are unchanged):

```json
{
  "extraKnownMarketplaces": {
    "mobile-app-config": {
      "source": { "source": "github", "repo": "dthuy62/mobile-app-config" }
    }
  },
  "enabledPlugins": {
    "mobile-app-config@mobile-app-config": true
  },
  "hooks": {
    "PreToolUse": [
      {
        "matcher": "Write|Edit|MultiEdit",
        "hooks": [
          {
            "type": "command",
            "command": "python3 \"${CLAUDE_PROJECT_DIR}/tools/agents/hooks/guard_generated.py\"",
            "timeout": 10
          }
        ]
      }
    ],
    "PostToolUse": [
      {
        "matcher": "Write|Edit|MultiEdit",
        "hooks": [
          {
            "type": "command",
            "command": "python3 \"${CLAUDE_PROJECT_DIR}/tools/agents/hooks/file_length.py\"",
            "timeout": 10
          }
        ]
      }
    ]
  }
}
```

`.codex/hooks.json`:

```json
{
  "hooks": {
    "PreToolUse": [
      {
        "matcher": "apply_patch|Edit|Write",
        "hooks": [
          {
            "type": "command",
            "command": "python3 \"$(git rev-parse --show-toplevel)/tools/agents/hooks/guard_generated.py\"",
            "timeout": 10,
            "statusMessage": "Checking for generated files"
          }
        ]
      }
    ],
    "PostToolUse": [
      {
        "matcher": "apply_patch|Edit|Write",
        "hooks": [
          {
            "type": "command",
            "command": "python3 \"$(git rev-parse --show-toplevel)/tools/agents/hooks/file_length.py\"",
            "timeout": 10,
            "statusMessage": "Checking file length"
          }
        ]
      }
    ]
  }
}
```

- [ ] **Step 4: Run the tests to see them pass**

Run: `python3 -m unittest discover -s tools/agents -t tools/agents -v`
Expected: all tests pass.

### Task 4: Stack skill and path-scoped rules

**Files:**
- Create: `.claude/skills/android-agentic-starter/SKILL.md`
- Create: `.claude/skills/android-agentic-starter/references/presentation.md`
- Create: `.claude/skills/android-agentic-starter/references/data-and-errors.md`
- Create: `.claude/skills/android-agentic-starter/references/review-checklist.md`
- Create: `.claude/rules/compose-ui.md`
- Create: `.claude/rules/testing.md`
- Test: `tools/agents/tests/test_skills_and_rules.py`

**Interfaces:**
- Produces: `frontmatter(text: str) -> dict[str, list[str]]` in `test_skills_and_rules.py` (keys → value lines), used only there.

- [ ] **Step 1: Write the failing test**

`tools/agents/tests/test_skills_and_rules.py`:

```python
from __future__ import annotations

import re
import unittest

from ._support import REPO_ROOT

SKILLS = REPO_ROOT / ".claude" / "skills"
RULES = REPO_ROOT / ".claude" / "rules"
LINK = re.compile(r"`(references/[^`]+\.md)`")


def frontmatter(text: str) -> dict[str, list[str]]:
    """Top-level keys of a '---' delimited header, each mapped to its value lines."""
    if not text.startswith("---\n"):
        return {}
    header = text[4:text.index("\n---\n", 4)]
    keys: dict[str, list[str]] = {}
    current = None
    for line in header.splitlines():
        if line and not line.startswith((" ", "-")) and ":" in line:
            current, value = line.split(":", 1)
            keys[current] = [value.strip()] if value.strip() else []
        elif current is not None and line.strip():
            keys[current].append(line.strip())
    return keys


class SkillsTest(unittest.TestCase):
    def test_every_skill_has_name_and_description_only(self) -> None:
        skill_dirs = [path for path in SKILLS.iterdir() if path.is_dir()]
        self.assertTrue(skill_dirs)
        for skill in skill_dirs:
            with self.subTest(skill=skill.name):
                meta = frontmatter((skill / "SKILL.md").read_text())
                self.assertEqual(sorted(meta), ["description", "name"])
                self.assertEqual(meta["name"], [skill.name])
                self.assertEqual(len(meta["description"]), 1)
                self.assertGreater(len(meta["description"][0]), 40)

    def test_reference_links_resolve(self) -> None:
        for skill_md in SKILLS.glob("*/SKILL.md"):
            for ref in LINK.findall(skill_md.read_text()):
                with self.subTest(skill=skill_md.parent.name, ref=ref):
                    self.assertTrue((skill_md.parent / ref).is_file())


class RulesTest(unittest.TestCase):
    def test_rules_are_path_scoped_and_short(self) -> None:
        rules = sorted(RULES.glob("*.md"))
        self.assertEqual([rule.name for rule in rules], ["compose-ui.md", "testing.md"])
        for rule in rules:
            with self.subTest(rule=rule.name):
                text = rule.read_text()
                meta = frontmatter(text)
                self.assertEqual(list(meta), ["paths"])
                self.assertTrue(meta["paths"] and all(item.startswith('- "') for item in meta["paths"]))
                self.assertLess(len(text.splitlines()), 40)


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run it to see it fail**

Run: `python3 -m unittest discover -s tools/agents -t tools/agents -v`
Expected: `SkillsTest` and `RulesTest` error with `FileNotFoundError` (no `.claude/skills` / `.claude/rules`).

- [ ] **Step 3: Write the skill files**

`.claude/skills/android-agentic-starter/SKILL.md`:

```markdown
---
name: android-agentic-starter
description: Use when writing or changing app code under app/src (ViewModels, use cases, repositories, DI, navigation) or its unit tests. Not for docs, Gradle scripts or agent configuration.
---

# App code in this starter

Layers: `ui/` (Compose + ViewModel) → `domain/` (models, ports, use cases) ← `data/` (Room, DataStore, engines).
Hilt bindings live in `di/RepositoryModule.kt`.

## Pick one presentation pattern per screen

- **MVVM-lite (default):** `@HiltViewModel`, one `StateFlow<UiState>`, user actions are plain functions.
  Example: `ui/settings`.
- **MVI:** only for interdependent state, streaming or async work, or one-shot effects.
  Extend `MviViewModel<State, Intent, Effect>`. Example: `ui/chat`.
- Move a screen to MVI when you would add a third boolean flag, a `SharedFlow` for events, or you hit ordering
  bugs between async calls. Never mix both in one screen.

## Read when relevant

- Screens and UI state: `references/presentation.md`
- Repositories, use cases, errors, coroutines: `references/data-and-errors.md`
- Before reporting done: `references/review-checklist.md`

## Done means

`./gradlew assembleDebug testDebugUnitTest lintDebug` passes, and new ViewModel or use case logic has a unit test
that uses fakes.
```

`.claude/skills/android-agentic-starter/references/presentation.md`:

```markdown
# Screens and UI state

- A stateful `XScreen(viewModel: XViewModel = hiltViewModel(), ...)` collects state and calls a stateless
  `XContent(state, onAction...)`. `XContent` has a `@Preview` and no Hilt.
- `UiState` is an immutable data class, or a sealed interface when states exclude each other
  (`Loading` / `Content` / `Error`). Never parallel booleans such as `isLoading` + `hasError`.
- Lists in state are `ImmutableList` (`persistentListOf()`, `toImmutableList()`).
- Collect with `collectAsStateWithLifecycle()`.
- One-shot events (snackbar, navigation) are MVI effects collected with `CollectEffects`, never a state flag that
  the UI resets.
- ViewModels expose `AppError`; the screen maps it to a string resource. ViewModels never hold `Throwable` or SDK
  types.
- Screens take navigation callbacks (`onOpenSettings`, `onBack`); `navigation/NavGraph.kt` wires them.
```

`.claude/skills/android-agentic-starter/references/data-and-errors.md`:

```markdown
# Data, errors and coroutines

- Repositories return `Outcome<T>` for expected failures. `data/` turns exceptions into `AppError`, usually with
  `outcomeOf { ... }` from `core/common`, which rethrows `CancellationException`.
- Never catch `CancellationException` without rethrowing it.
- Inject `DispatcherProvider`; never reference `Dispatchers.IO` directly.
- Add a use case only for real logic or reuse (`SendMessageUseCase`). No pass-through use cases.
- Interfaces go in `domain/repository` or `domain/engine`, implementations in `data/`, one `@Binds` in
  `di/RepositoryModule.kt`.
- Avoid primitive obsession for finite or risky concepts: enums, sealed types or `@JvmInline value class` instead
  of raw `String`, `Int` or `Boolean`. Parse wire values at the `data/` boundary.
- Prefer AndroidX/Jetpack or a maintained library over a hand-written helper. Versions go only in
  `gradle/libs.versions.toml`.
- Room: change entities or DAOs and bump the database version; `app/schemas/` is generated.
```

`.claude/skills/android-agentic-starter/references/review-checklist.md`:

```markdown
# Review checklist

- Does `ui/` import from `data/`, or `domain/` import Android, Room or DataStore types?
- Does one feature import another instead of going through `NavGraph.kt`?
- Is each screen MVVM-lite or MVI, not both, and would MVVM-lite have been enough?
- Is UI state one immutable value with explicit exclusive states?
- Are expected failures `Outcome`/`AppError`, and is `CancellationException` rethrown?
- Is every new string in `strings.xml` and every spacing from `Spacing`?
- Does new ViewModel or use case logic have a unit test with fakes?
- Did the change stay inside the files the task needed, and is every file under 400 lines?
```

- [ ] **Step 4: Write the rule files**

`.claude/rules/compose-ui.md`:

```markdown
---
paths:
  - "app/src/main/**/ui/**"
---

# Compose UI

- Adapt by window size, not device type or orientation: use the window size class,
  `NavigationSuiteScaffold` for top-level navigation, and a list-detail layout where a list opens details.
- On wide windows add panes or constrain content width instead of stretching rows and forms.
- Every interactive element has a semantic label (`contentDescription` or text) and a touch target of at least 48dp.
- Text comes from `res/values/strings.xml`; spacing from `ui/theme/Spacing`; colors and type from `MaterialTheme`.
- Use `LazyColumn`/`LazyVerticalGrid` with stable keys for long or unbounded content.
- Give each `XContent` a `@Preview`; keep previews free of Hilt and real data sources.
- Keep navigation, snackbars and dialogs at the screen boundary, driven by callbacks or effects.
```

`.claude/rules/testing.md`:

```markdown
---
paths:
  - "app/src/test/**"
  - "app/src/androidTest/**"
---

# Tests

- Test ViewModels and use cases on the JVM with fakes of domain interfaces; add a fake next to the existing ones
  (`ui/chat/Fakes.kt`) instead of using a mocking library.
- Use `MainDispatcherRule` for code that touches `Dispatchers.Main`.
- Assert on `state.value` and collected effects, not on internal calls.
- One behaviour per test; name tests after the behaviour (`sending blank message does nothing`).
- Cover the failure path: a fake that returns `Outcome.Failure` and the UI state it produces.
- Add Compose tests against `XContent` only when a screen has non-trivial rendering logic.
```

- [ ] **Step 5: Run the tests to see them pass**

Run: `python3 -m unittest discover -s tools/agents -t tools/agents -v`
Expected: all tests pass.

### Task 5: `new-feature` and `setup-starter` skills

**Files:**
- Create: `.claude/skills/new-feature/SKILL.md`
- Create: `.claude/skills/setup-starter/SKILL.md`
- Test: existing `tools/agents/tests/test_skills_and_rules.py` (covers every skill directory)

- [ ] **Step 1: Write `.claude/skills/new-feature/SKILL.md`**

```markdown
---
name: new-feature
description: Use when adding a new screen or feature under ui/ in this Android starter, from scaffolding to navigation and tests.
---

# Add a screen

1. Run `scripts/new-feature.sh <name>` (lowercase letters and digits). It creates `ui/<name>/` with
   `<Name>Route`, an MVVM-lite `<Name>ViewModel`, and `<Name>Screen` + `<Name>Content`.
2. Register it in `navigation/NavGraph.kt`: add `entry<<Name>Route> { <Name>Screen(onBack = { backStack.removeLastOrNull() }) }`
   and pass any other navigation as callbacks. The new screen never imports another feature.
3. Keep MVVM-lite unless the MVI rule in skill `android-agentic-starter` applies.
4. Add `app/src/test/kotlin/<package path>/ui/<name>/<Name>ViewModelTest.kt` with fakes of domain interfaces and
   `MainDispatcherRule` (see `ui/chat/Fakes.kt`).
5. Put user-facing text in `res/values/strings.xml`.
6. Run `./gradlew assembleDebug testDebugUnitTest lintDebug`.
```

- [ ] **Step 2: Write `.claude/skills/setup-starter/SKILL.md`**

```markdown
---
name: setup-starter
description: Use for first-time setup of a project made from this starter, or when changing the app name, package/applicationId, project name, flavors or Firebase.
---

# Set up the starter

Identity and build configuration change only through the mobile-app-config plugin (`/mobile-app-config:android`
in Claude Code, or the `mobile-app-config` CLI). Never edit `namespace`, `applicationId`, package folders or
`app_name` by hand. If the CLI is missing: `pipx install git+https://github.com/dthuy62/mobile-app-config.git`.

1. Ask for: app name, applicationId (reverse domain, e.g. `com.acme.notes`), root project name (kebab-case),
   whether to add `dev`/`stg`/`prod` flavors, and whether the app uses Firebase.
2. Rename: `mobile-app-config package-name --application-id <id> --app-name "<name>" --root-project-name "<project>"`.
3. Flavors, if wanted: `mobile-app-config flavors`, then `mobile-app-config validate-flavors`.
4. Firebase, if wanted (needs `firebase login`): `mobile-app-config firebase --mode single --project <project-id>`,
   or `--mode per-flavor --flavor dev=<id> --flavor stg=<id> --flavor prod=<id>`.
5. Verify: `./gradlew assembleDebug testDebugUnitTest lintDebug`. With flavors the tasks are per variant, for
   example `./gradlew assembleDevDebug testDevDebugUnitTest lintDevDebug`.
6. If the user changes a default decision recorded in `docs/decisions/`, write a new ADR with skill `adr`.
```

- [ ] **Step 3: Run the tests**

Run: `python3 -m unittest discover -s tools/agents -t tools/agents -v`
Expected: all tests pass (the skill test now covers three skills).

### Task 6: ADRs, `adr` skill and ARCHITECTURE links

**Files:**
- Create: `.claude/skills/adr/SKILL.md`, `.claude/skills/adr/references/template.md`
- Create: `docs/decisions/0001-use-a-single-app-module.md`
- Create: `docs/decisions/0002-default-to-mvvm-lite-and-use-mvi-when-needed.md`
- Create: `docs/decisions/0003-return-outcome-for-expected-failures.md`
- Create: `docs/decisions/0004-change-identity-via-mobile-app-config.md`
- Modify: `docs/ARCHITECTURE.md` (§1 last paragraph, §3 "Why not the others", §4 Errors bullet, §7)
- Test: `tools/agents/tests/test_decisions.py`

- [ ] **Step 1: Write the failing test**

`tools/agents/tests/test_decisions.py`:

```python
from __future__ import annotations

import re
import unittest

from ._support import REPO_ROOT

DECISIONS = REPO_ROOT / "docs" / "decisions"
FILE_NAME = re.compile(r"^\d{4}-[a-z0-9]+(?:-[a-z0-9]+)*\.md$")
STATUS = re.compile(r"^Status: (Proposed|Accepted|Rejected|Deprecated|Superseded by \d{4})( \(\d{4}-\d{2}-\d{2}\))?$", re.M)


class DecisionsTest(unittest.TestCase):
    def test_adrs_follow_the_format(self) -> None:
        files = sorted(DECISIONS.glob("*.md"))
        self.assertGreaterEqual(len(files), 4)
        numbers = []
        for adr in files:
            with self.subTest(adr=adr.name):
                self.assertRegex(adr.name, FILE_NAME)
                text = adr.read_text()
                self.assertTrue(text.startswith(f"# {int(adr.name[:4])}. "))
                self.assertRegex(text, STATUS)
                for section in ("## Context", "## Decision", "## Consequences"):
                    self.assertIn(section, text)
                numbers.append(int(adr.name[:4]))
        self.assertEqual(numbers, list(range(1, len(numbers) + 1)))

    def test_architecture_links_resolve(self) -> None:
        text = (REPO_ROOT / "docs" / "ARCHITECTURE.md").read_text()
        links = re.findall(r"\]\((decisions/[^)]+)\)", text)
        self.assertGreaterEqual(len(links), 3)
        for link in links:
            with self.subTest(link=link):
                self.assertTrue((REPO_ROOT / "docs" / link).is_file())


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run it to see it fail**

Run: `python3 -m unittest discover -s tools/agents -t tools/agents -v`
Expected: `DecisionsTest` fails (no ADR files; no `decisions/` links).

- [ ] **Step 3: Write the `adr` skill**

`.claude/skills/adr/SKILL.md`:

```markdown
---
name: adr
description: Use when making, recording, questioning or replacing an architecture decision in this repo, or when asked to write an ADR.
---

# Architecture decision records

ADRs live in `docs/decisions/`. Read the relevant ones before proposing a change to architecture.

## Write one when

- the decision changes structure, layers, dependencies or a public convention, or is costly to reverse;
- someone later will ask "why is it like this?".

Skip it for style choices a linter covers, one-file details, and temporary workarounds.

## How

1. Next number: highest `NNNN` in `docs/decisions/` plus one, four digits.
2. File name: `NNNN-<present-tense-verb-phrase>.md`, lowercase kebab-case (`0005-add-a-network-layer.md`).
3. Fill `references/template.md`: one decision per file; Context states the forces, Decision states the choice
   plainly, Consequences lists what gets easier and what gets harder.
4. Status line: `Status: Accepted (YYYY-MM-DD)` once agreed, `Proposed (YYYY-MM-DD)` before.
5. Never rewrite an accepted ADR. To change course, write a new ADR and set the old one's status to
   `Status: Superseded by NNNN`.
6. If `docs/ARCHITECTURE.md` describes the affected area, link the ADR from there.
```

`.claude/skills/adr/references/template.md`:

```markdown
# N. `<Decision as a short imperative sentence>`

Status: Proposed (YYYY-MM-DD)

## Context

`<The situation and the forces in tension: constraints, goals, what hurts today. Facts, not the answer.>`

## Decision

`<What we will do, stated plainly. Name the alternatives that were rejected and why in one line each.>`

## Consequences

`<What gets easier. What gets harder or riskier. Follow-up work or decisions this creates.>`
```

- [ ] **Step 4: Write the four ADRs**

`docs/decisions/0001-use-a-single-app-module.md`:

```markdown
# 1. Use a single app module

Status: Accepted (2026-10-15)

## Context

The starter must be quick to build, easy to read in one sitting, and easy for coding agents to navigate.
Multi-module setups add build logic, convention plugins and longer sync times before a project has any features.

## Decision

Keep one Gradle module, `:app`, organized by package: `ui/`, `domain/`, `data/`, `di/`, `navigation/`,
`core/`. Layer boundaries are enforced by review, `AGENTS.md` and (later) architecture tests, not by the compiler.
Rejected: feature modules from day one (cost before benefit).

## Consequences

Builds and onboarding stay simple. The compiler does not stop a `ui/` file from importing `data/`, so automated
checks must. The package split matches how the app would be cut into modules if a team later needs them.
```

`docs/decisions/0002-default-to-mvvm-lite-and-use-mvi-when-needed.md`:

```markdown
# 2. Default to MVVM-lite and use MVI when needed

Status: Accepted (2026-10-15)

## Context

Screens differ: settings forms need little structure, while chat-like screens stream data, run async work and emit
one-shot events. One heavy pattern everywhere adds boilerplate; no pattern lets state drift into parallel flags.

## Decision

Every screen uses unidirectional data flow with one immutable state. MVVM-lite (`StateFlow<UiState>`, plain
functions for actions) is the default. A screen moves to MVI (`MviViewModel<State, Intent, Effect>`) when it would
need a third boolean flag, a `SharedFlow` for events, or ordering between async calls. One pattern per screen.
Rejected: MVC (Activity becomes view and controller, hard to test); MVP (a View interface per screen and manual
lifecycle, which ViewModel plus state flows replace); MVVM with many separate flows (drifts into inconsistent state).

## Consequences

Simple screens stay small and complex screens get explicit intents and effects. Reviewers must check that the MVI
rule, not habit, drives the choice.
```

`docs/decisions/0003-return-outcome-for-expected-failures.md`:

```markdown
# 3. Return Outcome for expected failures

Status: Accepted (2026-10-15)

## Context

Network, database and model calls fail in expected ways. Exceptions thrown across layers leak SDK types into
ViewModels, are easy to forget, and can swallow coroutine cancellation.

## Decision

Repositories and engines return `Outcome<T>` (`Success` or `Failure(AppError)`). `data/` converts exceptions to
`AppError`, rethrowing `CancellationException`. ViewModels map `AppError` to UI state; screens map it to strings.
Rejected: throwing across layers; Kotlin `Result` (carries a raw `Throwable`).

## Consequences

Failure paths are visible in types and easy to fake in tests. Every data source needs a mapping to `AppError`,
and new error kinds need a string resource.
```

`docs/decisions/0004-change-identity-via-mobile-app-config.md`:

```markdown
# 4. Change identity via mobile-app-config

Status: Accepted (2026-10-15)

## Context

Every project made from the starter must change its package, applicationId, app name and project name. Hand edits
miss files (test packages, Room schema folders, imports), and agents repeat the same mistakes.

## Decision

Identity changes go through the `mobile-app-config` plugin (`package-name` command or `/mobile-app-config:android`).
Template class names carry no project name (`MainApplication`, `AppTheme`, `Theme.App`) so the plugin covers the
whole rename. Rejected: a starter-specific rename script (duplicates the plugin).

## Consequences

Renaming is one tested command that agents can run. The starter depends on an external plugin repository; the
README documents installing it.
```

- [ ] **Step 5: Link the ADRs from `docs/ARCHITECTURE.md`**

Replace in §1:

```
It is one Gradle module on purpose: simple to build, simple to learn. The boundaries are enforced by review
and by `AGENTS.md`, not by the compiler. If a team later needs compiler-enforced boundaries, the package
split (`ui/`, `domain/`, `data/`) already matches how it would be cut into modules.
```

with:

```
It is one Gradle module on purpose; see [ADR 1](decisions/0001-use-a-single-app-module.md).
```

Replace in §3 the block from `Why not the others:` through the line `- **MVVM with many separate flows per screen**: drifts into inconsistent state. Keep one `UiState`.` with:

```
Why this split and not MVC, MVP or many flows per screen: [ADR 2](decisions/0002-default-to-mvvm-lite-and-use-mvi-when-needed.md).
```

Replace in §4 the Errors bullet:

```
- **Errors**: `data/` converts exceptions to `AppError`; repositories return `Outcome<T>`; ViewModel maps `AppError`
  to UI state; screens map it to a string resource.
```

with:

```
- **Errors**: `data/` converts exceptions to `AppError`; repositories return `Outcome<T>`; ViewModel maps `AppError`
  to UI state; screens map it to a string resource ([ADR 3](decisions/0003-return-outcome-for-expected-failures.md)).
```

Replace the body of §7 (everything after the `## 7. Working with AI agents` heading) with:

```
Agents start from `AGENTS.md` and load skills (`.claude/skills/`) and path-scoped rules (`.claude/rules/`) on demand.
Decisions and their reasons live in `docs/decisions/`, one file per decision.
```

- [ ] **Step 6: Run the tests to see them pass**

Run: `python3 -m unittest discover -s tools/agents -t tools/agents -v`
Expected: all tests pass.

### Task 7: Multi-agent wiring, `AGENTS.md`, `CLAUDE.md` and README

**Files:**
- Create symlinks: `.agents/skills -> ../.claude/skills`, `.opencode/skills -> ../.claude/skills`
- Create: `.cursor/rules/android.mdc`
- Replace: `AGENTS.md`
- Replace: `CLAUDE.md` (symlink → regular file)
- Modify: `README.md` (new "Working with AI agents" section)
- Test: `tools/agents/tests/test_agent_entry_points.py`

- [ ] **Step 1: Write the failing test**

`tools/agents/tests/test_agent_entry_points.py`:

```python
from __future__ import annotations

import re
import unittest

from ._support import REPO_ROOT

TABLE_ROW = re.compile(r"^\|(?P<task>[^|]+)\|(?P<target>[^|]+)\|$")
SKILL_REF = re.compile(r"skill `([a-z0-9-]+)`")
PATH_REF = re.compile(r"`([.\w/-]+(?:\.md|/))`")


class AgentEntryPointsTest(unittest.TestCase):
    def test_agents_md_is_short(self) -> None:
        lines = (REPO_ROOT / "AGENTS.md").read_text().splitlines()
        self.assertLessEqual(len(lines), 60)

    def test_pointers_resolve(self) -> None:
        text = (REPO_ROOT / "AGENTS.md").read_text()
        table = text.split("## When you need more", 1)[1]
        rows = [m for line in table.splitlines() if (m := TABLE_ROW.match(line.strip()))]
        targets = [m["target"] for m in rows if not set(m["target"].strip()) <= set("-: ")][1:]
        self.assertGreaterEqual(len(targets), 6)
        for target in targets:
            for skill in SKILL_REF.findall(target):
                with self.subTest(skill=skill):
                    self.assertTrue((REPO_ROOT / ".claude/skills" / skill / "SKILL.md").is_file())
            for path in PATH_REF.findall(target):
                with self.subTest(path=path):
                    self.assertTrue((REPO_ROOT / path).exists())

    def test_claude_md_imports_agents_md(self) -> None:
        claude = REPO_ROOT / "CLAUDE.md"
        self.assertFalse(claude.is_symlink())
        first = next(line for line in claude.read_text().splitlines() if line.strip())
        self.assertEqual(first.strip(), "@AGENTS.md")

    def test_skill_folders_are_shared_through_symlinks(self) -> None:
        target = (REPO_ROOT / ".claude/skills").resolve()
        for link in (".agents/skills", ".opencode/skills"):
            with self.subTest(link=link):
                path = REPO_ROOT / link
                self.assertTrue(path.is_symlink())
                self.assertEqual(path.resolve(), target)

    def test_cursor_rule_points_to_agents_md(self) -> None:
        text = (REPO_ROOT / ".cursor/rules/android.mdc").read_text()
        self.assertIn("alwaysApply: true", text)
        self.assertIn("AGENTS.md", text)


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run it to see it fail**

Run: `python3 -m unittest discover -s tools/agents -t tools/agents -v`
Expected: `test_agents_md_is_short` fails (67 lines), `test_pointers_resolve` fails (no table), `test_claude_md_imports_agents_md` fails (symlink), the symlink and Cursor tests error.

- [ ] **Step 3: Create the symlinks and the Cursor rule**

```bash
mkdir -p .agents .opencode .cursor/rules
ln -s ../.claude/skills .agents/skills
ln -s ../.claude/skills .opencode/skills
```

`.cursor/rules/android.mdc`:

```markdown
---
description: Project rules for this Android starter
alwaysApply: true
---

Follow `AGENTS.md` at the repository root. For a specific task, open the file its "When you need more" table names.
```

- [ ] **Step 4: Replace `AGENTS.md`**

````markdown
# AGENTS.md

Android starter: one `:app` module, Jetpack Compose, Hilt, Room, DataStore, Navigation 3.
Package root = `namespace` in `app/build.gradle.kts`. Layers: `ui/` → `domain/` ← `data/`, wired in `di/`.

## Verify (run before saying a task is done)

```bash
./gradlew assembleDebug testDebugUnitTest lintDebug
```

## Never

- Import `data/` from `ui/`, or Android, Room or DataStore types from `domain/`.
- Import one `ui/<feature>` from another; navigation goes through callbacks in `navigation/NavGraph.kt`.
- Edit generated files (`build/`, `.gradle/`, `app/schemas/`); a hook blocks it.
- Change the package, applicationId or app name by hand; use the `mobile-app-config` plugin.
- Put dependency versions anywhere but `gradle/libs.versions.toml`.
- Mix MVVM-lite and MVI in one screen, or add MVC or MVP.

## When you need more

| Task | Read first |
|---|---|
| Write ViewModel, use case, repository or DI code | skill `android-agentic-starter` |
| Add a screen | skill `new-feature` |
| Change Compose UI | `.claude/rules/compose-ui.md` |
| Write or fix tests | `.claude/rules/testing.md` |
| Make or question an architecture decision | `docs/decisions/` and skill `adr` |
| First-time setup, rename, flavors, Firebase | skill `setup-starter` |

Skills live in `.claude/skills/` (also reachable as `.agents/skills/` and `.opencode/skills/`).
````

- [ ] **Step 5: Replace `CLAUDE.md` with a regular file**

```bash
rm CLAUDE.md
```

`CLAUDE.md`:

```markdown
@AGENTS.md

## Claude Code only

- Skills are in `.claude/skills/`; the identity plugin runs as `/mobile-app-config:android`.
- Hooks in `.claude/settings.json` block edits to generated files and flag Kotlin files over 400 lines.
```

- [ ] **Step 6: Add the README section**

Insert before `## Not included on purpose (add when needed)`:

```markdown
## Working with AI agents

- `AGENTS.md` holds the commands and never-break rules for every agent; `CLAUDE.md` imports it.
- Skills in `.claude/skills/` (`android-agentic-starter`, `new-feature`, `setup-starter`, `adr`) load on demand.
  Codex and OpenCode read them through the `.agents/skills` and `.opencode/skills` symlinks. On Windows, enable
  Developer Mode and `git config --global core.symlinks true` before cloning, or those agents will not see them.
- Architecture decisions and their reasons: `docs/decisions/`.
- Hooks (`.claude/settings.json`, `.codex/hooks.json`) block edits to generated files and flag Kotlin files over
  400 lines. They need `python3`. Test the agent layer with `python3 -m unittest discover -s tools/agents -t tools/agents`.
```

- [ ] **Step 7: Run all checks**

Run: `python3 -m unittest discover -s tools/agents -t tools/agents -v`
Expected: all tests pass.

Run: `wc -l AGENTS.md`
Expected: a number ≤ 60.

Run: `./gradlew assembleDebug testDebugUnitTest lintDebug`
Expected: `BUILD SUCCESSFUL` (no app code changed).

### Task 8: Live hook check in Claude Code (controller, no code changes)

- [ ] **Step 1: Generated-file edit is denied**

```bash
claude -p "Use the Edit tool to change the identityHash value in app/schemas/com.example.agenticstarter.data.local.AppDatabase/1.json to \"x\". Do not use Bash." --allowedTools Edit,Read
```

Expected: the reply says the edit was blocked and mentions Room/`app/schemas`; the file is unchanged
(`grep identityHash app/schemas/*/1.json` still shows the original hash).

- [ ] **Step 2: Length warning fires**

Create a 450-line Kotlin file through Claude Code and check the reply mentions the 400-line limit:

```bash
claude -p "Use the Write tool to create app/src/main/kotlin/com/example/agenticstarter/LengthProbe.kt containing a Kotlin comment line repeated 450 times. Then tell me exactly what any hook told you." --allowedTools Write
rm -f app/src/main/kotlin/com/example/agenticstarter/LengthProbe.kt
```

Expected: the reply quotes "has 450 lines (limit 400)". The probe file is deleted afterwards.
