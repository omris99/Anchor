// GET /users/{id}/family/linked-elders
// Dashboard-authenticated. {id} = the family member's own user id.
// Returns the elder(s) this member is approved-linked to.
// No GSI exists on member_user_id (only elderly_user_id-index), so this
// scans — consistent with the existing MVP-scale lookup pattern used
// elsewhere in this API (e.g. resolveUserIdFromWatchKey).

const { DynamoDBClient } = require("@aws-sdk/client-dynamodb");
const { DynamoDBDocumentClient, ScanCommand } = require("@aws-sdk/lib-dynamodb");

const ddb = DynamoDBDocumentClient.from(
  new DynamoDBClient({ region: process.env.AWS_REGION || "us-east-1" })
);

const FAMILY_MEMBERS_TABLE = process.env.FAMILY_MEMBERS_TABLE || "Anchor_FamilyMembers";

exports.handler = async (event) => {
  const memberUserId = event.pathParameters?.id;
  if (!memberUserId) return reply(400, { error: "Missing user id in path" });

  try {
    const result = await ddb.send(new ScanCommand({
      TableName: FAMILY_MEMBERS_TABLE,
      FilterExpression: "member_user_id = :mid AND #s = :approved",
      ExpressionAttributeNames: { "#s": "status" },
      ExpressionAttributeValues: { ":mid": memberUserId, ":approved": "approved" },
    }));

    const elders = (result.Items || []).map(item => ({
      elderly_user_id: item.elderly_user_id,
      elder_name: item.elder_name || null,
    }));

    return reply(200, { elders });
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
