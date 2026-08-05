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
| **Settings** | BYOK API keys for Gemini / Claude / OpenRouter (encrypted on-device), searchable model picker, heatmap width, AI bar toggle, backup & restore — reachable via the ⚙ in the Ask Tend sheet |

**Swipe left/right** anywhere on the four main tabs to switch between them.

## Architecture

- **UI**: single-activity Jetpack Compose, custom components matching the prototype pixel values
  (Space Grotesk display font bundled in `res/font/`)
- **State**: one `MainViewModel` exposing `StateFlow`s; screens are pure functions of state
- **Persistence**: Room (`habits`, `habit_logs`, `tasks`, `plan_blocks`, `notes`), seeded on first
  launch with the prototype's demo data (deterministic LCG history so heatmaps look right)
- **Settings**: Jetpack DataStore; API keys are stored in `EncryptedSharedPreferences`, one per
  provider, so switching providers doesn't lose the other key
- **AI**: "Ask Tend" works offline out of the box. Offline is **not** a local model — it's a
  regex rule engine in `AiProtocol.simulate()`: habit-sounding phrasing becomes a habit, "at 5pm"
  becomes a plan block, anything else becomes a task, and whole-day planning is declined rather
  than faked. Add a key for the real thing.

### Providers (BYOK)

| Provider | Transport | Models |
|---|---|---|
| **Gemini** | REST, `responseMimeType: application/json` | live from `/v1beta/models` |
| **Claude** | official [Anthropic Java SDK](https://github.com/anthropics/anthropic-sdk-java) | live from `/v1/models` |
| **OpenRouter** | OpenAI-shaped REST, `response_format: json_object` | live from `/api/v1/models` |

Whichever provider is selected returns a structured JSON action
(`add_task` / `add_plan` / `add_habit`) that the app applies.

OpenRouter gives one key access to ~340 models from ~58 vendors, including free
ones. Its catalogue is filtered to models that can be pinned to JSON **and**
emit text only (`OpenRouterCatalog`) — the second condition matters, because
Google's Lyria *music* models are free and advertise `response_format`. The
picker shows a shortlist of cheap, well-known models before the full list, and
has a search field.

Model lists are fetched live and cached to DataStore, so the picker opens
instantly on a cold start and refreshes in the background (5-minute TTL). A
failed refresh keeps the last known list rather than emptying the picker.

## Motion & haptics

`ui/motion/` is a small shared layer, not per-screen animation code.

| File | What it holds |
|---|---|
| `TendMotion.kt` | Spring and scale tokens, plus `LocalReduceMotion` |
| `TendHaptics.kt` | `TendHaptic` vocabulary → `HapticFeedbackConstants`, API-gated |
| `MotionModifiers.kt` | `bouncyTap`, `successPop`, `shakeOnError` |
| `TendLottie.kt` | Lottie wrapper with a Compose fallback |
| `CelebrationOverlay.kt` | Full-screen celebration, tap-to-dismiss and self-retiring |

**The feel comes from asymmetric damping.** A control collapses under the
finger with a critically damped spring (instant, no wobble, so the press reads
as physical contact) and springs back with an underdamped one (`dampingRatio
0.42`, so the release rebounds). Duration-based easing can't express that
difference; a spring pair can, in under 250ms.

**Haptics go through `View.performHapticFeedback`**, not `Vibrator`. That needs
no `VIBRATE` permission, the constants are remapped per device by the OEM's
haptic profile, and it respects the system touch-feedback setting for free.
Constants added after `minSdk` 26 (`CONFIRM`/`REJECT` at 30, `TOGGLE_ON`/`OFF`
at 34) are gated and degrade rather than going silent.

> Compose's own `HapticFeedbackType` only exposes `LongPress` and
> `TextHandleMove` on Compose UI 1.7, which this project uses; `Confirm`,
> `Reject` and `SegmentTick` landed in 1.8. On a BOM bump, `TendHaptics.constantFor()`
> is the only thing that needs to change.

Rules of thumb: `tapNoRipple` for any tap (light haptic, no movement),
`bouncyTap` for controls that should move, `TendHaptic.Confirm` only for
actions that actually completed, and Lottie only for celebrations.

Reduced motion is honoured from the platform animator duration scale, so the
accessibility "Remove animations" toggle disables every bounce, pop, shake and
celebration from one place.

Lottie assets live in `res/raw/`. The two shipped files are **generated
placeholders** — replace them with designed animations, keeping the filenames.

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
