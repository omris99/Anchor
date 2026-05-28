// POST /medication-reminders/{id}/confirm
// Watch-authenticated (X-Watch-Key). Marks a medication as taken.
// Updates Anchor_MedicationReminders (PK=user_id, SK=id) status="taken".
// Also writes a transparency record to Anchor_Alerts (per API.md /medication/confirm
// pattern — "Writes a medication_taken row to Alerts").

const { DynamoDBClient } = require("@aws-sdk/client-dynamodb");
const {
  DynamoDBDocumentClient,
  UpdateCommand,
  PutCommand,
  ScanCommand,
} = require("@aws-sdk/lib-dynamodb");

const ddb = DynamoDBDocumentClient.from(
  new DynamoDBClient({ region: process.env.AWS_REGION || "us-east-1" })
);

const USERS_TABLE = process.env.USERS_TABLE || "Anchor_Users";
const MEDS_TABLE = process.env.MEDS_TABLE || "Anchor_MedicationReminders";
const ALERTS_TABLE = process.env.ALERTS_TABLE || "Anchor_Alerts";

exports.handler = async (event) => {
  const watchKey = headerLookup(event, "x-watch-key");
  if (!watchKey) return reply(401, { error: "Missing X-Watch-Key header" });

  const userId = await resolveUserIdFromWatchKey(watchKey);
  if (!userId) return reply(401, { error: "Invalid X-Watch-Key" });

  const medicationId = event?.pathParameters?.id;
  if (!medicationId) return reply(400, { error: "Missing path param: id" });

  let body = {};
  try { body = event.body ? JSON.parse(event.body) : {}; } catch { /* tolerate empty */ }
  const ts = body?.timestamp || Date.now();
  const tsIso = new Date(ts).toISOString();

  try {
    await ddb.send(new UpdateCommand({
      TableName: MEDS_TABLE,
      Key: { user_id: userId, id: medicationId },
      UpdateExpression: "SET #s = :s, status_timestamp = :t",
      ExpressionAttributeNames: { "#s": "status" },
      ExpressionAttributeValues: { ":s": "taken", ":t": ts },
      ConditionExpression: "attribute_exists(id)",
    }));

    await ddb.send(new PutCommand({
      TableName: ALERTS_TABLE,
      Item: {
        user_id: userId,
        timestamp: tsIso,
        type: "medication_taken",
        medication_id: medicationId,
        is_emergency: false,
        status: "resolved",
      },
    }));

    return reply(201, { medicationId, timestamp: ts, status: "taken" });
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
    Limit: 1,
  }));
  return result.Items?.[0]?.id || null;
}
