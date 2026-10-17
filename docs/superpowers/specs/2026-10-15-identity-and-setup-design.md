# Sub-project 1: Identity and setup

Date: 2026-10-15
Status: draft, waiting for review
Parent effort: turn `android-base-2026` into `android-agentic-starter`, an open-source Android starter built for
AI-assisted development, modelled on the AI layer of `tsnAnh/flutter-agentic-starter` ("direction B: guardrails first").

## 1. Goal

A person who clones the starter can give it their own identity (app name, package, applicationId, project name)
with one command that an agent can also run, and nothing in the source still says "base" afterwards.

Identity changes are delegated to the existing `mobile-app-config` plugin (`dthuy62/mobile-app-config`). The starter
does not ship its own rename script.

## 2. Background

- The Flutter starter ships a Dart setup wizard (`dart run project_setup`) that renames the app and installs agent
  hooks. Its Android equivalent already exists as `mobile-app-config package-name`.
- A trial run of `mobile-app-config package-name --application-id com.example.agenticstarter --app-name ...
  --root-project-name ...` on a copy of this repo moved main and test sources, rewrote `package`/`import` lines,
  updated `namespace`, `applicationId`, `app_name` and `rootProject.name`. `assembleDebug` and `testDebugUnitTest`
  passed afterwards.
- The same trial exposed three plugin defects (section 4) and four identity leftovers in the starter that the plugin
  cannot know about: `BaseApplication`, `BaseTheme`, `Theme.Base`, and `BASE_PKG` hard-coded in
  `scripts/new-feature.sh`.
- `scripts/new-feature.sh` builds its path with `${BASE_PKG//./\/}`. On macOS bash 3.2 that keeps the backslash and
  produced a stray `app/src/main/kotlin/com\/example\/base/ui/demo/` tree, which is not wired into `NavGraph`.

## 3. Starter changes (this repo)

### 3.1 Identity-neutral class and theme names

Names that do not carry the project name never need renaming again.

| Before | After | Files |
|---|---|---|
| `BaseApplication` | `MainApplication` | class file (renamed), `AndroidManifest.xml` |
| `BaseTheme` | `AppTheme` | `ui/theme/Theme.kt`, `MainActivity.kt` |
| `Theme.Base` | `Theme.App` | `res/values/themes.xml`, `AndroidManifest.xml` |

### 3.2 Template identity

Applied by running the plugin itself (dogfooding), after 3.1:

| Field | Value |
|---|---|
| applicationId / namespace / source package | `com.example.agenticstarter` |
| `app_name` | `Android Agentic Starter` |
| `rootProject.name` | `android-agentic-starter` |

The run must not leave `android-app-config.json`, `app/src/dev/` or `app/src/prod/` behind. If the plugin fix in
4.2 is not merged yet, delete those three outputs by hand after the run.

### 3.3 `scripts/new-feature.sh`

- Read the package from `namespace = "..."` in `app/build.gradle.kts`; fail with a clear message when it is missing.
  No hard-coded package.
- Build the path with `${PKG//.//}`, which behaves the same on bash 3.2 and 5.x.
- Delete the stray `app/src/main/kotlin/com\/example\/base/` tree.

### 3.4 Plugin wiring for agents

- New `.claude/settings.json` (checked in):

  ```json
  {
    "extraKnownMarketplaces": {
      "mobile-app-config": { "source": { "source": "github", "repo": "dthuy62/mobile-app-config" } }
    },
    "enabledPlugins": { "mobile-app-config@mobile-app-config": true }
  }
  ```

  Claude Code then offers to install the plugin when someone trusts the cloned folder. Key formats are checked
  against the current Claude Code settings reference during implementation.
- `.gitignore` must not ignore `.claude/settings.json`; `.claude/settings.local.json` stays ignored.
- README states the Codex path (`codex plugin marketplace add dthuy62/mobile-app-config`) and the shell path
  (`python3 -m pip install git+https://github.com/dthuy62/mobile-app-config.git`, then
  `mobile-app-config package-name ...`).

### 3.5 Docs

- `README.md`: title `Android Agentic Starter`; layout tree uses `app/src/main/kotlin/<your/package>/`; "First 10
  minutes" step 1 becomes "Give the app its identity" with the shell command
  (`mobile-app-config package-name --application-id ... --app-name ... --root-project-name ...`) and the Claude Code
  skill (`/mobile-app-config:android`).
- `AGENTS.md`: structure block starts at `<package root>` instead of `com.example.base`, and lists
  `MainApplication.kt`. Add one line: "Identity changes (package, applicationId, app name) go through the
  `mobile-app-config` plugin, never by hand."
- `docs/ARCHITECTURE.md`: no identity references today; only touched if a reference appears.

### 3.6 Not in this sub-project

- A `setup-starter` skill (interactive wizard that asks for name, package, flavors, Firebase and calls the plugin):
  sub-project 2.
- Flavors, Firebase, assets, network security for the starter itself: stay optional, documented only.
- Renaming the local folder `android-base-2026` and the GitHub repository: done by the owner outside the session.

## 4. Plugin changes (`/Users/dthuy/Workspace/mobile-app-config`)

Work happens on a local branch `fix/neutral-defaults-and-claude-marketplace`. Pushing, tagging and releasing are
left to the owner.

### 4.1 Neutral defaults and the `dev` / `stg` / `prod` set

Flavor names are abbreviated consistently: the default set is `dev`, `stg`, `prod` (in that order).

- `config.py` `DEFAULT_CONFIG.flavors`:

  | Flavor | `applicationIdSuffix` | `appName` | `APP_ENVIRONMENT` | `APP_BASE_URL` |
  |---|---|---|---|---|
  | `dev` | `.dev` | `[DEV] My App` | `dev` | `http://10.0.2.2:3000` |
  | `stg` | `.stg` | `[STG] My App` | `stg` | `` |
  | `prod` | `` | `My App` | `prod` | `` |

  `CAREER_OPS_ENVIRONMENT` / `CAREER_OPS_DEFAULT_BASE_URL` and "Career Ops" disappear.
- `DEFAULT_CONFIG.firebase.flavors` gains `"stg": {"project": ""}`.
- An existing `android-app-config.json` decides which flavors exist: `normalize_config` keeps filling missing fields
  from defaults but stops adding default flavors the file does not list. Without this, every existing two-flavor
  config would silently gain `stg`.
- One naming rule, in one function `flavor_app_name(flavor, app_name)` in `config.py`: `prod` → `app_name`, any other
  flavor → `[<FLAVOR UPPERCASE>] app_name` (so `dev` → `[DEV] X`, `stg` → `[STG] X`). `infer_flavors`,
  `infer_config` and `package-name` all use it.
- Consequence: `mobile-app-config flavors` on a project without flavors now creates three flavors, and the expected
  variants become `devDebug`, `stgDebug`, `prodDebug` (plus release). `firebase --mode per-flavor` needs a
  `--flavor stg=...` mapping too.
- Docs updated to the new set: `skills/android/references/config-schema.md` (defaults and the per-flavor Firebase
  example), `README.md`, `skills/android/SKILL.md`, `skills/android-flavors/SKILL.md`,
  `skills/android-help/SKILL.md`, `skills/android-firebase/SKILL.md`.
- Grep the repo (excluding `dist/`, `.git/`, caches) for `CAREER_OPS` and `Career Ops`; remaining hits are allowed
  only in test fixtures that model an existing user project.

### 4.2 `package-name` touches only what exists

- Writes `android-app-config.json` only when the file already exists. Without it, the command stays a pure
  in-place edit, matching the README promise that the config file is created only by `init` or by explicit
  persistence.
- With `--app-name`, updates flavor `app_name` resources for every declared flavor, meaning listed in an
  existing `android-app-config.json` or found in `productFlavors` of the module's `build.gradle.kts` (via the
  existing `infer_flavors`), using `flavor_app_name`. It never creates `src/<flavor>/` for an undeclared flavor.
  Today only `dev` and `prod` are renamed; after the change a declared `stg` (or a user's own `staging`) is renamed
  too, by the same rule.
- Existing tests that read `android-app-config.json` after a `package-name` run on a project without one are
  updated: they either run `init` first (when they check config contents) or assert the file is absent.

### 4.3 Claude Code marketplace

- Add `.claude-plugin/marketplace.json`:

  ```json
  {
    "name": "mobile-app-config",
    "description": "Deterministic mobile app configuration for coding agents",
    "owner": { "name": "dthuy62" },
    "plugins": [
      { "name": "mobile-app-config", "source": "./", "description": "Android flavors, package names, Firebase, assets, network security, design tokens" }
    ]
  }
  ```

- `claude plugin validate .` must pass. If a root-level `"./"` source is rejected, switch to a `github` source for
  the same repo.
- README "Claude Code" section: replace the manual-only path with
  `claude plugin marketplace add dthuy62/mobile-app-config` and `claude plugin install mobile-app-config@mobile-app-config`,
  keep `--plugin-dir` as the local-development path.
- Bump `version` to `1.3.0` (default flavor set changed) in `pyproject.toml`, `.claude-plugin/plugin.json`, `.codex-plugin/plugin.json`; add a
  `CHANGELOG.md` entry.

### 4.4 Plugin tests (pytest)

- `package-name` on a project without config and without flavors: no `android-app-config.json`, no `src/dev`,
  no `src/prod`; main `app_name` updated.
- `package-name --app-name` on a project whose `productFlavors` declares only `create("dev")` and has no config:
  `src/dev/res/values/strings.xml` gets `[DEV] <name>`; `src/stg/` and `src/prod/` are not created.
- `package-name --app-name "My App"` on the existing-flavors fixture (`dev`, `prod`, `staging`) renames all three:
  `[DEV] My App`, `My App`, `[STAGING] My App`.
- `DEFAULT_CONFIG` has flavors `dev`, `stg`, `prod` in that order, with the values in 4.1.
- `flavors` on a project without flavors creates `create("stg")` with `applicationIdSuffix = ".stg"` and
  `src/stg/res/values/strings.xml` containing `[STG] <app name>`.
- `package-name` with an existing config file: config is updated as today.
- `DEFAULT_CONFIG` contains neither `CAREER_OPS` nor `Career Ops`.
- `test_skill_structure.py` (or a new test) asserts `.claude-plugin/marketplace.json` exists, parses, and its plugin
  entry name equals `plugin.json` `name`.

## 5. Order of work

The starter is changed in its working tree only: no commits, no branch. Commits happen only in the plugin repo.


1. Plugin: 4.1, 4.2, 4.4, then 4.3. Run `python3 -m pytest`.
2. Starter: 3.1 (rename classes and theme), verify build.
3. Starter: 3.2 using the fixed plugin from the local checkout (`PYTHONPATH=.../skills/android/scripts`).
4. Starter: 3.3, 3.4, 3.5.

## 6. Acceptance criteria

Starter:
- `./gradlew assembleDebug testDebugUnitTest lintDebug` passes.
- `grep -rnE 'com\.example\.base|BaseApplication|BaseTheme|Theme\.Base|Android Base'` over the repo (excluding
  `build/`, `.gradle/`, `.git/` and `docs/superpowers/`) returns nothing.
- No `android-app-config.json`, `app/src/dev/`, `app/src/prod/`, or `com\` directory exists.
- `scripts/new-feature.sh probe` on macOS bash 3.2 creates `app/src/main/kotlin/com/example/agenticstarter/ui/probe/`
  with package `com.example.agenticstarter.ui.probe`, and the project compiles with it; the probe is then deleted.
- After a second `mobile-app-config package-name --application-id com.acme.demo` on a scratch copy, the build still
  passes and `scripts/new-feature.sh` uses `com.acme.demo` without edits (proves the template is rename-proof).

Plugin:
- `python3 -m pytest` passes, including the new tests.
- `claude plugin validate .` passes.

## 7. Risks

- `extraKnownMarketplaces` prompts install only after the user trusts the folder; agents in other tools (Codex,
  Cursor) rely on the README instructions. Accepted.
- The starter depends on a plugin in another repository; if the plugin is unavailable, the README shell path
  (`pip install` + CLI) still works, and a manual rename remains possible.
- Changing when `package-name` writes the config file is a behaviour change for existing plugin users who relied on
  the side effect. Called out in `CHANGELOG.md`.
- The default flavor set grows from `dev`/`prod` to `dev`/`stg`/`prod`, so existing users re-running `flavors` or
  `firebase --mode per-flavor` without a config file get a third flavor or a missing-mapping error. Called out in
  `CHANGELOG.md`; the minor version bump to 1.3.0 signals it.
