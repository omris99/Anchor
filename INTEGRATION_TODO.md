# Anchor — תוכנית אינטגרציה שעון ↔ AWS ↔ דאשבורד

> **מטרה:** לחבר את AnchorWatchApp ואת AnchorDashboardApp לאותו AWS backend כך שנתונים מהשעון יופיעו בדאשבורד בזמן אמת.
>
> סטטוס: 🟢 הושלם | 🟡 חלקי | 🔴 חסר
>
> **הערה חשובה על auth:** הלמבדות החדשות מבצעות `X-Watch-Key` auth **ב-inline** (Scan ב-Anchor_Users) — אין צורך ב-Lambda Authorizer נפרד לפאזות הנוכחיות. זו החלטה נכונה ל-MVP.

---

## 🟢 מה כבר בנוי לגמרי

### Backend
| Lambda | Route | Auth | קובץ |
|---|---|---|---|
| `auth-register` | `POST /auth/register` | ללא | `lambdas/auth-register/` |
| `auth-login` | `POST /auth/login` | ללא | `lambdas/auth-login/` |
| `auth-confirm` | `POST /auth/confirm` | ללא | `lambdas/auth-confirm/` |
| `auth-verify-mfa` | `POST /auth/verify-mfa` | ללא | `lambdas/auth-verify-mfa/` |
| `checkins` | `POST /checkins` | X-Watch-Key | `lambdas/checkins/` |
| `emergency` | `POST /emergency` | X-Watch-Key | `lambdas/emergency/` |
| `emergency-acknowledge` | `POST /emergency/{id}/acknowledge` | JWT | `lambdas/emergency-acknowledge/` |
| `medication-reminders-get` | `GET /medication-reminders/{userId}` | X-Watch-Key | `lambdas/medication-reminders-get/` |
| `medication-reminders-confirm` | `POST /medication-reminders/{id}/confirm` | X-Watch-Key | `lambdas/medication-reminders-confirm/` |
| `medication-reminders-missed` | `POST /medication-reminders/{id}/missed` | X-Watch-Key | `lambdas/medication-reminders-missed/` |
| `watch-init-pairing` | `POST /watch/init-pairing` | ללא | `lambdas/watch-init-pairing/` |
| `watch-pair` | `POST /users/{id}/watch/pair` | JWT | `lambdas/watch-pair/` |

### שעון (AnchorWatchApp)
| קומפוננט | מה בנוי |
|---|---|
| `CheckInActivity` + `CheckInSyncWorker` | כבר מחוברים ל-`PartnerApi.checkIn()` — ממתין רק לendpoint |
| `MedicationAlarmService` + `MedicationSyncWorker` | כבר מחוברים ל-`PartnerApi.medication()` |
| `EmergencySyncWorker` | כבר מחובר ל-`PartnerApi.emergency()` |
| `PartnerApiAdapter.kt` | כל ה-DTOs, Retrofit services, WatchKeyStore — מוכן לחלוטין |
| `FallAlertController` | Grace period 10 שניות + `onTrigger` callback — ממתין לחיבור |

### דאשבורד (AnchorDashboardApp)
| קומפוננט | מה בנוי |
|---|---|
| `ApiClient.js` | JWT auth מוכן |
| `WatchPairingScreen` | QR scanner עם `expo-camera` עובד — ממתין לחיבור API |
| כל שאר המסכים | UI מלא עם mock data + TODO comments מסומנים |

---

## 🔴 מה חסר — סדר ביצוע

```
פאזה 0 → עדכון deploy-lambdas.sh + create-api-gateway.sh  ← הדחוף ביותר!
          (ה-8 lambdas החדשות עדיין לא deployed ולא נגישות)

פאזה 1 → Watch Pairing (מסך QR בשעון + חיבור דאשבורד)
          (חוסם הכל — שעון ללא watch_api_key לא יכול לשלוח כלום)

פאזה 2 → חיבור דאשבורד ל-APIs הקיימים
          (WatchPairing, MedicationReminders, DailyReports, Emergency)

פאזה 3 → Family Linking  (lambdas + דאשבורד)

פאזה 4 → Health Data + Daily Reports  (lambdas + שעון + דאשבורד)

פאזה 5 → FCM Push Notifications
```

---

## ✅ פאזה 0 — עדכון סקריפטי Deploy (דחוף — ה-8 lambdas לא deployed עדיין)

> **זה הצעד הראשון.** ה-8 lambdas החדשות קיימות כקוד אבל לא הועלו ל-AWS ולא מחוברות ל-API Gateway.

### 🔴 0.1 `scripts/deploy-lambdas.sh` — הוספת ה-8 lambdas החדשות

**קוד קיים רלוונטי (שורות 52–55 ב-`deploy-lambdas.sh`):**
```bash
# --- Deploy all auth lambdas ---
echo ""
echo "[2/3] Deploying Lambda functions..."
deploy_lambda "auth-register"   "auth-register"
deploy_lambda "auth-login"      "auth-login"
deploy_lambda "auth-confirm"    "auth-confirm"
deploy_lambda "auth-verify-mfa" "auth-verify-mfa"
```

**התיקון — הוספת הלמבדות החדשות:**
```bash
# --- Deploy all auth lambdas ---
echo ""
echo "[2/3] Deploying Lambda functions..."
deploy_lambda "auth-register"   "auth-register"
deploy_lambda "auth-login"      "auth-login"
deploy_lambda "auth-confirm"    "auth-confirm"
deploy_lambda "auth-verify-mfa" "auth-verify-mfa"

# --- Deploy watch + core lambdas ---
deploy_lambda "watch-init-pairing"          "watch-init-pairing"
deploy_lambda "watch-pair"                  "watch-pair"
deploy_lambda "checkins"                    "checkins"
deploy_lambda "medication-reminders-get"    "medication-reminders-get"
deploy_lambda "medication-reminders-confirm" "medication-reminders-confirm"
deploy_lambda "medication-reminders-missed" "medication-reminders-missed"
deploy_lambda "emergency"                   "emergency"
deploy_lambda "emergency-acknowledge"       "emergency-acknowledge"
```

**⚠️ env vars:** הלמבדות החדשות צריכות `USERS_TABLE`, `CHECKINS_TABLE`, `MEDS_TABLE`, `ALERTS_TABLE`. לעדכן את הפונקציה `deploy_lambda` כך שתקבל env vars כארגומנט נוסף, או לעדכן לאחר deploy ב-`update-function-configuration`.

---

### 🔴 0.2 `scripts/create-api-gateway.sh` — הוספת routes לה-8 lambdas החדשות

**קוד קיים רלוונטי (שורות הroutes האחרונות):**
```bash
create_route "auth-register"   "POST" "/auth/register"
create_route "auth-login"      "POST" "/auth/login"
create_route "auth-confirm"    "POST" "/auth/confirm"
create_route "auth-verify-mfa" "POST" "/auth/verify-mfa"
```

**התיקון — הוספת routes:**
```bash
# Pairing routes
create_route "watch-init-pairing" "POST" "/watch/init-pairing"
create_route "watch-pair"         "POST" "/users/{id}/watch/pair"

# Check-in route
create_route "checkins" "POST" "/checkins"

# Medication routes (watch — X-Watch-Key)
create_route "medication-reminders-get"     "GET"  "/medication-reminders/{userId}"
create_route "medication-reminders-confirm" "POST" "/medication-reminders/{id}/confirm"
create_route "medication-reminders-missed"  "POST" "/medication-reminders/{id}/missed"

# Emergency routes
create_route "emergency"             "POST" "/emergency"
create_route "emergency-acknowledge" "POST" "/emergency/{id}/acknowledge"
```

**⚠️ CORS header:** הלמבדות החדשות לא מחזירות `Access-Control-Allow-Origin`. לוודא שה-API Gateway stage מוגדר עם `AllowOrigins='["*"]'` (קיים ב-create-api-gateway.sh) או להוסיף headers לכל lambda response.

---

## ✅ פאזה 1 — Watch Pairing (מסך QR בשעון)

> **חוסם הכל.** ה-backend כבר מוכן (`watch-init-pairing` + `watch-pair`). חסר רק מסך בשעון שיציג QR ויישמור את `watch_api_key` ב-`WatchKeyStore`.

### 🔴 1.1 `AnchorWatchApp` — מסך Pairing חדש (Kotlin/Compose Wear)

**מה קיים ב-`PartnerApiAdapter.kt`:** `PartnerPairingApi.initPairing()` (שורה 217) כבר מממש `POST /watch/init-pairing` ומחזיר `InitPairingResponseDto(pairing_token, expires_at)`. **צריך רק מסך שיקרא לו.**

**קובץ חדש — `screens/WatchPairingScreen.kt`:**
```kotlin
@Composable
fun WatchPairingScreen(
    pairingApi: PartnerPairingApi,
    watchKeyStore: WatchKeyStore,
    onPaired: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf<String?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // שלב 1: קבל pairing_token מהשרת
    LaunchedEffect(Unit) {
        val result = pairingApi.initPairing()
        if (result == null) errorMsg = "שגיאה — נסה שוב"
        else token = result.pairing_token
    }

    // שלב 2: polling — כל 3 שניות בדוק אם כבר נשמר watch_api_key
    LaunchedEffect(token) {
        if (token == null) return@LaunchedEffect
        repeat(100) { // ~5 דקות timeout
            delay(3_000)
            if (watchKeyStore.apiKey() != null) {
                onPaired()
                return@LaunchedEffect
            }
        }
        errorMsg = "פג תוקף — נסה שוב"
    }

    Column(
        Modifier.fillMaxSize().background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "התחברות",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        when {
            errorMsg != null -> Text(errorMsg!!, color = Color.Red, fontSize = 12.sp)
            token != null    -> QrCodeImage(content = token!!, modifier = Modifier.size(140.dp))
            else             -> CircularProgressIndicator(Modifier.size(40.dp))
        }
        token?.let {
            Text("סרוק מהאפליקציה", color = Color.LightGray, fontSize = 11.sp)
        }
    }
}
```

**⚠️ ספריית QR:** צריך להוסיף תלות ל-`build.gradle` — לדוגמה `com.github.alexzhirkevich:compose-qr-code` או `com.google.zxing:core` ליצירת Bitmap.

**⚠️ איפה לקרוא למסך:** מ-`MainActivity` הישן (ב-`presentation/MainActivity.kt`) אם ה-`WatchKeyStore.apiKey()` הוא `null` — הצג `WatchPairingScreen`, אחרת הצג `MainWatchScreen`.

---

### 🔴 1.2 `AnchorWatchApp` — שמירת `watch_api_key` לאחר pairing

**מה עושה `watch-pair`:** מחזיר `{ user_id, watch_id, watch_api_key }` לדאשבורד. **אבל השעון צריך גם לקבל את ה-key הזה** — הדרך הנכונה היא שהשעון יעשה polling ל-`WatchKeyStore` עד שה-key יישמר.

**⚠️ שאלה ארכיטקטורית:** כרגע `watch-pair` מחזיר את ה-`watch_api_key` **לדאשבורד** — אבל השעון לא מקבל אותו ישירות. פתרון:
- **אפשרות א' (מומלץ):** לאחר ש-`watch-pair` שומר ב-DynamoDB, השעון עושה `GET /watch/credentials?token=<pairing_token>` (endpoint חדש קטן) שמחזיר את ה-key.
- **אפשרות ב':** הדאשבורד מחזיר את ה-`watch_api_key` דרך QR code שני שהשעון סורק.

**המלצה: אפשרות א'** — endpoint קטן נוסף:

**קובץ חדש — `lambdas/watch-credentials/index.js`:**
```javascript
// GET /watch/credentials?token=<pairing_token>
// הגע לאחר ש-watch-pair כבר רץ — מחזיר watch_api_key לשעון
exports.handler = async (event) => {
    const token = event.queryStringParameters?.token;
    if (!token) return reply(400, { error: "Missing token" });

    const userResult = await ddb.send(new ScanCommand({
        TableName: USERS_TABLE,
        FilterExpression: "watch_id = :w",
        // watch-init-pairing שמר watch_id ב-pairing record
        // watch-pair העתיק את watch_id לuser row
        ExpressionAttributeValues: { ":w": token }, // פשטות: use watch_id as lookup
    }));
    // ...מחזיר { watch_api_key, user_id }
};
```

---

### 🔴 1.3 `AnchorDashboardApp` — חיבור `WatchPairingScreen` ל-API

**קוד קיים רלוונטי (`WatchPairingScreen.js`, שורות 24–27):**
```javascript
const handleBarcodeScanned = ({ data }) => {
    if (scanned) return;
    setScanned(true);
    // TODO: POST /watch/pair — שליחת watchId לשרת לקישור עם המשתמש הנוכחי
    setPairedWatchId(data);
};
```

**התיקון:**
```javascript
import { apiRequest } from '../../logic/services/api/ApiClient';
import { useContext } from 'react';
import { UserContext } from '../../App';

const { user } = useContext(UserContext);

const handleBarcodeScanned = async ({ data }) => {
    if (scanned) return;
    setScanned(true);
    try {
        await apiRequest(`/users/${user.userId}/watch/pair`, {
            method: 'POST',
            body: JSON.stringify({ pairing_token: data }),
        });
        setPairedWatchId(data);
    } catch (err) {
        Alert.alert('שגיאה', 'לא ניתן לקשר את השעון. ודא שה-QR עדכני ונסה שוב.');
        setScanned(false);
    }
};
```

---

## ✅ פאזה 2 — חיבור דאשבורד ל-APIs הקיימים

> ה-lambdas כבר קיימות ואחרי פאזה 0 הן יהיו live. שלב זה מחבר את המסכים בדאשבורד.

### 🔴 2.1 `MedicationRemindersScreen.js` — טעינה ויצירה ומחיקה

**קוד קיים רלוונטי (`MedicationRemindersScreen.js`, שורה 47):**
```javascript
// TODO: LOAD — טעינת תזכורות קיימות מהשרת בעת כניסה למסך.
// יש לקרוא ל-GET /users/{userId}/medication-reminders ולמלא את reminders בתוצאה.
useEffect(() => {}, []);
```

**⚠️ חסר lambda:** `GET /medication-reminders/{userId}` קיים אבל הוא **X-Watch-Key** protected — לדאשבורד צריך endpoint נפרד עם JWT auth. ראה פאזה 3.3.

**בינתיים** ניתן לחבר GET+POST+DELETE ב-`/users/{id}/medication-reminders` (JWT) — אבל הlambda הזו עדיין לא קיימת. **לפרוס בפאזה 3.3.**

---

### 🔴 2.2 `DailyReportsScreen.js` — טעינת check-ins ותרופות

**קוד קיים רלוונטי (`DailyReportsScreen.js`, שורה 8):**
```javascript
// TODO: LOAD — יש לטעון דיווחים יומיים אמיתיים מהשרת.
// GET /users/{userId}/reports
const MOCK_REPORTS = [...];
```

**⚠️ חסר lambda:** `GET /users/{id}/reports` לא קיים עדיין. **לפרוס בפאזה 4.2.**

**בינתיים** — ניתן לשנות ל-`GET /users/{id}/checkins` שמשאיב ישירות מ-`Anchor_DailyCheckIns`.

---

### 🔴 2.3 `EmergencyEventScreen.js` — אישור התראה

**קוד קיים (`EmergencyEventScreen.js`):** מציג את פרטי הcritical event. לחיצה על "קיבלתי" צריכה לקרוא ל-`POST /emergency/{id}/acknowledge`.

**התיקון — בלחיצה על "קיבלתי":**
```javascript
const handleAcknowledge = async () => {
    try {
        await apiRequest(`/emergency/${alert.id}/acknowledge`, {
            method: 'POST',
            body: JSON.stringify({ user_id: alert.userId, timestamp: alert.timestamp }),
        });
        navigation.goBack();
    } catch (err) {
        Alert.alert('שגיאה', 'לא ניתן לאשר — נסה שוב');
    }
};
```

---

### 🔴 2.4 `EmergencyHistoryScreen.js` — היסטוריית חירום

**⚠️ חסר lambda:** `GET /users/{id}/emergency-history` לא קיים עדיין. **לפרוס בפאזה 4.3.**

---

## ✅ פאזה 3 — Family Linking (lambdas חסרות לחלוטין)

> כל 3 הlambdas הבאות **לא קיימות כלל** ב-`anchor-backend/lambdas/`.

### 🔴 3.1 `lambdas/family-request/index.js` — קובץ חדש

**Route:** `POST /users/{id}/family/request` — JWT auth.
בן משפחה שולח בקשת קישור לפי מספר טלפון של מבוגר. מוצא את המבוגר ב-`Anchor_Users` (GSI על `phone`), יוצר רשומה ב-`Anchor_FamilyMembers` עם `status: "pending"`.

**⚠️ דרישת DynamoDB:** GSI על `Anchor_Users` לפי `phone` — להוסיף ל-`create-tables.sh`.

```javascript
// POST /users/{id}/family/request
exports.handler = async (event) => {
    const requesterId = event?.requestContext?.authorizer?.jwt?.claims?.sub;
    const { phone_number } = JSON.parse(event.body || "{}");

    // מצא מבוגר לפי טלפון
    const elderResult = await ddb.send(new ScanCommand({
        TableName: USERS_TABLE,
        FilterExpression: "phone = :p AND user_type = :t",
        ExpressionAttributeValues: { ":p": phone_number, ":t": "elderly" },
        Limit: 1,
    }));
    if (!elderResult.Items?.length) {
        return reply(404, { error: "Elderly user not found with that phone number" });
    }
    const elderlyUserId = elderResult.Items[0].id;

    const requestId = crypto.randomUUID();
    await ddb.send(new PutCommand({
        TableName: FAMILY_TABLE,
        Item: {
            id: requestId,
            elderly_user_id: elderlyUserId,
            family_member_id: requesterId,
            status: "pending",
            created_at: new Date().toISOString(),
        },
    }));
    return reply(201, { request_id: requestId });
};
```

---

### 🔴 3.2 `lambdas/family-list/index.js` — קובץ חדש

**Route:** `GET /users/{id}/family/requests` — JWT auth.
מבוגר מושך בקשות ממתינות. שאילתה ב-`Anchor_FamilyMembers` לפי `elderly_user_id` (GSI קיים) עם `status = "pending"`.

```javascript
// GET /users/{id}/family/requests
exports.handler = async (event) => {
    const userId = event?.requestContext?.authorizer?.jwt?.claims?.sub;
    const result = await ddb.send(new QueryCommand({
        TableName: FAMILY_TABLE,
        IndexName: "elderly_user_id-index",
        KeyConditionExpression: "elderly_user_id = :u",
        FilterExpression: "#s = :p",
        ExpressionAttributeNames: { "#s": "status" },
        ExpressionAttributeValues: { ":u": userId, ":p": "pending" },
    }));
    return reply(200, { requests: result.Items || [] });
};
```

---

### 🔴 3.3 `lambdas/family-respond/index.js` — קובץ חדש

**Routes:**
- `POST /users/{id}/family/approve/{requestId}` — מאשר, מעדכן `status: "active"`
- `DELETE /users/{id}/family/request/{requestId}` — דוחה, מוחק רשומה

```javascript
exports.handler = async (event) => {
    const method = event.requestContext.http.method;
    const { requestId } = event.pathParameters;

    if (method === "POST") {
        await ddb.send(new UpdateCommand({
            TableName: FAMILY_TABLE,
            Key: { id: requestId },
            UpdateExpression: "SET #s = :s, approved_at = :t",
            ExpressionAttributeNames: { "#s": "status" },
            ExpressionAttributeValues: { ":s": "active", ":t": new Date().toISOString() },
        }));
        return reply(200, { status: "approved" });
    }
    if (method === "DELETE") {
        await ddb.send(new DeleteCommand({
            TableName: FAMILY_TABLE, Key: { id: requestId },
        }));
        return reply(200, { status: "rejected" });
    }
};
```

---

### 🔴 3.4 `lambdas/medication-reminders-dashboard/index.js` — קובץ חדש

**Routes (JWT auth — מהדאשבורד):**
- `GET /users/{id}/medication-reminders` — רשימת תרופות
- `POST /users/{id}/medication-reminders` — הוסף תרופה
- `DELETE /users/{id}/medication-reminders/{medId}` — מחק תרופה

**⚠️ הבדל מ-`medication-reminders-get`:** הlambda הקיימת היא **X-Watch-Key** auth. הדאשבורד זקוק ל-**JWT** auth על אותם נתונים. שתי lambdas נפרדות — אותה טבלה.

---

### 🔴 3.5 `AnchorDashboardApp` — חיבור `LinkManagementScreen`

**קוד קיים רלוונטי (`LinkManagementScreen.js`, שורות 25–102):**
```javascript
const approveRequest = (requestId) => {
    // TODO: POST /users/{userId}/family/approve — אישור בקשת קישור
    setLinkRequests(prev => prev.filter(request => request.id !== requestId));
};
const rejectRequest = (requestId) => {
    // TODO: DELETE /users/{userId}/family/request/{requestId} — דחיית בקשת קישור
    setLinkRequests(prev => prev.filter(request => request.id !== requestId));
};
// שורה 80: TODO: GET /users/{userId}/family/requests — טעינת בקשות קישור ממתינות
const sendLinkRequest = () => {
    // TODO: POST /users/{userId}/family/request — שליחת בקשת קישור למבוגר לפי טלפון
};
```

**התיקון — `ElderlyView`:**
```javascript
useEffect(() => {
    apiRequest(`/users/${user.userId}/family/requests`)
        .then(data => setLinkRequests(data.requests ?? []))
        .catch(() => {});
}, []);

const approveRequest = async (requestId) => {
    await apiRequest(`/users/${user.userId}/family/approve/${requestId}`, { method: 'POST' });
    setLinkRequests(prev => prev.filter(r => r.id !== requestId));
    Alert.alert('אושר', 'הקישור אושר בהצלחה!');
};

const rejectRequest = async (requestId) => {
    await apiRequest(`/users/${user.userId}/family/request/${requestId}`, { method: 'DELETE' });
    setLinkRequests(prev => prev.filter(r => r.id !== requestId));
};
```

**התיקון — `FamilyView`:**
```javascript
const sendLinkRequest = async () => {
    if (!phoneNumber.trim()) return;
    try {
        await apiRequest(`/users/${user.userId}/family/request`, {
            method: 'POST',
            body: JSON.stringify({ phone_number: phoneNumber }),
        });
        Alert.alert('נשלח', 'הבקשה נשלחה — ממתין לאישור המבוגר');
        setPhoneNumber('');
    } catch (err) {
        Alert.alert('שגיאה', err.message);
    }
};
```

---

## ✅ פאזה 4 — Health Data + Daily Reports

### 🔴 4.1 `lambdas/health-data/index.js` — קובץ חדש

**Routes:**
- `POST /health-data` — X-Watch-Key auth, שומר ב-`Anchor_BiometricData`
- `GET /users/{id}/health-data` — JWT auth, מחזיר נתונים לגרף

**שדות ב-body:** `{ measurement_type: "heart_rate"|"steps"|"sleep", value: number, timestamp: number }`

### 🔴 4.2 `AnchorWatchApp` — `HealthDataSyncService` (חדש לגמרי)

**מה עושה:** קורא ל-Android Health Services API, אוסף `heart_rate` + `steps` + `sleep`, ושולח ל-`POST /health-data` כל 15 דקות דרך WorkManager.

**⚠️ זה הרכיב החדש היחיד בשעון שלא קיים כלל.** כל שאר השירותים בשעון כבר בנויים.

```kotlin
class HealthDataSyncWorker(appContext: Context, params: WorkerParameters)
    : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val api = PartnerApi.healthData(applicationContext) // צריך להוסיף ל-PartnerApiAdapter
        val healthClient = HealthServices.getClient(applicationContext).measureClient
        // קריאה ל-Health Services API לצורך heart rate + steps
        // שליחה ל-POST /health-data
        return Result.success()
    }
}
```

### 🔴 4.3 `lambdas/daily-reports/index.js` — קובץ חדש

**Route:** `GET /users/{id}/reports` — JWT auth.
Aggregation של check-ins + תרופות + נתוני בריאות + ניתוח OpenAI.

**⚠️ env var:** `OPENAI_API_KEY` ב-Lambda environment.

**מבנה תשובה:**
```json
{
  "reports": [{
    "date": "2026-05-28",
    "wake_up_time": "07:14",
    "general_feeling_emoji": "🙂",
    "medications_taken": [...],
    "medications_missed": [...],
    "battery_percent": 72,
    "ai_summary": "...",
    "abnormal_metrics": [...]
  }]
}
```

### 🔴 4.4 `lambdas/emergency-history/index.js` — קובץ חדש

**Route:** `GET /users/{id}/emergency-history` — JWT auth. מושך מ-`Anchor_Alerts` עם `is_emergency = true`, ממוין לפי timestamp.

### 🔴 4.5 `AnchorDashboardApp` — חיבור `HealthDataScreen`

**קוד קיים רלוונטי (`HealthDataScreen.js`, שורה 22):**
```javascript
// TODO: LOAD — יש לטעון נתוני בריאות אמיתיים מהשרת.
// GET /users/{userId}/health-data?metric=heartRate&range=30d
const MOCK_DATA = { heartRate: {...}, steps: {...}, sleep: {...} };
```

**התיקון:**
```javascript
const [healthData, setHealthData] = useState(MOCK_DATA); // fallback ל-mock בזמן טעינה

useEffect(() => {
    apiRequest(`/users/${user.userId}/health-data?metric=${selectedMetric}&range=30d`)
        .then(data => setHealthData(prev => ({ ...prev, [selectedMetric]: data })))
        .catch(() => {}); // נשאר עם mock בשגיאה
}, [selectedMetric]);
```

---

## ✅ פאזה 5 — FCM Push Notifications

> ה-lambdas `emergency` ו-`medication-reminders-missed` כבר כוללות `// TODO Phase 5+: FCM fanout`. זה השלב להשלים אותן.

### 🔴 5.1 `scripts/setup-sns-fcm.sh` — סקריפט חדש

**מה עושה:** יצירת SNS Platform Application ל-FCM, שמירת Platform ARN ב-Parameter Store.

```bash
aws sns create-platform-application \
  --name "anchor-fcm" \
  --platform GCM \
  --attributes PlatformCredential="${FCM_SERVER_KEY}" \
  --profile anchor --region us-east-1
```

### 🔴 5.2 `lambdas/fcm-token/index.js` — קובץ חדש

**Route:** `POST /users/me/fcm-token` — JWT auth. שומר FCM device token ב-`Anchor_Users` + יוצר SNS Endpoint ARN.

### 🔴 5.3 `AnchorDashboardApp` — Register FCM Token בעת Login

**היכן לחבר — `App.js` לאחר `setUser` מוצלח:**
```javascript
import messaging from '@react-native-firebase/messaging';

// לאחר login מוצלח
const fcmToken = await messaging().getToken();
await apiRequest('/users/me/fcm-token', {
    method: 'POST',
    body: JSON.stringify({ fcm_token: fcmToken }),
});
```

### 🔴 5.4 עדכון lambdas `emergency` + `medication-reminders-missed`

**קוד קיים רלוונטי (`emergency/index.js`, שורה ~59):**
```javascript
// TODO Phase 5+: FCM multicast to confirmed family device tokens via SNS.
return reply(200, { alertId, status: "pending" });
```

**התיקון — הוספת SNS fanout לפני ה-return:**
```javascript
// שלוף FCM endpoint ARNs של בני משפחה מקושרים
const familyMembers = await ddb.send(new QueryCommand({
    TableName: FAMILY_TABLE,
    IndexName: "elderly_user_id-index",
    KeyConditionExpression: "elderly_user_id = :u",
    FilterExpression: "#s = :a",
    ExpressionAttributeNames: { "#s": "status" },
    ExpressionAttributeValues: { ":u": userId, ":a": "active" },
}));

await Promise.allSettled(
    (familyMembers.Items || [])
        .filter(m => m.sns_endpoint_arn)
        .map(m => sns.send(new PublishCommand({
            TargetArn: m.sns_endpoint_arn,
            Message: JSON.stringify({ alertId, type: normalizedType, userId, timestamp: ts }),
        })))
);
```

---

## סדר עדיפויות ותלויות

| פאזה | תת-סעיף | אומדן | השפעה | סטטוס | תלוי ב |
|---|---|---|---|---|---|
| 0.1–0.2 | עדכון deploy + API Gateway scripts | ~1 שעה | **הכי דחוף** — מעלה 8 lambdas קיימות | 🔴 | אין |
| 1.1–1.2 | מסך Pairing בשעון + watch-credentials | ~3 שעות | חוסם הכל | 🔴 | פאזה 0 |
| 1.3 | דאשבורד — WatchPairingScreen API | ~30 דקות | חוסם הכל | 🔴 | פאזה 0 |
| 2.3 | דאשבורד — Emergency acknowledge | ~30 דקות | safety | 🔴 | פאזה 0 |
| 3.1–3.3 | Family Linking lambdas | ~2.5 שעות | גישה לנתונים | 🔴 | פאזה 0 |
| 3.4 | Medication Dashboard lambda (JWT) | ~1 שעה | UI מסכי תרופות | 🔴 | פאזה 0 |
| 3.5 | דאשבורד — LinkManagement API | ~1 שעה | קישור משפחה | 🔴 | פאזה 3.1–3.3 |
| 2.1 | דאשבורד — MedicationReminders API | ~30 דקות | תרופות בUI | 🔴 | פאזה 3.4 |
| 4.1 | health-data lambda | ~1.5 שעות | גרפים | 🔴 | פאזה 0 |
| 4.2 | HealthDataSyncService בשעון | ~3 שעות | גרפים | 🔴 | פאזה 1 |
| 4.3–4.4 | daily-reports + emergency-history | ~2 שעות | reports + history | 🔴 | פאזה 4.1 |
| 4.5 | דאשבורד — HealthData + Reports API | ~1 שעה | UI מסכים | 🔴 | פאזה 4.3 |
| 5.1–5.4 | FCM Push (SNS + token + fanout) | ~4 שעות | push notifications | 🔴 | פאזה 3 |
