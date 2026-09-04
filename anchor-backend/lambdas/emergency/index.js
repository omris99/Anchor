// POST /emergency
// Watch-authenticated (X-Watch-Key). Triggers an SOS or fall alert.
// Writes to Anchor_Alerts with is_emergency=true, status=pending.
// Then sends Expo push notifications to the elder's own mobile and all linked
// family members' mobiles via mobile_fcm_token (stores Expo Push Token).

const { DynamoDBClient } = require("@aws-sdk/client-dynamodb");
const {
  DynamoDBDocumentClient,
  PutCommand,
  GetCommand,
  ScanCommand,
  QueryCommand,
  BatchGetCommand,
} = require("@aws-sdk/lib-dynamodb");
const crypto = require("crypto");
const https  = require("https");

const ddb = DynamoDBDocumentClient.from(
  new DynamoDBClient({ region: process.env.AWS_REGION || "us-east-1" })
);

const USERS_TABLE          = process.env.USERS_TABLE          || "Anchor_Users";
const ALERTS_TABLE         = process.env.ALERTS_TABLE         || "Anchor_Alerts";
const FAMILY_MEMBERS_TABLE = process.env.FAMILY_MEMBERS_TABLE || "Anchor_FamilyMembers";

exports.handler = async (event) => {
  const watchKey = headerLookup(event, "x-watch-key");
  if (!watchKey) return reply(401, { error: "Missing X-Watch-Key header" });

  const userId = await resolveUserIdFromWatchKey(watchKey);
  if (!userId) return reply(401, { error: "Invalid X-Watch-Key" });

  let body;
  try { body = JSON.parse(event.body); }
  catch { return reply(400, { error: "Invalid JSON body" }); }

  const { event_id, type, timestamp, lat, lng } = body;
  const location = (lat != null && lng != null) ? { lat, lng } : null;
  if (!type) return reply(400, { error: "Missing required field: type" });
  const normalizedType = String(type).toUpperCase();
  if (!["SOS", "FALL"].includes(normalizedType)) {
    return reply(400, { error: "type must be one of: SOS, FALL (case-insensitive)" });
  }

  const ts = new Date(timestamp || Date.now()).toISOString();
  const alertId = event_id || crypto.randomUUID();

  try {
    await ddb.send(new PutCommand({
      TableName: ALERTS_TABLE,
      Item: {
        user_id: userId,
        timestamp: ts,
        id: alertId,
        type: normalizedType,
        details: location ? { location } : {},
        is_emergency: true,
        status: "pending",
      },
    }));

    // Await the push so Lambda doesn't terminate before the HTTP request completes.
    await sendEmergencyPush(userId, alertId, normalizedType, ts, location);

    return reply(200, { alertId, status: "pending" });
  } catch (err) {
    return reply(500, { error: err.message });
  }
};

async function sendEmergencyPush(elderId, alertId, alertType, timestamp, location) {
  const tokens = await collectMobileTokens(elderId);
  console.log(`[Emergency] Collected ${tokens.length} push token(s) for elder ${elderId}:`, tokens);
  if (tokens.length === 0) return;

  const locationStr = location ? `${location.lat},${location.lng}` : null;

  const messages = tokens.map(expoPushToken => ({
    to: expoPushToken,
    title: "קריאת חירום!",
    body: alertType === "SOS" ? "זוהתה לחיצה על כפתור SOS" : "זוהתה נפילה!",
    data: {
      type: "emergency",
      alertId,
      alertType,
      timestamp,
      ...(locationStr ? { location: locationStr } : {}),
    },
    priority: "high",
    sound: "default",
  }));

  console.log("[Emergency] Sending Expo push, payload:", JSON.stringify(messages));
  const response = await postJson("https://exp.host/--/api/v2/push/send", messages);
  console.log("[Emergency] Expo push response:", JSON.stringify(response));
}

// Collects mobile_fcm_token from the elder themselves + all linked family members.
async function collectMobileTokens(elderId) {
  const tokens = new Set();

  // 1. Elder's own mobile token
  const elderResult = await ddb.send(new GetCommand({
    TableName: USERS_TABLE,
    Key: { id: elderId },
    ProjectionExpression: "mobile_fcm_token",
  }));
  if (elderResult.Item?.mobile_fcm_token) {
    tokens.add(elderResult.Item.mobile_fcm_token);
  }

  // 2. Family members linked to this elder
  try {
    const familyResult = await ddb.send(new QueryCommand({
      TableName: FAMILY_MEMBERS_TABLE,
      IndexName: "elderly_user_id-index",
      KeyConditionExpression: "elderly_user_id = :eid",
      FilterExpression: "#s = :approved",
      ExpressionAttributeNames: { "#s": "status" },
      ExpressionAttributeValues: { ":eid": elderId, ":approved": "approved" },
      ProjectionExpression: "member_user_id",
    }));

    const memberIds = (familyResult.Items || [])
      .map(r => r.member_user_id)
      .filter(Boolean);

    if (memberIds.length > 0) {
      const keys = memberIds.map(id => ({ id }));
      const batchResult = await ddb.send(new BatchGetCommand({
        RequestItems: {
          [USERS_TABLE]: {
            Keys: keys,
            ProjectionExpression: "mobile_fcm_token",
          },
        },
      }));
      for (const item of batchResult.Responses?.[USERS_TABLE] || []) {
        if (item.mobile_fcm_token) tokens.add(item.mobile_fcm_token);
      }
    }
  } catch {
    // Family table query failure is non-fatal
  }

  return [...tokens];
}

function postJson(url, body) {
  return new Promise((resolve, reject) => {
    const data = JSON.stringify(body);
    const u = new URL(url);
    const req = https.request({
      hostname: u.hostname,
      path: u.pathname + u.search,
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Content-Length": Buffer.byteLength(data),
        "Accept": "application/json",
        "Accept-Encoding": "gzip, deflate",
      },
    }, (res) => {
      let raw = "";
      res.on("data", (chunk) => (raw += chunk));
      res.on("end", () => {
        try { resolve(JSON.parse(raw)); } catch { resolve({}); }
      });
    });
    req.on("error", reject);
    req.write(data);
    req.end();
  });
}

function reply(statusCode, payload) {
  return {
    statusCode,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  };
}

function headerLookup(event, name) {
  const headers = event?.headers || {};
  return headers[name] || headers[name.toLowerCase()] || headers[name.toUpperCase()];
}

async function resolveUserIdFromWatchKey(watchKey) {
  const result = await ddb.send(new ScanCommand({
    TableName: USERS_TABLE,
    FilterExpression: "watch_api_key = :k",
    ExpressionAttributeValues: { ":k": watchKey },
    ProjectionExpression: "id",
  }));
  return result.Items?.[0]?.id || null;
}
