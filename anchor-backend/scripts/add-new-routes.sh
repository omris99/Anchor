#!/bin/bash
# Adds routes for all non-auth lambdas to the existing anchor-api API Gateway.
# Safe to run after deploy-lambdas.sh — does not touch existing auth routes.
set -e

PROFILE="anchor"
REGION="us-east-1"
ACCOUNT_ID="976586160011"
API_ID="u7cxnohim6"

echo "=== Adding new routes to anchor-api ($API_ID) ==="
echo ""

# Helper: create one integration + route, then allow API GW to invoke the lambda.
# Args: <lambda-suffix> <METHOD> <path>
add_route() {
  local FUNC_SUFFIX=$1
  local METHOD=$2
  local ROUTE_PATH=$3
  local FULL_NAME="anchor-${FUNC_SUFFIX}"
  local LAMBDA_ARN="arn:aws:lambda:${REGION}:${ACCOUNT_ID}:function:${FULL_NAME}"

  INTEGRATION_ID=$(aws apigatewayv2 create-integration \
    --api-id "$API_ID" \
    --integration-type AWS_PROXY \
    --integration-uri "$LAMBDA_ARN" \
    --payload-format-version 2.0 \
    --region "$REGION" \
    --profile "$PROFILE" \
    --query 'IntegrationId' --output text)

  aws apigatewayv2 create-route \
    --api-id "$API_ID" \
    --route-key "$METHOD $ROUTE_PATH" \
    --target "integrations/$INTEGRATION_ID" \
    --region "$REGION" \
    --profile "$PROFILE" > /dev/null

  # Use a timestamp suffix to avoid duplicate statement-id errors on re-runs.
  aws lambda add-permission \
    --function-name "$FULL_NAME" \
    --statement-id "apigw-${FUNC_SUFFIX}-$(date +%s%N)" \
    --action lambda:InvokeFunction \
    --principal apigateway.amazonaws.com \
    --source-arn "arn:aws:execute-api:${REGION}:${ACCOUNT_ID}:${API_ID}/*/*" \
    --region "$REGION" \
    --profile "$PROFILE" > /dev/null

  echo "  ✓  $METHOD $ROUTE_PATH  →  $FULL_NAME"
}

# --- Watch pairing ---
add_route "watch-init-pairing" "POST" "/watch/init-pairing"
add_route "watch-pair"         "POST" "/users/{id}/watch/pair"
add_route "watch-credentials"  "GET"  "/watch/credentials"
add_route "watch-fcm-token"    "POST" "/watch/fcm-token"
add_route "watch-unpair"       "POST" "/users/{id}/watch/unpair"

# --- Check-ins ---
add_route "checkins"         "POST" "/checkins"
add_route "checkins-get"     "GET"  "/users/{id}/checkins"
add_route "checkins-request" "POST" "/users/{id}/checkins/request"

# --- Medication reminders (watch — X-Watch-Key) ---
add_route "medication-reminders-get"          "GET"  "/medication-reminders/{userId}"
add_route "medication-reminders-confirm"      "POST" "/medication-reminders/{id}/confirm"
add_route "medication-reminders-missed"       "POST" "/medication-reminders/{id}/missed"
add_route "medication-reminders-schedule-ack" "POST" "/medication-reminders/{id}/schedule-ack"

# --- Medication reminders (dashboard — JWT) ---
add_route "medication-reminders-dashboard" "GET"    "/users/{id}/medication-reminders"
add_route "medication-reminders-dashboard" "POST"   "/users/{id}/medication-reminders"
add_route "medication-reminders-dashboard" "DELETE" "/users/{id}/medication-reminders/{medId}"

# --- Water reminders (watch — X-Watch-Key) ---
add_route "water-reminders-get"          "GET"  "/water-reminders/{userId}"
add_route "water-reminders-confirm"      "POST" "/water-reminders/{id}/confirm"
add_route "water-reminders-missed"       "POST" "/water-reminders/{id}/missed"
add_route "water-reminders-schedule-ack" "POST" "/water-reminders/{id}/schedule-ack"

# --- Water reminders (dashboard — JWT) ---
add_route "water-reminders-dashboard" "GET" "/users/{id}/water-reminders"
add_route "water-reminders-dashboard" "PUT" "/users/{id}/water-reminders"

# --- Emergency ---
add_route "emergency"             "POST" "/emergency"
add_route "emergency-acknowledge" "POST" "/emergency/{id}/acknowledge"

# --- Mobile FCM token (dashboard) ---
add_route "mobile-fcm-token" "POST" "/users/{id}/mobile-fcm-token"

# --- User profile ---
add_route "user-profile" "GET" "/users/{id}/profile"

# --- User wellness status ---
add_route "user-status" "GET" "/users/{id}/status"

# --- Health metrics ---
add_route "health-metrics-post"    "POST" "/health-metrics"
add_route "health-metrics-get"     "GET"  "/users/{id}/health-metrics/latest"
add_route "health-metrics-history" "GET"  "/users/{id}/health-metrics/history"

echo ""
echo "All routes added. API is live at:"
echo "  https://${API_ID}.execute-api.${REGION}.amazonaws.com"
