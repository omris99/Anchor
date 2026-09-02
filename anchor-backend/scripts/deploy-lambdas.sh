#!/bin/bash
set -e

PROFILE="anchor"
REGION="us-east-1"
ACCOUNT_ID="976586160011"
COGNITO_CLIENT_ID="1smq0heh9hmht2tti3rnb4usvi"
ROLE_NAME="LabRole"
ROLE_ARN="arn:aws:iam::${ACCOUNT_ID}:role/${ROLE_NAME}"

# Navigate to anchor-backend root
cd "$(dirname "$0")/.."

echo "=== Anchor Lambda Deployment ==="
echo ""

# --- IAM Role ---
# משתמשים ב-LabRole הקיים (AWS Academy לא מאפשר יצירת roles חדשים)
echo "[1/3] Using existing LabRole..."
aws iam get-role --role-name "$ROLE_NAME" --profile "$PROFILE" --query 'Role.Arn' --output text > /dev/null
echo "  ✓ LabRole confirmed"

# --- Deploy Lambda helper ---
deploy_lambda() {
  local FUNC_SUFFIX=$1   # e.g. auth-register
  local LAMBDA_DIR=$2    # e.g. auth-register
  local FULL_NAME="anchor-${FUNC_SUFFIX}"

  echo "  Packaging lambdas/${LAMBDA_DIR}..."
  cd "lambdas/${LAMBDA_DIR}"
  zip -q -r function.zip index.js

  if aws lambda get-function --function-name "$FULL_NAME" --region "$REGION" --profile "$PROFILE" > /dev/null 2>&1; then
    aws lambda update-function-code \
      --function-name "$FULL_NAME" \
      --zip-file fileb://function.zip \
      --region "$REGION" \
      --profile "$PROFILE" > /dev/null
    echo "  ✓ $FULL_NAME updated"
  else
    aws lambda create-function \
      --function-name "$FULL_NAME" \
      --runtime nodejs18.x \
      --role "$ROLE_ARN" \
      --handler index.handler \
      --zip-file fileb://function.zip \
      --environment "Variables={COGNITO_CLIENT_ID=${COGNITO_CLIENT_ID}}" \
      --timeout 15 \
      --region "$REGION" \
      --profile "$PROFILE" > /dev/null
    echo "  ✓ $FULL_NAME created"
  fi

  rm function.zip
  cd ../..
}

# --- Deploy all lambdas ---
echo ""
echo "[2/3] Deploying Lambda functions..."

# Auth (already deployed — update if code changed)
deploy_lambda "auth-register"   "auth-register"
deploy_lambda "auth-login"      "auth-login"
deploy_lambda "auth-confirm"    "auth-confirm"
deploy_lambda "auth-verify-mfa" "auth-verify-mfa"

# Watch pairing
deploy_lambda "watch-init-pairing" "watch-init-pairing"
deploy_lambda "watch-pair"         "watch-pair"
deploy_lambda "watch-credentials"  "watch-credentials"

# Watch FCM token registration
deploy_lambda "watch-fcm-token" "watch-fcm-token"
deploy_lambda "watch-unpair"    "watch-unpair"

# Check-ins
deploy_lambda "checkins"         "checkins"
deploy_lambda "checkins-get"     "checkins-get"
deploy_lambda "checkins-request" "checkins-request"

# Medication reminders
deploy_lambda "medication-reminders-get"          "medication-reminders-get"
deploy_lambda "medication-reminders-confirm"      "medication-reminders-confirm"
deploy_lambda "medication-reminders-missed"       "medication-reminders-missed"
deploy_lambda "medication-reminders-schedule-ack" "medication-schedule-ack"
deploy_lambda "medication-reminders-dashboard"    "medication-reminders-dashboard"

# Water reminders
deploy_lambda "water-reminders-get"          "water-reminders-get"
deploy_lambda "water-reminders-confirm"      "water-reminders-confirm"
deploy_lambda "water-reminders-missed"       "water-reminders-missed"
deploy_lambda "water-reminders-schedule-ack" "water-reminders-schedule-ack"
deploy_lambda "water-reminders-dashboard"    "water-reminders-dashboard"

# Emergency
deploy_lambda "emergency"             "emergency"
deploy_lambda "emergency-acknowledge" "emergency-acknowledge"
deploy_lambda "emergency-alerts-get"  "emergency-alerts-get"

# Mobile FCM token (dashboard)
deploy_lambda "mobile-fcm-token" "mobile-fcm-token"

# User profile
deploy_lambda "user-profile" "user-profile"

# User wellness status
deploy_lambda "user-status" "user-status"

# Health metrics (watch → dashboard)
deploy_lambda "health-metrics-post" "health-metrics-post"
deploy_lambda "health-metrics-get"  "health-metrics-get"

echo ""
echo "[3/3] Done!"
echo ""
echo "Next step: run scripts/add-new-routes.sh"
