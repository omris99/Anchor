// GET /users/{id}/family/requests
// Dashboard-authenticated. {id} = the elder's own user id.
// Returns pending family link requests waiting for this elder's approval.

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
      FilterExpression: "#s = :pending",
      ExpressionAttributeNames: { "#s": "status" },
      ExpressionAttributeValues: { ":eid": elderlyUserId, ":pending": "pending" },
    }));

    const requests = (result.Items || []).map(item => ({
      id: item.id,
      member_user_id: item.member_user_id,
      member_name: item.member_name || null,
      member_phone: item.member_phone || null,
      requested_at: item.requested_at || null,
    }));

    return reply(200, { requests });
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
