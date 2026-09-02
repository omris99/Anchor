// POST /water-reminders/{id}/confirm
// Watch-authenticated (X-Watch-Key). Marks a water reminder as drank.
// Updates Anchor_WaterReminders (PK=user_id, SK=id) status="taken".
// No dashboard/family push — water drinking is not alert-worthy the way a missed
// medication or SOS is; unlike medication-reminders-confirm this stays silent.

const { DynamoDBClient } = require("@aws-sdk/client-dynamodb");
const {
  DynamoDBDocumentClient,
  UpdateCommand,
  ScanCommand,
} = require("@aws-sdk/lib-dynamodb");

const ddb = DynamoDBDocumentClient.from(
  new DynamoDBClient({ region: process.env.AWS_REGION || "us-east-1" })
);

const USERS_TABLE = process.env.USERS_TABLE || "Anchor_Users";
const WATER_TABLE = process.env.WATER_TABLE || "Anchor_WaterReminders";

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

  try {
    await ddb.send(new UpdateCommand({
      TableName: WATER_TABLE,
      Key: { user_id: userId, id: waterReminderId },
      UpdateExpression: "SET #s = :s, status_timestamp = :t",
      ExpressionAttributeNames: { "#s": "status" },
      ExpressionAttributeValues: { ":s": "taken", ":t": ts },
      ConditionExpression: "attribute_exists(id)",
    }));

    return reply(201, { waterReminderId, timestamp: ts, status: "taken" });
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
