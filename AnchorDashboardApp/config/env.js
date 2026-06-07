import Constants from "expo-constants";

// Single source of truth for runtime backend configuration.
//
// Values are injected in app.config.js (from EXPO_PUBLIC_* env vars, or production
// defaults) and surfaced here via expo-constants, so screens and services never hardcode
// endpoints or Cognito identifiers. To point at a different environment, set the
// EXPO_PUBLIC_* vars (see .env.example) — no code change required.

const extra = Constants.expoConfig?.extra ?? {};

export const API_BASE_URL = extra.apiBaseUrl;
export const COGNITO = extra.cognito ?? {};
