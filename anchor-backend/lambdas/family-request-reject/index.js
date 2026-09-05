// DELETE /users/{id}/family/request/{requestId}
// Dashboard-authenticated. {id} = the elder's own user id.
// Deletes a pending Anchor_FamilyMembers row (rejects the link request).

const { DynamoDBClient } = require("@aws-sdk/client-dynamodb");
const {
  DynamoDBDocumentClient,
  GetCommand,
  DeleteCommand,
} = require("@aws-sdk/lib-dynamodb");

const ddb = DynamoDBDocumentClient.from(
  new DynamoDBClient({ region: process.env.AWS_REGION || "us-east-1" })
);

const FAMILY_MEMBERS_TABLE = process.env.FAMILY_MEMBERS_TABLE || "Anchor_FamilyMembers";

exports.handler = async (event) => {
  const elderlyUserId = event.pathParameters?.id;
  const requestId = event.pathParameters?.requestId;
  if (!elderlyUserId || !requestId) {
    return reply(400, { error: "Missing user id or request id in path" });
  }

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

    await ddb.send(new DeleteCommand({
      TableName: FAMILY_MEMBERS_TABLE,
      Key: { id: requestId },
    }));

    return reply(200, { request_id: requestId, deleted: true });
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
