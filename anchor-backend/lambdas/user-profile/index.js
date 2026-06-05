// GET /users/{id}/profile  (JWT auth — dashboard)
//
// Returns the user's profile fields that are not in the Cognito JWT:
// watch_id, watch_paired_at.

const { DynamoDBClient } = require("@aws-sdk/client-dynamodb");
const { DynamoDBDocumentClient, GetCommand } = require("@aws-sdk/lib-dynamodb");

const ddb = DynamoDBDocumentClient.from(
  new DynamoDBClient({ region: process.env.AWS_REGION || "us-east-1" })
);

const USERS_TABLE = process.env.USERS_TABLE || "Anchor_Users";

exports.handler = async (event) => {
  const userId = event.pathParameters?.id;
  if (!userId) {
    return reply(400, { error: "Missing user id in path" });
  }

  try {
    const result = await ddb.send(new GetCommand({
      TableName: USERS_TABLE,
      Key: { id: userId },
      ProjectionExpression: "watch_id, watch_name, watch_paired_at",
    }));

    if (!result.Item) {
      return reply(404, { error: "User not found" });
    }

    return reply(200, {
      watch_id: result.Item.watch_id || null,
      // watch_name is the friendly device name sent during init-pairing (optional field).
      watch_name: result.Item.watch_name || null,
      watch_paired_at: result.Item.watch_paired_at || null,
    });
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
