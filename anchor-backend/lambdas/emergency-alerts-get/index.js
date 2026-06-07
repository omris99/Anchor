// GET /users/{id}/emergency-alerts  (JWT auth — dashboard only)
// Returns all emergency alerts for a user, newest first.

const { DynamoDBClient } = require("@aws-sdk/client-dynamodb");
const { DynamoDBDocumentClient, QueryCommand } = require("@aws-sdk/lib-dynamodb");

const ddb = DynamoDBDocumentClient.from(
  new DynamoDBClient({ region: process.env.AWS_REGION || "us-east-1" })
);

const ALERTS_TABLE = process.env.ALERTS_TABLE || "Anchor_Alerts";

exports.handler = async (event) => {
  const userId = event.pathParameters?.id;
  if (!userId) return reply(400, { error: "Missing user id in path" });

  try {
    const result = await ddb.send(new QueryCommand({
      TableName: ALERTS_TABLE,
      KeyConditionExpression: "user_id = :u",
      ExpressionAttributeValues: { ":u": userId },
      ScanIndexForward: false,
    }));

    const alerts = (result.Items || []).map(item => ({
      id: item.id,
      timestamp: item.timestamp,
      type: item.type,
      status: item.status,
      location: item.details?.location ?? null,
    }));

    return reply(200, { alerts });
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
