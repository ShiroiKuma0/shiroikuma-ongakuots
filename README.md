<div align="center">

<img src="fastlane/metadata/android/en-US/images/icon.png" width="120" alt="白い熊 音楽乙 app icon" />

# 白い熊 音楽乙

**A YouTube Music client for Android — ad-free, background playback, synced lyrics.**

白い熊's personal fork of [SimpMusic](https://github.com/maxrave-dev/SimpMusic) by
[maxrave-dev](https://github.com/maxrave-dev), in the house black-and-yellow.
Installs **side-by-side** with the official SimpMusic.

</div>

---

## What this fork is

`shiroikuma.ongakuots` is a private build of SimpMusic carrying 白い熊's own changes. It exists to
be a place for those changes, not to compete with or replace upstream — **if you are looking for
SimpMusic itself, go to [maxrave-dev/SimpMusic](https://github.com/maxrave-dev/SimpMusic)**, which
is where all the real work happens and where issues and support belong.

## How it differs from stock SimpMusic

| | 白い熊 音楽乙 | SimpMusic |
| --- | --- | --- |
| Package | `shiroikuma.ongakuots` — installs alongside | `com.maxrave.simpmusic` |
| Icon & mark | black / yellow `#FFFF00` line-art trace | cyan note on white |
| Telemetry | **none** — no Sentry, no Firebase Crashlytics | opt-out crash reporting in the full build |
| Google Cast | not linked | in the full build |
| In-app updater | removed — this fork is built locally | checks upstream's GitHub releases |
| Upstream promotion | removed — blog dialog, blog-post notifications, sponsor and ProductHunt links | present |
| ABI | `arm64-v8a` only | armeabi-v7a + arm64-v8a + x86_64 |
| Auto-backup folder | `Download/shiroikuma-ongakuots` | `Download/SimpMusic` |

Everything else — the player, the scraper, the lyrics, the library — is upstream's, and the
lyrics and chart services the app talks to are still maxrave-dev's own.

## Branches

| Branch | Purpose |
| --- | --- |
| `custom` | 白い熊's work. The default branch — this is what you are looking at. |
| `master` | A pure mirror of `upstream/dev`, fast-forward only. |

Versions read `<upstream>+<upstream-base date>.<HH-MM UTC>.g<sha8>+<build>` — the middle group pins
the exact upstream commit a build sits on, and moves only on a sync.

## Building

```bash
git clone --recurse-submodules https://github.com/ShiroiKuma0/shiroikuma-ongakuots
cd shiroikuma-ongakuots
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=$HOME/android-sdk
./gradlew buildFork          # signed release APK, needs a keystore.properties
```

Without a `keystore.properties` the release APK is unsigned; `./gradlew :androidApp:assembleDebug`
needs no key. See `CLAUDE.md` and `.claude/skills/build-apk` for the full pipeline.

## Licence

GPL-3.0, the same as upstream. ©2023-2025 Tuan Minh Nguyen Duc (maxrave-dev) for SimpMusic; fork
changes by [ShiroiKuma0](https://github.com/ShiroiKuma0). See [LICENSE](LICENSE).
