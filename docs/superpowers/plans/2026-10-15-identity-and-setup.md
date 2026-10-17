# Identity and Setup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the starter rename-proof and give it the identity `android-agentic-starter`, delegating identity changes to a fixed `mobile-app-config` plugin that uses a consistent `dev`/`stg`/`prod` flavor set and installs from a Claude Code marketplace.

**Architecture:** Two repositories. The plugin (`/Users/dthuy/Workspace/mobile-app-config`, Python 3.9+, pytest) gets neutral `dev`/`stg`/`prod` defaults with one naming rule, a side-effect-free `package-name`, and a `.claude-plugin/marketplace.json`. The starter (`/Users/dthuy/Workspace/android-base-2026`, Kotlin/Compose/Gradle) drops every name that carries the project identity, is renamed by running the fixed plugin, and wires the plugin in `.claude/settings.json`.

**Tech Stack:** Python 3 + pytest (plugin); Kotlin, Gradle KTS, bash 3.2-compatible shell (starter); Claude Code plugin marketplace.

**Spec:** `docs/superpowers/specs/2026-10-15-identity-and-setup-design.md`

## Global Constraints

- Plugin: work on local branch `fix/neutral-defaults-and-claude-marketplace`, commit per task; never push, tag or release.
- Starter: change the working tree only. No commits, no branches.
- Template identity: applicationId/namespace/source package `com.example.agenticstarter`; `app_name` `Android Agentic Starter`; `rootProject.name` `android-agentic-starter`.
- Neutral class/theme names: `MainApplication`, `AppTheme`, `Theme.App`.
- Default flavor set, in order: `dev`, `stg`, `prod`. Suffixes `.dev`, `.stg`, none. BuildConfig fields `APP_ENVIRONMENT` (`dev`/`stg`/`prod`) and `APP_BASE_URL` (`http://10.0.2.2:3000` for `dev`, empty otherwise). App names `[DEV] My App`, `[STG] My App`, `My App`.
- Flavor naming rule (single function `flavor_app_name` in `config.py`): `prod` → `<name>`; any other flavor → `[<FLAVOR UPPERCASE>] <name>`.
- `package-name` writes `android-app-config.json` only when it already exists, and names flavor resources only for declared flavors (existing config file or Gradle `productFlavors`).
- Plugin version after this work: `1.3.0` in `pyproject.toml`, `.claude-plugin/plugin.json`, `.codex-plugin/plugin.json`.
- Starter verification command: `./gradlew assembleDebug testDebugUnitTest lintDebug`.
- `scripts/new-feature.sh` must work under `/bin/bash` (3.2 on macOS).
- Plugin commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. Running `package-name` twice on a project without a config file must stay idempotent and still leave no config file (Task 2, idempotency test).
2. A project whose config file lists `dev`/`stg`/`prod` but whose Gradle has no `productFlavors` must still get all three flavor names updated, because the config declares them (Task 2, existing-config test).
3. Existing users with a two-flavor config file must keep exactly their two flavors after upgrading; the new `stg` default applies only when no config file exists. Today `normalize_config` merges default flavors into a config file, so this needs a fix (Task 1, test `test_existing_config_keeps_its_flavors`).
4. `scripts/new-feature.sh` on a project whose `app/build.gradle.kts` has no `namespace` line must fail with a clear message instead of creating `app/src/main/kotlin/ui/<name>` (Task 5, step 4).
5. A second rename of the finished starter to a different package must build and keep `new-feature.sh` working without edits (Task 7).

---

## Part A: plugin (`/Users/dthuy/Workspace/mobile-app-config`)

Run every command in this part from `/Users/dthuy/Workspace/mobile-app-config`.

### Task 1: Neutral `dev`/`stg`/`prod` defaults and one naming rule

**Files:**
- Modify: `skills/android/scripts/android_mobile_config/config.py` (`DEFAULT_CONFIG` flavors and firebase flavors, new `flavor_app_name`, `infer_config`, `infer_flavors`, `normalize_config`)
- Modify docs: `skills/android/references/config-schema.md`, `README.md`, `skills/android/SKILL.md`, `skills/android-flavors/SKILL.md`, `skills/android-help/SKILL.md`, `skills/android-firebase/SKILL.md`
- Test: `tests/test_config_init.py`, `tests/test_flavors.py`, `tests/test_firebase.py`

**Interfaces:**
- Produces: `flavor_app_name(flavor: str, app_name: str) -> str` in `android_mobile_config.config` (used by Task 2); `DEFAULT_CONFIG["flavors"]` keys `["dev", "stg", "prod"]`.

- [ ] **Step 1: Create the branch**

```bash
git switch -c fix/neutral-defaults-and-claude-marketplace
```

- [ ] **Step 2: Write the failing tests**

Append to `tests/test_config_init.py`:

```python
def test_default_config_uses_neutral_dev_stg_prod() -> None:
    import json

    from android_mobile_config.config import DEFAULT_CONFIG

    text = json.dumps(DEFAULT_CONFIG)
    assert "CAREER_OPS" not in text
    assert "Career Ops" not in text
    flavors = DEFAULT_CONFIG["flavors"]
    assert list(flavors) == ["dev", "stg", "prod"]
    assert flavors["dev"] == {
        "applicationIdSuffix": ".dev",
        "appName": "[DEV] My App",
        "buildConfigFields": {"APP_ENVIRONMENT": "dev", "APP_BASE_URL": "http://10.0.2.2:3000"},
    }
    assert flavors["stg"] == {
        "applicationIdSuffix": ".stg",
        "appName": "[STG] My App",
        "buildConfigFields": {"APP_ENVIRONMENT": "stg", "APP_BASE_URL": ""},
    }
    assert flavors["prod"] == {
        "applicationIdSuffix": "",
        "appName": "My App",
        "buildConfigFields": {"APP_ENVIRONMENT": "prod", "APP_BASE_URL": ""},
    }
    assert list(DEFAULT_CONFIG["firebase"]["flavors"]) == ["dev", "stg", "prod"]


def test_flavor_app_name_rule() -> None:
    from android_mobile_config.config import flavor_app_name

    assert flavor_app_name("dev", "Demo") == "[DEV] Demo"
    assert flavor_app_name("stg", "Demo") == "[STG] Demo"
    assert flavor_app_name("prod", "Demo") == "Demo"
    assert flavor_app_name("staging", "Demo") == "[STAGING] Demo"


def test_init_without_flavors_names_all_default_flavors_from_app_name(tmp_path) -> None:
    project = copy_fixture(tmp_path, "kotlin_no_flavors_app")
    assert run_cli(project, "init").returncode == 0
    flavors = read_config(project)["flavors"]
    assert [flavors[name]["appName"] for name in flavors] == ["[DEV] No Flavors", "[STG] No Flavors", "No Flavors"]


def test_existing_config_keeps_its_flavors(tmp_path) -> None:
    import json

    project = copy_fixture(tmp_path, "kotlin_no_flavors_app")
    (project / "android-app-config.json").write_text(
        json.dumps({"flavors": {"dev": {"applicationIdSuffix": ".dev"}, "prod": {"applicationIdSuffix": ""}}})
    )
    assert run_cli(project, "flavors").returncode == 0
    gradle = (project / "app" / "build.gradle.kts").read_text()
    assert 'create("stg")' not in gradle
    assert not (project / "app" / "src" / "stg").exists()
```

Check the imports at the top of `tests/test_config_init.py`; if `copy_fixture`, `read_config` or `run_cli` is missing, extend the import to `from .helpers import copy_fixture, read_config, run_cli`.

Append to `tests/test_flavors.py`:

```python
def test_flavors_creates_dev_stg_prod(tmp_path) -> None:
    project = copy_fixture(tmp_path, "kotlin_no_flavors_app")
    result = run_cli(project, "flavors")
    assert result.returncode == 0, result.stderr
    text = (project / "app" / "build.gradle.kts").read_text()
    assert text.index('create("dev")') < text.index('create("stg")') < text.index('create("prod")')
    assert 'applicationIdSuffix = ".stg"' in text
    stg_strings = project / "app" / "src" / "stg" / "res" / "values" / "strings.xml"
    assert '<string name="app_name">[STG] No Flavors</string>' in stg_strings.read_text()
```

- [ ] **Step 3: Run them to see them fail**

Run: `python3 -m pytest tests/test_config_init.py tests/test_flavors.py -v`
Expected: four new tests FAIL (`test_flavor_app_name_rule` with ImportError). `test_existing_config_keeps_its_flavors` passes now but is a guard: it would fail as soon as `stg` joins the defaults, because `normalize_config` merges default flavors into the file's list. Step 4 adds `stg` and the `normalize_config` fix together; to see the guard work, add `stg` first, run it (FAIL), then apply the fix.

- [ ] **Step 4: Implement in `config.py`**

Replace `DEFAULT_CONFIG["flavors"]` with:

```python
    "flavors": {
        "dev": {
            "applicationIdSuffix": ".dev",
            "appName": "[DEV] My App",
            "buildConfigFields": {
                "APP_ENVIRONMENT": "dev",
                "APP_BASE_URL": "http://10.0.2.2:3000",
            },
        },
        "stg": {
            "applicationIdSuffix": ".stg",
            "appName": "[STG] My App",
            "buildConfigFields": {
                "APP_ENVIRONMENT": "stg",
                "APP_BASE_URL": "",
            },
        },
        "prod": {
            "applicationIdSuffix": "",
            "appName": "My App",
            "buildConfigFields": {
                "APP_ENVIRONMENT": "prod",
                "APP_BASE_URL": "",
            },
        },
    },
```

Replace `DEFAULT_CONFIG["firebase"]["flavors"]` with:

```python
        "flavors": {
            "dev": {"project": ""},
            "stg": {"project": ""},
            "prod": {"project": ""},
        },
```

Add after `config_path`:

```python
def flavor_app_name(flavor: str, app_name: str) -> str:
    """prod keeps the plain name; every other flavor gets a prefix such as [DEV] or [STG]."""
    return app_name if flavor == "prod" else f"[{flavor.upper()}] {app_name}"
```

In `infer_config`, replace

```python
    else:
        config["flavors"]["prod"]["appName"] = app_name
        config["flavors"]["dev"]["appName"] = f"[DEV] {app_name}"
```

with

```python
    else:
        for name, flavor in config["flavors"].items():
            flavor["appName"] = flavor_app_name(name, app_name)
```

In `normalize_config`, make a config file's flavor list authoritative (defaults still fill in missing fields of the flavors it lists, but never add flavors). Replace the function with:

```python
def normalize_config(raw: dict[str, Any]) -> dict[str, Any]:
    config = deepcopy(DEFAULT_CONFIG)
    deep_update(config, raw)
    if isinstance(raw.get("flavors"), dict):
        # The file decides which flavors exist; defaults only fill fields of those flavors.
        config["flavors"] = {name: config["flavors"][name] for name in raw["flavors"]}
    if not isinstance(config.get("flavors"), dict) or not config["flavors"]:
        raise ConfigError("Config must define at least one flavor")
    return config
```

In `infer_flavors`, delete the line `flavor_app_name = app_name if name == "prod" else f"[{name.upper()}] {app_name}"` and set `"appName": flavor_app_name(name, app_name),` in the dict it builds.

- [ ] **Step 5: Update existing tests for the third flavor**

- `tests/test_flavors.py::test_validate_flavors_checks_gradle_tasks_when_wrapper_exists`: the fake wrapper line becomes
  `gradlew.write_text("#!/bin/sh\nprintf '%s\\n' assembleDevDebug assembleStgDebug assembleProdDebug\n")`.
- `tests/test_firebase.py` fake CLI (`firebase_env`): add a third app to the `apps` list:
  `{"appId": "app-com-example-noflavors-stg", "packageName": "com.example.noflavors.stg"},`.
- `tests/test_firebase.py::test_firebase_flags_write_config_and_single_project_files`: the packages assertion becomes
  `assert packages == ["com.example.noflavors", "com.example.noflavors.dev", "com.example.noflavors.stg"]`.
- `tests/test_firebase.py::test_firebase_per_flavor_writes_flavor_files`: add `"--flavor", "stg=stg-project",` between the `dev` and `prod` mappings, and add
  `assert "com.example.noflavors.stg" in (project / "app" / "src" / "stg" / "google-services.json").read_text()`.
- `tests/test_config_init.py::test_init_force_rewrites_config` keeps passing: `init --force` re-infers from Gradle, not from the old file.

Run any other failing test, read its assertion, and update it only if it encodes the old two-flavor default; otherwise fix the code.

- [ ] **Step 6: Run the full suite**

Run: `python3 -m pytest -q`
Expected: all pass. `test_existing_flavors_are_detected` still asserts `CAREER_OPS_ENVIRONMENT`; that value comes from the fixture's Gradle file, which models a user project, so it stays.

- [ ] **Step 7: Update docs to the new set**

- `skills/android/references/config-schema.md`: replace the `flavors` example with the three flavors exactly as in Step 4 (JSON form), add `"stg": {"project": ""}` to the firebase example, and change the per-flavor command to `mobile-app-config firebase --mode per-flavor --flavor dev=my-dev --flavor stg=my-stg --flavor prod=my-prod`.
- `README.md` line 10: `` - Create and validate `dev`, `stg` and `prod` variants. ``; line 114: `Use $android-flavors to configure dev, stg and prod flavors.`; line 155 and every "expected variants" sentence: `` `devDebug`, `stgDebug`, `prodDebug`, `devRelease`, `stgRelease`, and `prodRelease` ``; every `--flavor dev=my-dev --flavor prod=my-prod` example gains `--flavor stg=my-stg` in the middle.
- Same two replacements (variants sentence, per-flavor example) in `skills/android/SKILL.md`, `skills/android-flavors/SKILL.md`, `skills/android-help/SKILL.md`, `skills/android-firebase/SKILL.md`; in `skills/android/SKILL.md` and `skills/android-flavors/SKILL.md` descriptions, `dev/prod` / `dev prod` becomes `dev/stg/prod` / `dev stg prod`.

Run: `grep -rn "prodRelease\|prod=my-prod\|dev/prod\|dev prod" README.md skills | grep -v stg`
Expected: no output.

Run: `grep -rn "CAREER_OPS\|Career Ops" --exclude-dir=.git --exclude-dir=dist --exclude-dir=.pytest_cache --exclude-dir=__pycache__ .`
Expected: hits only in `tests/fixtures/` and in test lines that use fixture data.

Run: `python3 scripts/validate_skill.py`
Expected: `Skills valid: ...`.

- [ ] **Step 8: Commit**

```bash
git add -A skills tests README.md
git commit -m "feat(config): neutral dev/stg/prod defaults with one naming rule

Replace Career Ops leftovers with APP_ENVIRONMENT/APP_BASE_URL defaults,
add the stg flavor, and name flavors through flavor_app_name.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 2: `package-name` touches only what exists

**Files:**
- Modify: `skills/android/scripts/android_mobile_config/package_name.py` (`configure_package_name`, `update_app_names`, new `declared_flavor_names`)
- Modify: `README.md` (package-name paragraph)
- Test: `tests/test_package_name.py`

**Interfaces:**
- Consumes: `flavor_app_name(flavor, app_name)`, `infer_flavors(text, app_name)`, `config_path(root)` from `android_mobile_config.config`.
- Produces: `declared_flavor_names(root: Path, config: dict[str, Any]) -> set[str]`.

- [ ] **Step 1: Write the new failing tests** (append to `tests/test_package_name.py`)

```python
def test_package_name_without_config_creates_no_config_or_flavor_dirs(tmp_path) -> None:
    project = copy_fixture(tmp_path, "kotlin_no_flavors_app")

    result = run_cli(project, "package-name", "--application-id", "com.acme.demo", "--app-name", "Demo")

    assert result.returncode == 0, result.stderr
    assert not (project / "android-app-config.json").exists()
    for flavor in ("dev", "stg", "prod"):
        assert not (project / "app" / "src" / flavor).exists()
    strings = project / "app" / "src" / "main" / "res" / "values" / "strings.xml"
    assert '<string name="app_name">Demo</string>' in strings.read_text()


def test_package_name_app_name_only_touches_declared_flavors(tmp_path) -> None:
    project = copy_fixture(tmp_path, "kotlin_no_flavors_app")
    gradle = project / "app" / "build.gradle.kts"
    gradle.write_text(
        gradle.read_text().replace(
            "  signingConfigs {",
            '  flavorDimensions += "environment"\n\n'
            "  productFlavors {\n"
            '    create("dev") {\n'
            '      dimension = "environment"\n'
            "    }\n"
            "  }\n\n"
            "  signingConfigs {",
        )
    )

    result = run_cli(project, "package-name", "--application-id", "com.acme.demo", "--app-name", "Demo")

    assert result.returncode == 0, result.stderr
    dev_strings = project / "app" / "src" / "dev" / "res" / "values" / "strings.xml"
    assert '<string name="app_name">[DEV] Demo</string>' in dev_strings.read_text()
    assert not (project / "app" / "src" / "stg").exists()
    assert not (project / "app" / "src" / "prod").exists()
    assert not (project / "android-app-config.json").exists()


def test_package_name_updates_flavors_declared_only_in_existing_config(tmp_path) -> None:
    project = copy_fixture(tmp_path, "kotlin_no_flavors_app")
    assert run_cli(project, "init").returncode == 0

    result = run_cli(project, "package-name", "--application-id", "com.acme.demo", "--app-name", "Demo")

    assert result.returncode == 0, result.stderr
    config = read_config(project)
    assert config["packageName"]["applicationId"] == "com.acme.demo"
    assert [config["flavors"][name]["appName"] for name in ("dev", "stg", "prod")] == ["[DEV] Demo", "[STG] Demo", "Demo"]
    stg_strings = project / "app" / "src" / "stg" / "res" / "values" / "strings.xml"
    assert '<string name="app_name">[STG] Demo</string>' in stg_strings.read_text()
```

- [ ] **Step 2: Run them to see them fail**

Run: `python3 -m pytest tests/test_package_name.py -k "creates_no_config or only_touches_declared or declared_only_in_existing" -v`
Expected: all three FAIL (config file and undeclared flavor dirs get created; `stg` is not renamed).

- [ ] **Step 3: Update existing tests that relied on the old behaviour**

In `tests/test_package_name.py`:

1. `test_package_name_updates_gradle_and_sources`: replace the last line
   `assert read_config(project)["packageName"]["applicationId"] == "com.aistudio.taskarena.kymzap"`
   with `assert not (project / "android-app-config.json").exists()`.
2. `test_package_name_app_name_updates_main_and_flavor_resources`: insert right after the `main_strings.write_text(...)` call and before `result = run_cli(...)`:

   ```python
       assert run_cli(project, "init").returncode == 0
   ```

   and replace `assert config["flavors"]["staging"]["appName"] == "[STAGING] Career Ops"` with

   ```python
       assert config["flavors"]["staging"]["appName"] == "[STAGING] My App"
       assert "<string name=\"app_name\">[STAGING] My App</string>" in (
           project / "app" / "src" / "staging" / "res" / "values" / "strings.xml"
       ).read_text()
   ```

3. `test_package_name_is_idempotent_and_updates_firebase_packages`: replace the lines from `first = ...` up to (not including) `firebase = ...` with

   ```python
       first = run_cli(project, "package-name", "--application-id", "com.aistudio.taskarena.kymzap")
       assert first.returncode == 0, first.stderr
       gradle = project / "app" / "build.gradle.kts"
       before = gradle.read_text()
       second = run_cli(project, "package-name", "--application-id", "com.aistudio.taskarena.kymzap")
       assert second.returncode == 0, second.stderr
       assert gradle.read_text() == before
       assert not (project / "android-app-config.json").exists()
   ```

4. `test_package_name_updates_divergent_identities`: replace
   `assert read_config(project)["packageName"]["applicationId"] == "me.dthuy.careerops"`
   with `assert not (project / "android-app-config.json").exists()`, and change the final idempotency block to

   ```python
       before = (gradle.read_text(), moved_activity.read_text(), moved_user.read_text())
       second = run_cli(project, "package-name", "--application-id", "me.dthuy.careerops")

       assert second.returncode == 0, second.stderr
       assert (gradle.read_text(), moved_activity.read_text(), moved_user.read_text()) == before
   ```

Run: `grep -n "read_config" tests/test_package_name.py` and check every remaining call follows an `init` or a test-written config file.

- [ ] **Step 4: Implement in `package_name.py`**

Extend the import:

```python
from .config import ConfigError, config_path, flavor_app_name, infer_flavors, write_config
```

At the end of `configure_package_name`, replace

```python
    config.setdefault("packageName", {})["applicationId"] = target
    write_config(config_path(root), config)
```

with

```python
    config.setdefault("packageName", {})["applicationId"] = target
    path = config_path(root)
    if path.exists():
        write_config(path, config)
```

Replace `update_app_names` with:

```python
def update_app_names(root: Path, config: dict[str, Any], app_name: str) -> None:
    module = config["module"]
    update_or_create_app_name_resource(root / module / "src" / "main" / "res" / "values" / "strings.xml", app_name)
    flavors = config.get("flavors", {})
    for flavor in sorted(declared_flavor_names(root, config)):
        name = flavor_app_name(flavor, app_name)
        if flavor in flavors:
            flavors[flavor]["appName"] = name
        update_or_create_app_name_resource(root / module / "src" / flavor / "res" / "values" / "strings.xml", name)


def declared_flavor_names(root: Path, config: dict[str, Any]) -> set[str]:
    """Flavors the project actually has: listed in an existing config file or in Gradle productFlavors."""
    gradle_text = (root / config["module"] / "build.gradle.kts").read_text()
    names = set(infer_flavors(gradle_text, ""))
    if config_path(root).exists():
        names |= set(config.get("flavors", {}))
    return names
```

- [ ] **Step 5: Update the README paragraph**

In `README.md` under "Package name sync", replace
``Optional `--app-name` updates display-name resources for `main`, `dev`, and `prod`;``
with
``Optional `--app-name` updates display-name resources for `main` and for every flavor the project declares (`[DEV] Name`, `[STG] Name`, `Name` for `prod`), without creating resources for undeclared flavors; `android-app-config.json` is updated only if it already exists;``

- [ ] **Step 6: Run the full suite**

Run: `python3 -m pytest -q`
Expected: all pass.

- [ ] **Step 7: Commit**

```bash
git add skills/android/scripts/android_mobile_config/package_name.py tests/test_package_name.py README.md
git commit -m "fix(package-name): only touch config and flavors that exist

package-name no longer creates android-app-config.json or resources for
undeclared flavors, and names every declared flavor with flavor_app_name.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 3: Claude Code marketplace, version 1.3.0

**Files:**
- Create: `.claude-plugin/marketplace.json`
- Modify: `.claude-plugin/plugin.json`, `.codex-plugin/plugin.json`, `pyproject.toml` (version), `README.md` ("Claude Code Plugin Manual" section), `CHANGELOG.md`
- Test: `tests/test_skill_structure.py`

**Interfaces:**
- Produces: marketplace `mobile-app-config` with plugin entry `mobile-app-config`; install id `mobile-app-config@mobile-app-config` (used by Task 6).

- [ ] **Step 1: Write the failing tests** (append to `tests/test_skill_structure.py`)

```python
def test_claude_marketplace_lists_the_plugin() -> None:
    import json

    marketplace = json.loads((ROOT / ".claude-plugin" / "marketplace.json").read_text())
    plugin = json.loads((ROOT / ".claude-plugin" / "plugin.json").read_text())
    assert marketplace["name"] == "mobile-app-config"
    assert marketplace["owner"]["name"]
    entries = {entry["name"]: entry for entry in marketplace["plugins"]}
    assert plugin["name"] in entries
    assert entries[plugin["name"]]["source"] == "./"


def test_versions_match_across_manifests() -> None:
    import json
    import re

    claude = json.loads((ROOT / ".claude-plugin" / "plugin.json").read_text())["version"]
    codex = json.loads((ROOT / ".codex-plugin" / "plugin.json").read_text())["version"]
    pyproject = re.search(r'^version = "([^"]+)"', (ROOT / "pyproject.toml").read_text(), re.M).group(1)
    assert claude == codex == pyproject == "1.3.0"
```

- [ ] **Step 2: Run them to see them fail**

Run: `python3 -m pytest tests/test_skill_structure.py -k "marketplace or versions_match" -v`
Expected: FAIL (`marketplace.json` missing; versions are `1.2.0`/`1.2.0`/`1.2.1`).

- [ ] **Step 3: Create `.claude-plugin/marketplace.json`**

```json
{
  "name": "mobile-app-config",
  "description": "Deterministic mobile app configuration for coding agents",
  "owner": {
    "name": "dthuy62"
  },
  "plugins": [
    {
      "name": "mobile-app-config",
      "source": "./",
      "description": "Android flavors, package names, Firebase, assets, network security, design tokens"
    }
  ]
}
```

- [ ] **Step 4: Bump versions to `1.3.0`**

Set `"version": "1.3.0"` in `.claude-plugin/plugin.json` and `.codex-plugin/plugin.json`, and `version = "1.3.0"` in `pyproject.toml`.

- [ ] **Step 5: Validate with Claude Code**

Run: `claude plugin validate .`
Expected: last line `✔ Validation passed`.
If it rejects `"source": "./"`, change the entry to `"source": {"source": "github", "repo": "dthuy62/mobile-app-config"}`, change the test's last assertion to `assert entries[plugin["name"]]["source"]["repo"] == "dthuy62/mobile-app-config"`, and re-run.

- [ ] **Step 6: Update README and CHANGELOG**

In `README.md`, replace the whole `### Claude Code Plugin Manual` section (heading through its closing code block that shows `.claude-plugin/plugin.json`) with:

````markdown
### Claude Code

Add the marketplace and install the plugin:

```bash
claude plugin marketplace add dthuy62/mobile-app-config
claude plugin install mobile-app-config@mobile-app-config
```

Skills are namespaced by the plugin, for example `/mobile-app-config:android` or
`/mobile-app-config:android-flavors`.

A project can ask everyone who opens it to install the plugin by checking in `.claude/settings.json`:

```json
{
  "extraKnownMarketplaces": {
    "mobile-app-config": { "source": { "source": "github", "repo": "dthuy62/mobile-app-config" } }
  },
  "enabledPlugins": { "mobile-app-config@mobile-app-config": true }
}
```

For local plugin development, load the checkout directly:

```bash
claude --plugin-dir ./mobile-app-config
```
````

Prepend to `CHANGELOG.md` under `# Changelog`:

```markdown
## 1.3.0

- Default flavor set is now `dev`, `stg`, `prod` (suffixes `.dev`, `.stg`, none). Projects without a config file get three flavors from `flavors`; `firebase --mode per-flavor` needs a `--flavor stg=...` mapping. Existing `android-app-config.json` files keep their own flavors.
- Replaced project-specific defaults (`Career Ops`, `CAREER_OPS_*`) with neutral ones (`My App`, `APP_ENVIRONMENT`, `APP_BASE_URL`).
- One flavor naming rule everywhere: `prod` keeps the app name; other flavors get `[DEV]`, `[STG]`, `[<FLAVOR>]` prefixes.
- `package-name` no longer creates `android-app-config.json`; it updates the file only when it already exists. Run `init` first to keep a config file.
- `package-name --app-name` renames every declared flavor and no longer creates resources for undeclared flavors.
- Added `.claude-plugin/marketplace.json` so Claude Code can install the plugin with `claude plugin marketplace add dthuy62/mobile-app-config`.
```

- [ ] **Step 7: Run the full suite and the skill validator**

Run: `python3 -m pytest -q && python3 scripts/validate_skill.py`
Expected: all tests pass; validator prints `Skills valid: ...`.

- [ ] **Step 8: Commit**

```bash
git add .claude-plugin/marketplace.json .claude-plugin/plugin.json .codex-plugin/plugin.json pyproject.toml README.md CHANGELOG.md tests/test_skill_structure.py
git commit -m "feat(plugin): add Claude Code marketplace and release 1.3.0

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

## Part B: starter (`/Users/dthuy/Workspace/android-base-2026`)

Run every command in this part from `/Users/dthuy/Workspace/android-base-2026`. Working tree only: do not run `git add`, `git commit` or `git switch`. Paths use `com/example/base` until Task 4 step 5 moves them.

### Task 4: Identity-neutral names and template identity

**Files:**
- Rename: `app/src/main/kotlin/com/example/base/BaseApplication.kt` → `MainApplication.kt`
- Modify: `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/themes.xml`, `app/src/main/kotlin/com/example/base/ui/theme/Theme.kt`, `app/src/main/kotlin/com/example/base/MainActivity.kt`
- Delete: `app/src/main/kotlin/com\/` (stray tree)
- Then moved by the plugin: everything under `app/src/{main,test}/kotlin/com/example/base/` → `com/example/agenticstarter/`; plus `app/build.gradle.kts`, `settings.gradle.kts`, `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: plugin from Part A, run from the local checkout.
- Produces: package `com.example.agenticstarter`, classes `MainApplication`, `AppTheme`, style `Theme.App` (used by Tasks 5–7).

- [ ] **Step 1: Rename the application class**

```bash
mv app/src/main/kotlin/com/example/base/BaseApplication.kt app/src/main/kotlin/com/example/base/MainApplication.kt
sed -i '' 's/class BaseApplication : Application()/class MainApplication : Application()/' app/src/main/kotlin/com/example/base/MainApplication.kt
sed -i '' 's/android:name=".BaseApplication"/android:name=".MainApplication"/' app/src/main/AndroidManifest.xml
```

- [ ] **Step 2: Rename the Compose theme and the XML theme**

```bash
sed -i '' 's/^fun BaseTheme(/fun AppTheme(/' app/src/main/kotlin/com/example/base/ui/theme/Theme.kt
sed -i '' -e 's/import com.example.base.ui.theme.BaseTheme/import com.example.base.ui.theme.AppTheme/' -e 's/BaseTheme(darkTheme = dark)/AppTheme(darkTheme = dark)/' app/src/main/kotlin/com/example/base/MainActivity.kt
sed -i '' 's/name="Theme.Base"/name="Theme.App"/' app/src/main/res/values/themes.xml
sed -i '' 's/@style\/Theme.Base"/@style\/Theme.App"/' app/src/main/AndroidManifest.xml
```

Run: `grep -rnE 'BaseApplication|BaseTheme|Theme\.Base' app/src`
Expected: no output.

- [ ] **Step 3: Verify the build before moving packages**

Run: `./gradlew assembleDebug testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Remove the stray feature tree**

The plugin rewrites package lines in every Kotlin file, including the stray tree; delete it first so it is not carried along.

```bash
rm -rf "app/src/main/kotlin/com\\"
```

Run: `find app/src -path '*\\*'`
Expected: no output.

- [ ] **Step 5: Apply the template identity with the plugin**

```bash
PYTHONPATH=/Users/dthuy/Workspace/mobile-app-config/skills/android/scripts \
  python3 -m android_mobile_config.cli package-name \
  --application-id com.example.agenticstarter \
  --app-name "Android Agentic Starter" \
  --root-project-name "android-agentic-starter"
```

Expected output: `package name configured: com.example.base -> com.example.agenticstarter`.

Run: `ls android-app-config.json app/src/dev app/src/stg app/src/prod 2>&1 | grep -c "No such file"`
Expected: `4`. If any exists, Part A was not applied; stop and fix the plugin instead of deleting by hand.

- [ ] **Step 6: Verify**

Run: `./gradlew assembleDebug testDebugUnitTest lintDebug`
Expected: `BUILD SUCCESSFUL`.

Run: `grep -rnE 'com\.example\.base' app settings.gradle.kts`
Expected: no output.

### Task 5: `scripts/new-feature.sh` reads the package from Gradle

**Files:**
- Modify: `scripts/new-feature.sh:6-8`

**Interfaces:**
- Consumes: `namespace = "<pkg>"` line in `app/build.gradle.kts`.
- Produces: `ui/<name>/` under the namespace path, package `<pkg>.ui.<name>`.

- [ ] **Step 1: Show the bug on the current script**

Run: `/bin/bash scripts/new-feature.sh probe; find app/src/main/kotlin -path '*\\*' | head -3`
Expected: files appear under a `com\` path (bash 3.2 keeps the backslash) and with package `com.example.base`.
Clean up: `rm -rf "app/src/main/kotlin/com\\"`.

- [ ] **Step 2: Replace the package/path lines**

Replace

```bash
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BASE_PKG="com.example.base"
SRC="$ROOT/app/src/main/kotlin/${BASE_PKG//./\/}"
```

with

```bash
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
GRADLE="$ROOT/app/build.gradle.kts"
BASE_PKG="$(sed -nE 's/^[[:space:]]*namespace[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/p' "$GRADLE" | head -n 1)"
[[ -n "$BASE_PKG" ]] || { echo "namespace not found in app/build.gradle.kts"; exit 1; }
SRC="$ROOT/app/src/main/kotlin/${BASE_PKG//.//}"
```

- [ ] **Step 3: Probe with bash 3.2 and compile**

```bash
/bin/bash scripts/new-feature.sh probe
head -1 app/src/main/kotlin/com/example/agenticstarter/ui/probe/ProbeRoute.kt
./gradlew assembleDebug
```

Expected: `package com.example.agenticstarter.ui.probe`, then `BUILD SUCCESSFUL`.
Clean up: `rm -rf app/src/main/kotlin/com/example/agenticstarter/ui/probe`.

- [ ] **Step 4: Probe the missing-namespace error on a scratch copy**

```bash
SCRATCH="$(mktemp -d)"
mkdir -p "$SCRATCH/scripts" "$SCRATCH/app"
cp scripts/new-feature.sh "$SCRATCH/scripts/"
printf 'android {\n}\n' > "$SCRATCH/app/build.gradle.kts"
/bin/bash "$SCRATCH/scripts/new-feature.sh" probe; echo "exit $?"
ls "$SCRATCH/app"
rm -rf "$SCRATCH"
```

Expected: `namespace not found in app/build.gradle.kts`, `exit 1`, and `ls` shows only `build.gradle.kts`.

### Task 6: Plugin wiring and docs

**Files:**
- Create: `.claude/settings.json`
- Modify: `.gitignore`, `README.md` (title, Layout, First 10 minutes), `AGENTS.md` (structure block, one rule)

**Interfaces:**
- Consumes: install id `mobile-app-config@mobile-app-config` from Task 3.

- [ ] **Step 1: Create `.claude/settings.json`**

```json
{
  "extraKnownMarketplaces": {
    "mobile-app-config": {
      "source": { "source": "github", "repo": "dthuy62/mobile-app-config" }
    }
  },
  "enabledPlugins": {
    "mobile-app-config@mobile-app-config": true
  }
}
```

Append to `.gitignore` so personal overrides and Kotlin caches never get committed later:

```gitignore
.kotlin/
.claude/settings.local.json
```

- [ ] **Step 2: Validate the settings file and that it would be tracked**

Run: `python3 -m json.tool .claude/settings.json > /dev/null && echo ok; git check-ignore .claude/settings.json || echo tracked`
Expected: `ok`, then `tracked`.

- [ ] **Step 3: Update `README.md`**

- Line 1: `# Android Base 2026` → `# Android Agentic Starter`.
- In the Layout block, `app/src/main/kotlin/com/example/base` → `app/src/main/kotlin/<your/package>`.
- Replace step 1 of "First 10 minutes" with:

```markdown
1. Give the app its identity with the [mobile-app-config](https://github.com/dthuy62/mobile-app-config) plugin.
   Claude Code offers to install it when you open this folder (see `.claude/settings.json`); then run
   `/mobile-app-config:android` and ask for your package and app name. From a shell:
   ```bash
   python3 -m pip install git+https://github.com/dthuy62/mobile-app-config.git
   mobile-app-config package-name --application-id com.acme.app --app-name "Acme" --root-project-name "acme-android"
   ```
   Codex: `codex plugin marketplace add dthuy62/mobile-app-config`. Flavors (`dev`, `stg`, `prod`) are optional:
   `mobile-app-config flavors`.
```

- [ ] **Step 4: Update `AGENTS.md`**

- In the Structure block, replace the first two lines

```
com.example.base
├── BaseApplication.kt, MainActivity.kt, MainViewModel.kt
```

with

```
<package root>          (namespace in app/build.gradle.kts)
├── MainApplication.kt, MainActivity.kt, MainViewModel.kt
```

- Append to "Dependency rules (never violate)":

```markdown
- Identity changes (package, applicationId, app name) go through the `mobile-app-config` plugin, never by hand.
```

### Task 7: Acceptance check (no code changes)

**Files:** none modified in the repo; uses a scratch copy.

- [ ] **Step 1: Full verification**

Run: `./gradlew assembleDebug testDebugUnitTest lintDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: No old identity left**

Run: `grep -rnE 'com\.example\.base|BaseApplication|BaseTheme|Theme\.Base|Android Base' . --exclude-dir=build --exclude-dir=.gradle --exclude-dir=.git --exclude-dir=.kotlin --exclude-dir=.idea --exclude-dir=superpowers`
Expected: no output.

Run: `ls android-app-config.json app/src/dev app/src/stg app/src/prod 2>&1 | grep -c "No such file"; find app/src -path '*\\*' | wc -l`
Expected: `4`, then `0`.

- [ ] **Step 3: Rename-proof check on a scratch copy**

```bash
SCRATCH="$(mktemp -d)/starter"
rsync -a --exclude build --exclude .gradle --exclude .kotlin --exclude .idea ./ "$SCRATCH/"
cd "$SCRATCH"
PYTHONPATH=/Users/dthuy/Workspace/mobile-app-config/skills/android/scripts \
  python3 -m android_mobile_config.cli package-name --application-id com.acme.demo
/bin/bash scripts/new-feature.sh probe
head -1 app/src/main/kotlin/com/acme/demo/ui/probe/ProbeRoute.kt
./gradlew assembleDebug testDebugUnitTest
cd - && rm -rf "$(dirname "$SCRATCH")"
```

Expected: `package com.acme.demo.ui.probe`, then `BUILD SUCCESSFUL`.

- [ ] **Step 4: Report**

Summarize: plugin commits on `fix/neutral-defaults-and-claude-marketplace`, the starter's uncommitted working-tree changes (`git status --short`), verification output, and the owner-only steps that remain: push the plugin branch and release 1.3.0, commit the starter when ready, rename the local folder and GitHub repository to `android-agentic-starter`.
