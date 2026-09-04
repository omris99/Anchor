// POST /users/{id}/family/request
// Dashboard-authenticated. {id} = the requesting family member's own user id.
// Body: { elderly_phone, member_name, member_phone }
// The elder's phone number lives in Cognito (not DynamoDB), so the elder is
// resolved via Cognito ListUsers, then a pending row is written to
// Anchor_FamilyMembers (queryable later via the elderly_user_id-index GSI).

const { DynamoDBClient } = require("@aws-sdk/client-dynamodb");
const {
  DynamoDBDocumentClient,
  PutCommand,
  QueryCommand,
} = require("@aws-sdk/lib-dynamodb");
const {
  CognitoIdentityProviderClient,
  ListUsersCommand,
} = require("@aws-sdk/client-cognito-identity-provider");
const crypto = require("crypto");

const ddb = DynamoDBDocumentClient.from(
  new DynamoDBClient({ region: process.env.AWS_REGION || "us-east-1" })
);
const cognito = new CognitoIdentityProviderClient({ region: process.env.AWS_REGION || "us-east-1" });

const FAMILY_MEMBERS_TABLE = process.env.FAMILY_MEMBERS_TABLE || "Anchor_FamilyMembers";
const USER_POOL_ID = process.env.COGNITO_USER_POOL_ID;

exports.handler = async (event) => {
  const memberUserId = event?.pathParameters?.id;
  if (!memberUserId) return reply(400, { error: "Missing user id in path" });

  let body;
  try {
    body = JSON.parse(event.body);
  } catch {
    return reply(400, { error: "Invalid JSON body" });
  }

  const { elderly_phone, member_name, member_phone } = body || {};
  if (!elderly_phone) {
    return reply(400, { error: "Missing required field: elderly_phone" });
  }

  const normalizedPhone = elderly_phone.startsWith("+")
    ? elderly_phone
    : "+972" + elderly_phone.replace(/^0/, "");

  try {
    // 1. Resolve the elder by phone number via Cognito. Phone numbers are not
    // enforced unique in this pool (username is email, MFA is off), so more
    // than one account can share a number — fetch every match and prefer the
    // elderly one instead of assuming the first result is it.
    const listResult = await cognito.send(new ListUsersCommand({
      UserPoolId: USER_POOL_ID,
      Filter: `phone_number = "${normalizedPhone}"`,
    }));

    const matches = (listResult.Users || []).map(u =>
      Object.fromEntries((u.Attributes || []).map(a => [a.Name, a.Value]))
    );
    if (matches.length === 0) {
      return reply(404, { error: "No user found with this phone number" });
    }

    const elderlyMatch = matches.find(a => a["custom:user_type"] === "elderly");
    if (!elderlyMatch) {
      return reply(400, { error: "This phone number does not belong to an elderly user" });
    }

    const elderlyUserId = elderlyMatch.sub;
    if (elderlyUserId === memberUserId) {
      return reply(400, { error: "Cannot send a link request to yourself" });
    }

    // 2. Reject duplicate requests for the same (elder, member) pair — any
    // existing row is either "pending" or "approved" (reject deletes rows).
    const existing = await ddb.send(new QueryCommand({
      TableName: FAMILY_MEMBERS_TABLE,
      IndexName: "elderly_user_id-index",
      KeyConditionExpression: "elderly_user_id = :eid",
      FilterExpression: "member_user_id = :mid",
      ExpressionAttributeValues: { ":eid": elderlyUserId, ":mid": memberUserId },
    }));
    if ((existing.Items || []).length > 0) {
      return reply(409, { error: "A link request already exists for this user" });
    }

    // 3. Create the pending request.
    const requestId = crypto.randomUUID();
    await ddb.send(new PutCommand({
      TableName: FAMILY_MEMBERS_TABLE,
      Item: {
        id: requestId,
        elderly_user_id: elderlyUserId,
        elder_name: elderlyMatch.name || null,
        member_user_id: memberUserId,
        member_name: member_name || null,
        member_phone: member_phone || null,
        status: "pending",
        requested_at: Date.now(),
      },
    }));

    return reply(201, { request_id: requestId, elderly_user_id: elderlyUserId, status: "pending" });
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
