# Prototype TODO

משימות שנותרו לפני שהפרוטוטייפ מוכן להדגמה.

---

## 1. תרופות בדיווח היומי

**בעיה:** הדיווח היומי מציג רק מיקום, סוללה, ומצב רוח. תרופות שננטלו/שנותר לנטול לא מגיעות.

**אין צורך ב-endpoint חדש.** הלוגיקה כולה ב-dashboard בלבד:

### איך זה עובד
- השעון קורא `POST /medication-reminders/{id}/confirm` כשנלחץ "taken" → שומר `status="taken"` + `status_timestamp` (ms) על הרשומה.
- `GET /users/{id}/medication-reminders` (dashboard endpoint, כבר קיים) מחזיר את כל התרופות כולל `status`, `status_timestamp`, `scheduled_time`, `days_of_week`.

### חישוב בדאשבורד
עבור כל תרופה:
- **נלקחה היום**: `status === "taken"` AND `status_timestamp` הוא מהיום (השוואת תאריך).
- **נותר לנטול היום**: `days_of_week` כולל את יום השבוע הנוכחי AND לא עומדת בתנאי "נלקחה היום".

### Dashboard
- [DailyReportsScreen.js](AnchorDashboardApp/ui/Screens/DailyReportsScreen.js) שורות 64–78 — להוסיף `useEffect` נוסף שקורא `GET /users/{userId}/medication-reminders`, מחשב taken/pending לפי היום, ומזין אותם ל-`todayReport`.
- `checkinToReport()` ישאר ריק (`medicationsTaken: []`) — התרופות יגיעו מה-fetch הנפרד.

**הערה:** ה-UI ב-`ReportCard` (שורות 99–119) כבר מוכן להציג תרופות — רק ה-fetch חסר.

---

## 2. ✅ באג QR — "קוד QR כבר נוצל" קופץ כמה פעמים

**בעיה:** `onBarcodeScanned` ב-expo-camera מתפעל מספר פעמים ברצף על אותו QR, לפני ש-`setScanned(true)` מספיק לעדכן את ה-state (כי setState אסינכרוני). כך נשלחות כמה קריאות API בו-זמנית — הראשונה מצליחה, השאר מחזירות 404 ומופיע האלרט "קוד QR כבר נוצל".

**הפתרון:**
- להוסיף `useRef` בתור guard סינכרוני לצד ה-`useState`:
  ```js
  const scannedRef = useRef(false);
  
  const handleBarcodeScanned = async ({ data }) => {
      if (scannedRef.current) return;
      scannedRef.current = true;
      setScanned(true);
      // ...
  };
  ```
- ב-`handlePairAgain` לאפס גם את ה-ref: `scannedRef.current = false;`

**קובץ:** [WatchPairingScreen.js](AnchorDashboardApp/ui/Screens/WatchPairingScreen.js) שורות 23–47.

---

## 3. ✅ הצגת סטטוס שעון מקושר ב-LinkManagementScreen

**בעיה:** אם המבוגר כבר קישר שעון, מסך "ניהול קישורים" עדיין מציג רק כפתור "קשר שעון" — אין אינדיקציה שהשעון מקושר.

**מה צריך לעשות:**

### Dashboard
- [LinkManagementScreen.js](AnchorDashboardApp/ui/Screens/LinkManagementScreen.js) — `ElderlyView` צריך לבדוק אם `user.watchId` (או `watch_paired_at`) קיים, ואז:
  - להציג תחת כפתור "קשר שעון" שורת סטטוס: **"שעון מקושר"** + (watch_id מקוצר, לדוגמה 8 תווים ראשונים)
  - לשנות את כפתור "קשר שעון" ל"קשר שעון אחר" כשיש שעון קיים

### Backend / UserContext
- לוודא שה-`UserContext` נושא את `watchId` ו/או `watchPairedAt` אחרי login.
- אם לא: להוסיף `GET /users/{id}/profile` שמחזיר את הפרטים כולל `watch_id`, או להוסיף את השדה ל-login response.

**קבצים רלוונטיים:**
- [LinkManagementScreen.js](AnchorDashboardApp/ui/Screens/LinkManagementScreen.js) שורות 61–90 (`ElderlyView`)
- `AnchorDashboardApp/logic/contexts/UserContext.js` — לוודא שהuser object מכיל `watchId`

---

---

## 4. ✅ באג מיקום — Check-in שולח תמיד את אותן קואורדינטות

**בעיה:** `CheckInActivity` קרא `getLastKnownLocation` וחזר מיד עם ה-cache הישן של ה-device/emulator — ללא בדיקת גיל. כך נשלחו תמיד אותן קואורדינטות (`32.0477, 34.7603`) בכל check-in.

**הפתרון:** להוסיף בדיקת גיל לפני שמשתמשים ב-`lastKnownLocation` — אם ישן מ-5 דקות, דולגים עליו ומבקשים fix חדש.

**קובץ:** [CheckInActivity.kt](AnchorWatchApp/app/src/main/kotlin/com/anchor/watch/CheckInActivity.kt) שורות 94–100.

---

## סדר עדיפויות מוצע

| # | משימה | קושי | השפעה על הדגמה | סטטוס |
|---|-------|------|----------------|--------|
| 1 | תיקון באג QR (issue 2) | נמוך | גבוהה — קריטי לpairing | ✅ בוצע |
| 2 | הצגת שעון מקושר (issue 3) | נמוך–בינוני | גבוהה — UX ברור | ✅ בוצע |
| 3 | תיקון מיקום ב-check-in (issue 4) | נמוך | גבוהה — נתוני מיקום אמיתיים | ✅ בוצע |
| 4 | תרופות בדיווח יומי (issue 1) | גבוה (דורש backend) | בינונית — mock data קיים | ⏳ נשאר |
