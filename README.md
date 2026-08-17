<div align="center">

<img src="fastlane/metadata/android/en-US/images/icon.png" width="120" alt="白い熊 音楽乙 app icon" />

# 白い熊 音楽乙

**A YouTube Music client for Android — ad-free, background playback, synced lyrics, in black and yellow.**

A fork of [SimpMusic](https://github.com/maxrave-dev/SimpMusic) with **major additions**: a full
black-and-yellow UI with every colour, font, size and shape settable in-app; external font import;
a category-ZIP Export / Import; headless backup automation; Android Auto in the house colours; and
no telemetry at all.

Installs **side-by-side** with the official SimpMusic (app id `shiroikuma.ongakuots`).

**📥 Latest release: [`1.7.0+2026-08-15.17-42.g9155f673+010`](https://github.com/ShiroiKuma0/shiroikuma-ongakuots/releases/latest)** — [all releases & APK downloads »](https://github.com/ShiroiKuma0/shiroikuma-ongakuots/releases)

</div>

---

## 🎨 白い熊 音楽乙 UI — the whole look, settable

One page holds everything the app's appearance is built from. **23 colour slots** in seven groups —
surfaces, text, accent, lines, player, semantic, Android Auto — each with a plain-language line
saying what it actually paints. The picker is four RGBA sliders under a live preview, with a row of
one-click boxes above them prefilled with the slot's own default followed by every colour picked
before, so the palette you are building is always one tap away.

Type, shape and density are sliders: text size as a percentage applied to every style at once,
weight 100–900, corner roundness and border thickness both reaching **0**, icon size, and row
spacing — which is the page's own density, so it tightens as you drag it.

**Every control previews itself.** The page edits the same state the whole app renders from, so a
slider lands on the live app on the next frame — no apply button, no restart.

Reached by **long-pressing the settings cog** on the home screen, or from the top of Settings.

---

## ⬛ Black and yellow, by default

Not a theme you switch on — the default. Black surfaces, yellow text, yellow borders, everywhere:
the player and its traced transport button, the lyrics, the immersive artist/album/playlist pages,
every dialog, the mini player, the navigation rail, the seek bar. The master switch falls back to
stock Material with every edit kept, so the two can be compared without losing work.

---

## 🎛 Every fork change is a switch

The page holds not just how the fork *looks* but what it **does** differently. Flat player backdrop,
flat page backgrounds, traced transport button, traced action buttons, house mini player, band side
rules, frames — each defaults to the fork's behaviour and restores upstream's when turned off. So the
section doubles as a way to see exactly what this fork changed, one switch at a time.

---

## 🔤 Your own fonts

Import any `.ttf`/`.otf` through the picker; the file is copied into the app's own store, so it
survives a reboot in a way a borrowed SAF grant does not. Every option in the picker is **drawn in
its own glyphs** — the only honest way to choose a typeface.

---

## 💾 Export / Import — one ZIP, five categories

A category archive holding everything the app lets you set: the UI theme, imported fonts, all app
settings, the library and listening history, and (opt-in) the downloaded audio. Pick a folder once;
the panel shows the newest archive in it as it opens. Written `.part` and renamed only when closed,
so a killed export never leaves something a later restore mistakes for a backup.

The entry names are deliberately the ones **upstream's own backup uses**, so an archive written here
restores through stock SimpMusic, and a stock archive restores here.

---

## 🤖 Backup automation (保存復元)

Implements the sister-app export contract: three token-gated broadcast actions, the export running in
a foreground service with a wakelock, live progress with real counts, one guaranteed terminal reply,
and a cancel that is safe to send at any time and always removes the partial file. Lets
白い熊 自由作業盤 back this app up headlessly as part of a batch across every sister app.

---

## 🚗 Android Auto in the house colours

A car theme makes `CarColor.PRIMARY`/`SECONDARY` the house yellow, and a runtime colour follows the
two Auto slots on the UI page. Auto's host draws its own backgrounds and enforces its own contrast
for driver-distraction reasons, so the yellow lands on what the app is allowed to tint — the
floating action button, the now-playing row marker, the action strip.

---

## 🔇 Nothing phones home

Built FOSS: no Sentry, no Firebase Crashlytics, no Google Cast SDK, Last.fm stubbed. The in-app
updater is removed rather than repointed — it asks GitHub for *upstream's* newest release, which
could only ever offer an APK that cannot install over this one. Upstream's blog-promo dialog, its
daily blog-RSS notification worker, and its sponsor and review links are gone too.

---

## Differences at a glance

| | 白い熊 音楽乙 | SimpMusic |
| --- | --- | --- |
| Package | `shiroikuma.ongakuots` — installs alongside | `com.maxrave.simpmusic` |
| Look | black / yellow `#FFFF00`, fully settable in-app | Material You from a seed colour |
| Fonts | any imported file, previewed in its own glyphs | bundled Poppins |
| Backup | category ZIP + headless automation | single backup/restore file |
| Fork changes | each one a switch, revertible to upstream's behaviour | n/a |
| Telemetry | **none** | opt-out crash reporting in the full build |
| In-app updater | removed | checks upstream's GitHub releases |
| ABI | `arm64-v8a` only | armeabi-v7a + arm64-v8a + x86_64 |

---

## Built on SimpMusic

A fork of [SimpMusic](https://github.com/maxrave-dev/SimpMusic) by
[maxrave-dev](https://github.com/maxrave-dev) — a genuinely excellent, genuinely free YouTube Music
client, and all of the hard parts here are theirs: the player, the scraper, the lyrics pipeline, the
library. **If you are looking for SimpMusic itself, go there** — that is where the work happens and
where issues and support belong. This repo is one person's private build with their own changes on
top. The lyrics and chart services the app talks to remain maxrave-dev's own.

Released under the **GPL-3.0**, the same as upstream.

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
./gradlew buildFork          # signed release APK; needs a keystore.properties
```

Without a `keystore.properties` the release APK is unsigned; `./gradlew :androidApp:assembleDebug`
needs no key. The `core` submodule is not optional — the `:common`, `:data`, `:domain`, service and
media modules resolve out of it. See `CLAUDE.md` and `.claude/skills/build-apk` for the full pipeline.
