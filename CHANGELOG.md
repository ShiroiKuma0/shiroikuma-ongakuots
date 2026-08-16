# Changelog

This file carries **both histories**: 白い熊 音楽乙's releases first, then upstream SimpMusic's own
release notes below. Upstream keeps no `CHANGELOG.md` of its own — its notes live one file per
versionCode in `fastlane/metadata/android/en-US/changelogs/` plus the GitHub release bodies, and
every sync folds the new ones in (see `.claude/skills/upstream-new-version`, Step 7). Our entries
are never mixed into theirs.

Fork versions read `<upstream>+<base date>.<HH-MM UTC>.g<sha8>+<build>`: the middle group pins the
upstream commit the build sits on, and moves only on a sync. The installed `versionCode` is
`<upstream code> * 10000 + <build>`, independent of the pin.

## 白い熊 音楽乙 — fork releases

### Unreleased — repository set-up (2026-08-16)

The fork's scaffolding, before any behavioural change to the app:

- **Forked** `maxrave-dev/SimpMusic` to `ShiroiKuma0/shiroikuma-ongakuots`, with the `core` git
  submodule. `master` mirrors `upstream/dev` (the bleeding default branch, not the `vX.Y.Z` tags on
  `main`); `custom` carries every commit below and is rebased onto `master` on each sync.
- **Side-by-side install**: `applicationId` `shiroikuma.ongakuots`, label **白い熊 音楽乙**. The
  Kotlin namespace stays `com.maxrave.simpmusic` — build-time only, and renaming it would break
  BuildKonfig, Room and Koin for nothing.
- **Fork versioning** (`gradle/shiroikuma-fork.gradle.kts`, applied by one line in the root build):
  `versionName = <upstream>+<upstream-base date>.<HH-MM UTC>.g<sha8>+<build, 3 digits>` and
  `versionCode = <upstream code> * 10000 + <build>`. Upstream's own literals in
  `gradle/libs.versions.toml` are read, never edited, so an upstream bump flows in on rebase by
  itself. `:androidApp` and `:composeApp` read the same computed values, so the version shown in-app
  matches the APK exactly. First build: `1.7.0+2026-08-15.17-42.g9155f673+001`, versionCode `560001`.
- **`buildFork`** — one task that assembles the signed release, copies it to
  `~/tmp/shiroikuma-ongakuots_<versionName>_arm64-v8a.apk`, bumps `BUILD_NUMBER` and records
  `LAST_BUILT_VERSION_CODE`. The counter never resets, and the task refuses to build at or below the
  highest code already shipped.
- **Signing**: our own PKCS12/RSA-4096 key (`shiroikuma-ongakuots.jks`, alias `ongakuots`), read
  from a gitignored `keystore.properties`. Upstream ships no Gradle signing config at all — its CI
  signs externally with `apksigner` — so this is new code, deliberately kept as a shim that never
  touches upstream's own build logic.
- **FOSS build** (`isFullBuild=false`): no Sentry, no Firebase Crashlytics, no Google Cast SDK,
  Last.fm stubbed. Nothing phones home.
- **arm64-v8a only**, ABI splitting off — one deterministic APK and a far shorter build.
- **Fork documentation**: our `CLAUDE.md` section above upstream's own guide (whose "answer in
  English + Vietnamese" language rule is explicitly void here), plus the `build-apk` and
  `upstream-new-version` skills beside upstream's `.claude/skills/`.

## Upstream — SimpMusic

Upstream's notes are folded in here on each sync, newest first. At fork time the base was
`upstream/dev` at `9155f673` (2026-08-15 17:42 UTC), one release past **v1.7.0** (versionCode 56,
released 2026-08-07). Upstream's full release history:
<https://github.com/maxrave-dev/SimpMusic/releases>.
