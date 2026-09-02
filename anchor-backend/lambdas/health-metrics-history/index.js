// GET /users/{id}/health-metrics/history?days=30
// JWT-authenticated (dashboard). Returns all health readings for the given
// user within the last N days (default 30) from Anchor_BiometricData,
// oldest first.

const { DynamoDBClient } = require("@aws-sdk/client-dynamodb");
const { DynamoDBDocumentClient, QueryCommand } = require("@aws-sdk/lib-dynamodb");

const ddb = DynamoDBDocumentClient.from(
  new DynamoDBClient({ region: process.env.AWS_REGION || "us-east-1" })
);

const METRICS_TABLE = process.env.METRICS_TABLE || "Anchor_BiometricData";
const DEFAULT_DAYS = 30;

exports.handler = async (event) => {
  const userId = event.pathParameters?.id;
  if (!userId) return reply(400, { error: "Missing user id in path" });

  const days = parseInt(event.queryStringParameters?.days, 10) || DEFAULT_DAYS;
  const startTimestamp = new Date(Date.now() - days * 24 * 60 * 60 * 1000).toISOString();

  try {
    const result = await ddb.send(new QueryCommand({
      TableName: METRICS_TABLE,
      KeyConditionExpression: "user_id = :u AND #ts >= :start",
      ExpressionAttributeNames: { "#ts": "timestamp" },
      ExpressionAttributeValues: { ":u": userId, ":start": startTimestamp },
      ScanIndexForward: true, // oldest first
    }));

    return reply(200, { items: result.Items || [] });
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
