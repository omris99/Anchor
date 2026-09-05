// POST /water-reminders/{id}/missed
// Watch-authenticated (X-Watch-Key). Marks a water reminder as missed.
// Updates Anchor_WaterReminders status="missed" and writes an Alert row
// (mirrors medication-reminders-missed). FCM fanout is out of scope here.

const { DynamoDBClient } = require("@aws-sdk/client-dynamodb");
const {
  DynamoDBDocumentClient,
  UpdateCommand,
  PutCommand,
  ScanCommand,
} = require("@aws-sdk/lib-dynamodb");
const crypto = require("crypto");

const ddb = DynamoDBDocumentClient.from(
  new DynamoDBClient({ region: process.env.AWS_REGION || "us-east-1" })
);

const USERS_TABLE = process.env.USERS_TABLE || "Anchor_Users";
const WATER_TABLE = process.env.WATER_TABLE || "Anchor_WaterReminders";
const ALERTS_TABLE = process.env.ALERTS_TABLE || "Anchor_Alerts";

exports.handler = async (event) => {
  const watchKey = headerLookup(event, "x-watch-key");
  if (!watchKey) return reply(401, { error: "Missing X-Watch-Key header" });

  const userId = await resolveUserIdFromWatchKey(watchKey);
  if (!userId) return reply(401, { error: "Invalid X-Watch-Key" });

  const waterReminderId = event?.pathParameters?.id;
  if (!waterReminderId) return reply(400, { error: "Missing path param: id" });

  let body = {};
  try { body = event.body ? JSON.parse(event.body) : {}; } catch { /* tolerate empty */ }
  const ts = body?.timestamp || Date.now();
  const tsIso = new Date(ts).toISOString();
  const alertId = crypto.randomUUID();

  try {
    await ddb.send(new UpdateCommand({
      TableName: WATER_TABLE,
      Key: { user_id: userId, id: waterReminderId },
      UpdateExpression: "SET #s = :s, status_timestamp = :t",
      ExpressionAttributeNames: { "#s": "status" },
      ExpressionAttributeValues: { ":s": "missed", ":t": ts },
      ConditionExpression: "attribute_exists(id)",
    }));

    await ddb.send(new PutCommand({
      TableName: ALERTS_TABLE,
      Item: {
        user_id: userId,
        timestamp: tsIso,
        id: alertId,
        type: "water_missed",
        water_reminder_id: waterReminderId,
        is_emergency: false,
        status: "pending",
      },
    }));

    return reply(201, {
      waterReminderId,
      alertId,
      status: "missed",
      timestamp: ts,
    });
  } catch (err) {
    const status = err.name === "ConditionalCheckFailedException" ? 404 : 500;
    return reply(status, { error: err.message });
  }
};

function reply(statusCode, payload) {
  return {
    statusCode,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  };
}

function headerLookup(event, name) {
  const headers = event?.headers || {};
  return headers[name] || headers[name.toLowerCase()] || headers[name.toUpperCase()];
}

async function resolveUserIdFromWatchKey(watchKey) {
  const result = await ddb.send(new ScanCommand({
    TableName: USERS_TABLE,
    FilterExpression: "watch_api_key = :k",
    ExpressionAttributeValues: { ":k": watchKey },
    ProjectionExpression: "id",
  }));
  return result.Items?.[0]?.id || null;
}
