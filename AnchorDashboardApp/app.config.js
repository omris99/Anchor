// Dynamic Expo config.
//
// Expo loads the static app.json first and passes it here as `config`; we spread it
// through unchanged and only inject runtime backend values into `extra`. This lets the
// app target different environments (dev / staging / prod) without editing source code.
//
// Resolution order for each value:
//   1. EXPO_PUBLIC_* env var (a local .env, or an EAS build profile's `env` block)
//   2. the production default below (keeps Expo Go working with zero setup)
//
// These are PUBLIC client config values (they ship in any app bundle) — not secrets.

export default ({ config }) => ({
  ...config,
  extra: {
    ...config.extra,
    apiBaseUrl:
      process.env.EXPO_PUBLIC_API_BASE_URL ??
      "https://u7cxnohim6.execute-api.us-east-1.amazonaws.com",
    cognito: {
      region: process.env.EXPO_PUBLIC_COGNITO_REGION ?? "us-east-1",
      userPoolId:
        process.env.EXPO_PUBLIC_COGNITO_USER_POOL_ID ?? "us-east-1_KXDRK5VnC",
      userPoolClientId:
        process.env.EXPO_PUBLIC_COGNITO_CLIENT_ID ?? "1smq0heh9hmht2tti3rnb4usvi",
    },
  },
});
