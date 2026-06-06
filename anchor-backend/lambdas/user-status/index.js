// GET /users/{id}/status  (JWT auth — dashboard)
// Returns a wellness status (green / yellow / red) with a Hebrew reason string.
// Demo-level logic based on available DynamoDB data:
//   red    → pending unacknowledged emergency in last 48h
//   red    → no check-in ever, or last check-in > 48h ago
//   yellow → missed medication in last 24h
//   yellow → last check-in > 24h ago (but < 48h)
//   green  → everything looks normal

const { DynamoDBClient } = require("@aws-sdk/client-dynamodb");
const { DynamoDBDocumentClient, QueryCommand } = require("@aws-sdk/lib-dynamodb");

const ddb = DynamoDBDocumentClient.from(
  new DynamoDBClient({ region: process.env.AWS_REGION || "us-east-1" })
);

const ALERTS_TABLE   = process.env.ALERTS_TABLE   || "Anchor_Alerts";
const CHECKINS_TABLE = process.env.CHECKINS_TABLE || "Anchor_DailyCheckIns";
const MEDS_TABLE     = process.env.MEDS_TABLE     || "Anchor_MedicationReminders";

exports.handler = async (event) => {
  const userId = event.pathParameters?.id;
  if (!userId) return reply(400, { error: "Missing user id in path" });

  const now = Date.now();
  const h24ago = new Date(now - 24 * 60 * 60 * 1000).toISOString();
  const h48ago = new Date(now - 48 * 60 * 60 * 1000).toISOString();

  try {
    // 1. Pending emergency in last 48h → red
    const alertsResult = await ddb.send(new QueryCommand({
      TableName: ALERTS_TABLE,
      KeyConditionExpression: "user_id = :u AND #ts >= :t48",
      FilterExpression: "is_emergency = :yes AND #s = :pending",
      ExpressionAttributeNames: { "#ts": "timestamp", "#s": "status" },
      ExpressionAttributeValues: {
        ":u": userId,
        ":t48": h48ago,
        ":yes": true,
        ":pending": "pending",
      },
      ScanIndexForward: false,
    }));

    if (alertsResult.Items?.length > 0) {
      return reply(200, { status: "red", reason: "קריאת חירום פעילה שלא טופלה" });
    }

    // 2. Check last check-in
    const checkinsResult = await ddb.send(new QueryCommand({
      TableName: CHECKINS_TABLE,
      KeyConditionExpression: "user_id = :u",
      ExpressionAttributeValues: { ":u": userId },
      ScanIndexForward: false,
      Limit: 1,
    }));

    const lastCheckin = checkinsResult.Items?.[0];
    if (!lastCheckin) {
      return reply(200, { status: "red", reason: "לא נרשם דיווח מעולם" });
    }

    const lastCheckinMs = new Date(lastCheckin.timestamp).getTime();
    if (lastCheckinMs < now - 48 * 60 * 60 * 1000) {
      return reply(200, { status: "red", reason: "אין דיווח מזה יותר מ-48 שעות" });
    }

    // 3. Missed medication in last 24h → yellow
    const medsResult = await ddb.send(new QueryCommand({
      TableName: MEDS_TABLE,
      KeyConditionExpression: "user_id = :u",
      FilterExpression: "#s = :missed AND status_timestamp >= :t24ms",
      ExpressionAttributeNames: { "#s": "status" },
      ExpressionAttributeValues: {
        ":u": userId,
        ":missed": "missed",
        ":t24ms": now - 24 * 60 * 60 * 1000,
      },
    }));

    if (medsResult.Items?.length > 0) {
      return reply(200, { status: "yellow", reason: "תרופה לא נלקחה היום" });
    }

    // 4. No check-in today → yellow
    if (lastCheckinMs < now - 24 * 60 * 60 * 1000) {
      return reply(200, { status: "yellow", reason: "אין דיווח מהיום" });
    }

    return reply(200, { status: "green", reason: "הכל נראה תקין" });
  } catch (err) {
    return reply(500, { error: err.message });
  }
};

function reply(statusCode, payload) {
  return {
    statusCode,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  };
}
