// POST /checkins
// Watch-authenticated (X-Watch-Key). Writes a daily check-in event to
// Anchor_DailyCheckIns. PK=user_id, SK=timestamp (ISO string).

const { DynamoDBClient } = require("@aws-sdk/client-dynamodb");
const {
  DynamoDBDocumentClient,
  UpdateCommand,
  QueryCommand,
  ScanCommand,
} = require("@aws-sdk/lib-dynamodb");

const ddb = DynamoDBDocumentClient.from(
  new DynamoDBClient({ region: process.env.AWS_REGION || "us-east-1" })
);

const USERS_TABLE    = process.env.USERS_TABLE    || "Anchor_Users";
const CHECKINS_TABLE = process.env.CHECKINS_TABLE || "Anchor_DailyCheckIns";
const MEDS_TABLE     = process.env.MEDS_TABLE     || "Anchor_MedicationReminders";

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

  const { event_id, status, timestamp, lat, lng, battery_percent } = body;
  if (!status) {
    return reply(400, { error: "Missing required field: status" });
  }
  if (!["sad", "neutral", "happy", "no_response"].includes(status)) {
    return reply(400, { error: "status must be one of: sad, neutral, happy, no_response" });
  }

  const ts = new Date(timestamp || Date.now()).toISOString();
  const id = event_id || `${userId}#${ts}`;

  // Snapshot current medication statuses so historical reports stay accurate
  // even as the medication list changes over time.
  let medicationsSnapshot = [];
  try {
    const medsResult = await ddb.send(new QueryCommand({
      TableName: MEDS_TABLE,
      KeyConditionExpression: "user_id = :u",
      ExpressionAttributeValues: { ":u": userId },
    }));
    medicationsSnapshot = (medsResult.Items || []).map(m => ({
      id: m.id,
      name: m.medication_name,
      scheduled_time: m.scheduled_time,
      status: m.status || "pending",
    }));
  } catch {
    // Non-fatal — check-in is saved without medication data
  }

  // Use UpdateCommand so retries (which send lat/lng=null) don't overwrite
  // real location data written by the original submission.
  let updateExpr = "SET #s = :status, id = :id, event_id = :eid, medications = if_not_exists(medications, :meds)";
  const exprNames  = { "#s": "status" };
  const exprValues = { ":status": status, ":id": id, ":eid": id, ":meds": medicationsSnapshot };

  if (lat != null)             { updateExpr += ", lat = :lat";  exprValues[":lat"] = lat; }
  if (lng != null)             { updateExpr += ", lng = :lng";  exprValues[":lng"] = lng; }
  if (battery_percent != null) { updateExpr += ", battery_percent = :bp"; exprValues[":bp"] = battery_percent; }

  try {
    await ddb.send(new UpdateCommand({
      TableName: CHECKINS_TABLE,
      Key: { user_id: userId, timestamp: ts },
      UpdateExpression: updateExpr,
      ExpressionAttributeNames: exprNames,
      ExpressionAttributeValues: exprValues,
    }));
    return reply(201, { id, status, timestamp: ts, lat, lng, battery_percent });
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
