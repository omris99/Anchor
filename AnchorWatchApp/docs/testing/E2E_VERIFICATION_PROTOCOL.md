# Anchor Watch — End-to-End Verification Protocol (Home Laptop)

> **Audience:** Solo developer or QA running the watch app against the live AWS backend from a Windows laptop with Android Studio + an emulator (or a paired physical Wear OS watch over ADB).
>
> **Pre-flight:** the unit test suite (41 active tests) is green AND the instrumented test under `app/src/androidTest/kotlin/com/anchor/watch/exampletests/` passes against a booted Wear OS emulator. Only proceed to manual E2E once both gates are green.
>
> **AWS profile:** every CLI command in this document assumes `--profile anchor` per `CLAUDE.md`. Region is `us-east-1`. API Gateway base URL is `https://u7cxnohim6.execute-api.us-east-1.amazonaws.com` per `anchor-backend/api-config.json`.

---

## 0. Environment checklist (run once per session)

| Check | Command | Expected |
|---|---|---|
| Emulator / watch online | `adb devices` | At least one device listed as `device` (not `unauthorized`) |
| Java is the Studio JBR | `echo $env:JAVA_HOME` (PowerShell) | `C:\Program Files\Android\Android Studio\jbr` |
| Android SDK present | `echo $env:ANDROID_HOME` | `C:\Users\<you>\AppData\Local\Android\Sdk` |
| AWS session is fresh | `aws sts get-caller-identity --profile anchor` | JSON with `Account: 976586160011` and a `voclabs` ARN. **If `ExpiredToken` → refresh credentials before continuing.** |
| DynamoDB tables exist | `aws dynamodb list-tables --profile anchor --region us-east-1` | Includes `Anchor_Users`, `Anchor_Alerts`, `Anchor_DailyCheckIns`, `Anchor_MedicationReminders`, `Anchor_BiometricData`, `Anchor_FamilyMembers` |

If any row fails, stop and fix it before proceeding — E2E findings are meaningless on a broken environment.

---

## 1. Installing the debug build to the device

From the IDE terminal (`Alt + F12`):

```powershell
.\gradlew.bat :app:installDebug
```

Or, from the IDE toolbar:
1. Pick the Wear OS device from the device dropdown (top-right, next to the green ▶).
2. Click ▶ **Run 'app'**.
3. Watch the bottom-right corner for "APK installed in Xs".

> 💡 The current MainActivity (in `src/main/java/com/anchor/anchorwatchapp/presentation/MainActivity.kt`) is still the partner's stub WearApp until Group 6 (wiring) lands. To exercise the transplanted SOURCE screens via the launcher, the MainActivity needs to be updated to host `MainWatchScreen()` — until then, exercise screens via the instrumented test rule (Part 1) or via the explicit `am start` commands below.

To launch SOURCE-side activities directly:
```powershell
# Daily check-in lockscreen activity
adb shell am start -n com.anchor.anchorwatchapp/com.anchor.watch.CheckInActivity

# Medication reminder lockscreen (you'll need an existing medication row in Room — see §6)
adb shell am start -n com.anchor.anchorwatchapp/com.anchor.watch.MedicationActivity --es medication_id "med_001"

# Fall-alert lockscreen activity
adb shell am start -n com.anchor.anchorwatchapp/com.anchor.watch.FallAlertActivity
```

(`applicationId = com.anchor.anchorwatchapp` per `build.gradle.kts`; activity-class paths use the `com.anchor.watch.*` namespace because that's where SOURCE files live.)

---

## 2. Network monitoring — three layers of visibility

### Layer A — Logcat (cheapest, always on)

`PartnerApiAdapter` already wires an OkHttp `HttpLoggingInterceptor` at `Level.BASIC`. Every outbound request prints to Logcat with the `OkHttp` tag.

In Android Studio:
1. Open **View → Tool Windows → Logcat** (`Alt + 6`).
2. In the top filter bar, set:
   - **Device:** your Wear emulator
   - **Process:** `com.anchor.anchorwatchapp` (the applicationId, even though SOURCE namespace is com.anchor.watch)
   - **Filter:** `tag:OkHttp | tag:HttpLoggingInterceptor`
3. Trigger any action (e.g. tap SOS in the instrumented test). You'll see lines like:
   ```
   --> POST https://u7cxnohim6.execute-api.us-east-1.amazonaws.com/emergency
   <-- 200 https://u7cxnohim6.execute-api.us-east-1.amazonaws.com/emergency (143ms, 87-byte body)
   ```

To see **full request bodies** during a debug session, temporarily uncomment the `Level.BODY` line in `PartnerApiAdapter.kt` (do this on a throwaway branch — body logging includes the `X-Watch-Key`, which should never leak to production logs).

### Layer B — Network Inspector (visual, structured)

Android Studio's App Inspection includes a real-time HTTP/HTTPS inspector that does not require any code changes.

1. Open **View → Tool Windows → App Inspection** (the bug icon on the right edge).
2. Pick your device + process.
3. Click the **Network Inspector** tab.
4. Trigger network calls from the watch (instrumented test, manual flow, etc.).
5. Each request appears as a row with method, path, status, latency, request size, response size.
6. Click a row → see request headers (including `X-Watch-Key`), request body, response headers, response body, call stack.

> 💡 OkHttp 4.x + AGP 9 + Wear OS 5 should "just work" with App Inspection. If you see "No data — inspect timeline" indefinitely, restart App Inspection and re-trigger; this is a known intermittent.

### Layer C — mitmproxy / Charles (for SSL pinning bypass / external observation)

We do not currently pin certs on the watch — so a plain MITM proxy works:

```powershell
# install mitmproxy on Windows
choco install mitmproxy

# start interactive mode on port 8080
mitmweb
```

Then on the emulator: **Settings → Connectivity → Wi-Fi → AndroidWifi → Modify Network → Advanced → Proxy → Manual** → Hostname `10.0.2.2` (the emulator's "host machine" alias), Port `8080`.

Install the mitmproxy CA cert on the emulator at first request (visit `mitm.it` from the watch's browser, install the Android cert). After that, every HTTPS call from the watch shows up in the mitmweb UI with full plaintext bodies.

This layer is mostly useful when you need to **inject** fault responses (e.g. simulate a 503 to verify the watch's offline queue), not for everyday observation.

---

## 3. AWS backend verification — did the request actually land?

### 3.1 DynamoDB row appearance

After triggering an SOS (§5.A), confirm `Anchor_Alerts` received a row:

```powershell
aws dynamodb scan `
  --table-name Anchor_Alerts `
  --filter-expression "is_emergency = :ie" `
  --expression-attribute-values '{\":ie\":{\"BOOL\":true}}' `
  --max-items 5 `
  --profile anchor `
  --region us-east-1
```

You're looking for a row whose `event_id` matches the UUID logged by `EmergencyOrchestrator.dispatch()` (search Logcat for `id =` near the time of the click).

For check-ins:
```powershell
aws dynamodb scan `
  --table-name Anchor_DailyCheckIns `
  --max-items 5 `
  --profile anchor `
  --region us-east-1
```

For medication confirmations / misses (they land in `Anchor_Alerts` per DECISIONS.md §8):
```powershell
aws dynamodb scan `
  --table-name Anchor_Alerts `
  --filter-expression "#t IN (:taken, :missed)" `
  --expression-attribute-names '{\"#t\":\"type\"}' `
  --expression-attribute-values '{\":taken\":{\"S\":\"medication_taken\"},\":missed\":{\"S\":\"medication_missed\"}}' `
  --max-items 10 `
  --profile anchor `
  --region us-east-1
```

### 3.2 CloudWatch — did the Lambda execute?

Every Lambda invocation writes to a `/aws/lambda/<function-name>` log group. List the most recent log streams for the SOS lambda:

```powershell
aws logs describe-log-streams `
  --log-group-name "/aws/lambda/anchor-emergency" `
  --order-by LastEventTime --descending --max-items 3 `
  --profile anchor --region us-east-1
```

Tail the latest stream:

```powershell
aws logs tail "/aws/lambda/anchor-emergency" --follow --profile anchor --region us-east-1
```

(Tailing keeps streaming — `Ctrl + C` to stop. Useful: keep this open in a side terminal while triggering events on the watch.)

If a Lambda is missing entirely from CloudWatch, the request **never reached the backend** — start debugging at Layer A/B in §2 above.

### 3.3 API Gateway — did the request even hit the gateway?

If you see the request in Layer B (Network Inspector → 200 OK) but no row in DynamoDB **and** no CloudWatch log entry, the Lambda integration is misconfigured. Check API Gateway:

```powershell
aws apigatewayv2 get-apis --profile anchor --region us-east-1
# find the api-id (it's the prefix of the base URL: u7cxnohim6)

aws apigatewayv2 get-routes --api-id u7cxnohim6 --profile anchor --region us-east-1
# should list: POST /emergency, POST /checkins, GET /medication-reminders/{userId}, etc.
```

If a route is missing entirely, that endpoint hasn't been deployed yet — see `anchor-backend/scripts/create-api-gateway.sh`.

---

## 4. Simulating real-world physical triggers

The Wear OS emulator's **Extended Controls** panel (the `…` icon next to the emulator window, or `Ctrl + Shift + L`) exposes virtual hardware injectors. Each tab below is a real-world trigger you can simulate without leaving your laptop.

### 4.1 Fall detection (Virtual Sensors tab)

`FallDetectionService` listens to `Sensor.TYPE_ACCELEROMETER` and looks for a spike > 2.5 g followed by stillness < 0.3 g inside 2 s. To trigger a fall:

1. Boot the emulator and launch the app.
2. Open Extended Controls → **Virtual Sensors → Accelerometer**.
3. Switch the mode to **Move**.
4. Hold the sliders so X/Y/Z magnitude exceeds 2.5 g for ~50 ms — easiest: drag Z to +25 (m/s², ≈ 2.55 g).
5. Immediately release to (0, 0, 9.8) — that's the rest position (~1 g) and well below the 0.3 g stillness threshold once normalized by `SensorManager.GRAVITY_EARTH`.
6. Within 2 seconds the `FallAlertActivity` should pop up with the 30-second countdown.
7. Pass criterion: the activity launches AND tapping "אני בסדר" cancels it AND Logcat shows `FallDetection: false_positive_cancelled at <timestamp>`.

Alternative (more deterministic): use the `adb sensor` shell:
```powershell
adb shell sensor service status
adb shell sensor service set linear_acceleration 0 0 26  # spike
Start-Sleep -Milliseconds 100
adb shell sensor service set linear_acceleration 0 0 0   # stillness
```

### 4.2 Location (GPS tab)

`EmergencyEventEntity` doesn't currently include lat/lng — but the partner API contract allows it. To verify location pipes through once added:

1. Extended Controls → **Location**.
2. Single point mode → enter Tel Aviv: `32.0853, 34.7818`.
3. Click **Send**.
4. `LocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)` on the watch returns this fix.

### 4.3 Network state — testing the offline queue (Cellular tab)

The most important E2E test for the SOS flow is the offline-then-reconnect cycle:

1. Pre-flight: tail `anchor-emergency` CloudWatch logs (§3.2).
2. Extended Controls → **Cellular → Data status → Denied**.
3. Watch confirms loss of connectivity (in `EmergencyService` the `EmergencySyncWorker` already has `NetworkType.CONNECTED` constraint).
4. Trigger SOS via your launcher path.
5. Logcat: confirm `EmergencyState.Sent(online=false)` is emitted; the `EmergencyLocalStore` should now have a row with `isSynced = 0`.
6. Inspect the queued row via App Inspection → **Database Inspector** → `anchor-watch.db` → `emergency_events` table → SELECT * WHERE isSynced = 0.
7. **Re-enable network:** Extended Controls → Cellular → Data status → **Home**.
8. Within 30 seconds (WorkManager retry window), the row should flip to `isSynced = 1` AND a row should appear in `Anchor_Alerts` in DynamoDB AND `anchor-emergency` CloudWatch should show a fresh invocation.

This single test exercises every layer: Room persistence, WorkManager retry, OkHttp interceptor auth header, API Gateway route, Lambda execution, DynamoDB write.

### 4.4 Battery (Battery tab)

`PROPERTY_SPECIAL_USE` foreground services on Wear OS get killed harder under low battery. To exercise the survival path:

1. Extended Controls → **Battery → Charge level → 10 %**, **Charger connection → None**, **Health → Good**.
2. Trigger a medication alarm (§6).
3. Force-stop the app (`adb shell am force-stop com.anchor.anchorwatchapp`).
4. Wait for the alarm time.
5. **Pass:** the `MedicationActivity` still appears on the lock screen even though the app process was killed (this validates that `MedicationAlarmReceiver` is properly registered in the manifest and survives a process kill).

### 4.5 Doze mode (manual ADB)

```powershell
# Force device into Doze idle
adb shell dumpsys deviceidle force-idle

# Verify state
adb shell dumpsys deviceidle | findstr "mState"
# Expected: mState=IDLE

# Wait for a medication alarm to fire — should still fire because
# MedicationAlarmService.schedule uses setAlarmClock() (Doze-exempt).
# A regression to setExact() would skip Doze. This is your canary.

# Exit Doze
adb shell dumpsys deviceidle unforce
```

### 4.6 Boot survival (manual reboot)

```powershell
# Save Room DB state (medications, queued events) before reboot
adb shell run-as com.anchor.anchorwatchapp ls -la /data/data/com.anchor.anchorwatchapp/databases/

# Reboot the emulator
adb reboot

# Wait for boot complete
adb wait-for-device

# Verify SosReceiver fired on BOOT_COMPLETED
adb logcat -d | findstr "SosReceiver\|EmergencySyncWorker\|MedicationAlarmService"
# Expected: log entries showing re-armed alarms + queued workers
```

The pass criterion is that any scheduled medication alarms set BEFORE reboot still fire at their original time AFTER reboot — `SosReceiver.rearmMedications` is the implementation that makes this work, and this is the only way to verify it.

### 4.7 Locale / RTL flip (manual)

```powershell
# Force English (LTR) to confirm RTL isn't being faked
adb shell setprop persist.sys.locale en-US
adb shell stop
adb shell start
# After restart, the app should STILL be RTL because themes.xml hard-codes
# android:layoutDirection=rtl regardless of system locale.

# Reset to Hebrew
adb shell setprop persist.sys.locale he
adb shell stop
adb shell start
```

---

## 5. End-to-end test scenarios (the gold path)

Each scenario below is a complete script: trigger → observation points → pass criterion. Run all six before any release.

### 5.A SOS happy path (online)

| Step | Action | Observe |
|---|---|---|
| 1 | Tail `/aws/lambda/anchor-emergency` logs (§3.2) | Open stream |
| 2 | Launch app, navigate to SOS (or `adb shell am startservice`) | EmergencyService starts |
| 3 | Wait for 10-second countdown | Logcat: `EmergencyState.CountingDown` 10→1 |
| 4 | Do NOT cancel | After countdown: Logcat `EmergencyState.Dispatching` → `EmergencyState.Sent(online=true)` |
| 5 | Check Network Inspector | `POST /emergency` → 200 in <500 ms |
| 6 | Check DynamoDB `Anchor_Alerts` (§3.1) | New row with `is_emergency=true`, `status="pending"` |
| 7 | Check CloudWatch tail | One INVOCATION line for `anchor-emergency` |

**Pass:** all 7 observations green.

### 5.B SOS offline + reconnect

See §4.3 step-by-step. **Pass:** row appears in DynamoDB after re-enabling network, no rows lost.

### 5.C Daily check-in fires at scheduled time

| Step | Action | Observe |
|---|---|---|
| 1 | Schedule a check-in for `T+2 minutes` via `CheckInSchedulerService(ctx).schedule(LocalTime.of(...))` | Run from a debug shortcut or `adb shell am broadcast` |
| 2 | Lock the watch screen | Display goes off |
| 3 | At T+2, watch should wake | `CheckInActivity` appears with three emojis |
| 4 | Tap 😊 (happy) | Activity closes |
| 5 | Network Inspector | `POST /checkins` with `status=happy` |
| 6 | DynamoDB `Anchor_DailyCheckIns` | New row with `status="happy"` |

### 5.D Medication reminder fires + confirm

Requires a pre-seeded medication. Insert via `adb`:
```powershell
# Open the SQLite database via Database Inspector and INSERT into medications:
# id="med_demo", name="אספירין", scheduledTime="14:30", status="pending", userId="me", isSynced=1
# OR programmatically through a test hook (not implemented yet — flag for follow-up).
```

| Step | Action | Observe |
|---|---|---|
| 1 | Set scheduledTime to current time + 1 min | DB row updated |
| 2 | Call `MedicationAlarmService.schedule(ctx, "med_demo", triggerAtMillis)` | AlarmManager logs the registration |
| 3 | Wait | `MedicationActivity` opens on lockscreen, vibrates |
| 4 | Tap "נטלתי" | Activity closes |
| 5 | Network Inspector | `POST /medication-reminders/med_demo/confirm` → 200 |
| 6 | DynamoDB | `Anchor_Alerts` row with `type=medication_taken` |
| 7 | DB `medications` table | `isSynced=1`, `status="taken"`, `statusTimestamp=<now>` |

### 5.E Fall detection + cancel

| Step | Action | Observe |
|---|---|---|
| 1 | Verify `FallDetectionService` is running | `adb shell dumpsys activity services` includes it as foreground |
| 2 | Spike accelerometer (§4.1) | `FallAlertActivity` opens with 30s countdown |
| 3 | Tap "אני בסדר" within 30s | Activity closes, Logcat: `FallDetection: false_positive_cancelled` |
| 4 | No POST to `/emergency` | Network Inspector: empty |
| 5 | DynamoDB | No new `Anchor_Alerts` row |

**Variant:** do NOT cancel. After 30s the controller triggers `onTrigger` → `EmergencyService.start(ctx, 1)` (1-second grace, since the user already had 30s) → full SOS dispatch as in 5.A.

### 5.F Boot survival

See §4.6. **Pass:** medication alarm scheduled before reboot still fires after reboot at the original time.

---

## 6. Test data seeding helpers

Until the dashboard has CRUD UI for medications, seed test data via the Database Inspector:

1. App Inspection → **Database Inspector**.
2. Select `anchor-watch.db` from the dropdown.
3. Click the `medications` table.
4. Click ⊕ **Insert row**:
   ```
   id = med_demo_001
   name = אספירין
   scheduledTime = 09:00
   status = pending
   userId = me
   isSynced = 1
   statusTimestamp = (null)
   ```
5. Run SQL queries from the "Open New Query Tab" button to inspect / mutate.

---

## 7. Pass/fail gates

For a release candidate to ship:

- [ ] All 41 JVM unit tests pass (`./gradlew :app:testDebugUnitTest`)
- [ ] All instrumented tests in `androidTest/` pass on a Wear OS API 34+ emulator AND on at least one physical watch
- [ ] All 6 E2E scenarios in §5 pass against the production AWS backend
- [ ] DynamoDB rows appear within 5 seconds of each user action (latency budget per TESTING.md)
- [ ] No row remains `isSynced = 0` in the local Room DB after 30 seconds of network availability
- [ ] Logcat shows no uncaught exceptions in the `com.anchor.anchorwatchapp` process during any scenario

---

## 8. Common gotchas

| Symptom | Likely cause | Fix |
|---|---|---|
| Instrumented test fails: `Wear OS feature not supported on this device` | Running against a phone emulator | Use a Wear OS AVD per §1.2 |
| Network Inspector shows nothing | App Inspection lost its connection during APK reinstall | Restart App Inspection panel, re-trigger |
| `aws sts` returns `ExpiredToken` | voclabs sessions are temporary | Refresh the session via the lab console |
| Medication alarm doesn't fire | `SCHEDULE_EXACT_ALARM` permission denied (Wear OS 5+) | Settings → Apps → Anchor → Special permissions → Alarms & reminders |
| `POST /emergency` returns 401 | No `X-Watch-Key` in DataStore yet | Pairing hasn't been run; for testing seed via `WatchKeyStore.savePairingResult(...)` from a debug shortcut |
| DynamoDB `ResourceNotFoundException` | Wrong table name OR wrong region | Confirm `--region us-east-1` and `--profile anchor` |
| HTTPS calls hang on the emulator | No system-time sync after cold boot | `adb shell date -s '@$(date +%s)'` to force a sync |
