# Anchor — Production Deployment Guide

Definitive guide for deploying the **Anchor** eldercare system to production. Three
components ship together:

- **anchor-backend/** — AWS (Lambda + API Gateway + DynamoDB + Cognito)
- **AnchorDashboardApp/** — React Native / Expo (family & caregiver dashboard)
- **AnchorWatchApp/** — Kotlin / Wear OS (the elder's watch)

> For local development (not production), see [`SETUP.md`](SETUP.md).
> For the pre-release device test plan, see [`QA_CHECKLIST.md`](QA_CHECKLIST.md).

---

## 0. Architecture & network topology

```
┌─────────────────┐   X-Watch-Key (API key)    ┌──────────────────────┐
│  Wear OS watch  │ ─────────────────────────► │                      │
│ (AnchorWatchApp)│                            │   API Gateway (HTTP) │
└─────────────────┘                            │   id: u7cxnohim6     │      ┌───────────────┐
                                               │   us-east-1          │ ───► │  16 Lambdas   │ ───► DynamoDB (6 tables)
┌─────────────────┐   Cognito JWT (Authorization) │                   │      │  LabRole      │ ───► Cognito / SNS+FCM
│  Dashboard app  │ ─────────────────────────► │   base_url in        │      │  nodejs18.x   │
│(AnchorDashboard)│                            │   api-config.json    │      └───────────────┘
└─────────────────┘                            └──────────────────────┘
```

- **Two auth paths into one API Gateway:** the **watch** authenticates with a static
  API key header `X-Watch-Key`; the **dashboard** authenticates with a **Cognito JWT**
  (`Authorization: <idToken>`).
- **Account `976586160011`, region `us-east-1`.** Every AWS command uses `--profile anchor`.
- **Public client config** (API Gateway URL, Cognito pool/client IDs) ships inside the app
  bundles — these are *not secrets*. Real secrets (keystores, FCM keys) never enter the repo.

---

## 1. Prerequisites

| Need | Detail |
|---|---|
| AWS CLI + `anchor` profile | `aws configure --profile anchor` · verify with `aws sts get-caller-identity --profile anchor`. Temporary voclabs/`LabRole` session — refresh when it expires. |
| Node.js + npm | For Lambda packaging and the Expo build. |
| Expo account + EAS CLI | `npm i -g eas-cli && eas login` (production Dashboard builds). |
| JDK 17 + Android SDK 36 | For the Wear OS release build (`AnchorWatchApp`). |
| Release keystore (watch) | Generated once, stored **outside** git (see §4). |

---

## 2. Backend (AWS)

All scripts live in `anchor-backend/scripts/` and hardcode `--profile anchor`, `us-east-1`,
account `976586160011`, and the existing **`LabRole`** (AWS Academy forbids creating roles).

### 2.1 First-time environment bring-up (in order)
```bash
cd anchor-backend
aws sts get-caller-identity --profile anchor   # confirm the session first
./scripts/create-tables.sh        # 6 DynamoDB tables
./scripts/create-auth.sh          # Cognito user pool + client
./scripts/create-api-gateway.sh   # the HTTP API (id u7cxnohim6)
./scripts/deploy-lambdas.sh       # package + create all 16 Lambdas
./scripts/add-new-routes.sh       # wire routes → integrations → invoke perms
```

### 2.2 Routine redeploy (code changes only)
```bash
cd anchor-backend
./scripts/deploy-lambdas.sh        # zips each lambdas/<name>/index.js, update-in-place
```
`deploy-lambdas.sh` creates the function if missing, otherwise `update-function-code`.
Runtime `nodejs18.x`, handler `index.handler`, timeout 15s, `COGNITO_CLIENT_ID` injected as env.

### 2.3 ⚠️ Adding/changing routes
`add-new-routes.sh` is **NOT idempotent** — it unconditionally `create-integration` +
`create-route`, so re-running it on existing routes fails with duplicate route-key errors.
**Run it only when you add new routes**, and add just the new `add_route ...` lines.
Lambda **code** updates never need it (the integration already points at the function name).

### 2.4 Verify
```bash
# api-config.json holds the live base URL the clients use:
#   { "api_id": "u7cxnohim6", "base_url": "https://u7cxnohim6.execute-api.us-east-1.amazonaws.com" }
curl -i -H "Authorization: <cognito-idToken>" \
  https://u7cxnohim6.execute-api.us-east-1.amazonaws.com/users/<id>/checkins
```
Confirm the `base_url` matches what the Dashboard is built with (§3.2).

---

## 3. Frontend — Dashboard (Expo / React Native)

Production replaces the Expo Go dev workflow with **EAS Build** (signed, store-ready binaries).

### 3.1 One-time EAS setup
```bash
cd AnchorDashboardApp
npm i -g eas-cli
eas login
eas build:configure        # creates eas.json and writes extra.eas.projectId into app.json
```

> **Decision B note:** `eas.json` is **not** committed yet (kept out to hold pre-merge code
> risk at zero). Generate it at release time with `eas build:configure`. A typical `eas.json`:
> ```json
> {
>   "cli": { "version": ">= 12.0.0" },
>   "build": {
>     "preview":    { "distribution": "internal", "android": { "buildType": "apk" } },
>     "production": { "autoIncrement": true,
>                     "env": { "EXPO_PUBLIC_API_BASE_URL": "https://u7cxnohim6.execute-api.us-east-1.amazonaws.com" } }
>   },
>   "submit": { "production": {} }
> }
> ```

### 3.2 Environment configuration (already wired in code)
Backend config is **no longer hardcoded** — `app.config.js` injects it into `extra`, read at
runtime via `expo-constants` in `config/env.js`, consumed by `ApiClient.js` and
`config/awsAuthConfig.js`. Override per environment with `EXPO_PUBLIC_*` vars (see
`.env.example`); if unset, the **production defaults** in `app.config.js` apply.

| Variable | Default (production) |
|---|---|
| `EXPO_PUBLIC_API_BASE_URL` | `https://u7cxnohim6.execute-api.us-east-1.amazonaws.com` |
| `EXPO_PUBLIC_COGNITO_REGION` | `us-east-1` |
| `EXPO_PUBLIC_COGNITO_USER_POOL_ID` | `us-east-1_KXDRK5VnC` |
| `EXPO_PUBLIC_COGNITO_CLIENT_ID` | `1smq0heh9hmht2tti3rnb4usvi` |

For staging, set these in the EAS build profile's `env` block (or a local `.env`) — no code edit.

### 3.3 Build & distribute
```bash
cd AnchorDashboardApp
npx expo install --fix      # confirm SDK-54 alignment before building ("up to date")
eas build -p android --profile production    # AAB for Play
eas build -p ios --profile production        # IPA for TestFlight
# internal test build (sideloadable APK):
eas build -p android --profile preview
```

### 3.4 Submit
```bash
eas submit -p android --profile production   # Google Play
eas submit -p ios --profile production       # App Store / TestFlight
```

> **⚠️ Push notifications (Issue #7, not yet built):** production Expo Push needs the EAS
> `projectId` (added by `eas build:configure`) **and** FCM credentials uploaded to Expo
> (`eas credentials`). Until then, the SNS+FCM emergency fan-out to the Dashboard is inactive.

---

## 4. Watch app (Wear OS release)

`AnchorWatchApp/` · applicationId `com.anchor.anchorwatchapp` · namespace `com.anchor.watch` ·
minSdk 34 · targetSdk/compileSdk 36.

> **⚠️ Current gap (documented, not yet changed — Decision B):** `app/build.gradle.kts`
> `buildTypes.release` has **no `signingConfig`**, and `versionCode = 1`. Production release
> artifacts must be signed and the version bumped per release. Steps below close that gap.

### 4.1 Generate a release keystore (once, store OUTSIDE git)
```bash
keytool -genkeypair -v -keystore anchor-watch-release.jks \
  -alias anchor-watch -keyalg RSA -keysize 2048 -validity 10000
```
`*.jks` is already git-ignored. Put credentials in `AnchorWatchApp/keystore.properties`
(also git-ignored):
```
storeFile=../anchor-watch-release.jks
storePassword=********
keyAlias=anchor-watch
keyPassword=********
```

### 4.2 Wire signing into `app/build.gradle.kts`
Add a `signingConfigs.release` that loads `keystore.properties`, reference it from
`buildTypes.release { signingConfig = signingConfigs.getByName("release") }`, and bump
`versionCode`/`versionName` for the release.

### 4.3 Build the signed release
```bash
cd AnchorWatchApp
# Play Store (recommended):
./gradlew :app:bundleRelease     # → app/build/outputs/bundle/release/app-release.aab
# Direct sideload:
./gradlew :app:assembleRelease   # → app/build/outputs/apk/release/app-release.apk
```
> SDK path: if Gradle reports "SDK location not found", prefix with
> `ANDROID_HOME="$LOCALAPPDATA/Android/Sdk" ANDROID_SDK_ROOT="$LOCALAPPDATA/Android/Sdk"`.

### 4.4 Install on a device (sideload / pilot)
```bash
adb -s <device-id> install -r app/build/outputs/apk/release/app-release.apk
adb -s <device-id> shell am start -n \
  com.anchor.anchorwatchapp/com.anchor.anchorwatchapp.presentation.MainActivity
```

---

## 5. Rollout sequence (exact order)

Clients depend on a live backend, so **backend goes first**:

1. **Backend** — `deploy-lambdas.sh` (+ `add-new-routes.sh` only if routes changed). Verify a
   route with a Cognito JWT **and** with an `X-Watch-Key` (§2.4).
2. **Smoke-test the API** before touching clients — auth, checkins, medication, emergency.
3. **Dashboard** — `eas build -p android/ios --profile production`, distribute, then run the
   full [`QA_CHECKLIST.md`](QA_CHECKLIST.md) on a physical device (closes the native JNI crash
   + validates Auth/Pairing).
4. **Watch** — build the **signed** release (§4), install on the pilot watch.
5. **End-to-end** — pair a real watch from the **production** Dashboard; verify medication
   reminders fire, SOS → emergency reaches the Dashboard, and daily check-ins land.
6. **Sign-off** — record commit SHA, API base URL, Dashboard build id, watch versionCode.

### Rollback
- **Lambda:** redeploy the previous `index.js` (`deploy-lambdas.sh` updates in place); for
  instant rollback keep published versions (`aws lambda update-function-code ... --publish`).
- **Dashboard:** re-distribute the prior EAS build / store release.
- **Watch:** re-install the previous signed APK (a higher `versionCode` was used, so bump
  again if you must supersede it).

---

## 6. Post-deploy verification & troubleshooting

| Symptom | Fix |
|---|---|
| AWS calls fail: expired token | Re-run `aws configure --profile anchor` with a fresh session. |
| `add-new-routes.sh` errors on duplicate route | Expected on existing routes — only add lines for **new** routes (§2.3). |
| Dashboard hits the wrong backend | Check `app.config.js` defaults / `EXPO_PUBLIC_*` in the EAS profile vs `api-config.json`. |
| EAS build fails on credentials | `eas credentials` to (re)configure Android keystore / iOS certs. |
| Watch release won't install (signature) | Uninstall the debug build first, or bump `versionCode`; ensure the same keystore is reused. |
| Push not delivered | Expected until Issue #7 — needs EAS `projectId` + FCM creds in Expo (§3.4). |
