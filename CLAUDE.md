# Anchor — הנחיות לעבודה עם Claude Code

## מה הפרויקט
מערכת מלאה לניטור מבוגרים, המורכבת משלושה רכיבים:

- **AnchorWatchApp/** — אפליקציית Wear OS (Kotlin), כבר קיימת.  
  רצה על שעון המבוגר. אחראית על: הצגת שעון, check-in יומי, תזכורות תרופות, כפתור SOS, זיהוי נפילות, שידור נתוני בריאות לbackend.  
  **עיצוב**: "כיוון 2 · חם" — רקע תכלת בהיר (`#D6E8F5`), טקסט כהה (`#1C2B3A`), כפתורי ביטול לבנים. Colors ב-`app/src/main/res/values/colors.xml`.

- **Dashboard App** — אפליקציה cross-platform לנייד (React Native), **בפיתוח**.  
  מיועדת לבני משפחה ומטפלים. תאפשר: מעקב אחרי מצב המבוגר בזמן אמת, צפייה בדוחות יומיים ונתוני בריאות, הגדרת תרופות ותזכורות, ניהול קישורים (קישור שעון + קישור בני משפחה), קבלת התראות חירום.  
  פועלת על Android 14+ ו-iOS 18+.

- **anchor-backend/** — AWS Backend (Lambda + API Gateway + DynamoDB + Cognito).  
  משרת את שני הרכיבים האחרים.

## AWS
- **תמיד** להשתמש ב-`--profile anchor` בכל פקודת AWS CLI
- Account: `976586160011`, Region: `us-east-1`
- Credentials: temporary session (voclabs role) — יש לרענן כשיפוגו

## מה כבר בנוי
- DynamoDB: 6 טבלאות (ראה `anchor-backend/scripts/create-tables.sh`)
- Cognito User Pool: `us-east-1_KXDRK5VnC`, Client: `1smq0heh9hmht2tti3rnb4usvi`
  - username: **email** (לא phone), MFA כבוי, אימות דרך אימייל
- API Gateway: `https://u7cxnohim6.execute-api.us-east-1.amazonaws.com` (`api-config.json`)
- Lambda auth endpoints: register ✅, login ✅, confirm ✅, verify-mfa ✅
- Dashboard App:
  - מסכי auth (register, confirm, login) ✅
  - HomeScreen ✅ — ניווט ל-5 מסכים ראשיים + `WellnessStatusCard` + נקודת קישוריות (ירוק = pushToken + watch_paired_at + watch_fcm_registered + mobile_push_registered כולם קיימים; אפור = חסר אחד)
  - MedicationRemindersScreen ✅ — UI מלא + מחובר ל-backend. כל תרופה ברשימה מציגה סטטוס `watch_scheduled` (ירוק "מוגדרת" / כתום "ממתינה") לפי שדה `watch_scheduled_at` מה-API. כולל כפתור רענון ידני.
  - HealthDataScreen ✅ — מציג HR ו-steps בזמן אמת מ-`GET /users/{id}/health-metrics/latest` (עם `useFocusEffect`), fallback למדגם. גרף חודשי + ייצוא PDF (stub)
  - DailyReportsScreen ✅ — דיווח יומי + היסטוריה. מציג: מצב רוח, סוללה, מיקום, תרופות שננטלו/שנותר לנטול (מגיעות מ-`checkin.medications` — snapshot שנשמר בזמן הcheck-in)
  - PreferencesScreen ✅ — toggles, תזכורות מים וארוחות, כפתור התנתקות אדום (logoutUser + Amplify signOut + setUser(null))
  - LinkManagementScreen ✅ — route `connections`, תוכן לפי `user.userType`: מבוגר רואה קישור שעון + אישור בקשות; בן משפחה רואה שליחת בקשה לפי טלפון
  - WatchPairingScreen ✅ — route `watch-pairing`, סורק QR אמיתי (expo-camera), נגיש למבוגר בלבד. ממתין לחיבור backend (`/watch/pair`)

## כללי עבודה
- **ללא CDK** — רק CLI scripts. כל תשתית מוקמת דרך `anchor-backend/scripts/`
- **ה-PRD אינו קדוש** — אם יש פתרון טוב יותר, מיישמים אותו
- החלטות ארכיטקטורה מתועדות ב-`DECISIONS.md` בשורש הפרויקט
- **Tests (Watch App)** — קבצי הtest הועברו מ-`src/test/` ל-`app/tests/unit/` (unit) ו-`app/tests/instrumented/` (instrumented). Gradle מוגדר בהתאם ב-`build.gradle.kts`.

## ארכיטקטורה — נקודות מפתח
- **שעון** מזדהה עם API Key (לא JWT) — header: `X-Watch-Key`
- **watch_name** — נשמר ב-`Anchor_Users`. השעון שולח `device_name` ב-body של `POST /watch/init-pairing` → נשמר בrecord הזמני → מועבר לuser row בpairing. מוחזר מ-`GET /users/{id}/profile`.
- **תרופות** — השעון סנכרן לוקאלית (pull כל 15 דקות) + FCM silent push מיידי: יצירה → `medication_sync`, מחיקה → `medication_delete` עם `med_id` (ביטול אלרם ישיר בלי fetch כל הרשימה). אחרי כל `AlarmManager.setAlarmClock()`, השעון שולח `POST /medication-reminders/{id}/schedule-ack` (fire-and-forget) → מעדכן `watch_scheduled_at` ב-DynamoDB → הדאשבורד מציג נקודה ירוקה. אם ה-ACK נכשל, ה-sync הבא (15 דק') ישלח אותו מחדש.
- **Push notifications** — FCM: שלושה סוגי silent push לשעון: `medication_sync` (סנכרון תרופות אחרי יצירה), `medication_delete` (ביטול אלרם ספציפי אחרי מחיקה — כולל `med_id`), `request_checkin` (פותח `CheckInActivity`). Expo push לדאשבורד: `emergency` (SOS/נפילה), `medication_taken` (אישור נטילת תרופה עם שם התרופה)
- **FCM token של שעון** — נשמר ב-`watch_fcm_token` ב-`Anchor_Users`. נרשם דרך `POST /watch/fcm-token` אחרי pairing
- **CheckInContext** — DTO (lat, lng, batteryPercent) שנשלח עם כל check-in מהשעון. מוסיפים שדות עתידיים רק כאן (לא ב-`CheckInEntity` שב-Room)
- **Location (Watch)** — `utils/LocationProvider.kt` מכיל `suspend fun requestBestLocation(context)` משותף ל-`CheckInActivity` ול-`EmergencyService`. לוגיקה: last-known רענן (< 5 דק') → live fix מכל providers (עד 20 שניות) → null
- **Checkins Lambda** — משתמש ב-`UpdateCommand` (לא `PutCommand`) כדי שretry ריק לא יחליף location אמיתי. שומר snapshot של תרופות (`medications: [{id, name, scheduled_time, status}]`) בזמן הcheck-in עם `if_not_exists` — כך retry לא מחליף את הsnapshot המקורי ברשימה עדכנית
- **Daily Report** — חישוב בזמן אמת + OpenAI API לניתוח
- **Watch Pairing** — QR code על השעון, המבוגר סורק מהדאשבורד
- **Family Linking** — הזנת מספר טלפון + אישור המבוגר
- **HealthMetricsService (Watch)** — `PassiveListenerService` נהרס ונוצר מחדש על-ידי ה-OS בכל delivery. `STEPS_DAILY` הוא delta type — מגיע רק כשיש צעדים חדשים, לא על כל HR delivery. לכן `latestSteps` **לא** יכול להיות instance variable — יש לשמור ב-SharedPreferences (`health_metrics / last_steps`) ולטעון בכל invocation.
- **FallDetectionService — ארכיטקטורה קריטית** — הספירה לאחור (**9 שניות**, `FallDetectionConstants.GRACE_PERIOD_MS`) והפעלת `EmergencyService` חייבות לחיות ב-`FallDetectionService`, לא ב-`FallAlertActivity`. הסיבה: אנדרואיד לא מבטיח פתיחת Activity מ-background. `activeController: FallAlertController?` נחשף כ-`companion object @Volatile` — ה-Activity קורא אותו בponCreate; אם null → מסתיים מיידית. `FallAlertController.start()` הוא idempotent — כשה-Screen קורא `start()` מהצד שלו, הוא לא מאפס ספירה שכבר רצה מהשירות. קורא `EmergencyService.start(ctx, 1, "FALL")` — שולח `type="FALL"` לbackend.
- **EmergencyService.start()** — חתימה: `start(context, graceSeconds, type: String = "SOS")`. ה-type עובר כ-Intent extra (`EXTRA_TYPE`) → `EmergencyOrchestrator.pendingType` → `dispatch()` שולח אותו ב-body ל-`POST /emergency`. `SosScreen` קורא ללא פרמטר שלישי (ברירת מחדל "SOS"). Backend מקבל `"SOS"` ו-`"FALL"` (case-insensitive). **התנהגות אזעקה**: האזעקה (`TYPE_ALARM` + `USAGE_ALARM`) מתחילה בתחילת `ACTION_START` (לאורך כל הספירה) ונעצרת ב-`onCountdownComplete` — כלומר ה-dispatch עצמו שקט. `FallDetectionService` מפעיל אזעקה משלו ברגע הזיהוי ועוצר אותה ב-`acknowledgeAlertHandled()` לפני שקורא ל-`EmergencyService.start()`.
- **Fall Alert Launch — 3 שכבות** — (1) `PowerManager.FULL_WAKE_LOCK or ACQUIRE_CAUSES_WAKEUP` (חובה — בלעדיו כל השאר נכשל בשקט כשהמסך כבוי), (2) `startActivity()` ישיר, (3) `AlarmManager.setAlarmClock()` שעוקף rate-limiting. גם `USE_FULL_SCREEN_INTENT` חובה ב-Manifest. **רטט** מבוצע מה-Service לפני כל הניסיונות — מובטח גם אם Activity לא נפתחת.
- **Audio + Vibration on Wear OS** — כדי שצליל ישמע על Wear OS (כולל כשהשעון ב-Theater Mode / DND), חייבים לצרף `AudioAttributes` עם `setUsage(AudioAttributes.USAGE_ALARM)` לכל `Ringtone`. בלי זה הצליל נחסם בשקט. הפטרן הזה קיים ב-`MedicationAlarmService.playGentleChime()` ו-`CheckInActivity.onCreate()`. **הבחנה חשובה**: SOS/נפילה משתמשים ב-`RingtoneManager.TYPE_ALARM` (צלצול אזעקה חוזר), ואילו תזכורות תרופות משתמשות ב-`TYPE_NOTIFICATION` (צלצול עדין חד-פעמי) — שניהם עם `USAGE_ALARM` ב-`AudioAttributes` כדי לעקוף DND. וויברציה עדינה: `VibrationEffect.createWaveform(longArrayOf(0, 120, 80, 120), -1)` — שתי נקישות קצרות עם pause ביניהן.
- **Cold-Start WakeLock Pattern** — כל הפעלה של Activity מ-BroadcastReceiver או FCM (לא מ-service שרץ כבר) דורשת `ACQUIRE_CAUSES_WAKEUP` **בנקודת הטריגר**: תרופות — ב-`MedicationAlarmReceiver.onReceive()`; check-in — ב-`WatchFcmService.onMessageReceived()`. Firebase מחזיק CPU WakeLock ב-`onMessageReceived()` אך לא מדליק מסך. כל Activity שנפתח מ-background חייב: `FLAG_SHOW_WHEN_LOCKED | FLAG_TURN_SCREEN_ON` לפני `super.onCreate()` + `setShowWhenLocked(true)` + `setTurnScreenOn(true)` + `requestDismissKeyguard()`.
- **MedicationAlarmService — 3 שכבות ב-launchActivity()**: WakeLock + `startActivity()` + `AlarmManager.setAlarmClock(now, pi)` + fullScreenIntent notification. Foreground channel = IMPORTANCE_LOW (`anchor_medication_v2`); Alert channel = IMPORTANCE_HIGH (`anchor_medication_alert_v1`) — `setSilent(true)` על IMPORTANCE_HIGH מבטל fullScreenIntent בשקט, לכן הערוצים חייבים להיות נפרדים.
- **WatchFcmService — request_checkin — 3 שכבות**: (1) `ACQUIRE_CAUSES_WAKEUP` WakeLock + `startActivity()` ישיר (מהיר כשFCM מעניק BAL window), (2) `AlarmManager.setAlarmClock(now, pi)` — עוקף BAL לגמרי, אמין גם ב-cold-start, (3) fullScreenIntent notification ב-channel `anchor_checkin_alert` (IMPORTANCE_HIGH). **שורש הבאג שתוקן**: WatchFcmService אינו foreground service — `startActivity()` לבד נחסם ב-BAL כשהprocess קר. `FallDetectionService` (START_STICKY, נקרא גם ב-`MainActivity.onCreate()`) שומר את הprocess חי ומונע cold-start.
- **Expo Push Token — timing issue** — ה-token נרשם ב-`App.js` רק כשמתחברים (`useEffect` על `user?.userId`). אם SOS נלחץ לפני שהדאשבורד נפתח ונרשם, Lambda `emergency` ימצא `mobile_fcm_token` ריק ב-DynamoDB ו-push לא ישלח. פתרון: לצאת ולהיכנס מחדש לדאשבורד.
- **DynamoDB Limit+FilterExpression** — אל תוסיף `Limit` ל-Scan/Query עם `FilterExpression` — `Limit` מגביל הערכה לא תוצאות (גרם לבאגים ב-`resolveUserIdFromWatchKey` וב-`emergency-acknowledge`)
- **Hebrew localization (Watch)** — תיקיית משאבים: `values-iw/` (לא `values-he/`). `localeFilters += listOf("en", "he", "iw")`. כל Activity מוסיף `attachBaseContext` עם `LocaleHelper.wrapContext(base)`. `DailyCheckInScreen` Row עטוף ב-`CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr)` כדי שסדר הפרצופים לא יתהפך.

## מה כבר בנוי (endpoints)
- Auth: register, login, confirm, verify-mfa ✅
- Watch: init-pairing, pair, credentials, fcm-token ✅
- Checkins: POST /checkins (שומר lat/lng/battery_percent + snapshot תרופות), GET /users/{id}/checkins ✅
- Checkins request: POST /users/{id}/checkins/request (JWT — שולח FCM request_checkin לשעון) ✅
- Medication reminders: GET+POST+DELETE /users/{id}/medication-reminders (dashboard) ✅
- Medication reminders: GET /medication-reminders/{userId}, confirm (שולח Expo push למבוגר ולמשפחה עם שם התרופה), missed, schedule-ack (watch — X-Watch-Key) ✅
- Emergency: POST /emergency (שומר + Expo push למבוגר ולמשפחה), POST /emergency/{id}/acknowledge ✅
  - acknowledge מצפה ל-`user_id` ב-body (לא JWT claims — API GW `AuthorizationType: NONE`)
- Emergency alerts: GET /users/{id}/emergency-alerts (JWT) ✅
- Mobile FCM token: POST /users/{id}/mobile-fcm-token (JWT) — שמירת Expo Push Token של הדאשבורד ✅
- User profile: GET /users/{id}/profile — מחזיר watch_id, watch_name, watch_paired_at, watch_fcm_registered (bool), mobile_push_registered (bool) (לדאשבורד, JWT auth) ✅
- Wellness status: GET /users/{id}/status — מחזיר `{ status: "green"|"yellow"|"red", reason: string }`. לוגיקה: pending emergency → אדום, אין check-in → אדום/צהוב, תרופה missed → צהוב, אחרת ירוק ✅
- Health metrics: POST /health-metrics (X-Watch-Key, כותב ל-`Anchor_BiometricData`), GET /users/{id}/health-metrics/latest (JWT) ✅

## סדר endpoints שנשאר לבנות
1. `/users/{id}/family/request`, `/users/{id}/family/approve`
2. `/users/{id}/reports` (+ OpenAI)

