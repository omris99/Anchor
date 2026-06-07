// POST /medication-reminders/{id}/schedule-ack
// Watch-authenticated (X-Watch-Key).
// Called by the watch after it successfully schedules an alarm for a medication.
// Sets watch_scheduled_at on the DynamoDB item so the dashboard can show a green dot.

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
const MEDS_TABLE  = process.env.MEDS_TABLE  || "Anchor_MedicationReminders";

exports.handler = async (event) => {
  const watchKey = headerLookup(event, "x-watch-key");
  if (!watchKey) return reply(401, { error: "Missing X-Watch-Key header" });

  const userId = await resolveUserIdFromWatchKey(watchKey);
  if (!userId) return reply(401, { error: "Invalid X-Watch-Key" });

  const medicationId = event?.pathParameters?.id;
  if (!medicationId) return reply(400, { error: "Missing path param: id" });

  const now = Date.now();

  try {
    await ddb.send(new UpdateCommand({
      TableName: MEDS_TABLE,
      Key: { user_id: userId, id: medicationId },
      UpdateExpression: "SET watch_scheduled_at = :t",
      ExpressionAttributeValues: { ":t": now },
      ConditionExpression: "attribute_exists(id)",
    }));
    return reply(200, { medicationId, watch_scheduled_at: now });
  } catch (err) {
    const status = err.name === "ConditionalCheckFailedException" ? 404 : 500;
    return reply(status, { error: err.message });
  }
};

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

function reply(statusCode, payload) {
  return {
    statusCode,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  };
}
