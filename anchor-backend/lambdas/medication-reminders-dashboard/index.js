// /users/{id}/medication-reminders  (JWT auth — dashboard only)
//
// GET    — fetch all reminders for the user
// POST   — create a new reminder; the watch syncs it on its next poll to
//          GET /medication-reminders/{userId}  (separate X-Watch-Key lambda)
// DELETE /users/{id}/medication-reminders/{medId} — remove a reminder

const { DynamoDBClient } = require("@aws-sdk/client-dynamodb");
const {
  DynamoDBDocumentClient,
  QueryCommand,
  PutCommand,
  DeleteCommand,
} = require("@aws-sdk/lib-dynamodb");
const crypto = require("crypto");

const ddb = DynamoDBDocumentClient.from(
  new DynamoDBClient({ region: process.env.AWS_REGION || "us-east-1" })
);

const MEDS_TABLE = process.env.MEDS_TABLE || "Anchor_MedicationReminders";

exports.handler = async (event) => {
  const method = event.requestContext?.http?.method;
  const userId = event.pathParameters?.id;

  if (!userId) {
    return reply(400, { error: "Missing user id in path" });
  }

  if (method === "GET") {
    return handleGet(userId);
  }

  if (method === "POST") {
    return handlePost(userId, event.body);
  }

  if (method === "DELETE") {
    const medId = event.pathParameters?.medId;
    return handleDelete(userId, medId);
  }

  return reply(405, { error: "Method not allowed" });
};

async function handleGet(userId) {
  try {
    const result = await ddb.send(new QueryCommand({
      TableName: MEDS_TABLE,
      KeyConditionExpression: "user_id = :u",
      ExpressionAttributeValues: { ":u": userId },
    }));
    return reply(200, { medications: result.Items || [] });
  } catch (err) {
    return reply(500, { error: err.message });
  }
}

async function handlePost(userId, rawBody) {
  let body;
  try {
    body = JSON.parse(rawBody);
  } catch {
    return reply(400, { error: "Invalid JSON body" });
  }

  const { medication_name, scheduled_time, days_of_week } = body;
  if (!medication_name || !scheduled_time) {
    return reply(400, { error: "Missing required fields: medication_name, scheduled_time" });
  }

  const item = {
    user_id: userId,
    id: crypto.randomUUID(),
    medication_name,
    scheduled_time,                          // "HH:MM" format, e.g. "08:00"
    days_of_week: days_of_week ?? [0, 1, 2, 3, 4, 5, 6],  // defaults to every day
    status: "pending",
    created_at: new Date().toISOString(),
  };

  try {
    await ddb.send(new PutCommand({ TableName: MEDS_TABLE, Item: item }));
    return reply(201, item);
  } catch (err) {
    return reply(500, { error: err.message });
  }
}

async function handleDelete(userId, medId) {
  if (!medId) {
    return reply(400, { error: "Missing medId in path" });
  }
  try {
    await ddb.send(new DeleteCommand({
      TableName: MEDS_TABLE,
      Key: { user_id: userId, id: medId },
    }));
    return reply(200, { deleted: true });
  } catch (err) {
    return reply(500, { error: err.message });
  }
}

function reply(statusCode, payload) {
  return {
    statusCode,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  };
}
