# Changelog

This file carries **both histories**: 白い熊 音楽乙's releases first, then upstream SimpMusic's own
release notes below. Upstream keeps no `CHANGELOG.md` of its own — its notes live one file per
versionCode in `fastlane/metadata/android/en-US/changelogs/` plus the GitHub release bodies, and
every sync folds the new ones in (see `.claude/skills/upstream-new-version`, Step 7). Our entries
are never mixed into theirs.

Fork versions read `<upstream>+<base date>.<HH-MM UTC>.g<sha8>+<build>`: the middle group pins the
upstream commit the build sits on, and moves only on a sync. The installed `versionCode` is
`<upstream code> * 10000 + <build>`, independent of the pin.

## 白い熊 音楽乙 1.7.0+2026-08-15.17-42.g9155f673+010 — 2026-08-17

Still on upstream **1.7.0** at `upstream/dev` `9155f673` — upstream has not moved, so the pin is
unchanged and only the build counter did. versionCode `560010`. Everything here is the delta
since `+008`; build `+009` was not published.

### The last white surface

- **The fullscreen video player** was the one immersive screen the theming sweep never reached: 17
  `Color.White` sites — the transport tints, both sliders' thumb and track, the title and the
  overlay icons — plus `Color.DarkGray` on the unavailable previous/next, which is the same class of
  literal. It rendered white-on-artwork while the rest of the app was already black-yellow.

### Traced action buttons

- The album and playlist **Play pill** and the round **shuffle / download** actions were filled
  solid, so tinting the fill with the accent gave a solid yellow lozenge rather than the house look.
  They are traced now — black inside, border around, accent content — matching the player's
  transport button.
- The **artist page** needed more than a swap: upstream sources those three circles' accent from the
  artist's name-logo *image*, so they came out in whatever colour that artwork happened to be. With
  the theme on it resolves to the border colour, and the filled "following" state stops filling.

### Fork behaviour, settable

A new **Fork behaviour** section on the UI page, above Colours, in three groups:

- **Backgrounds** — flat player backdrop · flat page backgrounds
- **Controls** — traced transport button · traced action buttons · house mini player
- **Edges** — band side rules · frames (separate from border *thickness*, which sizes them)

Each defaults to the fork's behaviour and restores upstream's when turned off, so the section is also
a way to see exactly what the fork changed, one switch at a time. Each flag is read by the helper
that already owned that decision, or at the single call site that does.

Deliberately **not** switchable, and recorded as such: the removed in-app updater and the removed
blog-promo dialog and blog-RSS worker are removals rather than preferences — the updater can only
ever offer upstream's APK, which cannot install over ours — and Cast and Last.fm are gated by
`isFullBuild` at **build** time, so they cannot be runtime switches.

### Documentation

- The **`core` submodule being our fork** is now recorded in `CLAUDE.md` and both skills, with the
  sync step, the push order (core before the parent, since the parent records a sha in it) and the
  trap that `git submodule sync` rewrites core's `origin` to the HTTPS URL. Previously the docs still
  described it as upstream's, so a sync would have left it stale — or a later change would have
  repointed it and silently dropped the Android Auto work.
- The **theming architecture** is written down as the four mechanisms that no colour scheme reaches
  — Material's `surfaceTint`, the hardcoded force-dark set, the artwork-derived page backgrounds, and
  `drawBehind` painting side rules under the content — since those are what a rebase can silently
  undo, alongside the mechanical literal layer and which helper each goes through.
- The **regression greps are verified rather than asserted**: two must print nothing, three must
  print a hit. Stock fallbacks now carry an explicit `// sk-stock-fallback` marker that the grep
  excludes, so a future one opts out by saying what it is instead of the grep growing a list of
  special cases.

## 白い熊 音楽乙 1.7.0+2026-08-15.17-42.g9155f673+008 — 2026-08-17

The first published build, on upstream **1.7.0** at `upstream/dev` `9155f673` (2026-08-15 17:42 UTC).
versionCode `560008`, `arm64-v8a`. Everything the fork adds, in full — later entries are deltas.

### Identity & packaging

- **`shiroikuma.ongakuots`**, label **白い熊 音楽乙**, installable beside the official SimpMusic. The
  Kotlin namespace stays `com.maxrave.simpmusic` — build-time only, and renaming it would break
  BuildKonfig, Room and Koin for nothing.
- **Black-yellow traced launcher icon**: yellow `#FFFF00` line-art on black, the shape black-filled
  and yellow-stroked so it reads as a trace over the black adaptive background. Traced from
  upstream's own `drawable/monochrome.xml`, scaled 1.45× about the viewport centre so the whole mark
  stays inside the safe zone under both the circular and the squircle mask. Our own adaptive
  foreground and background vectors, so upstream's stays untouched and never conflicts.
  `tools/gen-icons.sh` regenerates the five density launcher sets, both in-app icons and the store
  icon from one source, `design/shiroikuma-ongakuots-icon.svg`.
- **FOSS build** (`isFullBuild=false`): no Sentry, no Firebase Crashlytics, no Google Cast SDK,
  Last.fm stubbed. Nothing phones home.
- **arm64-v8a only**, ABI splitting off — one deterministic APK and a far shorter build.
- **Fork versioning** in `gradle/shiroikuma-fork.gradle.kts`: versionName carries the upstream-base
  pin (date, HH-MM UTC and 8-char sha of the merge-base), versionCode is `<upstream> * 10000 +
  BUILD_NUMBER`. Upstream's literals in `gradle/libs.versions.toml` are read, never edited, so an
  upstream bump flows in on rebase by itself. `:androidApp` and `:composeApp` read the same computed
  values, so the version shown in-app matches the APK exactly.
- **`./gradlew buildFork`** — assembles the signed release, copies it to `~/tmp/` under the house
  filename, bumps the counter, and refuses to build at or below the highest code already shipped.
- **Own signing key** (PKCS12/RSA-4096) read from a gitignored `keystore.properties`. Upstream ships
  no Gradle signing config at all, so this is a shim beside its build logic rather than an edit to it.
- The **`core` submodule is forked too** (`ShiroiKuma0/shiroikuma-ongakuots-core`), because the
  Android Auto code lives there.

### 白い熊 音楽乙 UI — the fork's configuration hub

- A settings page in the kxkb grammar: bold headings underlined to their own text width, a thin
  full-width hairline opening each section, a 24 dp-per-level indent ladder, and row padding that is
  itself a setting (default 2 dp), so the page is tight and its density control previews itself.
- Reached by **long-pressing the settings cog** on the home screen, and from a row at the top of
  Settings above upstream's own appearance rows.
- **23 colour slots** in seven groups (surfaces, text, accent, lines, player, semantic, Android
  Auto), each carrying a black-yellow default and a line saying what it actually paints. Colours are
  stored in a map keyed by slot id, so an untouched slot falls back and adding one later needs no
  migration.
- **The picker**: four RGBA sliders under a live preview, with the one-click swatch row above them
  prefilled with the slot's own default followed by every colour picked before.
- **Typography**: font, a size percentage applied to every text style at once, and weight 100–900.
- **Shape & density**: corner roundness and border thickness (both reaching 0), icon size, row spacing.
- **Live previews** for colours, type, shape and icons — and the whole app repaints as a slider
  moves, because the page edits the very state the app renders from. Writes land in memory first and
  persist after, so nothing waits on a DataStore round-trip.
- **Reset** returns every colour, font, size and shape to stock black-yellow in one row.

### External fonts

- Import any font file through the picker; it is copied into the app's own store rather than
  referenced in place, because a SAF grant on someone else's file does not survive a reboot.
- Every option in the picker is **drawn in its own glyphs**.
- Imported fonts are carried by the backup, and can be removed from the picker.

### The black-yellow theme, app-wide

The house look is the **default**, not an option layered over one; the master switch falls back to
stock Material with every edit kept.

- `OngakuUi.applyTo()` expresses our chrome as a Material 3 `ColorScheme`, so every stock composable
  is restyled without being touched, and the semantic colours outside that scheme (like heart, sung
  lyric, shimmer) are mapped too.
- **`surfaceTint` pinned to the surface.** Material lightens an elevated surface by blending the tint
  into it; with the accent there, every card, sheet and app bar drifted off black. A raised surface is
  now raised by its border.
- **The immersive screens' hardcoded set replaced.** `rememberSurfaceDarkColors()` and
  `typo(forceDark)` carry literal `#242424` / `Color.White` / `#A8A8A8` values that no colour scheme
  can reach — the reason the artist, album, playlist and player screens stayed grey-on-white.
- **`toImmersiveBackground()`** derived the album/playlist/artist page background from the cover's
  dominant colour and merely darkened it, so a grey-blue sleeve gave a grey-blue page. Flat black now.
- **The player's palette gradient** did the same for the now-playing sheet, header included. Flat now.
- **~110 `Color.White` literals** routed through the theme across the player and the five immersive
  screens, plus the seven hand-rolled `if (forceDark) Color.White` content colours in the row
  composables, and `seed` (upstream's light blue) on active shuffle/repeat.
- **Lyrics**: a line *not* being sung is yellow and the line being sung is white, word-by-word
  included — the current line is marked by going white against yellow rather than by everything else
  going faint. Three module-level dim greys replaced.
- **The like heart** was a drawable carrying its own pink that nothing tinted; it takes the Favourite
  slot now.
- **The transport button is traced, not filled**: `PlayCircle`/`PauseCircle` are filled *disc*
  glyphs, so tinting one could only give a solid puck. It is a black circle with an accent ring and a
  bare glyph.
- **Borders**: the player's cards, the collapsed toolbar and all 18 `AlertDialog` sites carry the
  house frame — Material gives a dialog no border and no global hook, so it goes on each call site,
  and on the `Surface` inside for the three `BasicAlertDialog`s where a border on the window would
  trace the wrong rectangle.
- **Side rules** on the content strip beside the navigation rail and on the player sheet, drawn
  *after* the content — `drawBehind` puts them under the children, where every opaque background
  covers them.
- The mini-player pill is black with a yellow border instead of following the artwork palette; the
  seek bar takes the Played and Remaining slots; the in-app note badge is yellow.

### Export / Import

- A **category ZIP** — `manifest.json` plus one entry or entry tree per category: the UI theme,
  imported fonts, all app settings, the library and listening history, and downloaded audio (off by
  default, being large and re-downloadable). Import **merges**: a category absent from an archive is
  skipped rather than cleared.
- The entry names for settings, library and downloads are deliberately **upstream's own**, so an
  archive written here restores through stock SimpMusic's Restore and stock archives restore here.
- The panel follows the Kōjiki flow: a SAF folder box that is **red until set** and yellow after, the
  newest archive in it queried as the panel opens, a flat checklist between accent hairlines, and the
  ArcaneChat pill bar — Cancel alone left, Import and Export grouped right.
- Written `.part` and renamed only once closed, so a killed export leaves nothing a later restore
  would mistake for a real backup.
- Success raises a yellow-bordered dialog whose acknowledgement closes the panel **and** the UI page;
  failures leave both open. A restore that needs a fresh process offers **Restart now** or **Later**.
- The engine is headless — it takes an `OutputStream` and a progress callback — so the panel and the
  automation drive one implementation.

### 保存復元 backup automation

The sister-app export contract, so 白い熊 自由作業盤 can back this app up as part of a batch.

- Three actions on one exported receiver with no `android:permission` — the caller cannot hold one,
  so the token is the gate: `EXPORT_STATE`, `LIST_CATEGORIES`, `CANCEL_EXPORT`.
- The receiver does **no work**: `goAsync()` does not extend the broadcast window, and a receiver
  that outstays it is ANR'd and killed mid-export, losing the archive *and* the reply. It gates and
  hands off to a **foreground service** (`dataSync`, partial wakelock, `startForeground` within 5 s).
- **Exactly one terminal reply** per request, `AtomicBoolean`-guarded, as a fresh broadcast with
  `FLAG_INCLUDE_STOPPED_PACKAGES` — never a binder and never the ordered-broadcast result, both of
  which EMUI drops between third-party apps. `automation disabled` and `bad token` are distinct.
- **Progress** carries the category id in `item`, `current`/`total` as the position being written,
  unit `区分`, and real bytes from a counting stream; throttled to 500 ms, each one a heartbeat.
- **Cancel** is fire-and-forget and safe at any time — a silent no-op when nothing is running,
  otherwise a volatile flag the write loop checks between entries, so the export unwinds at a
  boundary and deletes its partial. The panel's own button routes through the same path and reads
  **Stop** while an export runs.
- **Directory precedence**: the `path` extra → the configured SAF folder → `ERROR:no-directory`.
  All-Files-Access is checked rather than discovered by failing, so an absolute path with no grant
  and no configured folder answers exactly `ERROR:no-storage-access`.
- **Token**: 24 `SecureRandom` bytes, hex, generated lazily, compared constant-time, in its own prefs
  file that is never exported. The switch defaults **off**, and both rows sit inside the
  Export/Import section per the contract.

### Android Auto

- A car theme resource makes `CarColor.PRIMARY`/`SECONDARY` the house yellow.
- A runtime colour follows the two Auto slots on the UI page, mirrored into two plain hex DataStore
  keys so `:media3` never has to parse a Compose theme.
- Applied to the search FAB's background and, through a `ForegroundCarColorSpan`, to the now-playing
  row marker — the two places the templates let an app choose a colour. Everything else on an Auto
  screen is the host's, by driver-distraction rule.

### De-branding

- The app names itself **白い熊 音楽乙** across all 27 locales, and every user-visible GitHub link
  points at this fork: the Credit screen's source and issue buttons, the Settings author row, the
  review dialog's star link.
- **Removed** as upstream's own promotion: the blog-promo dialog on the fifth launch, the daily
  blog-RSS notification worker (and any left-over instance is cancelled on start) and its setting,
  the sponsor row, and the ProductHunt and buymeacoffee links.
- **The in-app updater is gone** — three Settings rows and the start-up check. It asks GitHub for the
  newest *upstream* release, and that URL lives in the `core` submodule; it could only ever offer an
  APK that cannot install over ours.
- Auto-backups write to `Download/shiroikuma-ongakuots` as `shiroikuma-ongakuots_backup_*.zip`, so
  they never land among — or get reaped with — the official app's.
- README, store listing and title rebranded; upstream's `FUNDING.yml` removed.
- **Deliberately kept**: the names of maxrave-dev's own external services this app talks to —
  *SimpMusic Lyrics*, *SimpMusic Charts* and the `simpmusic.org` playlist converter. Renaming those
  would misattribute someone else's servers. Upstream's copyright line stays with our fork line
  beneath it, and the Credit screen links to the upstream project.

## Upstream — SimpMusic

Upstream's notes are folded in here on each sync, newest first. At fork time the base was
`upstream/dev` at `9155f673` (2026-08-15 17:42 UTC), one release past **v1.7.0** (versionCode 56,
released 2026-08-07). Upstream's full release history:
<https://github.com/maxrave-dev/SimpMusic/releases>.
