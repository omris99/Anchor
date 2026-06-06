// POST /emergency/{id}/acknowledge
// Dashboard-authenticated. Flips an Anchor_Alerts row to status=acknowledged.
// Body: { user_id: string } — the elder's user ID who owns the alert.

const { DynamoDBClient } = require("@aws-sdk/client-dynamodb");
const {
  DynamoDBDocumentClient,
  QueryCommand,
  UpdateCommand,
} = require("@aws-sdk/lib-dynamodb");

const ddb = DynamoDBDocumentClient.from(
  new DynamoDBClient({ region: process.env.AWS_REGION || "us-east-1" })
);

const ALERTS_TABLE = process.env.ALERTS_TABLE || "Anchor_Alerts";

exports.handler = async (event) => {
  const alertId = event?.pathParameters?.id;
  if (!alertId) return reply(400, { error: "Missing path param: id" });

  let body = {};
  try { body = event.body ? JSON.parse(event.body) : {}; } catch { /* tolerate empty */ }

  const userId = body?.user_id;
  if (!userId) return reply(400, { error: "Missing required field: user_id" });

  try {
    // Query all alerts for this user, filter by alert id attribute.
    // No Limit — FilterExpression is applied after Limit so Limit must be absent.
    const result = await ddb.send(new QueryCommand({
      TableName: ALERTS_TABLE,
      KeyConditionExpression: "user_id = :u",
      FilterExpression: "id = :i",
      ExpressionAttributeValues: { ":u": userId, ":i": alertId },
    }));

    const target = result.Items?.[0];
    if (!target) return reply(404, { error: "Alert not found" });

    await ddb.send(new UpdateCommand({
      TableName: ALERTS_TABLE,
      Key: { user_id: target.user_id, timestamp: target.timestamp },
      UpdateExpression: "SET #s = :s, acknowledged_at = :t",
      ExpressionAttributeNames: { "#s": "status" },
      ExpressionAttributeValues: {
        ":s": "acknowledged",
        ":t": new Date().toISOString(),
      },
    }));

    return reply(200, { alertId, status: "acknowledged" });
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
