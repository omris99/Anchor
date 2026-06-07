// POST /users/{id}/mobile-fcm-token  (JWT auth — dashboard only)
// Saves the dashboard app's FCM push token to Anchor_Users so the backend
// can send emergency push notifications to family members and the elder.

const { DynamoDBClient } = require("@aws-sdk/client-dynamodb");
const { DynamoDBDocumentClient, UpdateCommand } = require("@aws-sdk/lib-dynamodb");

const ddb = DynamoDBDocumentClient.from(
  new DynamoDBClient({ region: process.env.AWS_REGION || "us-east-1" })
);

const USERS_TABLE = process.env.USERS_TABLE || "Anchor_Users";

exports.handler = async (event) => {
  const userId = event.pathParameters?.id;
  if (!userId) return reply(400, { error: "Missing user id in path" });

  let body;
  try { body = JSON.parse(event.body || "{}"); }
  catch { return reply(400, { error: "Invalid JSON body" }); }

  const { fcm_token } = body;
  if (!fcm_token) return reply(400, { error: "Missing required field: fcm_token" });

  try {
    await ddb.send(new UpdateCommand({
      TableName: USERS_TABLE,
      Key: { id: userId },
      UpdateExpression: "SET mobile_fcm_token = :t",
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
