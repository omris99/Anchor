#!/bin/bash

PROFILE="anchor"
REGION="us-east-1"
ACCOUNT_ID=$(aws sts get-caller-identity --profile $PROFILE --query Account --output text)

echo "Setting up Cognito authentication..."

# ─── 1. IAM Role ─────────────────────────────────────────────────────────────
# משתמשים ב-LabRole הקיים (AWS Academy לא מאפשר יצירת roles חדשים)

echo "Using existing LabRole for SMS..."

SMS_ROLE_ARN=$(aws iam get-role \
  --role-name LabRole \
  --profile $PROFILE \
  --query 'Role.Arn' --output text)

echo "SMS Role ARN: $SMS_ROLE_ARN"

# ─── 2. User Pool ─────────────────────────────────────────────────────────────

echo "Creating User Pool..."

USER_POOL_ID=$(aws cognito-idp create-user-pool \
  --pool-name AnchorUserPool \
  --policies '{
    "PasswordPolicy": {
      "MinimumLength": 8,
      "RequireUppercase": true,
      "RequireLowercase": true,
      "RequireNumbers": true,
      "RequireSymbols": false
    }
  }' \
  --mfa-configuration ON \
  --sms-configuration '{
    "SnsCallerArn": "'"$SMS_ROLE_ARN"'",
    "ExternalId": "AnchorCognitoSMS"
  }' \
  --auto-verified-attributes phone_number email \
  --username-attributes phone_number \
  --schema \
    Name=phone_number,Required=true,Mutable=true \
    Name=email,Required=true,Mutable=true \
    Name=name,Required=true,Mutable=true \
    'Name=user_type,AttributeDataType=String,Mutable=false,DeveloperOnlyAttribute=false,StringAttributeConstraints={MinLength=1,MaxLength=20}' \
  --account-recovery-setting '{
    "RecoveryMechanisms": [{"Priority": 1, "Name": "verified_email"}]
  }' \
  --profile $PROFILE --region $REGION \
  --query 'UserPool.Id' --output text)

echo "User Pool ID: $USER_POOL_ID"

# ─── 3. הגדרת הודעת SMS ───────────────────────────────────────────────────────

echo "Configuring SMS MFA message..."

aws cognito-idp set-user-pool-mfa-config \
  --user-pool-id $USER_POOL_ID \
  --sms-mfa-configuration '{
    "SmsAuthenticationMessage": "Your Anchor verification code is {####}",
    "SmsConfiguration": {
      "SnsCallerArn": "'"$SMS_ROLE_ARN"'",
      "ExternalId": "AnchorCognitoSMS"
    }
  }' \
  --mfa-configuration ON \
  --profile $PROFILE --region $REGION --output text > /dev/null

# ─── 4. User Pool Client ──────────────────────────────────────────────────────

echo "Creating User Pool Client..."

CLIENT_ID=$(aws cognito-idp create-user-pool-client \
  --user-pool-id $USER_POOL_ID \
  --client-name AnchorAppClient \
  --no-generate-secret \
  --explicit-auth-flows \
    ALLOW_USER_PASSWORD_AUTH \
    ALLOW_REFRESH_TOKEN_AUTH \
  --access-token-validity 30 \
  --id-token-validity 30 \
  --refresh-token-validity 30 \
  --token-validity-units '{
    "AccessToken": "minutes",
    "IdToken": "minutes",
    "RefreshToken": "days"
  }' \
  --profile $PROFILE --region $REGION \
  --query 'UserPoolClient.ClientId' --output text)

echo "Client ID: $CLIENT_ID"

# ─── סיכום ────────────────────────────────────────────────────────────────────

echo ""
echo "=== Auth Setup Complete ==="
echo "User Pool ID : $USER_POOL_ID"
echo "Client ID    : $CLIENT_ID"
echo "Region       : $REGION"
echo ""
echo "שמור את הערכים האלה — הם נדרשים לחיבור מהאפליקציה"
