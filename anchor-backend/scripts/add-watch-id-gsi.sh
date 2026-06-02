#!/bin/bash
# Adds the watch_id-index GSI to the EXISTING Anchor_Users table (non-destructive).
#
# Why: GET /watch/credentials must look a user up by watch_id while the watch polls
# during pairing. Anchor_Users is keyed only on `id`, so the handler previously had
# to Scan — and the Scan's `Limit: 1` made it return 404 even after a successful
# pair, freezing the watch on its QR screen. This GSI lets the handler Query instead.
#
# Safe to re-run: it checks whether the index already exists and exits early if so.
# Unlike create-tables.sh, this does NOT delete/recreate anything — run it against live data.
set -e

PROFILE="anchor"
REGION="us-east-1"
TABLE="Anchor_Users"
INDEX="watch_id-index"

echo "=== Adding ${INDEX} GSI to ${TABLE} ==="

EXISTING=$(aws dynamodb describe-table \
  --table-name "$TABLE" \
  --region "$REGION" --profile "$PROFILE" \
  --query "Table.GlobalSecondaryIndexes[?IndexName=='${INDEX}'].IndexName | [0]" \
  --output text 2>/dev/null || echo "None")

if [ "$EXISTING" == "$INDEX" ]; then
  echo "  ✓ Index ${INDEX} already exists — nothing to do."
  exit 0
fi

echo "  Creating GSI (triggers a background backfill of existing rows)..."
aws dynamodb update-table \
  --table-name "$TABLE" \
  --attribute-definitions AttributeName=watch_id,AttributeType=S \
  --global-secondary-index-updates '[{
    "Create": {
      "IndexName": "'"${INDEX}"'",
      "KeySchema": [{"AttributeName":"watch_id","KeyType":"HASH"}],
      "Projection": {"ProjectionType":"INCLUDE","NonKeyAttributes":["watch_api_key"]}
    }
  }]' \
  --region "$REGION" --profile "$PROFILE" \
  --query 'TableDescription.TableName' --output text

echo "  Waiting for ${INDEX} to become ACTIVE (Query fails until then)..."
while true; do
  STATUS=$(aws dynamodb describe-table \
    --table-name "$TABLE" \
    --region "$REGION" --profile "$PROFILE" \
    --query "Table.GlobalSecondaryIndexes[?IndexName=='${INDEX}'].IndexStatus | [0]" \
    --output text)
  echo "    index status: ${STATUS}"
  [ "$STATUS" == "ACTIVE" ] && break
  sleep 5
done

echo ""
echo "  ✓ ${INDEX} is ACTIVE."
echo "  Next: redeploy the watch-credentials Lambda so it Queries the index:"
echo "    aws lambda update-function-code --function-name anchor-watch-credentials \\"
echo "      --zip-file fileb://function.zip --region ${REGION} --profile ${PROFILE}"
echo "  (or just re-run scripts/deploy-lambdas.sh)"
