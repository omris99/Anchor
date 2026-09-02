// GET /water-reminders/{userId}
// Watch-authenticated (X-Watch-Key). Returns active water reminders for
// the user (resolved from X-Watch-Key; path {userId} = "me" sentinel allowed).
// Read from Anchor_WaterReminders (PK=user_id, SK=id).

const { DynamoDBClient } = require("@aws-sdk/client-dynamodb");
const {
  DynamoDBDocumentClient,
  QueryCommand,
  ScanCommand,
} = require("@aws-sdk/lib-dynamodb");

const ddb = DynamoDBDocumentClient.from(
  new DynamoDBClient({ region: process.env.AWS_REGION || "us-east-1" })
);

const USERS_TABLE = process.env.USERS_TABLE || "Anchor_Users";
const WATER_TABLE = process.env.WATER_TABLE || "Anchor_WaterReminders";

exports.handler = async (event) => {
  const watchKey = headerLookup(event, "x-watch-key");
  if (!watchKey) {
    return reply(401, { error: "Missing X-Watch-Key header" });
  }

  const userId = await resolveUserIdFromWatchKey(watchKey);
  if (!userId) {
    return reply(401, { error: "Invalid X-Watch-Key" });
  }

  const pathUser = event?.pathParameters?.userId;
  if (pathUser && pathUser !== "me" && pathUser !== userId) {
    return reply(403, { error: "X-Watch-Key does not authorize access to that user" });
  }

  try {
    const result = await ddb.send(new QueryCommand({
      TableName: WATER_TABLE,
      KeyConditionExpression: "user_id = :u",
      ExpressionAttributeValues: { ":u": userId },
    }));

    const waterReminders = (result.Items || []).map((w) => ({
      id: w.id,
      user_id: w.user_id,
      scheduled_time: w.scheduled_time,
      days_of_week: w.days_of_week || [0, 1, 2, 3, 4, 5, 6],
      status: w.status || "pending",
      status_timestamp: w.status_timestamp || null,
    }));

    return reply(200, { water_reminders: waterReminders });
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
