// POST /health-metrics
// Watch-authenticated (X-Watch-Key). Writes a health reading to
// Anchor_BiometricData. PK=user_id, SK=timestamp (ISO string).
// Body: { heart_rate: number, steps: number, timestamp?: number }

const { DynamoDBClient } = require("@aws-sdk/client-dynamodb");
const { DynamoDBDocumentClient, PutCommand, ScanCommand } = require("@aws-sdk/lib-dynamodb");

const ddb = DynamoDBDocumentClient.from(
  new DynamoDBClient({ region: process.env.AWS_REGION || "us-east-1" })
);

const USERS_TABLE    = process.env.USERS_TABLE    || "Anchor_Users";
const METRICS_TABLE  = process.env.METRICS_TABLE  || "Anchor_BiometricData";

exports.handler = async (event) => {
  const watchKey = headerLookup(event, "x-watch-key");
  if (!watchKey) return reply(401, { error: "Missing X-Watch-Key header" });

  const userId = await resolveUserIdFromWatchKey(watchKey);
  if (!userId) return reply(401, { error: "Invalid X-Watch-Key" });

  let body;
  try {
    body = JSON.parse(event.body);
  } catch {
    return reply(400, { error: "Invalid JSON body" });
  }

  const { heart_rate, steps, timestamp } = body;
  if (heart_rate == null && steps == null) {
    return reply(400, { error: "At least one of heart_rate or steps is required" });
  }

  const ts = new Date(timestamp || Date.now()).toISOString();

  const item = {
    user_id: userId,
    timestamp: ts,
  };
  if (heart_rate != null) item.heart_rate = heart_rate;
  if (steps != null)      item.steps = steps;

  try {
    await ddb.send(new PutCommand({ TableName: METRICS_TABLE, Item: item }));
    return reply(201, { user_id: userId, timestamp: ts, heart_rate, steps });
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
