# Anchor — הנחיות לעבודה עם Claude Code

## מה הפרויקט
מערכת מלאה לניטור קשישים, המורכבת משלושה רכיבים:

- **AnchorWatchApp/** — אפליקציית Wear OS (Kotlin), כבר קיימת.  
  רצה על שעון הקשיש. אחראית על: הצגת שעון, check-in יומי, תזכורות תרופות, כפתור SOS, זיהוי נפילות, שידור נתוני בריאות לbackend.

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
  - HomeScreen ✅ — ניווט ל-5 מסכים ראשיים
  - MedicationRemindersScreen ✅ — UI מלא (שם תרופה, שעה, ימים, רשימה), ממתין לחיבור backend (`/medication-reminders`)
  - HealthDataScreen ✅ — גרף חודשי, ניטור אחרון, מדדים חריגים, ייצוא PDF (stub)
  - DailyReportsScreen ✅ — דיווח יומי + היסטוריה, ממתין לחיבור backend (`/users/{id}/reports`)
  - PreferencesScreen ✅ — toggles, תזכורות מים וארוחות, כפתור התנתקות אדום (logoutUser + Amplify signOut + setUser(null))
  - LinkManagementScreen ✅ — route `connections`, תוכן לפי `user.userType`: קשיש רואה קישור שעון + אישור בקשות; בן משפחה רואה שליחת בקשה לפי טלפון
  - WatchPairingScreen ✅ — route `watch-pairing`, סורק QR אמיתי (expo-camera), נגיש לקשיש בלבד. ממתין לחיבור backend (`/watch/pair`)

## כללי עבודה
- **ללא CDK** — רק CLI scripts. כל תשתית מוקמת דרך `anchor-backend/scripts/`
- **ה-PRD אינו קדוש** — אם יש פתרון טוב יותר, מיישמים אותו
- החלטות ארכיטקטורה מתועדות ב-`DECISIONS.md` בשורש הפרויקט

## ארכיטקטורה — נקודות מפתח
- **שעון** מזדהה עם API Key (לא JWT) — header: `X-Watch-Key`
- **תרופות** — השעון סנכרן לוקאלית (pull כל 15 דקות) + FCM silent push מיידי כשנוצרת תרופה חדשה
- **Push notifications** — FCM: silent push לשעון (medication sync), push רגיל לדאשבורד (emergency)
- **FCM token של שעון** — נשמר ב-`watch_fcm_token` ב-`Anchor_Users`. נרשם דרך `POST /watch/fcm-token` אחרי pairing
- **Daily Report** — חישוב בזמן אמת + OpenAI API לניתוח
- **Watch Pairing** — QR code על השעון, הקשיש סורק מהדאשבורד
- **Family Linking** — הזנת מספר טלפון + אישור הקשיש
- **DynamoDB ScanCommand** — אל תוסיף `Limit` ל-Scan+FilterExpression — `Limit` מגביל הערכה לא תוצאות

## מה כבר בנוי (endpoints)
- Auth: register, login, confirm, verify-mfa ✅
- Watch: init-pairing, pair, credentials, fcm-token ✅
- Checkins: POST /checkins, GET /users/{id}/checkins ✅
- Medication reminders: GET+POST+DELETE /users/{id}/medication-reminders (dashboard) ✅
- Medication reminders: GET /medication-reminders/{userId}, confirm, missed (watch) ✅
- Emergency: POST /emergency, POST /emergency/{id}/acknowledge ✅

## סדר endpoints שנשאר לבנות
1. `/users/{id}/family/request`, `/users/{id}/family/approve`
2. `/users/{id}/reports` (+ OpenAI)
3. FCM push לדאשבורד על emergency
