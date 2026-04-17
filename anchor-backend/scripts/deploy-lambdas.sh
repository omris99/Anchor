#!/bin/bash
set -e

PROFILE="anchor"
REGION="us-east-1"
ACCOUNT_ID="976586160011"
COGNITO_CLIENT_ID="6fuplbkqkeos4d2oojtr0vo91v"
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

# --- Deploy all auth lambdas ---
echo ""
echo "[2/3] Deploying Lambda functions..."
deploy_lambda "auth-register"   "auth-register"
deploy_lambda "auth-login"      "auth-login"
deploy_lambda "auth-confirm"    "auth-confirm"
deploy_lambda "auth-verify-mfa" "auth-verify-mfa"

echo ""
echo "[3/3] Done!"
echo ""
echo "Next step: run scripts/create-api-gateway.sh"
