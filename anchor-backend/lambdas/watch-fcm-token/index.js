// POST /watch/fcm-token
// Watch-authenticated (X-Watch-Key).
// Saves the watch's FCM token to Anchor_Users so the backend can send
// silent medication-sync push notifications to the watch.

const { DynamoDBClient } = require("@aws-sdk/client-dynamodb");
const {
  DynamoDBDocumentClient,
  ScanCommand,
  UpdateCommand,
} = require("@aws-sdk/lib-dynamodb");

const ddb = DynamoDBDocumentClient.from(
  new DynamoDBClient({ region: process.env.AWS_REGION || "us-east-1" })
);

const USERS_TABLE = process.env.USERS_TABLE || "Anchor_Users";

exports.handler = async (event) => {
  const watchKey = headerLookup(event, "x-watch-key");
  if (!watchKey) {
    console.log("[FCM-token] Request missing X-Watch-Key header — headers:", JSON.stringify(Object.keys(event?.headers || {})));
    return reply(401, { error: "Missing X-Watch-Key header" });
  }

  const userId = await resolveUserIdFromWatchKey(watchKey);
  if (!userId) {
    console.log("[FCM-token] Invalid X-Watch-Key — no matching user found");
    return reply(401, { error: "Invalid X-Watch-Key" });
  }
  console.log(`[FCM-token] Registering token for user ${userId}`);

  let body;
  try {
    body = JSON.parse(event.body || "{}");
  } catch {
    return reply(400, { error: "Invalid JSON body" });
  }

  const { fcm_token } = body;
  if (!fcm_token) {
    return reply(400, { error: "Missing required field: fcm_token" });
  }

  try {
    await ddb.send(new UpdateCommand({
      TableName: USERS_TABLE,
      Key: { id: userId },
      UpdateExpression: "SET watch_fcm_token = :t",
      ExpressionAttributeValues: { ":t": fcm_token },
    }));
    return reply(200, { ok: true });
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
