#!/bin/bash

PROFILE="anchor"
REGION="us-east-1"
PREFIX="Anchor_"

echo "Deleting existing Anchor tables..."

for TABLE in Users FamilyMembers BiometricData DailyCheckIns MedicationReminders WaterReminders Alerts; do
  aws dynamodb delete-table --table-name $TABLE \
    --profile $PROFILE --region $REGION --output text > /dev/null 2>&1 && echo "Deleted: $TABLE"
done

echo "Waiting for deletions to complete..."
sleep 5

echo "Creating Anchor DynamoDB tables..."

# Anchor_Users
aws dynamodb create-table \
  --table-name ${PREFIX}Users \
  --attribute-definitions \
    AttributeName=id,AttributeType=S \
  --key-schema \
    AttributeName=id,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --profile $PROFILE --region $REGION \
  --query 'TableDescription.TableName' --output text

# Anchor_FamilyMembers
aws dynamodb create-table \
  --table-name ${PREFIX}FamilyMembers \
  --attribute-definitions \
    AttributeName=id,AttributeType=S \
    AttributeName=elderly_user_id,AttributeType=S \
  --key-schema \
    AttributeName=id,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --global-secondary-indexes '[{
    "IndexName": "elderly_user_id-index",
    "KeySchema": [{"AttributeName":"elderly_user_id","KeyType":"HASH"}],
    "Projection": {"ProjectionType":"ALL"}
  }]' \
  --profile $PROFILE --region $REGION \
  --query 'TableDescription.TableName' --output text

# Anchor_BiometricData
aws dynamodb create-table \
  --table-name ${PREFIX}BiometricData \
  --attribute-definitions \
    AttributeName=user_id,AttributeType=S \
    AttributeName=timestamp,AttributeType=S \
  --key-schema \
    AttributeName=user_id,KeyType=HASH \
    AttributeName=timestamp,KeyType=RANGE \
  --billing-mode PAY_PER_REQUEST \
  --profile $PROFILE --region $REGION \
  --query 'TableDescription.TableName' --output text

# Anchor_DailyCheckIns
aws dynamodb create-table \
  --table-name ${PREFIX}DailyCheckIns \
  --attribute-definitions \
    AttributeName=user_id,AttributeType=S \
    AttributeName=timestamp,AttributeType=S \
  --key-schema \
    AttributeName=user_id,KeyType=HASH \
    AttributeName=timestamp,KeyType=RANGE \
  --billing-mode PAY_PER_REQUEST \
  --profile $PROFILE --region $REGION \
  --query 'TableDescription.TableName' --output text

# Anchor_MedicationReminders
aws dynamodb create-table \
  --table-name ${PREFIX}MedicationReminders \
  --attribute-definitions \
    AttributeName=user_id,AttributeType=S \
    AttributeName=id,AttributeType=S \
  --key-schema \
    AttributeName=user_id,KeyType=HASH \
    AttributeName=id,KeyType=RANGE \
  --billing-mode PAY_PER_REQUEST \
  --profile $PROFILE --region $REGION \
  --query 'TableDescription.TableName' --output text

# Anchor_WaterReminders
aws dynamodb create-table \
  --table-name ${PREFIX}WaterReminders \
  --attribute-definitions \
    AttributeName=user_id,AttributeType=S \
    AttributeName=id,AttributeType=S \
  --key-schema \
    AttributeName=user_id,KeyType=HASH \
    AttributeName=id,KeyType=RANGE \
  --billing-mode PAY_PER_REQUEST \
  --profile $PROFILE --region $REGION \
  --query 'TableDescription.TableName' --output text

# Anchor_Alerts
aws dynamodb create-table \
  --table-name ${PREFIX}Alerts \
  --attribute-definitions \
    AttributeName=user_id,AttributeType=S \
    AttributeName=timestamp,AttributeType=S \
  --key-schema \
    AttributeName=user_id,KeyType=HASH \
    AttributeName=timestamp,KeyType=RANGE \
  --billing-mode PAY_PER_REQUEST \
  --profile $PROFILE --region $REGION \
  --query 'TableDescription.TableName' --output text

echo "Done. Tables created:"
aws dynamodb list-tables --profile $PROFILE --region $REGION \
  --query 'TableNames[?starts_with(@, `Anchor_`)]' --output table
