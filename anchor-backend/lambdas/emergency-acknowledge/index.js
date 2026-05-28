// POST /emergency/{id}/acknowledge
// Dashboard-authenticated (Cognito JWT). Flips an Anchor_Alerts row to
// status=acknowledged. Caller must be the elderly user or a confirmed family
// member; for MVP we trust the JWT sub and rely on the application-layer
// linking having been established.

const { DynamoDBClient } = require("@aws-sdk/client-dynamodb");
const {
  DynamoDBDocumentClient,
  QueryCommand,
  UpdateCommand,
} = require("@aws-sdk/lib-dynamodb");

const ddb = DynamoDBDocumentClient.from(
  new DynamoDBClient({ region: process.env.AWS_REGION || "us-east-1" })
);

const ALERTS_TABLE = process.env.ALERTS_TABLE || "Anchor_Alerts";

exports.handler = async (event) => {
  const claims = event?.requestContext?.authorizer?.jwt?.claims;
  const callerSub = claims?.sub;
  if (!callerSub) {
    return reply(401, { error: "Missing or invalid Cognito JWT" });
  }

  const alertId = event?.pathParameters?.id;
  if (!alertId) {
    return reply(400, { error: "Missing path param: id" });
  }

  // Look up the alert by id (PK=user_id, SK=timestamp; alertId is a separate
  // attribute). MVP: scan-style Query on the small Alerts table partitioned by
  // user via repeated queries — for now, allow the caller to supply user_id in
  // body for direct PK lookup, otherwise reject.
  let body = {};
  try { body = event.body ? JSON.parse(event.body) : {}; } catch { /* tolerate empty */ }
  const userIdHint = body?.user_id || callerSub;
  const timestampHint = body?.timestamp;

  try {
    let target;
    if (timestampHint) {
      // Fast path: caller knows the exact SK.
      const direct = await ddb.send(new QueryCommand({
        TableName: ALERTS_TABLE,
        KeyConditionExpression: "user_id = :u AND #ts = :t",
        ExpressionAttributeNames: { "#ts": "timestamp" },
        ExpressionAttributeValues: { ":u": userIdHint, ":t": timestampHint },
        Limit: 1,
      }));
      target = direct.Items?.[0];
    } else {
      // Slow path: query all alerts for that user, filter by id attribute.
      const all = await ddb.send(new QueryCommand({
        TableName: ALERTS_TABLE,
        KeyConditionExpression: "user_id = :u",
        FilterExpression: "id = :i",
        ExpressionAttributeValues: { ":u": userIdHint, ":i": alertId },
        Limit: 1,
      }));
      target = all.Items?.[0];
    }

    if (!target) {
      return reply(404, { error: "Alert not found" });
    }

    await ddb.send(new UpdateCommand({
      TableName: ALERTS_TABLE,
      Key: { user_id: target.user_id, timestamp: target.timestamp },
      UpdateExpression: "SET #s = :s, acknowledged_at = :t, acknowledged_by = :b",
      ExpressionAttributeNames: { "#s": "status" },
      ExpressionAttributeValues: {
        ":s": "acknowledged",
        ":t": Date.now(),
        ":b": callerSub,
      },
    }));

    return reply(200, { alertId, status: "acknowledged" });
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
