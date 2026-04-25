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

## כללי עבודה
- **ללא CDK** — רק CLI scripts. כל תשתית מוקמת דרך `anchor-backend/scripts/`
- **ה-PRD אינו קדוש** — אם יש פתרון טוב יותר, מיישמים אותו
- החלטות ארכיטקטורה מתועדות ב-`DECISIONS.md` בשורש הפרויקט

## ארכיטקטורה — נקודות מפתח
- **שעון** מזדהה עם API Key (לא JWT) — header: `X-Watch-Key`
- **תרופות** — לא push לשעון, השעון סנכרן לוקאלית
- **Push notifications** — AWS SNS + FCM, לדאשבורד בלבד
- **Daily Report** — חישוב בזמן אמת + OpenAI API לניתוח
- **Watch Pairing** — QR code על השעון, הקשיש סורק מהדאשבורד
- **Family Linking** — הזנת מספר טלפון + אישור הקשיש

## סדר endpoints שנשאר לבנות
1. `/watch/init-pairing`, `/users/{id}/watch/pair`
2. `/users/{id}/family/request`, `/users/{id}/family/approve`
3. `/checkins`, `/medication-reminders`
4. `/users/{id}/reports` (+ OpenAI)
5. `/emergency` (+ FCM push)
