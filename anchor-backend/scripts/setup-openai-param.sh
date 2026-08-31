#!/bin/bash
# Stores the OpenAI API key in SSM Parameter Store as a SecureString.
# Shared by user-status (1.5) and daily-report (2.1) — both read the same
# parameter via the same "get key from SSM, cache it" helper pattern.
#
# Usage: ./setup-openai-param.sh sk-...
set -e

PROFILE="anchor"
REGION="us-east-1"
PARAM_NAME="/anchor/openai-api-key"

if [ -z "$1" ]; then
  echo "Usage: $0 <openai-api-key>"
  exit 1
fi

aws ssm put-parameter \
  --name "$PARAM_NAME" \
  --type "SecureString" \
  --value "$1" \
  --overwrite \
  --region "$REGION" \
  --profile "$PROFILE"

echo "✓ Stored OpenAI API key at $PARAM_NAME"
