# Anchor — סיכום החלטות ארכיטקטורה

> נוצר: אפריל 2026  
> מסמך זה מתעד נקודות שהיו עמומות ב-PRD והפתרונות שהוחלט עליהם בפועל.  
> ה-PRD אינו קדוש — אם נמצא לנכון לסטות ממנו, זה מקובל.

---

## 1. מי משתמש ב-auth endpoints?

**עמימות:** ה-PRD לא הבדיל בין auth של הדאשבורד לבין auth של השעון.

**החלטה:**
- Auth (login, register, MFA) — **אפליקציית הדאשבורד בלבד** (בני משפחה + מבוגרים שמתחברים דרך הנייד)
- השעון **לא עובר** flow של username/password — הוא מזדהה עם **API Key** (ראה סעיף 3)

---

## 2. קישור שעון לחשבון מבוגר (Watch Pairing)

**עמימות:** ה-PRD ציין QR code אך לא הסביר מה ה-QR מכיל ומי סורק אותו.

**החלטה:**
- השעון מציג QR code על המסך
- **המבוגר** מתחבר לאפליקציית הדאשבורד בחשבונו ולוחץ "קשר שעון"
- הדאשבורד פותח מצלמה וסורק את ה-QR
- **Flow טכני:**
  1. השעון קורא `POST /watch/init-pairing` ← מקבל pairing token זמני (תוקף 5 דקות)
  2. השעון מציג token זה כ-QR
  3. הדאשבורד סורק ← שולח `POST /users/{id}/watch/pair` עם ה-token
  4. Backend מאמת את ה-token ומקשר בין watch_id לחשבון המבוגר

---

## 3. אימות השעון מול ה-Backend (Watch Auth)

**עמימות:** ה-PRD לא ציין כיצד השעון מזדהה בקריאות API.

**החלטה: API Key**
- בסיום ה-pairing, ה-backend מייצר API Key ייחודי לשעון
- ה-Key נשמר ב-DynamoDB (watch_id → api_key)
- השעון שומר את ה-Key לוקאלית
- **כל בקשה מהשעון** מכילה: `Header: X-Watch-Key: <key>`
- Lambda מאמתת את ה-Key מול DynamoDB
- החלפת שעון = pairing חדש = Key חדש

**למה לא JWT:** דורש refresh tokens ו-session management — מסובך יותר ל-MVP

---

## 4. קישור בני משפחה למבוגר (Family Member Linking)

**עמימות:** ה-PRD הציג שני flow-ים — QR scan ו-הזנת מספר טלפון — בלי להבהיר מה עדיף.

**החלטה:**
- **בני משפחה מתקשרים דרך מספר טלפון**, לא דרך QR
- Flow:
  1. בן משפחה מזין מספר הטלפון של המבוגר בדאשבורד
  2. Backend שולח בקשת קישור למבוגר
  3. **המבוגר מאשר** (או דוחה) דרך אפליקציית הדאשבורד שלו
  4. לאחר אישור — בן המשפחה מקבל גישה לנתונים

---

## 5. Push Notifications

**עמימות:** ה-PRD ציין push notifications בכ-6 מקומות אך לא הגדיר את ה-stack הטכני.

**החלטה: AWS SNS + FCM**
- **לדאשבורד (React Native):** FCM (Firebase Cloud Messaging) — עובד לאנדרואיד ו-iOS
- **לשעון (Wear OS):** אין push — תזכורות מנוהלות לוקאלית (ראה סעיף 6)
- **Flow:**
  1. הדאשבורד רושם FCM token בעת login
  2. Backend שומר token ב-DynamoDB לכל משתמש
  3. Lambda שולחת push דרך AWS SNS ← FCM ← טלפון
- **מתי:** נוסיף לאחר שה-endpoints הבסיסיים יהיו מוכנים (endpoint ה-emergency תלוי בזה)

---

## 6. תזכורות תרופות לשעון

**עמימות:** ה-PRD לא ציין כיצד התזכורות מגיעות לשעון.

**החלטה: סנכרון לוקאלי (לא push)**
- **בני משפחה / מבוגר** יוצרים תזכורות דרך הדאשבורד → נשמרות ב-DynamoDB
- **השעון מסנכרן** את לוח התזכורות מה-backend כשיש חיבור אינטרנט (`GET /users/{id}/medication-reminders`)
- השעון שומר לוקאלית (Room DB) ומציג תזכורות **גם ללא אינטרנט**
- ה-backend **לא** אחראי לתזמון — זה אחריות השעון

---

## 7. Daily Report — מה הוא מכיל ואיך נחשב

**עמימות:** ה-PRD הציג wireframe אך לא הגדיר את מבנה הנתונים שה-backend מחזיר.

**החלטה: חישוב בזמן אמת + ניתוח OpenAI**

`GET /users/{id}/reports` עושה:
1. שולף נתוני היום מ-4 טבלאות: DailyCheckIns, MedicationReminders, BiometricData, Alerts
2. שולח ל-OpenAI API לניתוח דפוסים וחריגות
3. מחזיר JSON מלא לדאשבורד

**תוכן הדוח:**
- זמן ייקיצה
- אימוג'י מצב כללי (שבחר המבוגר)
- תרופות שנלקחו / שלא נלקחו
- נתוני בריאות (דופק, צעדים, שינה)
- אחוז סוללת השעון
- מיקום GPS אחרון
- ניתוח AI (תובנות בעברית)

**אין טבלת reports נפרדת** — הכל נחשב on-demand

---

## 8. סדר פיתוח ה-Endpoints

| שלב | Endpoints | הערות |
|-----|-----------|-------|
| **1 — בוצע** | POST /auth/register, /auth/login, /auth/confirm, /auth/verify-mfa | חי ב-AWS |
| **2 — הבא** | POST /watch/init-pairing, POST /users/{id}/watch/pair | ללא תלויות |
| **2 — הבא** | POST /users/{id}/family/request, POST /users/{id}/family/approve | ללא תלויות |
| **2 — הבא** | POST /checkins, GET/POST /medication-reminders | ללא תלויות |
| **3 — אחר כך** | GET /users/{id}/reports | תלוי OpenAI key |
| **4 — אחרון** | POST /emergency | תלוי push notifications (FCM) |

---

## טכנולוגיות שהוחלט עליהן

| רכיב | טכנולוגיה | סיבה |
|------|-----------|------|
| Auth | AWS Cognito | SMS MFA מובנה |
| DB | DynamoDB | פשוט, חינמי, ללא VPC |
| API | AWS API Gateway HTTP API | פשוט, זול |
| Functions | AWS Lambda (Node.js 18) | Serverless, ללא שרת |
| Push | AWS SNS + FCM | Native ל-AWS |
| AI | OpenAI API | ניתוח נתוני בריאות |
| Watch Auth | API Key | פשוט ל-MVP |
| IaC | CLI Scripts בלבד | ללא CDK, פשוט יותר ל-MVP |
