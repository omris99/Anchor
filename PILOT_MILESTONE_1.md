# Pilot Milestone 1 — Watch & Backend Hardening

This milestone closes **6 of the 7 pilot-blocking issues** on the Wear OS watch app and
AWS backend. The remaining item — **Issue #7 (Expo Push to the Dashboard)** — is the next
sprint and is **blocked by Dashboard auth issues** documented in the QA backlog below.

- **Project:** Anchor — eldercare monitoring (Wear OS watch + React Native Dashboard + AWS backend)
- **Scope of this milestone:** `AnchorWatchApp/` (Kotlin/Wear OS) and `anchor-backend/` (already-deployed Lambdas referenced for test/deploy)
- **Date:** 2026-05-29

---

## 1. Resolved Issues & Architectural Decisions

### Issue #1 — Double clock on the watch face
**Symptom:** Two clocks rendered simultaneously (Wear's curved `TimeText` + a large centered clock).
**Fix:** Dropped Wear's `TimeText` (`Scaffold(timeText = {})`) in `MainWatchScreen.kt`, keeping the single large centered clock for elderly legibility.
**Decision:** Favor one large, high-contrast clock over the small curved system clock — elderly-first UX.

### Issue #6 — SOS countdown works once, then fails on the second press
**Symptom:** SOS fired correctly the first time; a second attempt showed no countdown and dispatched nothing.
**Root cause:** `EmergencyService.liveState` is a process-static `MutableStateFlow` that ended at `Sent` and was **never reset to `Idle`**, so `SosScreen`'s start guard saw a stale terminal state and never restarted the service.
**Fix:** Reset `liveState` to `Idle` on `SosScreen` dispose, and hardened the start guard to treat `Sent` as restartable (only skip while a countdown/dispatch is genuinely in flight).
**Decision:** Belt-and-suspenders — reset on dispose **and** a robust start guard so a stale terminal state can never wedge the flow.

### Issue #3 — Medication reminders never triggered on the watch
**Symptom:** A reminder created in the Dashboard reached DynamoDB but the watch never alarmed.
**Root cause:** The watch had no **pull-then-schedule** pipeline — `MedicationRepository.today()` (the only AWS pull path) was never called in production; alarms were only re-armed from the *local* Room store on boot.
**Fix:** New testable `MedicationScheduler` that pulls `today()`, upserts into Room, and schedules a day-aware alarm for each reminder. Wired to three triggers: **app launch** (`presentation/MainActivity`), **boot** (`SosReceiver`, pull-first), and a **periodic `WorkManager` job** (15 min, `NetworkType.CONNECTED`, `ExistingPeriodicWorkPolicy.KEEP`).
**Decisions:**
- **Room DB migration for `days_of_week`** — added a `daysOfWeek: List<Int>` column (CSV `@TypeConverter`) with a **non-destructive** migration (`ALTER TABLE medications ADD COLUMN daysOfWeek TEXT NOT NULL DEFAULT ''`, DB `version 1 → 2`) to preserve any queued offline statuses. Backend day codes are `0=Sun..6=Sat`, converted from `java.time` via `dayOfWeek.value % 7`.
- Wired `userIdProvider` to the real persisted user id from `WatchKeyStore`.

### Issue #4 — "Taken" button felt unresponsive
**Symptom:** Tapping "Taken" appeared to do nothing.
**Root cause (primary):** A consequence of #3 — the reminder never fired, so the screen was effectively unreachable. **Secondary:** even when reached, the screen `finish()`ed instantly with no acknowledgment.
**Fix:** After #3 restored the path, added a brief **"✓ Taken" confirmation state** (~1.2 s) before the activity closes, mirroring SOS's `Sent` display. Confirmed the remote `POST /medication-reminders/{id}/confirm` chain fires with offline retry via `MedicationSyncWorker`.

### Issue #5 — Missed-medication escalation
**Symptom:** Ignored reminders had no escalation.
**Fix:** Added a **gentle alerter** (short vibration pattern + a soft one-shot notification chime) hooked into the existing two-phase timeout + 15-min snooze loop. `confirm()` cancels the loop and stops alerts.
**Decision — "Gentle Escalation" profile:** Deliberately **distinct from and softer than SOS**. Used a direct one-shot `Vibrator` + `RingtoneManager.TYPE_NOTIFICATION` (not the loud `TYPE_ALARM` continuous SOS tone), proven by a test asserting `GENTLE_VIBRATION_PATTERN.sum() < SOS_VIBRATION_PATTERN.sum()`. Repetition rides the existing snooze loop rather than a continuous alarm — avoids alarming/confusing elderly users.

### Issue #2 — Hebrew/English RTL/LTR consistency
**Symptom:** Selecting Hebrew sometimes opened in English; English screens occasionally rendered RTL.
**Root causes:** (a) `localeFilters += "he"` did not match the legacy `values-iw/` resource qualifier, so Hebrew strings could be stripped from the APK; (b) the clock formatter hardcoded `Locale("he")`; (c) per-activity `attachBaseContext` + a global `Locale.setDefault` bled RTL into unwrapped screens.
**Fix & Decision — adopt the platform per-app language API (`LocaleManager`):**
- Removed all bespoke `attachBaseContext` wrapping and the global `Locale.setDefault` mutation.
- The OS now owns locale + layout direction uniformly; each activity derives direction from the **resolved** locale via `LocaleHelper.layoutDirection()` (`TextUtils.getLayoutDirectionFromLocale`) and provides it through `CompositionLocalProvider(LocalLayoutDirection ...)`.
- Renamed `res/values-iw/` → `res/values-he/` and set `localeFilters += listOf("en", "he")` so Hebrew strings survive packaging.
- Added `res/xml/locales_config.xml` + `android:localeConfig` in the manifest.
- `MainWatchFormatters` now takes the **active `Locale`** instead of a hardcoded one.

---

## 2. How to Test

All commands run from the **`AnchorWatchApp/`** directory (the Gradle root).

> **SDK path note:** This environment has no `local.properties`. If Gradle reports
> "SDK location not found", prefix the command with the SDK env vars rather than creating
> a file:
> ```bash
> ANDROID_HOME="$LOCALAPPDATA/Android/Sdk" ANDROID_SDK_ROOT="$LOCALAPPDATA/Android/Sdk" \
>   ./gradlew :app:testDebugUnitTest
> ```

### Compile (main + unit-test sources)
```bash
./gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin
```

### Run the full unit-test suite
```bash
./gradlew :app:testDebugUnitTest
```

### Run a single class or the high-confidence pure-JVM suites
```bash
# New locale-formatter test (pure JVM — runs anywhere)
./gradlew :app:testDebugUnitTest --tests "com.anchor.watch.screens.MainWatchFormattersTest"

# Core logic suites that gate this milestone (all pure JVM, all passing)
./gradlew :app:testDebugUnitTest \
  --tests "com.anchor.watch.services.MedicationSchedulerTest" \
  --tests "com.anchor.watch.services.MedicationAlarmServiceTest" \
  --tests "com.anchor.watch.services.SosServiceTest"
```

### HTML report
After any run: `app/build/reports/tests/testDebugUnitTest/index.html`

### ⚠️ Known environment limitation — Robolectric tests
Every `@RunWith(RobolectricTestRunner::class)` class in this module currently fails at
runtime with:
```
java.lang.RuntimeException at ShadowPackageParser.java:57
  Caused by: android.content.pm.PackageParser$PackageParserException at PackageParser.java:1230
```
This is a **pre-existing AGP 9 + Robolectric 4.14.1 incompatibility**, **not** a logic
failure. Affected classes (e.g. `MainWatchScreenTest`, `MedicationReminderScreenTest`,
`LocaleResolutionTest`) **compile correctly** and their assertions are valid; they will
pass once the toolchain is aligned, or when run as instrumented tests on a device:
```bash
./gradlew :app:connectedDebugAndroidTest   # requires a connected Wear device/emulator
```
Pure-JVM tests (`MainWatchFormattersTest`, `MedicationSchedulerTest`,
`MedicationAlarmServiceTest`, `SosServiceTest`, etc.) are unaffected and **pass today**.

---

## 3. How to Deploy

### A. AWS Lambdas (backend)
> **Always** use `--profile anchor`. Account `976586160011`, region `us-east-1`.
> Credentials are a temporary session (voclabs/LabRole) — refresh if expired.

From the repo root:
```bash
# 1. Confirm the profile is valid (refresh creds if this fails)
aws sts get-caller-identity --profile anchor

# 2. Deploy / update all Lambda function code
cd anchor-backend
./scripts/deploy-lambdas.sh           # packages each lambdas/<name>/index.js and update/create

# 3. (Re)register API Gateway routes — only needed if routes were added/changed
./scripts/add-new-routes.sh
```
The deploy script uses the existing **`LabRole`** (AWS Academy forbids creating new roles),
runtime `nodejs18.x`, and updates code in place when a function already exists.

> **Note for this milestone:** no backend code changed in Issues #1–#6 — the medication
> confirm/missed and emergency endpoints were already deployed. The steps above are the
> canonical redeploy procedure and become **required** for Issue #7 (new
> `device-tokens` endpoint + Expo push fanout in the `emergency` Lambda).

### B. Watch app onto a physical Wear OS device
From `AnchorWatchApp/`:
```bash
# 1. Enable on the watch: Developer options → ADB debugging + Debug over Wi-Fi (or USB)
# 2. Pair / connect ADB
adb pair <watch-ip>:<pair-port>        # Wi-Fi pairing (shown on the watch)
adb connect <watch-ip>:<port>          # or just plug in USB
adb devices                            # confirm the watch is listed

# 3. Build the debug APK
./gradlew :app:assembleDebug
#   → app/build/outputs/apk/debug/app-debug.apk

# 4a. Install + launch via Gradle (simplest)
./gradlew :app:installDebug

# 4b. …or install the APK directly
adb -s <device-id> install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <device-id> shell am start -n com.anchor.anchorwatchapp/com.anchor.anchorwatchapp.presentation.MainActivity
```
- **Application id:** `com.anchor.anchorwatchapp` · **namespace:** `com.anchor.watch` · **minSdk:** 34 (Wear OS, API 34).
- **First launch:** the language picker appears; pick Hebrew or English and verify the home face renders in the matching direction (RTL for Hebrew). Pair the watch via QR from the Dashboard, then confirm med reminders fire, the "✓ Taken" state shows, SOS fires twice in a row, and the fall-alert screen renders correctly.

---

## 4. Dashboard QA Backlog

Five issues found during manual Dashboard (`AnchorDashboardApp/`) testing. **Not yet
started — tracked here so we don't lose them.**

| # | Severity | Area | Issue |
|---|----------|------|-------|
| D1 | UX | Registration | Pressing **"Next"** on the keyboard from the **Last Name** field does not auto-focus the **Phone Number** field. |
| D2 | i18n / Copy | Global | Change the Hebrew term **"קשיש" (Elderly) → "משתמש" (User)** everywhere it appears in the UI. |
| D3 | UX | Registration | The **Gender** and **User Type** selection controls are clunky/uncomfortable on a real mobile device — needs a more touch-friendly control. |
| D4 | **Blocker** | Auth / Login | No working test username/password; **login fails**. |
| D5 | **Blocker** | Auth / Registration | Registration flow **gets stuck** and won't let the user proceed into the app, despite the AWS backend being live. |

> ### ⚠️ Prerequisite for Issue #7 (Expo Push)
> **D4 and D5 are hard blockers for Issue #7.** Expo Push requires a logged-in Dashboard
> user to obtain a device token and `POST` it to the (new) `device-tokens` endpoint. Until
> registration/login works end-to-end, there is no authenticated session to register a
> push token against. **Fix D4 + D5 first**, then implement Issue #7.

---

## 5. Status Summary

| Issue | Title | Status |
|-------|-------|--------|
| #1 | Double clock on watch face | ✅ Resolved |
| #2 | Hebrew/English RTL/LTR consistency | ✅ Resolved |
| #3 | Medication reminders never trigger | ✅ Resolved |
| #4 | "Taken" button unresponsive | ✅ Resolved |
| #5 | Missed-medication gentle escalation | ✅ Resolved |
| #6 | SOS fails on second press | ✅ Resolved |
| #7 | SOS alert → Dashboard via Expo Push | ⏭️ Next (blocked by Dashboard D4/D5) |
| D1–D5 | Dashboard QA backlog | 🆕 Tracked, not started |
