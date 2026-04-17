#!/bin/bash
set -e

PROFILE="anchor"
REGION="us-east-1"
ACCOUNT_ID="976586160011"
API_NAME="anchor-api"

cd "$(dirname "$0")/.."

echo "=== Anchor API Gateway Setup ==="
echo ""

# Check if API already exists
EXISTING_API=$(aws apigatewayv2 get-apis \
  --region "$REGION" \
  --profile "$PROFILE" \
  --query "Items[?Name=='${API_NAME}'].ApiId" \
  --output text)

if [ -n "$EXISTING_API" ]; then
  echo "API '${API_NAME}' already exists (ID: ${EXISTING_API})"
  echo "To recreate it, delete it first or update routes manually."
  API_ID="$EXISTING_API"
else
  echo "[1/3] Creating HTTP API..."
  API_ID=$(aws apigatewayv2 create-api \
    --name "$API_NAME" \
    --protocol-type HTTP \
    --cors-configuration \
      AllowOrigins='["*"]',AllowMethods='["GET","POST","PUT","DELETE","OPTIONS"]',AllowHeaders='["Content-Type","Authorization"]' \
    --region "$REGION" \
    --profile "$PROFILE" \
    --query 'ApiId' --output text)
  echo "  ✓ API created — ID: $API_ID"
fi

echo ""
echo "[2/3] Creating routes & integrations..."

create_route() {
  local FUNC_SUFFIX=$1
  local METHOD=$2
  local ROUTE_PATH=$3
  local FULL_NAME="anchor-${FUNC_SUFFIX}"
  local LAMBDA_ARN="arn:aws:lambda:${REGION}:${ACCOUNT_ID}:function:${FULL_NAME}"

  # Create integration
  INTEGRATION_ID=$(aws apigatewayv2 create-integration \
    --api-id "$API_ID" \
    --integration-type AWS_PROXY \
    --integration-uri "$LAMBDA_ARN" \
    --payload-format-version 2.0 \
    --region "$REGION" \
    --profile "$PROFILE" \
    --query 'IntegrationId' --output text)

  # Create route
  aws apigatewayv2 create-route \
    --api-id "$API_ID" \
    --route-key "$METHOD $ROUTE_PATH" \
    --target "integrations/$INTEGRATION_ID" \
    --region "$REGION" \
    --profile "$PROFILE" > /dev/null

  # Allow API Gateway to invoke the Lambda
  aws lambda add-permission \
    --function-name "$FULL_NAME" \
    --statement-id "apigw-${FUNC_SUFFIX}-$(date +%s)" \
    --action lambda:InvokeFunction \
    --principal apigateway.amazonaws.com \
    --source-arn "arn:aws:execute-api:${REGION}:${ACCOUNT_ID}:${API_ID}/*/*" \
    --region "$REGION" \
    --profile "$PROFILE" > /dev/null

  echo "  ✓ $METHOD $ROUTE_PATH  ->  $FULL_NAME"
}

create_route "auth-register"   "POST" "/auth/register"
create_route "auth-login"      "POST" "/auth/login"
create_route "auth-confirm"    "POST" "/auth/confirm"
create_route "auth-verify-mfa" "POST" "/auth/verify-mfa"

echo ""
echo "[3/3] Deploying \$default stage..."
aws apigatewayv2 create-stage \
  --api-id "$API_ID" \
  --stage-name '$default' \
  --auto-deploy \
  --region "$REGION" \
  --profile "$PROFILE" > /dev/null 2>&1 || echo "  (stage already exists)"
echo "  ✓ Stage deployed"

BASE_URL="https://${API_ID}.execute-api.${REGION}.amazonaws.com"

# Save config
cat > api-config.json <<EOF
{
  "api_id": "${API_ID}",
  "base_url": "${BASE_URL}",
  "region": "${REGION}"
}
EOF

echo ""
echo "============================================"
echo "  API is LIVE"
echo "============================================"
echo "  Base URL: $BASE_URL"
echo ""
echo "  POST $BASE_URL/auth/register"
echo "  POST $BASE_URL/auth/login"
echo "  POST $BASE_URL/auth/confirm"
echo "  POST $BASE_URL/auth/verify-mfa"
echo "============================================"
echo "  Config saved to api-config.json"
