// POST /watch/init-pairing
// No auth required. Watch calls this to obtain a 5-min pairing token, which it
// displays as a QR for the elder to scan via the dashboard (DECISIONS.md §2).
// The token is persisted to Anchor_Users with type=pairing and a TTL.

const { DynamoDBClient } = require("@aws-sdk/client-dynamodb");
const { DynamoDBDocumentClient, PutCommand } = require("@aws-sdk/lib-dynamodb");
const crypto = require("crypto");

const ddb = DynamoDBDocumentClient.from(
  new DynamoDBClient({ region: process.env.AWS_REGION || "us-east-1" })
);

const USERS_TABLE = process.env.USERS_TABLE || "Anchor_Users";
const PAIRING_TTL_SECONDS = 5 * 60; // 5 minutes per DECISIONS.md §2

exports.handler = async () => {
  const pairingToken = crypto.randomUUID();
  const watchId = crypto.randomUUID();
  const issuedAt = Date.now();
  const expiresAt = issuedAt + PAIRING_TTL_SECONDS * 1000;

  try {
    await ddb.send(new PutCommand({
      TableName: USERS_TABLE,
      Item: {
        id: pairingToken,
        type: "pairing",
        watch_id: watchId,
        issued_at: issuedAt,
        expires_at: expiresAt,
        // DynamoDB TTL attribute (set the table's TimeToLiveSpecification.AttributeName
        // to "ttl" outside this Lambda; until then the cleanup is application-side).
        ttl: Math.floor(expiresAt / 1000),
      },
      ConditionExpression: "attribute_not_exists(id)",
    }));

    return {
      statusCode: 201,
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        pairing_token: pairingToken,
        watch_id: watchId,
        expires_at: expiresAt,
      }),
    };
  } catch (err) {
    return {
      statusCode: 500,
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ error: err.message }),
    };
  }
};
