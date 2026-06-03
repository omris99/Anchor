// POST /checkins
// Watch-authenticated (X-Watch-Key). Writes a daily check-in event to
// Anchor_DailyCheckIns. PK=user_id, SK=timestamp (ISO string).

const { DynamoDBClient } = require("@aws-sdk/client-dynamodb");
const {
  DynamoDBDocumentClient,
  PutCommand,
  ScanCommand,
} = require("@aws-sdk/lib-dynamodb");

const ddb = DynamoDBDocumentClient.from(
  new DynamoDBClient({ region: process.env.AWS_REGION || "us-east-1" })
);

const USERS_TABLE = process.env.USERS_TABLE || "Anchor_Users";
const CHECKINS_TABLE = process.env.CHECKINS_TABLE || "Anchor_DailyCheckIns";

exports.handler = async (event) => {
  const watchKey = headerLookup(event, "x-watch-key");
  if (!watchKey) {
    return reply(401, { error: "Missing X-Watch-Key header" });
  }

  const userId = await resolveUserIdFromWatchKey(watchKey);
  if (!userId) {
    return reply(401, { error: "Invalid X-Watch-Key" });
  }

  let body;
  try {
    body = JSON.parse(event.body);
  } catch {
    return reply(400, { error: "Invalid JSON body" });
  }

  const { event_id, status, timestamp } = body;
  if (!status) {
    return reply(400, { error: "Missing required field: status" });
  }
  if (!["sad", "neutral", "happy", "no_response"].includes(status)) {
    return reply(400, { error: "status must be one of: sad, neutral, happy, no_response" });
  }

  const ts = new Date(timestamp || Date.now()).toISOString();
  const id = event_id || `${userId}#${ts}`;

  try {
    await ddb.send(new PutCommand({
      TableName: CHECKINS_TABLE,
      Item: {
        user_id: userId,
        timestamp: ts,
        id,
        status,
        event_id: id,
      },
    }));

    return reply(201, { id, status, timestamp: ts });
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
  // MVP: Scan Anchor_Users for matching watch_api_key. At scale, add a GSI on
  // watch_api_key and switch this to a Query.
  const result = await ddb.send(new ScanCommand({
    TableName: USERS_TABLE,
    FilterExpression: "watch_api_key = :k",
    ExpressionAttributeValues: { ":k": watchKey },
    ProjectionExpression: "id",
  }));
  return result.Items?.[0]?.id || null;
}
