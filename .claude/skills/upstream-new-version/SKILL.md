---
name: upstream-new-version
description: Sync the shiroikuma-ongakuots fork (白い熊 音楽乙) onto new upstream work from maxrave-dev/SimpMusic — present 白い熊 a proceed-gated, tabular, descriptive summary of what the new upstream version introduces, then (only on an explicit go-ahead) fast-forward `master` from upstream/dev, rebase the `custom` patch stack onto it, merge the changelog, and build the new +1. Use when 白い熊 runs /upstream-new-version, says a new SimpMusic version is out, or asks to check for a new SimpMusic version, update/sync to upstream, bump to the latest SimpMusic, or rebase custom onto the latest upstream.
---

# Sync shiroikuma-ongakuots onto new upstream SimpMusic work

This fork tracks [maxrave-dev/SimpMusic](https://github.com/maxrave-dev/SimpMusic). `master` mirrors
`upstream/dev` (fast-forward only); `custom` carries our patches and is rebased onto it. This is the
**orchestration layer** — every concrete fact (identity, keystore, version scheme, the build
pipeline) lives in the **`build-apk`** skill. Read that first.

> **Never `git push` or `git commit` unprompted, and never `adb install`.** The whole rebase and
> build happen on the **local** tree as a scratchpad; a rebase is freely re-runnable
> (`git rebase --abort`) right up until 白い熊 says **"Push"**.

## Branch / remote model

| Branch | Role | Update mode |
| --- | --- | --- |
| `master` | Mirrors `upstream/dev`. No fork work here. | fast-forward only |
| `custom` | Our patches; the working branch and the GitHub default branch. | rebased onto `master` each sync |

`origin` = `git@github.com:ShiroiKuma0/shiroikuma-ongakuots` (push). `upstream` =
`https://github.com/maxrave-dev/SimpMusic` (fetch only, push URL `DISABLED`).

**Upstream tracking: `git`** — the bleeding branch tip (`dev`), not the `vX.Y.Z` release tags on
`main` (白い熊, 2026-08-16). We sync whenever commits land on `upstream/dev`. Two consequences run
through this whole procedure: the version carries an upstream-base **pin** (global `git-versioning`
skill), and `BUILD_NUMBER` **never resets**.

## Step 0 — Preconditions

- cwd is `~/git/shiroikuma-ongakuots`; working tree clean (`git status --short` empty —
  `keystore.properties` is gitignored, so it never shows). Currently on `custom`.
- Run git / `gh` / `gradlew` **unsandboxed** (`dangerouslyDisableSandbox: true`).

## Step 1 — Fetch and find out what actually changed

```bash
git fetch upstream --tags
git fetch origin
git log --oneline master..upstream/dev
git diff --stat master..upstream/dev
```

Upstream keeps **no** `CHANGELOG.md`. Its release notes are one file per versionCode under
`fastlane/metadata/android/en-US/changelogs/`, plus the GitHub release bodies:

```bash
git diff master..upstream/dev -- fastlane/metadata/android/en-US/changelogs/
git show upstream/dev:gradle/libs.versions.toml | grep -E 'version-name|version-code'
gh release list -R maxrave-dev/SimpMusic -L 5
```

And — before anything moves — find out which of **our** files upstream touched. This is the
conflict set, and it is the most useful thing to have in hand at the gate below:

```bash
git diff --name-only master..upstream/dev > /tmp/upstream-touched.txt
git diff --name-only master..custom | grep -Fxf /tmp/upstream-touched.txt
```

### The `core` submodule is a SECOND fork — sync it the same way

`core` is not upstream's submodule any more: it is `ShiroiKuma0/shiroikuma-ongakuots-core`, with the
same `master` (mirroring `upstream/multiplatform`) / `custom` model, and it carries the Android Auto
colours. **Never repoint `.gitmodules` back at `maxrave-dev/core`** — that silently drops our work.

```bash
git diff master..upstream/dev -- core          # a moved submodule pointer shows here
git -C core fetch upstream
git -C core log --oneline master..upstream/multiplatform
```

A large share of SimpMusic's real work (data layer, scraper, media) happens in that repo and is
invisible in the main repo's `git log`, so read it for the Step 2 table as well.

When it has moved, sync it **before** the parent, in its own directory:

```bash
git -C core checkout master && git -C core merge --ff-only upstream/multiplatform
git -C core checkout custom && git -C core rebase master
```

Two traps:

- **`git submodule sync` rewrites `core`'s `origin` from `.gitmodules`** (the HTTPS URL), and the
  push then fails asking for a username. Restore it with
  `git -C core remote set-url origin git@github.com:ShiroiKuma0/shiroikuma-ongakuots-core.git`.
- **Push `core` first, then the parent.** The parent records a commit sha in our core fork, so a
  pushed pointer to an unpushed core commit is a broken clone for everyone else.

Then `git submodule update --init --recursive` and commit the moved pointer with the rest.

## Step 2 — ⛔ PROCEED GATE: the new-features table (MANDATORY, before any branch moves)

**白い熊's standing request: before touching a single conflict, give them a tabular, descriptive
summary of what upstream introduced, and wait for an explicit go-ahead.** No fast-forward, no
rebase, no build until they say proceed. Never skip it, never fold it into the rebase turn.

It is a **descriptive** table, not a commit dump — say what each change *does*, in plain language
taken from the commit **body**, and what it means for our patches. Fold recurring noise (translation
bumps, dependency bumps, lint fixes) into one row each.

| Upstream change | What it does | Touches our patches? |
| --- | --- | --- |
| e.g. New Discord RPC toggle | Shows the current track on a Discord profile | No |
| e.g. Reworked SettingScreen sections | Splits settings into sub-screens | **Yes** — our UI page entry and de-branding hang off it |
| e.g. `version-code` 56 → 57 | Upstream released 1.8.0 | Version literals only; ours derive |
| e.g. `core` submodule → new sha | Data-layer rewrite in the separate repo | Check `:data` build after the rebase |

Flag in the **relevance** column anything that:

- touches a **file our customization layer patches** (the inventory in Step 6) — likely conflicts;
- reworks **CreditScreen / SettingScreen / ReviewDialog / About**, where our de-branding lives, or
  the launcher-icon assets;
- re-introduces **telemetry** on the `isFullBuild=false` path (Sentry, Crashlytics, any new
  phone-home) — our FOSS build must stay silent;
- is a **genuinely useful fix** for 白い熊 (playback, lyrics, download, scraper breakage).

Close with the version move, the commit count, the conflict set from Step 1, and the submodule
state — then **STOP and wait**. Only an explicit "proceed" / "go" / "yes" continues.

## Step 3 — Advance `master` (mirror; no fork work lives here)

```bash
git checkout master
git merge --ff-only upstream/dev
```

Do **not** push it here — the push is deferred to Step 9.

## Step 4 — Rebase `custom`

```bash
git checkout custom
git rebase master
git submodule update --init --recursive
```

Resolve conflicts so **all** our customizations survive. Reconcile, don't drop — if upstream
restructured a file we patch, port our change onto the new structure rather than forcing the old
diff.

**The recurring one is `CLAUDE.md`.** Upstream actively edits its own guide, and our fork section
sits on top of it in the same file. Keep **both**: our block first, upstream's revised text below.
Nothing else about our layer lives in that file.

**"Not huge" — resolve in place, `git add`, `git rebase --continue`:** context shifts where our hunk
obviously slots into moved-but-equivalent code; a `CLAUDE.md` overlap; a commit going empty because
upstream did the same thing.

**"Significant" — STOP and plan with 白い熊:** upstream refactored something a patch depends on (the
`defaultConfig` block, the `buildkonfig` block, the credit/settings screens); many commits conflict
or the same file conflicts repeatedly; or a **semantic** conflict where hunks merge textually but
behaviour changed (e.g. a new telemetry path our FOSS build does not cover). Gather `git status`,
the conflicted hunks and what upstream did to that file, identify which of **our** commits is
conflicting and why, then present options — resolve together, re-derive the commit, defer it, or
`git rebase --abort` (which returns the tree to exactly where it was; say so — aborting is safe and
re-runnable).

## Step 5 — Leave the build tail running — do NOT reset it

`BUILD_NUMBER` keeps counting **upward across the sync**, always. Because `master` tracks the
bleeding tip, most syncs land on commits where upstream's `version-code` has not moved — so
resetting the counter would send `versionCode` backwards (560030 installed, 560001 offered) and the
installer would refuse the update. `buildFork` enforces it: `LAST_BUILT_VERSION_CODE` records the
highest code ever built and the task fails rather than produce one at or below it.

**The version pin needs no edit.** It derives from `git merge-base HEAD master`, which the rebase
moves by itself, so the next build picks up the new date and sha automatically.

## Step 6 — Verify our customizations survived — the inventory

| What | Expected | Where |
| --- | --- | --- |
| Installed app id | `shiroikuma.ongakuots` | `androidApp/build.gradle.kts` → `defaultConfig.applicationId` |
| Code namespace | `com.maxrave.simpmusic` (**never rename**) | `androidApp/build.gradle.kts` → `namespace` |
| App label | `白い熊 音楽乙` | `androidApp/src/main/res/values/app_name.xml`, `composeApp/src/commonMain/composeResources/values/app_name.xml` |
| Fork version + pin + `buildFork` | `forkVersionName` / `forkVersionCode` / `upstreamPin` via `providers.exec` | `gradle/shiroikuma-fork.gradle.kts` |
| Root hook | `apply(from = "gradle/shiroikuma-fork.gradle.kts")` | `build.gradle.kts` |
| In-app version | fork version fed to `BuildKonfig` | `composeApp/build.gradle.kts` `buildkonfig` block |
| Signing shim | `keystore.properties` → `signingConfigs["shiroikuma"]`, wired into `release` | `androidApp/build.gradle.kts` |
| Build tail + FOSS flag | `BUILD_NUMBER`, `LAST_BUILT_VERSION_CODE`, `isFullBuild=false` | `gradle.properties` |
| Single ABI | `abis = arrayOf("arm64-v8a")`, `ndk.abiFilters` arm64 only, `splits.abi.isEnable = false` | `androidApp/build.gradle.kts` |
| Black-yellow icon | yellow line-art on black, adaptive | `androidApp/src/main/res/mipmap-*`, `drawable-v24/ic_launcher_foreground.xml` |
| De-branding | our name + our GitHub links everywhere user-visible | `CreditScreen.kt`, `SettingScreen.kt`, `ReviewDialog.kt`, strings |
| 白い熊 音楽乙 UI page | 23 colour slots, fonts, sizes, shapes, Export/Import, automation rows | `shiroikuma/OngakuUiScreen.kt` + `OngakuUi*.kt` + `OngakuPickers.kt` + `OngakuSurfaces.kt` |
| Page entry points | long-press the home cog; top row of Settings | `HomeScreen.kt` `HomeTopAppBar`, `SettingScreen.kt` item `shiroikuma_ui` |
| Theme reaches the app | `applyTo()` → ColorScheme; `surfaceTint` pinned to `surface` | `shiroikuma/OngakuUi.kt`, `ui/theme/Theme.kt` |
| Force-dark literals overridden | the immersive `#242424`/white/grey set | `ui/component/SurfaceDarkColors.kt`, `ui/theme/Typo.kt` |
| Artwork backgrounds flattened | album/playlist/artist page, player backdrop | `extension/UIExt.kt` `toImmersiveBackground`, `NowPlayingScreen.kt` |
| Side rules draw ON TOP | `drawWithContent`, never `drawBehind` | `shiroikuma/OngakuSurfaces.kt` `skSideBorders` |
| Category ZIP backup | headless engine + Kōjiki panel | `shiroikuma/SkBackup.android.kt`, `SkExportImportPanel.android.kt` |
| 保存復元 automation | receiver, foreground service, token, cancel | `shiroikuma/automation/*.kt` + manifest receiver/service |
| Android Auto colours | **in the `core` fork** | `core/media/media3/.../carapp/SkCarColors.kt`, `res/values/shiroikuma_car.xml` |
| Fork doc on top | our block above upstream's guide | `CLAUDE.md` |

### Regression greps — the checks a rebase will NOT flag

Our de-branding is a **mechanical sweep across many files**. If upstream adds one more link or
credit of the same shape, git merges it cleanly and the fork silently loses ground — no conflict, no
warning. Run these every sync; each must print nothing:

```bash
# no upstream GitHub / sponsor link may creep back into user-visible code
grep -rn "maxrave-dev" composeApp/src androidApp/src --include=*.kt --include=*.xml

# the label must not revert
grep -rn ">SimpMusic<" androidApp/src composeApp/src/commonMain/composeResources/values/

# our FOSS build must stay telemetry-free: no Sentry/Crashlytics init outside the gated modules
grep -rn "isFullBuild" gradle.properties

# THE theming regression. Every one of these is a literal no colour scheme reaches, so upstream
# adding one more of the same shape merges cleanly and the fork silently loses ground — which is
# exactly how the player and the immersive screens stayed grey after the theme was "done".
# A hit is not automatically wrong; it means: route it through skOnPlayer() / skContentColor().
grep -rn "Color\.White" composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/ui/screen/player \
    composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/ui/screen/other \
    composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/ui/screen/library \
  | grep -v "mutableStateOf(Color.White)" \
  | grep -v "else -> Color.White" \
  | grep -v "FullscreenPlayer.kt"

# the hand-rolled force-dark content colour — must go through skContentColor()
grep -rn "if (forceDark) Color.White" composeApp/src/commonMain/kotlin --include=*.kt \
  | grep -v "shiroikuma/OngakuSurfaces.kt"
```

**The three exclusions are deliberate, and each is load-bearing:**

- `mutableStateOf(Color.White)` — a `remember` lambda, where a `@Composable` call is illegal; it is
  a shadow colour, not content.
- `else -> Color.White` — the *stock* branch of our own `when`, i.e. the literal this fork replaces
  when the theme is switched off. Removing it would break the escape hatch.
- `FullscreenPlayer.kt` — **a known gap, not an exception.** 17 sites there were never swept (see
  "Known gaps" in `CLAUDE.md`); drop this exclusion the moment they are, and the grep goes back to
  covering the whole player.
- `OngakuSurfaces.kt` — the doc comment on `skContentColor()` quotes the pattern it replaces.

These two must then print **nothing**. The next three must each print a hit — they are the four causes
that made whole classes of surface stay grey, and losing one silently undoes the theme:

```bash
# elevation tint pinned, or every card drifts off black again
grep -n "surfaceTint = surface" composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/shiroikuma/OngakuUi.kt

# side rules drawn AFTER the content, never behind it
grep -n "drawWithContent" composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/shiroikuma/OngakuSurfaces.kt

# the force-dark sets and the artwork background still branch on our theme
grep -c "LocalOngakuUi" composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/ui/component/SurfaceDarkColors.kt \
    composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/ui/theme/Typo.kt \
    composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/extension/UIExt.kt
```

Then confirm the build script still evaluates:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=/home/shiroikuma/android-sdk \
  ./gradlew buildFork --dry-run --console=plain
```

## Step 7 — Merge the changelog

**白い熊's standing request: every release publishes a merged changelog.** Write the new fork entry
at the top of `CHANGELOG.md` — a per-release delta of *our* work, not a re-listing — then fold in
whatever upstream notes arrived with this rebase, under the *Upstream* section, attributed and dated:

```bash
git diff master@{1}..master -- fastlane/metadata/android/en-US/changelogs/
gh release view <tag> -R maxrave-dev/SimpMusic --json body --jq .body
```

Keep our history above, upstream's beneath. `/publish-version` publishes this merged file with the
release.

## Step 8 — Build the new `+1`

Via the **build-apk** skill (`./gradlew buildFork`), then deliver via the global **`/after-build`**
skill (no transfer prompt). Check the printed version: the pin's date and sha should have **moved**
to the new base, and the counter should have **continued** rather than reset. If the build fails on
the rebase result, treat it like a significant conflict — diagnose, and replan with 白い熊 rather
than patching blindly.

## Step 9 — Stop, then push only on "Push"

Let 白い熊 install and test on-device. Only on their explicit **"Push"**:

```bash
git -C core push origin master                 # the submodule FIRST — see below
git -C core push --force-with-lease origin custom
git push origin master
git push --force-with-lease origin custom     # history was rewritten by the rebase
```

**`core` before the parent, always.** The parent records a commit sha in our core fork; pushing a
pointer to a commit that is not yet on GitHub gives everyone else a clone that cannot check out.

`--force-with-lease`, never bare `--force`. Then update the base-version examples in `build-apk` and
in this skill if they have drifted, and treat the sync as incomplete until they match.

## Hard rules

- Never `adb install` / `adb uninstall` — 白い熊 installs manually from `/sdcard/tmp/`.
- Never commit/push unprompted; wait for "Push".
- Never rename the `com.maxrave.simpmusic` namespace — only `applicationId` differs.
- Never hand-edit upstream's `version-name` / `version-code` literals, and never reset `BUILD_NUMBER`.
- Never skip the Step 2 proceed gate.
- No Claude attribution in commits (see `CLAUDE.md`).
