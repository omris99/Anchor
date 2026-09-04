// GET /users/{id}/family/linked-members
// Dashboard-authenticated. {id} = the elder's own user id.
// Returns the family members approved-linked to this elder (for the
// "unlink" list — mirrors family-requests-get, but status=approved).

const { DynamoDBClient } = require("@aws-sdk/client-dynamodb");
const { DynamoDBDocumentClient, QueryCommand } = require("@aws-sdk/lib-dynamodb");

const ddb = DynamoDBDocumentClient.from(
  new DynamoDBClient({ region: process.env.AWS_REGION || "us-east-1" })
);

const FAMILY_MEMBERS_TABLE = process.env.FAMILY_MEMBERS_TABLE || "Anchor_FamilyMembers";

exports.handler = async (event) => {
  const elderlyUserId = event.pathParameters?.id;
  if (!elderlyUserId) return reply(400, { error: "Missing user id in path" });

  try {
    const result = await ddb.send(new QueryCommand({
      TableName: FAMILY_MEMBERS_TABLE,
      IndexName: "elderly_user_id-index",
      KeyConditionExpression: "elderly_user_id = :eid",
      FilterExpression: "#s = :approved",
      ExpressionAttributeNames: { "#s": "status" },
      ExpressionAttributeValues: { ":eid": elderlyUserId, ":approved": "approved" },
    }));

    const members = (result.Items || []).map(item => ({
      id: item.id,
      member_user_id: item.member_user_id,
      member_name: item.member_name || null,
      member_phone: item.member_phone || null,
    }));

    return reply(200, { members });
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
