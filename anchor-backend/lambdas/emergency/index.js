// POST /emergency
// Watch-authenticated (X-Watch-Key). Triggers an SOS or fall alert.
// Writes to Anchor_Alerts with is_emergency=true, status=pending.
// Per DECISIONS.md §8 emergency is the last endpoint; FCM fanout is deferred
// until SNS+FCM are wired (DECISIONS.md §5).

const { DynamoDBClient } = require("@aws-sdk/client-dynamodb");
const {
  DynamoDBDocumentClient,
  PutCommand,
  ScanCommand,
} = require("@aws-sdk/lib-dynamodb");
const crypto = require("crypto");

const ddb = DynamoDBDocumentClient.from(
  new DynamoDBClient({ region: process.env.AWS_REGION || "us-east-1" })
);

const USERS_TABLE = process.env.USERS_TABLE || "Anchor_Users";
const ALERTS_TABLE = process.env.ALERTS_TABLE || "Anchor_Alerts";

exports.handler = async (event) => {
  const watchKey = headerLookup(event, "x-watch-key");
  if (!watchKey) return reply(401, { error: "Missing X-Watch-Key header" });

  const userId = await resolveUserIdFromWatchKey(watchKey);
  if (!userId) return reply(401, { error: "Invalid X-Watch-Key" });

  let body;
  try { body = JSON.parse(event.body); }
  catch { return reply(400, { error: "Invalid JSON body" }); }

  const { event_id, type, timestamp, location } = body;
  if (!type) return reply(400, { error: "Missing required field: type" });
  const normalizedType = String(type).toUpperCase();
  if (!["SOS", "FALL"].includes(normalizedType)) {
    return reply(400, { error: "type must be one of: SOS, FALL (case-insensitive)" });
  }

  const ts = new Date(timestamp || Date.now()).toISOString();
  const alertId = event_id || crypto.randomUUID();

  try {
    await ddb.send(new PutCommand({
      TableName: ALERTS_TABLE,
      Item: {
        user_id: userId,
        timestamp: ts,
        id: alertId,
        type: normalizedType,
        details: location ? { location } : {},
        is_emergency: true,
        status: "pending",
      },
    }));

    // TODO Phase 5+: FCM multicast to confirmed family device tokens via SNS.

    return reply(200, { alertId, status: "pending" });
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
