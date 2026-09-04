// POST /users/{id}/family/approve
// Dashboard-authenticated. {id} = the elder's own user id.
// Body: { request_id }
// Flips a pending Anchor_FamilyMembers row to status="approved".

const { DynamoDBClient } = require("@aws-sdk/client-dynamodb");
const {
  DynamoDBDocumentClient,
  GetCommand,
  UpdateCommand,
} = require("@aws-sdk/lib-dynamodb");

const ddb = DynamoDBDocumentClient.from(
  new DynamoDBClient({ region: process.env.AWS_REGION || "us-east-1" })
);

const FAMILY_MEMBERS_TABLE = process.env.FAMILY_MEMBERS_TABLE || "Anchor_FamilyMembers";

exports.handler = async (event) => {
  const elderlyUserId = event.pathParameters?.id;
  if (!elderlyUserId) return reply(400, { error: "Missing user id in path" });

  let body;
  try {
    body = JSON.parse(event.body);
  } catch {
    return reply(400, { error: "Invalid JSON body" });
  }

  const requestId = body?.request_id;
  if (!requestId) return reply(400, { error: "Missing required field: request_id" });

  try {
    // Ownership check — the request must belong to this elder, not just exist.
    const existing = await ddb.send(new GetCommand({
      TableName: FAMILY_MEMBERS_TABLE,
      Key: { id: requestId },
    }));

    if (!existing.Item) return reply(404, { error: "Request not found" });
    if (existing.Item.elderly_user_id !== elderlyUserId) {
      return reply(403, { error: "This request does not belong to this user" });
    }

    await ddb.send(new UpdateCommand({
      TableName: FAMILY_MEMBERS_TABLE,
      Key: { id: requestId },
      UpdateExpression: "SET #s = :approved, approved_at = :t",
      ExpressionAttributeNames: { "#s": "status" },
      ExpressionAttributeValues: { ":approved": "approved", ":t": Date.now() },
    }));

    return reply(200, { request_id: requestId, status: "approved" });
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
