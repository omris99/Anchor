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

### 1.4 קישור שעון אחר — אישור התנתקות + חזרה למסך ברקוד

**הבעיה:** לחיצה על "קשר שעון אחר" (`LinkManagementScreen.js:97`) עוברת ישירות למסך `watch-pairing` בלי שום אישור, ואין שום מנגנון backend/watch שמנתק בפועל את השעון הקודם. השעון בוחר בין מסך ראשי למסך קישור **פעם אחת בלבד, ב-cold start**, לפי מפתח מקומי (`MainActivity.kt:149-150` — `hasKey = WatchKeyStore.get(...).apiKey() != null`). כלומר גם אם ננתק את השעון בצד השרת, השעון עצמו לא ידע להציג שוב את הברקוד עד שהאפליקציה תופעל מחדש.

**מה לעשות:**
1. **Dashboard:** לפני הניווט ל-`watch-pairing` (כש-`user?.watchId` קיים), להציג `Alert` עם אישור: *"האם אתה בטוח שברצונך להתנתק מהשעון הנוכחי ולקשר שעון חדש?"* — רק באישור לנווט הלאה.
2. **Backend:** endpoint חדש `POST /users/{id}/watch/unpair` — מנקה `watch_id` / `watch_api_key` / `watch_fcm_token` מרשומת המבוגר ב-`Anchor_Users`.
3. **Backend → Watch:** לפני הניקוי, לשלוח FCM silent push מסוג חדש (`watch_unpair`) ל-`watch_fcm_token` הנוכחי, כדי שהשעון ינקה את המפתח ויעבור למסך קישור **בזמן ריצה** — לא רק ב-cold start.
4. **Watch:** ב-`WatchFcmService` להוסיף טיפול ב-`watch_unpair` — `WatchKeyStore.clear()` + מעבר ל-`Screen.Pairing`. אם ה-Activity לא בחזית, נדרש מנגנון דומה לזה שכבר קיים ל-`request_checkin` (cold-start wake pattern) כדי שהשינוי יתפוס גם כשהאפליקציה ברקע.

**קבצים:**
- Dashboard: `AnchorDashboardApp/ui/Screens/LinkManagementScreen.js`
- Backend: `anchor-backend/lambdas/watch-unpair/index.js` (חדש)
- Watch: `WatchFcmService.kt`, `WatchKeyStore.kt`, `MainActivity.kt`

**קושי:** בינוני-גבוה — נוגע בשלושת הרכיבים, והחלק הלא-טריוויאלי הוא שהשעון היום בודק מצב קישור רק ב-cold start ולא באופן חי.

---

### 1.5 חיבור OpenAI API Key — ניתוח מצב הקשיש לנקודה הצבעונית במסך הבית ✅

**הבעיה:** `anchor-backend/lambdas/user-status/index.js` (שמחזיר את ה-`status`/`reason` שמניע את הנקודה הצבעונית ב-`WellnessStatusCard` בדאשבורד) מבוסס כרגע **רק** על כללים דטרמיניסטיים קשיחים (חירום פעילה, אין check-in, תרופה שפוספסה, מצב רוח עצוב) — אין קריאה ל-OpenAI בשום מקום בקוד היום.

**מה לעשות:**
1. לשמור OpenAI API key ב-SSM Parameter Store (משותף עם המשימה הקיימת 2.1 — Daily Report + OpenAI, אותו secret, אותו helper).
2. להרחיב את `user-status/index.js`: אחרי איסוף הנתונים (check-in אחרון, תרופות, HR/steps, מצב רוח), לשלוח ל-OpenAI תקציר ולבקש הערכה — האם הדפוס נראה תקין או מדאיג.
3. לשלב את תשובת ה-AI כשכבה **נוספת** מעל הכללים הקיימים, לא כתחליף — אם הקריאה ל-OpenAI נכשלת/timeout, ליפול חזרה ללוגיקה הדטרמיניסטית הקיימת (חשוב לדמו חי — לא לתלות את הנקודה הצבעונית לגמרי בזמינות API חיצוני).
4. להחזיר את נימוק ה-AI בתוך שדה `reason` הקיים כדי שהוא יוצג ב-`WellnessStatusCard` בלי צורך בשינוי UI.

**קבצים:** `anchor-backend/lambdas/user-status/index.js` + סקריפט הגדרת SSM parameter

**הערה:** משתף תשתית עם 2.1 — כדאי לבנות helper אחד ל"קריאה ל-OpenAI עם מפתח מ-SSM" ולהשתמש בו בשני ה-Lambdas.

**קושי:** בינוני — בעיקר backend, סיכון UI נמוך כי `WellnessStatusCard` כבר מציג כל `status`+`reason` שה-endpoint מחזיר.

**סטטוס:** בוצע ונפרס, אחרי שתי איטרציות תיקון על בסיס בדיקה אמיתית:
1. **גרסה ראשונה** נתנה ל-OpenAI לכתוב את מחרוזת ה-`reason` כטקסט חופשי — בבדיקה אמיתית זה יצא לא הגיוני (המודל כתב "הקשיש בדיכאון" כש-mood בפועל היה "happy").
2. **גרסה שנייה** תיקנה את זה חלקית — `reason` חזר להיות מחרוזת קבועה, אבל עדיין השתנתה לפי איזה כלל הפעיל אותה (6 גרסאות שונות).
3. **גרסה סופית**: `reason` הוא **אחת משלוש מחרוזות קבועות בלבד**, קבוע לפי `status` בדיוק (`STATUS_CAPTIONS` בקוד): ירוק="הכל תקין", צהוב="יש כמה דברים לבדוק", אדום="מצב חירום — נדרשת תשומת לב מיידית". כל הפירוט הספציפי (איזה כלל/concern בדיוק) עבר לשדה חדש `concerns: string[]` — רשימת "קניות" קבועה (`CONCERN_CATALOG` בקוד) שהדאשבורד מציג ב-modal שנפתח בלחיצה על הנקודה (`WellnessStatusCard.js`).

ה-AI (`gpt-4.1-mini` — הפרויקט ב-OpenAI לא היה לו גישה ל-`gpt-4o-mini`/`gpt-3.5-turbo`) מוגבל לסיווג סגור: מקבל רק 2 קטגוריות מוגדרות מראש (`low_activity`, `no_recent_heart_rate`) ומחזיר רק מזהי קטגוריה מהרשימה — לעולם לא פרוזה חופשית, ותוצאה חיובית מוסיפה פריט ל-`concerns` בלבד (אף פעם לא ל-`reason`). שאר ה-concerns (תרופה שפוספסה, אין check-in היום, מצב רוח עצוב, חירום, אין check-in 48ש) נשארים לגמרי דטרמיניסטיים כמו לפני חיבור ה-AI. מפתח OpenAI נשמר ב-SSM (`/anchor/openai-api-key`, דרך `scripts/setup-openai-param.sh`). טיימאאוט הLambda הועלה מ-3 שניות (ברירת מחדל ישנה) ל-15 שניות. נבדק end-to-end מול משתמש אמיתי ב-DynamoDB (כולל קריאות חוזרות לוודא יציבות) — עבד כצפוי.

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
| 1.4 | קישור שעון אחר — אישור + חזרה לברקוד | בינוני-גבוה | חוסם דמו |
| 1.5 | OpenAI לנקודה הצבעונית (user-status) | בינוני | ✅ בוצע |
| 2.1 | Daily Report + OpenAI | גבוה | בינונית |
| 2.2 | Emergency retry עד אישור | בינוני | בינונית |
| 2.3 | Fall grace period 9s→10s | טריוויאלי | נמוכה |
| 3.x | Roadmap (יקיצה, extended check-in, security hardening) | — | ציון בע"פ בלבד |

---

## הערה לגבי אבטחה (JWT authorizer / ownership checks)

יש פערי אבטחה פתוחים בבקאנד (API Gateway ללא JWT authorizer, Lambdas ללא ownership check) המתועדים ב-`MAIN_TODO.md` (PRIORITY 1 שם). אלו לא נכללים כאן כי הם לא ספציפיים לדרישות ה-PRD/MVP לתערוכה — אבל שווה לזכור שהם עדיין פתוחים כשעובדים על ה-Lambdas החדשים ברשימה הזו (family linking, preferences) — לכתוב אותם עם ownership check מההתחלה.
