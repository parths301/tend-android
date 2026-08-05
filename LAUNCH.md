# Shipping Tend to Google Play

Where the app actually stands, and what is left between here and a store listing.
Split by who can do it: the repo, or you.

---

## 1. The app itself

Every feature from the original design brief is built and wired to real data.

| Area | State |
|---|---|
| Today, Plan, Tasks, Stats, Habit detail | Done — Room-backed, no demo data |
| Home-screen widgets (Today, Streak) | Done — Glance, one-tap check-off |
| Reminders, nightly check-in, boot re-arm | Done — single chained alarm, inexact fallback |
| Calendar read + Auto-plan around events | Done — runtime permission, read-only |
| Ask Tend (BYOK Gemini / Claude / OpenRouter) | Done — offline rules without a key |
| Persisted chat: threads, selection, delete, drafts | Done — Room-backed, survives process death |
| AI / Offline mode toggle, one execution pipeline | Done — `AiExecutionRouter` is the only dispatcher |
| Clickable links to chat-created tasks/habits/plans | Done — stored ids, not text matching |
| Chat sheet ↔ full screen | Done — one implementation, two sizes |
| Encrypted Memory vault + recovery code | Done — PBKDF2/AES-GCM, excluded from AI structurally |
| Attachments in chat and Memory | Done — one shared rendering primitive |
| System status, advanced prompt/JSON, personalities | Done — validated, resettable, both modes |
| Fast time input (type / drag / step) | Done — one component, all five call sites |
| Daily-plan PDF export | Done |
| **Backup & restore (JSON, scheduled)** | **Done — new in 2.1.0, see §2** |
| Release signing config | Done — reads `keystore.properties` |
| Auto-backup exclusion for the encrypted key | Done |
| Unit tests in CI | Done — 92 tests on every push, incl. migration schema diff |

Nothing in the feature brief is outstanding. What remains is release plumbing
and store paperwork.

---

## 2. Backup & restore (new)

Settings → **Backup & restore**.

- **Format** — one JSON file per backup, `tend-backup-YYYY-MM-DD-HHmm.json`.
  Self-describing (`format`, `version`, `counts`), so it reads fine in any text
  editor and validates on the way back in.
- **Contents** — habits, check-ins, tasks, plan blocks, notes, and app settings.
  **API keys are never written**: the file lands in ordinary user storage, and a
  key belongs in the device keystore.
- **Where** — a folder you pick once through the system picker. Tend takes a
  persisted grant for that folder and nothing else, so backups survive reboots
  and outlive an uninstall.
- **Schedule** — Off / Daily / Weekly at a time you choose, run by WorkManager.
  Deferrable work, so it doesn't spend the app's exact-alarm budget. Keeps the
  newest *N* files (default 7) and deletes the rest.
- **Also** — "Save a copy…" to any location, "Share…" through the share sheet.
- **Restore** — pick a file, see what's in it, confirm. It replaces everything
  in a single transaction; a failure part-way leaves the old data intact.
  Orphaned and duplicate rows are dropped rather than corrupting the database.

Status of the last run (success or the actual error) is shown in Settings, so a
silent scheduled failure can't go unnoticed.

---

## 3. Left for you — Play Console

These need a human with an account and a credit card; none of them can be done
from the repo.

1. **Play Console account** — one-time $25. Choose personal vs organisation
   carefully; it determines the testing requirement below.
2. **Closed testing before production.** Personal developer accounts opened
   after Nov 2023 must run a closed test with a minimum number of testers for a
   continuous period before production access is granted. Check the current
   thresholds in the Console — this is usually the longest pole in the whole
   launch, so start it first.
3. **Upload key.** Create it once and never lose it:
   ```sh
   keytool -genkey -v -keystore tend-upload.jks -alias tend \
           -keyalg RSA -keysize 2048 -validity 10000
   ```
   Copy `keystore.properties.example` → `keystore.properties`, fill it in, and
   back up both the `.jks` and the passwords somewhere durable. Losing them
   means never updating `com.tend.app` again. Both files are git-ignored.
4. **Build the bundle** — Play takes an AAB, not an APK:
   ```sh
   ./gradlew bundleRelease   # app/build/outputs/bundle/release/app-release.aab
   ```
5. **Privacy policy URL.** [`PRIVACY.md`](PRIVACY.md) is written and ready —
   publish it somewhere with a stable URL (GitHub Pages works) and paste the
   link into the Console. Required: the app reads the calendar and can send text
   to a third-party AI provider.
6. **Data safety form.** Declare honestly:
   - Collected by the developer: **nothing.** There is no backend and no
     analytics.
   - Shared with third parties: **only if the user adds their own API key** —
     then their typed messages and a summary of their habits/tasks go to Google
     (Gemini), Anthropic (Claude), or OpenRouter. Not shared when no key is set.
   - **OpenRouter needs care on this form.** It is a router: a request goes to
     OpenRouter *and then on* to the company operating the chosen model
     (OpenAI, Google, Mistral, DeepSeek, …). The recipient therefore depends on
     the user's model choice, which is worth saying plainly in the listing
     rather than naming a single vendor.
   - Calendar: read on-device only, never leaves the device.
   - Backups: written to storage the user chooses, never uploaded by Tend.
   - **Memory vault**: encrypted at rest with a key derived from the user's
     password. Declare it as data stored on-device and encrypted; it is never
     transmitted, is excluded from backup files, and the app itself cannot read
     it while locked. Answer "yes" to *Is all user data encrypted in transit?*
     only in respect of the AI calls, which are HTTPS.
   - **Chat history and attachments**: stored on-device. Chat text is included
     in backup files; vault contents are not.
   - **Optional OCR** adds a Google Play services dependency
     (`play-services-mlkit-text-recognition`). It is off by default and the
     model downloads on first use. Recognition runs on-device — no image is
     uploaded — but the *model download* is a network request to Google, so if
     the listing claims "works entirely offline", qualify it.
7. **Content rating questionnaire**, target audience, and ads declaration (no
   ads).
8. **Store listing assets** — the one genuinely missing deliverable:
   - 512×512 icon (PNG, 32-bit)
   - 1024×500 feature graphic
   - 2–8 phone screenshots, min 320px on the short side
   - Short description (80 chars) and full description (4000 chars)
   The in-app adaptive icon exists; the 512×512 listing icon has to be exported
   separately.
9. **Confirm the current target-API requirement.** Tend targets API 35, which
   met the new-app bar as of the 2025 window. Play raises it annually — check
   the Console's requirement before you submit, and bump `targetSdk` if it moved.

---

## 4. Left for you — on a real device

CI proves the app compiles and the pure logic passes. It cannot prove the app
behaves. Before submitting, on a physical phone:

- [ ] Pick a backup folder, **Back up now**, and confirm the file appears in a
      file manager and opens as readable JSON.
- [ ] Turn on Daily, set the time a few minutes out, lock the phone, and confirm
      a file lands. (Doze can defer it — that is expected; check within the hour.)
- [ ] Restore onto a fresh install and confirm streaks, heatmaps and plans match.
- [ ] Revoke access to the backup folder in system settings and confirm Settings
      shows the failure rather than silently doing nothing.
- [ ] Check a habit off from the widget; check reminders fire with and without
      the exact-alarm permission.
- [ ] Add an API key, run Ask Tend and Auto-plan.
- [ ] Rotate the device, go through every tab, back out of Settings and Detail.

### Chat, Memory and settings (new in this release)

**Modes**
- [ ] With no key: the toggle shows AI but the chat says it's running offline,
      and replies are the rule engine's.
- [ ] Add a key, pick AI: replies come from the model and messages carry an
      "AI" tag; offline replies carry "OFFLINE".
- [ ] Switch to Offline with a key present: it stays offline. The choice sticks.

**Threads**
- [ ] New chat, send a message, force-stop the app, reopen — the thread and its
      messages are still there.
- [ ] Create two threads, switch between them, confirm each keeps its own draft.
- [ ] Rename, pin, and search a thread.
- [ ] **Clear chat** empties the thread but keeps it; **delete thread** removes
      it. Confirm they are genuinely different.

**Messages**
- [ ] Long-press a message → delete it. The task it created is still in Tasks.
- [ ] Select several messages, delete in bulk.
- [ ] Long-press → "Add to AI context": the message turns teal and stays that
      way after switching threads and back.
- [ ] In Offline mode, mark a message like "gym every morning", then send
      "add that" — it should create the habit, not a task called "That".

**Linked items**
- [ ] Create a task from chat, tap its chip → the task's editor opens.
- [ ] Rename that task in Tasks, return to the chat, tap the chip again → it
      still opens the right task.
- [ ] Delete the task, tap the chip → a clear "that task has been deleted"
      message, not a blank screen or a crash.

**Sheet ↔ full screen**
- [ ] Type a draft, attach a file, expand to full screen — both survive.
- [ ] Collapse back; back button collapses before it closes.

**Memory**
- [ ] Create a vault; write down the recovery code shown.
- [ ] Lock it, unlock with the password; lock again, unlock with the code.
- [ ] Wrong password shows an error and does not unlock.
- [ ] `add to memory the spare key is with Sam`, then `search memory spare` —
      the reply summarises the hit rather than pasting the contents.
- [ ] With **AI mode on and a key set**, run a memory command and confirm no
      network call is made (airplane mode is the easy check: it still works).
- [ ] Add an image with OCR off, then on, and confirm the toggle changes whether
      text inside the image is findable.
- [ ] Change the password, confirm existing entries still open.

**Settings**
- [ ] Every System Status row matches reality; toggle something and watch it
      change.
- [ ] The API key row never shows any part of the key.
- [ ] Advanced → JSON config: type `{"historyWindow": 999}` and confirm it
      refuses to save with a reason; reset restores the default.
- [ ] Advanced → prompt: save a custom prompt, send a message, reset it.
- [ ] Pick the "Brief" personality and confirm replies shorten **in both modes**.
- [ ] Force-stop and reopen: every setting above survived.

**Time input**
- [ ] Tap the time, type `930`, confirm it becomes 9:30 AM.
- [ ] Type nonsense, confirm the old value stands rather than becoming midnight.
- [ ] Drag the track; confirm haptic ticks and that +/− still work.
- [ ] Check all five places: check-in, backup time, task due, plan start, habit
      reminder.

**Migration (the one that can't be tested in CI)**
- [ ] Install the *previous* release, add a few habits and tasks, then install
      this build over it. The app must open with the old data intact — this is
      the v2→v3→v4 migration path, and it only ever runs on a device that
      already has data.

---

## 5. Deliberately deferred

Not blockers — recorded so they aren't rediscovered as surprises.

- **R8 / resource shrinking is off.** The Anthropic SDK resolves its models
  reflectively and a shrunk build of it can't be verified from CI alone. The
  keeps are already written in `app/proguard-rules.pro`; turning it on is a
  one-line change plus a device smoke test. Costs some APK size until then.
- **No crash reporting.** Nothing phones home, which is a privacy feature, but
  it also means a production crash is invisible. Play Console's own vitals will
  cover the basics.
- **No instrumented (device) tests.** The Compose UI is verified by hand.
- **No first-run onboarding.** The app opens to empty states that explain
  themselves; a guided setup would be an improvement, not a fix.
- **Backups are unencrypted.** That is the point — a readable file that outlives
  the app. If you'd rather have an encrypted option, it's an additive change to
  `BackupFormat`.
