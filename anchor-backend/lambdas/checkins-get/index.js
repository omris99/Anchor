// GET /users/{id}/checkins  (JWT auth — dashboard)
//
// Returns the check-in history for a user, newest first.
// Used by DailyReportsScreen to display how the elder answered "how are you feeling".

const { DynamoDBClient } = require("@aws-sdk/client-dynamodb");
const { DynamoDBDocumentClient, QueryCommand } = require("@aws-sdk/lib-dynamodb");

const ddb = DynamoDBDocumentClient.from(
  new DynamoDBClient({ region: process.env.AWS_REGION || "us-east-1" })
);

const CHECKINS_TABLE = process.env.CHECKINS_TABLE || "Anchor_DailyCheckIns";

exports.handler = async (event) => {
  const userId = event.pathParameters?.id;
  if (!userId) {
    return reply(400, { error: "Missing user id in path" });
  }

  try {
    const result = await ddb.send(new QueryCommand({
      TableName: CHECKINS_TABLE,
      KeyConditionExpression: "user_id = :u",
      ExpressionAttributeValues: { ":u": userId },
      ScanIndexForward: false,  // newest first
      Limit: 30,
    }));
    return reply(200, { checkins: result.Items || [] });
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
