# Anchor — Dev Setup (GitHub Codespaces)

This guide runs the **Dashboard app** (`AnchorDashboardApp/`, Expo SDK 54 / React Native 0.81)
from a **GitHub Codespace**. We use Codespaces because the corporate network's SSL
inspection (MITM proxy) breaks `npm install` and Expo's CDN/registry traffic on local
machines. A Codespace runs outside that proxy, so installs and the Expo dev tunnel work
reliably.

> The watch app (`AnchorWatchApp/`, Kotlin/Wear OS) is built with Gradle/Android Studio and
> is **not** covered here — see [`PILOT_MILESTONE_1.md`](PILOT_MILESTONE_1.md) §2–3.

---

## 1. Why Codespaces (read first)

- Local `npm install` / `npx expo start` fail behind the corporate SSL proxy
  (`UNABLE_TO_GET_ISSUER_CERT_LOCALLY`, hanging downloads, broken Expo tunnel).
- A Codespace is a cloud dev container — its traffic isn't intercepted, so dependency
  installs and the `--tunnel` connection to your phone work without certificate hacks.
- You still run **Expo Go on your own physical phone**; it connects to the Codespace over an
  Expo tunnel (not your LAN), so the phone and the Codespace don't need to share a network.

---

## 2. Prerequisites

- A GitHub account with **Codespaces** access on this repository.
- **Expo Go** installed on a physical device:
  - Android **14+**, or iOS **18+**.
  - The Expo Go version must support **Expo SDK 54** (install the current Expo Go from the
    store; if it's too new for SDK 54, grab the matching older Expo Go build).
- AWS credentials for the **`anchor`** profile (temporary `voclabs`/`LabRole` session —
  Account `976586160011`, region `us-east-1`). These expire; refresh when AWS calls start
  failing with an expired-token error.

---

## 3. Create the Codespace

1. On the repo's GitHub page: **Code ▾ → Codespaces → Create codespace on `main`**
   (or on your feature branch).
2. Open it in the browser, or **Open in VS Code Desktop** for the local editor experience.
3. Wait for the container to finish provisioning, then open a terminal in the Codespace.

---

## 4. Configure the AWS `anchor` profile

Always use `--profile anchor` for every AWS command (per `CLAUDE.md`).

```bash
aws configure --profile anchor
#   AWS Access Key ID:     <from the voclabs / AWS Academy session>
#   AWS Secret Access Key: <from the session>
#   Default region name:   us-east-1
#   Default output format: json
```

If the session also issues a **session token** (temporary credentials usually do), set it too:

```bash
aws configure set aws_session_token "<session-token>" --profile anchor
```

Verify before doing any backend work — this is the canary for an expired session:

```bash
aws sts get-caller-identity --profile anchor
```

> Temporary sessions expire. When `get-caller-identity` (or any AWS call) reports an expired
> token, re-run the `aws configure` steps above with fresh credentials.

---

## 5. Install dependencies

```bash
cd AnchorDashboardApp
npm install
npx expo install --fix     # aligns every native module to the versions Expo SDK 54 ships
```

`npx expo install --fix` must finish with **"Dependencies are up to date."** This is the step
that prevents the native `java.lang.String cannot be cast to java.lang.Boolean` crash — it
keeps `react-native-screens`, `react-native-safe-area-context`, `@react-native-community/netinfo`
and friends pinned to the binaries baked into Expo Go. If it reports changes, let it apply
them, then commit the updated `package.json` / `package-lock.json`.

---

## 6. Run the Dashboard against your phone

```bash
npx expo start -c --tunnel
```

- `-c` clears the Metro bundler cache (do this after dependency changes or odd bundling errors).
- `--tunnel` routes through Expo's relay so your phone reaches the Codespace even though they
  aren't on the same LAN — **required** from Codespaces.

Then **scan the QR code** shown in the terminal with **Expo Go** on your phone. The app should
load to the Welcome screen with no native crash.

---

## 7. Troubleshooting

| Symptom | Fix |
|---|---|
| First `--tunnel` run asks to install `@expo/ngrok` | Accept the prompt (or `npm i -g @expo/ngrok`), then re-run. |
| Bundler shows a `Require cycle:` warning | A screen imports from `App.js`; import shared state from `logic/contexts/` instead. |
| Dependency-version mismatch warnings in the bundler | Re-run `npx expo install --fix` and commit the result. |
| Native `String cannot be cast to Boolean` crash | A native module drifted off the SDK-54 version — `npx expo install --fix`; keep New Architecture on. |
| Stale/odd bundle after editing | Restart with `npx expo start -c`. |
| Expo Go shows "incompatible SDK" | Your Expo Go build doesn't match SDK 54 — install the matching Expo Go version. |
| AWS calls fail with expired token | Re-run the `aws configure --profile anchor` steps (§4) with fresh session credentials. |

---

## 8. Backend (optional, same Codespace)

Backend deploys also run cleanly from the Codespace. Always `--profile anchor`. See
[`PILOT_MILESTONE_1.md`](PILOT_MILESTONE_1.md) §3 and the scripts in `anchor-backend/scripts/`:

```bash
aws sts get-caller-identity --profile anchor   # confirm the session first
cd anchor-backend
./scripts/deploy-lambdas.sh                     # package + update/create each Lambda
./scripts/add-new-routes.sh                     # only when API Gateway routes changed
```
