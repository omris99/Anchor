// POST /users/{id}/watch/pair
// Dashboard calls this after the elder scans the watch's QR.
// Body: { pairing_token }
// Path:  {id} = elderly user's Cognito sub (= Anchor_Users.id)
// Effect: validates the temp pairing record, generates a permanent
//         watch_api_key, stamps watch_id + watch_api_key onto the elderly
//         user's Anchor_Users row, deletes the temp pairing record.

const { DynamoDBClient } = require("@aws-sdk/client-dynamodb");
const {
  DynamoDBDocumentClient,
  GetCommand,
  UpdateCommand,
  DeleteCommand,
} = require("@aws-sdk/lib-dynamodb");
const crypto = require("crypto");

const ddb = DynamoDBDocumentClient.from(
  new DynamoDBClient({ region: process.env.AWS_REGION || "us-east-1" })
);

const USERS_TABLE = process.env.USERS_TABLE || "Anchor_Users";

exports.handler = async (event) => {
  // Dashboard auth — Cognito JWT idToken; API Gateway HTTP API JWT authorizer
  // populates event.requestContext.authorizer.jwt.claims.sub.
  const userId =
    event?.pathParameters?.id ||
    event?.requestContext?.authorizer?.jwt?.claims?.sub;
  if (!userId) {
    return reply(401, { error: "Missing user id (path or JWT sub required)" });
  }

  let body;
  try {
    body = JSON.parse(event.body);
  } catch {
    return reply(400, { error: "Invalid JSON body" });
  }

  const pairingToken = body?.pairing_token;
  if (!pairingToken) {
    return reply(400, { error: "Missing required field: pairing_token" });
  }

  try {
    // 1. Look up the temp pairing record
    const pairing = await ddb.send(new GetCommand({
      TableName: USERS_TABLE,
      Key: { id: pairingToken },
    }));
    if (!pairing.Item || pairing.Item.type !== "pairing") {
      return reply(404, { error: "Pairing token not found or already used" });
    }
    if (pairing.Item.expires_at && pairing.Item.expires_at < Date.now()) {
      return reply(410, { error: "Pairing token expired" });
    }

    // 2. Generate a permanent watch API key
    const watchApiKey = crypto.randomBytes(32).toString("hex");
    const watchId = pairing.Item.watch_id;
    // device_name was stored in the pairing record by watch-init-pairing (optional).
    const deviceName = pairing.Item.device_name;

    // 3. Stamp the elder's user row with watch credentials (+ friendly name if present).
    await ddb.send(new UpdateCommand({
      TableName: USERS_TABLE,
      Key: { id: userId },
      UpdateExpression: "SET watch_id = :w, watch_api_key = :k, watch_paired_at = :t"
        + (deviceName ? ", watch_name = :n" : ""),
      ExpressionAttributeValues: {
        ":w": watchId,
        ":k": watchApiKey,
        ":t": Date.now(),
        ...(deviceName ? { ":n": deviceName } : {}),
      },
    }));

    // 4. Delete the temp pairing record
    await ddb.send(new DeleteCommand({
      TableName: USERS_TABLE,
      Key: { id: pairingToken },
    }));

    return reply(200, {
      user_id: userId,
      watch_id: watchId,
      watch_api_key: watchApiKey,
      // watch_name is returned so the dashboard can set it in context immediately.
      watch_name: deviceName || null,
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
