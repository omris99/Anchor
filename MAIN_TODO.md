# MAIN_TODO — Anchor

סדר עדיפויות מלא לסגירת פערים לפני דמו / הגשה.

---

## PRIORITY 1 — אבטחה (חובה לפני כל חשיפה חיצונית)

### 1.1 JWT Authorizer ב-API Gateway

**הבעיה:** `add-new-routes.sh` יוצר routes ללא `--authorizer-id`. כל בקשה עוברת ישירות ל-Lambda — ה-API Gateway לא בודק שום token.

**מה לעשות:**

1. ליצור JWT Authorizer שמצביע על Cognito User Pool:
   ```bash
   aws apigatewayv2 create-authorizer \
     --api-id u7cxnohim6 \
     --authorizer-type JWT \
     --identity-source '$request.header.Authorization' \
     --name cognito-jwt \
     --jwt-configuration \
       Audience=1smq0heh9hmht2tti3rnb4usvi,\
       Issuer=https://cognito-idp.us-east-1.amazonaws.com/us-east-1_KXDRK5VnC \
     --profile anchor --region us-east-1
   ```
2. לעדכן את `add-new-routes.sh` — להוסיף `--authorizer-id $AUTHORIZER_ID` לכל route של הדאשבורד (כל ה-`/users/{id}/...` חוץ מ-`/emergency/{id}/acknowledge` שהוא `AuthorizationType: NONE` בכוונה).
3. לכתוב סקריפט חד-פעמי `scripts/patch-existing-routes-auth.sh` שיעדכן את ה-routes הקיימים (כי הם כבר חיים).

**קבצים:**
- `anchor-backend/scripts/add-new-routes.sh`
- `anchor-backend/scripts/patch-existing-routes-auth.sh` (חדש)

---

### 1.2 אימות ownership בתוך ה-Lambdas

**הבעיה:** גם אחרי 1.1, Lambda שמקבל `GET /users/{id}/health-metrics/latest` לא מוודא שה-`sub` בטוקן שווה ל-`id` ב-path. משתמש מחובר יכול לשאול על userId של אחר.

**מה לעשות:** בכל Lambda של הדאשבורד, להוסיף בתחילת ה-handler:

```js
const tokenSub = event.requestContext?.authorizer?.jwt?.claims?.sub;
if (tokenSub !== userId) return reply(403, { error: "Forbidden" });
```

**Lambdas שצריכים את התיקון:**
- `health-metrics-get/index.js`
- `health-metrics-post/index.js` (רלוונטי רק אם עתידית ידאשבורד יכתוב)
- `medication-reminders-dashboard/index.js`
- `checkins-get/index.js`
- `checkins-request/index.js`
- `user-profile/index.js`
- `user-status/index.js`
- `mobile-fcm-token/index.js`
- `emergency-alerts-get/index.js`

**לא לגעת:**
- `emergency-acknowledge/index.js` — מכוון ללא auth (watch שולח)
- כל lambdas של השעון (X-Watch-Key) — מנגנון נפרד ועובד

---

### 1.3 (Future / Post-Demo) — GSI על watch_api_key

**הבעיה:** `resolveUserIdFromWatchKey()` עושה `ScanCommand` על כל `Anchor_Users` כדי למצוא את ה-user לפי `watch_api_key`. סקאן יקר ולא סקיילבל.

**הפתרון:** להוסיף GSI בשם `watch_api_key-index` עם PK=`watch_api_key` על `Anchor_Users`, ולהחליף ל-`QueryCommand`.

**לא דחוף לדמו** — הטבלה קטנה ורלוונטי רק כשהמספר משתמשים גדל.

---

## PRIORITY 2 — פיצ'רים חסרים

### 2.1 תרופות בדיווח יומי (נשאר מ-prototype_todo)

**הבעיה:** `DailyReportsScreen` מציג `medicationsTaken: []` תמיד — אין fetch.

**מה לעשות:** להוסיף `useEffect` שקורא `GET /users/{userId}/medication-reminders`, מחשב taken/pending לפי היום, ומזין ל-`todayReport`.

**קובץ:** `AnchorDashboardApp/ui/Screens/DailyReportsScreen.js` שורות 64–78

**פרטים מלאים:** `prototype_todo.md` סעיף 1.

---

### 2.2 Family Linking — Backend

**מה חסר:**
- `POST /users/{id}/family/request` — שליחת בקשת קישור לפי מספר טלפון
- `POST /users/{id}/family/approve` — אישור בקשה על-ידי המבוגר

**Lambdas לכתוב:**
- `anchor-backend/lambdas/family-request/index.js`
- `anchor-backend/lambdas/family-approve/index.js`

**סקריפט:** להוסיף routes ל-`add-new-routes.sh` אחרי שה-Lambdas קיימים.

---

### 2.3 Daily Report עם OpenAI

**מה חסר:**
- `GET /users/{id}/reports` — מחזיר דוח יומי + ניתוח OpenAI

**Lambda לכתוב:**
- `anchor-backend/lambdas/daily-report/index.js`

**הערות:** SSM Parameter לשמירת OpenAI API key. Input: checkin של אותו יום + תרופות + HR/steps.

---

## סדר ביצוע מוצע

| # | משימה | קושי | דחיפות |
|---|-------|------|---------|
| 1.1 | JWT Authorizer ב-API GW | נמוך | **קריטי** |
| 1.2 | ownership check בכל Lambda | בינוני | **קריטי** |
| 2.1 | תרופות בדיווח יומי | נמוך | גבוהה |
| 2.2 | Family Linking backend | גבוה | בינונית |
| 2.3 | Daily Report + OpenAI | גבוה | בינונית |
| 1.3 | GSI על watch_api_key | נמוך | נמוכה (post-demo) |
