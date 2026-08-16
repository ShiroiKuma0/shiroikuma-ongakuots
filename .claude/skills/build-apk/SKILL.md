---
name: build-apk
description: Build the signed release APK of shiroikuma-ongakuots (白い熊 音楽乙 — 白い熊's fork of maxrave-dev/SimpMusic, a Compose-Multiplatform YouTube Music client, app id shiroikuma.ongakuots) with the `buildFork` Gradle task, then deliver it automatically via the global /after-build skill (adb push if a phone is connected, else scp to skhw — no prompt). Always build first without asking permission to build. Use whenever 白い熊 mentions SimpMusic, shiroikuma-ongakuots, 音楽乙, "the ongakuots fork", asks to build the app, build the APK, make a release build, apply a change to the music fork, or build and send to the phone.
---

# Build the 白い熊 音楽乙 release APK and deliver it

> **Never ask whether to build — just build.** When this skill applies (白い熊 asked to build, or
> you've made changes ready to test), run the build immediately. Do **not** ask "shall I build?".
> There is **no** transfer question either: after a successful build, deliver the APK automatically
> via the global **`/after-build`** skill — no prompts at all.

> **The push destination is ALWAYS `/sdcard/tmp/`.** Never `/sdcard/Download/`.

> **Never run `adb install` / `pm install` / `adb uninstall`.** 白い熊 installs the APK themselves
> from the phone's file manager.

> **Never `git commit` or `git push` on your own.** Building does not include committing. After the
> build 白い熊 tests it; only when they explicitly say **"Push"** do you commit and
> `git push origin custom`. Their "Push" means commit-and-push-to-the-fork — unrelated to `adb push`.

## Project identity

| Item | Value |
|------|-------|
| Upstream repo | `maxrave-dev/SimpMusic` (remote `upstream`, HTTPS, **fetch only** — push URL `DISABLED`) |
| Fork repo | `git@github.com:ShiroiKuma0/shiroikuma-ongakuots.git` (remote `origin`, SSH — push here) |
| Local working tree | `~/git/shiroikuma-ongakuots` |
| Mirror branch | `master` — fast-forwards `upstream/dev`, never carries our changes |
| Custom branch | `custom` — all our commits, rebased onto `master`; the GitHub default branch |
| Custom applicationId | `shiroikuma.ongakuots` |
| Custom app label | `白い熊 音楽乙` |
| Kotlin namespace (**UNCHANGED**) | `com.maxrave.simpmusic` — R/BuildConfig/BuildKonfig package; never rename |
| UI settings page | `白い熊 音楽乙 UI` (name fixed 2026-08-16; contents still to be specified) |
| Build flavour | FOSS — `isFullBuild=false` (no Sentry, no Crashlytics, no Cast SDK, Last.fm stubbed) |
| Target ABI | `arm64-v8a` only, universal APK off → exactly one release APK |
| Gradle task | `./gradlew buildFork` |
| Built APK dir | `androidApp/build/outputs/apk/release/` |
| Delivered APK | `~/tmp/shiroikuma-ongakuots_<versionName>_arm64-v8a.apk` → `/sdcard/tmp/` |
| Keystore | `~/.android-keystores/shiroikuma-ongakuots.jks`, alias `ongakuots` |
| Git submodule | **`core`** (`maxrave-dev/core`) — `:common :data :domain` + service + media modules |
| Build JDK | OpenJDK 21 at `/usr/lib/jvm/java-21-openjdk-amd64` |
| Android SDK | `~/android-sdk`, platform `android-37`, build-tools `37.0.0` |
| Gradle / AGP / Kotlin | 9.5.1 wrapper / 9.2.1 / 2.4.10 |

Compose Multiplatform, Kotlin DSL, Clean Architecture. App module `:androidApp` (thin Android
shell); nearly all UI and logic live in `:composeApp`; `:desktopApp` is upstream's desktop target
and **we do not build it**.

## Build environment (this machine)

The default `java` is JDK 11, which cannot run Gradle 9.x, and the Android SDK is not on a default
env var. Export both in **every** invocation — Claude Code's non-interactive shell does not source
白い熊's profile:

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_HOME=/home/shiroikuma/android-sdk
export ANDROID_SDK_ROOT=/home/shiroikuma/android-sdk
```

Writes under `~/git/` are blocked by the command sandbox on this machine — run every build, git and
keystore command with `dangerouslyDisableSandbox: true`.

## Steps

1. **Note the version you are about to produce.** The counter and the floor live in
   `gradle.properties`; upstream's literals live in the version catalog:

   ```bash
   grep -E 'BUILD_NUMBER|LAST_BUILT_VERSION_CODE' gradle.properties
   grep -E 'version-name|version-code' gradle/libs.versions.toml
   ```

   The APK will be `shiroikuma-ongakuots_<versionName>_arm64-v8a.apk` using the `BUILD_NUMBER`
   value **before** the build (`buildFork` bumps it afterward). Don't reconstruct the name by hand
   — read the printed `>>>` line.

2. **Build** (release, signed) — from the repo root:

   ```bash
   export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=/home/shiroikuma/android-sdk
   ./gradlew buildFork --console=plain < /dev/null
   ```

   - `buildFork` runs `:androidApp:assembleRelease`, copies the signed APK to `~/tmp/<apk name>`,
     bumps `BUILD_NUMBER` and records `LAST_BUILT_VERSION_CODE` in `gradle.properties`.
   - It prints `>>> <path>`, `>>> versionCode <n>` and `>>> BUILD_NUMBER bumped to <n>` in cyan —
     use those to confirm the exact filename and code; confirm `BUILD SUCCESSFUL`.
   - A cold build downloads the Gradle distro plus a very large dependency set and compiles the
     whole Compose-Multiplatform tree — it can far exceed the foreground timeout. Run it with
     `run_in_background` and poll the output file.
   - **Fast iteration:** `./gradlew :androidApp:assembleDebug` is quicker (no R8) and installs
     side-by-side as `shiroikuma.ongakuots.dev`, but the shippable build is `buildFork`.

3. **At the end of every build, deliver via `/after-build`** — no exceptions, no asking. As soon as
   `BUILD SUCCESSFUL` appears and the APK is in `~/tmp/`, invoke the global **`/after-build`**
   skill; it runs `/adb-check` unsandboxed, then `/adb-push` to `/sdcard/tmp/` if a phone is
   connected, otherwise `/scp` to `skhw:~/tmp/`, and announces what landed.

## Signing

Upstream ships **no** Gradle signing config at all — its CI signs with `apksigner` from
`build_and_sign_apk.sh`. Our fork adds a `signingConfigs["shiroikuma"]` block at the top of
`androidApp/build.gradle.kts` that reads a gitignored `keystore.properties` at the repo root
(`storeFile` / `storePassword` / `keyAlias` / `keyPassword`), wired into `buildTypes.release` via
`signingConfig = signingConfigs.findByName("shiroikuma")`.

- Keystore: `~/.android-keystores/shiroikuma-ongakuots.jks`, alias `ongakuots`
  (PKCS12/RSA-4096, SHA384withRSA, 10000-day validity, created 2026-08-16).
- Password: `~/〇/[666] 私資料/[666][27] 暗号/android-keystores.org`; the `.jks` is mirrored to
  `~/〇/[666] 私資料/[666][27] 暗号/android-keystores/`.
- If `keystore.properties` is absent the configuration prints
  `shiroikuma: no keystore.properties — the release APK will be UNSIGNED.` and the APK will not
  install. Restore the file rather than shipping it. Regenerate with:

  ```bash
  cat > keystore.properties <<EOF
  storeFile=$HOME/.android-keystores/shiroikuma-ongakuots.jks
  storePassword=<from the vault>
  keyAlias=ongakuots
  keyPassword=<same>
  EOF
  chmod 600 keystore.properties
  ```

## Versioning (how the numbers are formed)

All of it is computed in **`gradle/shiroikuma-fork.gradle.kts`**, applied by one added line in the
root `build.gradle.kts`. `:androidApp` and `:composeApp` read the results from `rootProject.extra`,
so the string is formed once and the in-app version matches the APK exactly.

- Upstream's pair (`version-name = "1.7.0"` / `version-code = "56"`) stays in
  `gradle/libs.versions.toml` as untouched literals, so a rebase carries new upstream values in by
  itself. **Never hand-edit them.**
- `BUILD_NUMBER` in `gradle.properties` is our per-build increment, bumped by `buildFork`.
- Fork `versionName = "<upstream>+<base date>.<HH-MM>.g<sha8>+<BUILD_NUMBER padded to 3>"` →
  `1.7.0+2026-08-15.17-42.g9155f673+001`. **Upstream tracking is `git`**: the pin is the merge-base
  of `HEAD` and `master`, with that commit's committer date in **UTC**, so it moves only on a sync.
  See the global **`git-versioning`** skill.
- Fork `versionCode = <upstream version-code> * 10000 + BUILD_NUMBER` → `560001`.
- **`BUILD_NUMBER` never resets.** `master` mirrors the bleeding `upstream/dev`, whose `versionCode`
  stands still between upstream releases, so a reset would send our code backwards and the installer
  would read the new build as a downgrade. `LAST_BUILT_VERSION_CODE` records the highest code ever
  shipped and `buildFork` fails rather than build at or below it.
- If the tree has no local `master` (shallow clone), the pin degrades to empty and the build still
  succeeds — it must never fail over a missing sha.

## Traps specific to this project

- **The `core` submodule is not optional.** `settings.gradle.kts` resolves `:common :data :domain`,
  the service modules and the media modules out of `core/`. An un-initialised submodule fails
  configuration with "project ':common' … does not exist". Fix:
  `git submodule update --init --recursive`.
- **Never flip `isFullBuild` back to `true`** without a reason: the repo carries no
  `google-services.json`, and the full path wants a `SENTRY_AUTH_TOKEN`, a Sentry DSN and Last.fm
  credentials from `local.properties`. FOSS is both the private and the buildable path.
- **`namespace` stays `com.maxrave.simpmusic`** in every module. Only `applicationId` differs — that
  alone decides side-by-side coexistence with the official app. Renaming the namespace would break
  BuildKonfig, Room and Koin wiring for nothing.
- **Do not install our APK over the official SimpMusic** — different signing keys, so Android
  refuses. They coexist as distinct packages.
- Upstream's `build_and_sign_apk.sh` is left in place but is **not** our pipeline; ignore it.

## Related skills

- **`upstream-new-version`** — sync onto newer `upstream/dev` work: proceed-gated feature table,
  fast-forward `master`, rebase `custom`, merge the changelog, rebuild.
- Global **`/after-build`** (deliver), **`/publish-version`** (cut a GitHub release),
  **`/git-versioning`** (the version-pin format).

---

**Commit convention — no Claude attribution.** Never add a `Co-Authored-By: Claude …` /
"Generated with Claude" trailer to commit messages or PR bodies; end the message at the last line
of the body. This overrides the harness default. (Global rule: `~/.claude/CLAUDE.md`.)
