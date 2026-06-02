# Anchor — Pre-Release Device QA Checklist

Manual test plan to run **on physical devices** before merging `feature/auth-pairing-fix`
and handing off for pilot testing. Primary goals:

1. **Definitively close the native Android JNI crash** (`java.lang.String cannot be cast to
   java.lang.Boolean` at `createViewInstance`).
2. **Validate the Auth and Watch-Pairing flows** end-to-end against the production backend.

> Headless bundles passing is **necessary but not sufficient** — Metro compiles the graph but
> never executes the native runtime. These checks must run on a real device (Expo Go or a
> build). For how to launch, see [`SETUP.md`](SETUP.md).

---

## Test run header (fill in per run)

| Field | Value |
|---|---|
| Date / tester | |
| Commit SHA | |
| Dashboard delivery | Expo Go (SDK 54) ☐ / EAS preview build ☐ |
| Android device + OS | (Android 14+) |
| iOS device + OS | (iOS 18+) |
| Backend base URL | `https://u7cxnohim6.execute-api.us-east-1.amazonaws.com` |

---

## A. Native crash closure — Android (the JNI cast) 🔴 must pass

| # | Step | Expected | P/F |
|---|------|----------|-----|
| A1 | Launch via `npx expo start -c --tunnel`, scan with Expo Go on **Android 14+** | App loads to **Welcome**; no redbox; no native crash | |
| A2 | Watch the Metro terminal during load | **No** `Require cycle:` warning; **no** dependency-version-mismatch warnings | |
| A3 | Welcome → Home | Renders; greeting shows | |
| A4 | Home → each of the 5 main screens (Health, Daily Reports, Medication, Connections, Emergency History) and back | Every screen mounts; **no** `setProperty/updateProperties/createViewInstance` crash | |
| A5 | Open Preferences (header-hidden screen) and the native-stack transitions | Headers behave; no cast exception on `headerShown` screens | |
| A6 | Rotate device + background/foreground on several screens | No crash on re-create | |
| A7 | Use the camera screen (Watch Pairing) — native view mount | `expo-camera` view renders; no native cast | |

## B. Native crash closure — iOS smoke 🟠

| # | Step | Expected | P/F |
|---|------|----------|-----|
| B1 | Load on **iOS 18+** via Expo Go | App loads to Welcome; no crash | |
| B2 | Navigate Welcome → Home → all 5 screens | All mount cleanly | |

## C. Authentication flow

| # | Step | Expected | P/F |
|---|------|----------|-----|
| C1 | Register a new user (all fields) | Advances to **Confirm Sign-Up** (header hidden) | |
| C2 | Enter the emailed confirmation code | Confirms; proceeds into the app | |
| C3 | Log in with the new credentials | Lands on Home; Hebrew greeting matches gender | |
| C4 | Enable **"remember me"**, kill + relaunch | Auto-login restores the session to Home | |
| C5 | Wrong password | Correct Hebrew error alert; no crash | |
| C6 | Airplane mode, then login | Network-error Hebrew alert; no crash | |
| C7 | Preferences → **Logout** (red) | Returns to Welcome; greeting resets (UserContext cleared) | |
| C8 | Confirm requests carry the Cognito JWT | API calls succeed (200s) with `Authorization` header | |

## D. Watch pairing & family linking

| # | Step | Expected | P/F |
|---|------|----------|-----|
| D1 | Elder generates the **QR** on the watch | QR renders on the watch face | |
| D2 | Dashboard (elder user) → Watch Pairing → scan QR | Camera scans; `POST /users/{id}/watch/pair` succeeds | |
| D3 | Watch obtains its `X-Watch-Key` credential | Watch authenticates to the API with the key | |
| D4 | Family member sends a link request by phone number | `family/request` accepted | |
| D5 | Elder approves the request | Link established; family sees the elder's data | |
| D6 | Medication reminder created on Dashboard fires on watch | Watch alarms; **✓ Taken** confirms back to backend | |
| D7 | SOS on the watch | Emergency dispatched; appears in Dashboard Emergency History | |
| D8 | Daily check-in from the watch | Lands and shows in Daily Reports | |

## E. Regression guardrails

| # | Step | Expected | P/F |
|---|------|----------|-----|
| E1 | Hebrew RTL vs English LTR render correctly | Layout direction matches the selected language | |
| E2 | Health graph renders | `react-native-chart-kit` LineChart draws (no `victory-native`) | |
| E3 | No console errors referencing missing modules | Import-resolution clean at runtime | |

---

## Sign-off

- [ ] **Section A (Android JNI crash) fully PASS** — the original blocker is closed.
- [ ] Sections B–D pass (or failures logged as issues with repro steps).
- [ ] Tester: ____________________  Date: __________  Commit: __________

> Any failure in **Section A** blocks the merge to `main`. Log every other failure with
> device, OS, screen, and the exact error text.
