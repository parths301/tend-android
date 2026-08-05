# Tend

A personal companion Android app — habit tracking, daily planning, tasks, analytics, and an
AI assistant — implemented in **Kotlin + Jetpack Compose** from the Claude Design prototype
in [`project/Tend.dc.html`](project/Tend.dc.html) (design intent in [`HANDOFF.md`](HANDOFF.md)
and [`chats/chat1.md`](chats/chat1.md)).

## Screens

| Screen | What it does |
|---|---|
| **Today** | Week ring strip, category filter chips, habit cards with pixel heatmaps (Grid) or week rows (Week), tap-to-check with live streaks |
| **Plan** | Day timeline with focus blocks, calendar events, gap markers, and check-offs |
| **Tasks** | Grouped task list (Personal / Work / Health) plus an add-via-AI affordance |
| **Stats** | Stat tiles, dark insights card, 30-day focus-hours trend, per-habit completion bars |
| **Widgets** | Dark home-screen widget mockups: streak, quick-check, heatmap, Deep Work bars |
| **Habit detail** | Month calendar (with month paging), streak/best/rate tiles, notes with add-note input |
| **Ask Tend** | Bottom-sheet AI chat on every main tab — type "add buy groceries at 5pm" and it lands in your plan; suggestion chips for planning and weekly summary |
| **Settings** | BYOK Anthropic API key (encrypted on-device), model picker, heatmap width, AI bar toggle, backup & restore — reachable via the ⚙ in the Ask Tend sheet |

**Swipe left/right** anywhere on the four main tabs to switch between them.

## Architecture

- **UI**: single-activity Jetpack Compose, custom components matching the prototype pixel values
  (Space Grotesk display font bundled in `res/font/`)
- **State**: one `MainViewModel` exposing `StateFlow`s; screens are pure functions of state
- **Persistence**: Room (`habits`, `habit_logs`, `tasks`, `plan_blocks`, `notes`), seeded on first
  launch with the prototype's demo data (deterministic LCG history so heatmaps look right)
- **Settings**: Jetpack DataStore; the Anthropic API key is stored in `EncryptedSharedPreferences`
- **AI**: "Ask Tend" works offline out of the box with a built-in parser (mirrors the prototype's
  scripted assistant). Add your Anthropic API key in Settings to route it through the official
  [Anthropic Java SDK](https://github.com/anthropics/anthropic-sdk-java) — Claude then returns a
  structured JSON action (`add_task` / `add_plan` / `add_habit`) that the app applies. Default
  model: `claude-opus-4-8`. Network failures fall back to the offline parser.

## Backup & restore

Settings → **Backup & restore** writes everything (habits, check-ins, tasks,
plans, notes, settings) as a single self-describing JSON file into a folder you
pick once with the system file picker:

```
tend-backup-2026-08-05-0200.json
```

- **Scheduled** — Off / Daily / Weekly at a time you choose, run by WorkManager,
  keeping the newest N files and pruning the rest.
- **On demand** — "Back up now", "Save a copy…" anywhere, or "Share…".
- **Restore** — pick a file, review its contents, confirm. Replaces everything in
  one transaction; orphaned or duplicate rows are dropped rather than corrupting
  the database.
- **API keys are never written to the file** — they stay in the device keystore,
  and they're excluded from Android's cloud backup too.

Format details live in `data/backup/BackupFormat.kt`; `version` gates forward
compatibility, so a file from a newer build is refused rather than half-read.

## Building

Requires JDK 17+ and the Android SDK (compileSdk 35). Then:

```sh
./gradlew :app:assembleDebug
```

APK lands in `app/build/outputs/apk/debug/`. Min SDK 26 (Android 8.0), target SDK 35.

For a publishable build, copy `keystore.properties.example` → `keystore.properties`
and fill in your upload key; without it the release variant falls back to the
debug key so `assembleRelease` still runs locally and in CI.

> CI: `.github/workflows/android.yml` runs unit tests and lint, then builds the
> debug and release APKs on every push, uploading the debug APK as an artifact.

Everything between here and a Play Store listing is tracked in [`LAUNCH.md`](LAUNCH.md).
