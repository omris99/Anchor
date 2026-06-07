// GET /users/{id}/health-metrics/latest
// JWT-authenticated (dashboard). Returns the most recent health reading
// for the given user from Anchor_BiometricData.

const { DynamoDBClient } = require("@aws-sdk/client-dynamodb");
const { DynamoDBDocumentClient, QueryCommand } = require("@aws-sdk/lib-dynamodb");

const ddb = DynamoDBDocumentClient.from(
  new DynamoDBClient({ region: process.env.AWS_REGION || "us-east-1" })
);

const METRICS_TABLE = process.env.METRICS_TABLE || "Anchor_BiometricData";

exports.handler = async (event) => {
  const userId = event.pathParameters?.id;
  if (!userId) return reply(400, { error: "Missing user id in path" });

  try {
    const result = await ddb.send(new QueryCommand({
      TableName: METRICS_TABLE,
      KeyConditionExpression: "user_id = :u",
      ExpressionAttributeValues: { ":u": userId },
      ScanIndexForward: false, // newest first
      Limit: 1,
    }));

    const latest = result.Items?.[0] || null;
    return reply(200, { latest });
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
