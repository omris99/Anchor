# Anchor — הנחיות לעבודה עם Claude Code

## מה הפרויקט
מערכת מלאה לניטור קשישים, המורכבת משלושה רכיבים:

- **AnchorWatchApp/** — אפליקציית Wear OS (Kotlin), כבר קיימת.  
  רצה על שעון הקשיש. אחראית על: הצגת שעון, check-in יומי, תזכורות תרופות, כפתור SOS, זיהוי נפילות, שידור נתוני בריאות לbackend.  
  **עיצוב**: "כיוון 2 · חם" — רקע תכלת בהיר (`#D6E8F5`), טקסט כהה (`#1C2B3A`), כפתורי ביטול לבנים. Colors ב-`app/src/main/res/values/colors.xml`.

- **Dashboard App** — אפליקציה cross-platform לנייד (React Native), **בפיתוח**.  
  מיועדת לבני משפחה ומטפלים. תאפשר: מעקב אחרי מצב הקשיש בזמן אמת, צפייה בדוחות יומיים ונתוני בריאות, הגדרת תרופות ותזכורות, ניהול קישורים (קישור שעון + קישור בני משפחה), קבלת התראות חירום.  
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
  - HomeScreen ✅ — ניווט ל-5 מסכים ראשיים + `WellnessStatusCard` (נקודה פועמת ירוק/צהוב/אדום, מתרענן עם `useFocusEffect`)
  - MedicationRemindersScreen ✅ — UI מלא (שם תרופה, שעה, ימים, רשימה), ממתין לחיבור backend (`/medication-reminders`)
  - HealthDataScreen ✅ — גרף חודשי, ניטור אחרון, מדדים חריגים, ייצוא PDF (stub)
  - DailyReportsScreen ✅ — דיווח יומי + היסטוריה. מציג: מצב רוח, סוללה, מיקום, תרופות שננטלו/שנותר לנטול (מגיעות מ-`checkin.medications` — snapshot שנשמר בזמן הcheck-in)
  - PreferencesScreen ✅ — toggles, תזכורות מים וארוחות, כפתור התנתקות אדום (logoutUser + Amplify signOut + setUser(null))
  - LinkManagementScreen ✅ — route `connections`, תוכן לפי `user.userType`: קשיש רואה קישור שעון + אישור בקשות; בן משפחה רואה שליחת בקשה לפי טלפון
  - WatchPairingScreen ✅ — route `watch-pairing`, סורק QR אמיתי (expo-camera), נגיש לקשיש בלבד. ממתין לחיבור backend (`/watch/pair`)

## כללי עבודה
- **ללא CDK** — רק CLI scripts. כל תשתית מוקמת דרך `anchor-backend/scripts/`
- **ה-PRD אינו קדוש** — אם יש פתרון טוב יותר, מיישמים אותו
- החלטות ארכיטקטורה מתועדות ב-`DECISIONS.md` בשורש הפרויקט
- **Tests (Watch App)** — קבצי הtest הועברו מ-`src/test/` ל-`app/tests/unit/` (unit) ו-`app/tests/instrumented/` (instrumented). Gradle מוגדר בהתאם ב-`build.gradle.kts`.

## ארכיטקטורה — נקודות מפתח
- **שעון** מזדהה עם API Key (לא JWT) — header: `X-Watch-Key`
- **watch_name** — נשמר ב-`Anchor_Users`. השעון שולח `device_name` ב-body של `POST /watch/init-pairing` → נשמר בrecord הזמני → מועבר לuser row בpairing. מוחזר מ-`GET /users/{id}/profile`.
- **תרופות** — השעון סנכרן לוקאלית (pull כל 15 דקות) + FCM silent push מיידי כשנוצרת תרופה חדשה
- **Push notifications** — FCM: שני סוגי silent push לשעון: `medication_sync` (סנכרון תרופות), `request_checkin` (פותח `CheckInActivity`). Expo push לדאשבורד: `emergency` (SOS/נפילה), `medication_taken` (אישור נטילת תרופה עם שם התרופה)
- **FCM token של שעון** — נשמר ב-`watch_fcm_token` ב-`Anchor_Users`. נרשם דרך `POST /watch/fcm-token` אחרי pairing
- **CheckInContext** — DTO (lat, lng, batteryPercent) שנשלח עם כל check-in מהשעון. מוסיפים שדות עתידיים רק כאן (לא ב-`CheckInEntity` שב-Room)
- **Location (Watch)** — `utils/LocationProvider.kt` מכיל `suspend fun requestBestLocation(context)` משותף ל-`CheckInActivity` ול-`EmergencyService`. לוגיקה: last-known רענן (< 5 דק') → live fix מכל providers (עד 20 שניות) → null
- **Checkins Lambda** — משתמש ב-`UpdateCommand` (לא `PutCommand`) כדי שretry ריק לא יחליף location אמיתי. שומר snapshot של תרופות (`medications: [{id, name, scheduled_time, status}]`) בזמן הcheck-in עם `if_not_exists` — כך retry לא מחליף את הsnapshot המקורי ברשימה עדכנית
- **Daily Report** — חישוב בזמן אמת + OpenAI API לניתוח
- **Watch Pairing** — QR code על השעון, הקשיש סורק מהדאשבורד
- **Family Linking** — הזנת מספר טלפון + אישור הקשיש
- **DynamoDB Limit+FilterExpression** — אל תוסיף `Limit` ל-Scan/Query עם `FilterExpression` — `Limit` מגביל הערכה לא תוצאות (גרם לבאגים ב-`resolveUserIdFromWatchKey` וב-`emergency-acknowledge`)
- **Hebrew localization (Watch)** — תיקיית משאבים: `values-iw/` (לא `values-he/`). `localeFilters += listOf("en", "he", "iw")`. כל Activity מוסיף `attachBaseContext` עם `LocaleHelper.wrapContext(base)`. `DailyCheckInScreen` Row עטוף ב-`CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr)` כדי שסדר הפרצופים לא יתהפך.

## מה כבר בנוי (endpoints)
- Auth: register, login, confirm, verify-mfa ✅
- Watch: init-pairing, pair, credentials, fcm-token ✅
- Checkins: POST /checkins (שומר lat/lng/battery_percent + snapshot תרופות), GET /users/{id}/checkins ✅
- Checkins request: POST /users/{id}/checkins/request (JWT — שולח FCM request_checkin לשעון) ✅
- Medication reminders: GET+POST+DELETE /users/{id}/medication-reminders (dashboard) ✅
- Medication reminders: GET /medication-reminders/{userId}, confirm (שולח Expo push לקשיש ולמשפחה עם שם התרופה), missed (watch) ✅
- Emergency: POST /emergency (שומר + Expo push לקשיש ולמשפחה), POST /emergency/{id}/acknowledge ✅
  - acknowledge מצפה ל-`user_id` ב-body (לא JWT claims — API GW `AuthorizationType: NONE`)
- Emergency alerts: GET /users/{id}/emergency-alerts (JWT) ✅
- Mobile FCM token: POST /users/{id}/mobile-fcm-token (JWT) — שמירת Expo Push Token של הדאשבורד ✅
- User profile: GET /users/{id}/profile — מחזיר watch_id, watch_name, watch_paired_at (לדאשבורד, JWT auth) ✅
- Wellness status: GET /users/{id}/status — מחזיר `{ status: "green"|"yellow"|"red", reason: string }`. לוגיקה: pending emergency → אדום, אין check-in → אדום/צהוב, תרופה missed → צהוב, אחרת ירוק ✅

## סדר endpoints שנשאר לבנות
1. `/users/{id}/family/request`, `/users/{id}/family/approve`
2. `/users/{id}/reports` (+ OpenAI)

