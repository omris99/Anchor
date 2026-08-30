# MVP_TODO — Anchor

פערים לסגירה לפני הצגת הפרויקט בתערוכה, מבוססים על gap analysis מול ה-PRD המעודכן (16.03.2026).
כל ה-Must Have (4.1) ב-PRD בנוי ועובד בפועל. הרשימה הזו מתמקדת במה שנשאר.

---

## PRIORITY 1 — פערים שחוסמים את הדמו בתערוכה

### 1.1 Family Linking — Backend + חיבור ה-UI

**הבעיה:** `LinkManagementScreen.js` בנוי מלא כ-UI אבל מחובר ל-4 TODOs בלבד — שום קריאת backend אמיתית. בלי זה, אי אפשר להדגים "בן משפחה רואה את המבוגר" משני מכשירים אמיתיים — זה חוסם את כל תרחיש הליבה של הדאשבורד המשפחתי.

**מה חסר:**
- `POST /users/{id}/family/request` — בן משפחה שולח בקשת קישור לפי מספר טלפון של המבוגר
- `POST /users/{id}/family/approve` — המבוגר מאשר בקשה ממתינה
- `GET /users/{id}/family/requests` — רשימת בקשות קישור ממתינות (למבוגר)
- `DELETE /users/{id}/family/request/{requestId}` — דחיית בקשה

**Lambdas לכתוב:**
- `anchor-backend/lambdas/family-request/index.js`
- `anchor-backend/lambdas/family-approve/index.js`
- `anchor-backend/lambdas/family-requests-get/index.js`
- `anchor-backend/lambdas/family-request-reject/index.js`

**הערה:** טבלת `Anchor_FamilyMembers` כבר קיימת עם GSI על `elderly_user_id` — אין צורך ביצירת תשתית, רק Lambdas + routes.

**קובץ דאשבורד לחיבור:** `AnchorDashboardApp/ui/Screens/LinkManagementScreen.js` — שורות עם ה-TODOs (28, 45, 51, 108, 129)

---

### 1.2 PreferencesScreen — התחברות אמיתית לבקאנד

**הבעיה:** כל הטוגלים (דיווחים אוטומטיים, מעקב יקיצה, ניטור רפואי, תזכורות שתייה, תזכורות ארוחות) הם local state בלבד — שום דבר לא נשמר בשרת. רענון האפליקציה מאפס הכל. בהדגמה חיה זה ייראה כמו באג.

**מה חסר:**
- `PUT /users/{id}/preferences` — שמירת כל הטוגלים
- `GET /users/{id}/preferences` — טעינה בפתיחת המסך
- `POST /users/{id}/meal-reminders` / `DELETE /users/{id}/meal-reminders/{mealId}`

**Lambda לכתוב:**
- `anchor-backend/lambdas/preferences/index.js` (GET+PUT)
- `anchor-backend/lambdas/meal-reminders/index.js` (POST+DELETE)

**קובץ דאשבורד:** `AnchorDashboardApp/ui/Screens/PreferencesScreen.js` — שורות 86, 92, 132 (TODOs)

---

### 1.3 תזכורות שתייה / ארוחות — קצה לקצה

**הבעיה:** מעבר לשמירת ה-toggle (סעיף 1.2), אין שום מימוש בפועל: לא קיימים מסכי (g) Water Reminder ו-(h) Eating Reminder באפליקציית השעון, ואין לוגיקת תזמון בבקאנד ששולחת FCM silent push לשעון בזמן שנקבע.

**מה לעשות:**
1. Watch: `WaterReminderActivity`/`Screen` + `MealReminderActivity`/`Screen` — בדומה ל-`MedicationActivity` (WakeLock + AlarmManager + full-screen intent)
2. Backend: לוגיקת תזמון (EventBridge Scheduler / cron Lambda) ששולחת FCM `water_reminder` / `meal_reminder` silent push בזמנים שהוגדרו ב-Preferences
3. אישור תגובה חוזר לדאשבורד (בדומה ל-medication confirm)

**קושי:** גבוה יחסית (תשתית תזמון חדשה + 2 מסכי שעון חדשים). אם הזמן קצר — אפשר להדגים רק את ה-UI/toggle מ-1.2 ולציין את המימוש המלא כ-roadmap.

---

## PRIORITY 2 — Should-have משלימים (לא חוסמים דמו, משפרים אותו)

### 2.1 Daily Report עם OpenAI

**מה חסר:**
- `GET /users/{id}/reports` — מחזיר דוח יומי + ניתוח OpenAI

**Lambda לכתוב:**
- `anchor-backend/lambdas/daily-report/index.js`

**הערות:** SSM Parameter לשמירת OpenAI API key. Input: checkin של אותו יום + תרופות + HR/steps. המסך הקיים (`DailyReportsScreen`) כבר מציג נתונים אמיתיים בלי AI — זה תוספת של "תובנות", לא תיקון של משהו שבור.

---

### 2.2 Emergency retry עד אישור (PRD 2.4.6)

**הבעיה:** `emergency/index.js` שולח Expo push חד-פעמי. ה-PRD דורש retry כל דקה עד שבן משפחה אחד לפחות מאשר (`emergency-acknowledge`).

**מה לעשות:** EventBridge Scheduler / Step Function שמפעיל retry loop על alerts עם `status: "pending"`, נעצר כש-status הופך ל-`acknowledged`.

**קבצים:** `anchor-backend/lambdas/emergency/index.js`, `emergency-acknowledge/index.js`

---

### 2.3 Fall grace period — 9s בקוד מול 10s ב-PRD

**תיקון טריוויאלי (שורה אחת):**

`AnchorWatchApp/app/src/main/kotlin/com/anchor/watch/utils/FallDetectionConstants.kt:22`
```kotlin
const val GRACE_PERIOD_MS: Long = 9_000L  →  10_000L
```

---

## PRIORITY 3 — Roadmap (לא לבנות עכשיו, לציין בע"פ בתערוכה)

פיצ'רים Should Have שדורשים עבודת חיישנים/UX משמעותית ולא קריטיים ל-MVP:

- **3.1 זיהוי יקיצה (Wake-up detection)** — accelerometer-based, שדה "יקיצה" במסך הדוחות כרגע הוא שעת ה-check-in בפועל, לא זיהוי תנועה אמיתי
- **3.2 שאלות check-in מורחבות** ("אכלת היום?", "יצאת מהבית?") — כרגע רק אימוג'י מצב כללי
- **3.3 חיזוקי אבטחה נוספים מה-PRD** — MFA/OTP (הוחלט במודע לוותר, Cognito email-based מספיק ל-Must Have 4.1.6), נעילת חשבון אחרי 5 ניסיונות כושלים, session timeout אכיפתי בצד לקוח, audit log לאירועי אימות

---

## סדר ביצוע מוצע

| # | משימה | קושי | דחיפות |
|---|-------|------|---------|
| 1.1 | Family Linking — backend + UI wiring | בינוני-גבוה | **חוסם דמו** |
| 1.2 | PreferencesScreen — שמירה אמיתית | נמוך-בינוני | **חוסם דמו** |
| 1.3 | תזכורות שתייה/ארוחות קצה-לקצה | גבוה | חוסם דמו (אם יש זמן) |
| 2.1 | Daily Report + OpenAI | גבוה | בינונית |
| 2.2 | Emergency retry עד אישור | בינוני | בינונית |
| 2.3 | Fall grace period 9s→10s | טריוויאלי | נמוכה |
| 3.x | Roadmap (יקיצה, extended check-in, security hardening) | — | ציון בע"פ בלבד |

---

## הערה לגבי אבטחה (JWT authorizer / ownership checks)

יש פערי אבטחה פתוחים בבקאנד (API Gateway ללא JWT authorizer, Lambdas ללא ownership check) המתועדים ב-`MAIN_TODO.md` (PRIORITY 1 שם). אלו לא נכללים כאן כי הם לא ספציפיים לדרישות ה-PRD/MVP לתערוכה — אבל שווה לזכור שהם עדיין פתוחים כשעובדים על ה-Lambdas החדשים ברשימה הזו (family linking, preferences) — לכתוב אותם עם ownership check מההתחלה.
