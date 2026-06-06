// POST /medication-reminders/{id}/confirm
// Watch-authenticated (X-Watch-Key). Marks a medication as taken.
// Updates Anchor_MedicationReminders (PK=user_id, SK=id) status="taken".
// Also writes a transparency record to Anchor_Alerts and sends an Expo push
// notification to the elder's mobile and all linked family members.

const { DynamoDBClient } = require("@aws-sdk/client-dynamodb");
const {
  DynamoDBDocumentClient,
  UpdateCommand,
  PutCommand,
  GetCommand,
  QueryCommand,
  BatchGetCommand,
  ScanCommand,
} = require("@aws-sdk/lib-dynamodb");
const https = require("https");

const ddb = DynamoDBDocumentClient.from(
  new DynamoDBClient({ region: process.env.AWS_REGION || "us-east-1" })
);

const USERS_TABLE          = process.env.USERS_TABLE          || "Anchor_Users";
const MEDS_TABLE           = process.env.MEDS_TABLE           || "Anchor_MedicationReminders";
const ALERTS_TABLE         = process.env.ALERTS_TABLE         || "Anchor_Alerts";
const FAMILY_MEMBERS_TABLE = process.env.FAMILY_MEMBERS_TABLE || "Anchor_FamilyMembers";

exports.handler = async (event) => {
  const watchKey = headerLookup(event, "x-watch-key");
  if (!watchKey) return reply(401, { error: "Missing X-Watch-Key header" });

  const userId = await resolveUserIdFromWatchKey(watchKey);
  if (!userId) return reply(401, { error: "Invalid X-Watch-Key" });

  const medicationId = event?.pathParameters?.id;
  if (!medicationId) return reply(400, { error: "Missing path param: id" });

  let body = {};
  try { body = event.body ? JSON.parse(event.body) : {}; } catch { /* tolerate empty */ }
  const ts = body?.timestamp || Date.now();
  const tsIso = new Date(ts).toISOString();

  try {
    const updateResult = await ddb.send(new UpdateCommand({
      TableName: MEDS_TABLE,
      Key: { user_id: userId, id: medicationId },
      UpdateExpression: "SET #s = :s, status_timestamp = :t",
      ExpressionAttributeNames: { "#s": "status" },
      ExpressionAttributeValues: { ":s": "taken", ":t": ts },
      ConditionExpression: "attribute_exists(id)",
      ReturnValues: "ALL_NEW",
    }));

    const medicationName = updateResult.Attributes?.medication_name;

    await ddb.send(new PutCommand({
      TableName: ALERTS_TABLE,
      Item: {
        user_id: userId,
        timestamp: tsIso,
        type: "medication_taken",
        medication_id: medicationId,
        is_emergency: false,
        status: "resolved",
      },
    }));

    try { await sendMedicationTakenPush(userId, medicationName); } catch {}

    return reply(201, { medicationId, timestamp: ts, status: "taken" });
  } catch (err) {
    const status = err.name === "ConditionalCheckFailedException" ? 404 : 500;
    return reply(status, { error: err.message });
  }
};

async function sendMedicationTakenPush(elderId, medicationName) {
  const tokens = await collectMobileTokens(elderId);
  if (tokens.length === 0) return;

  const nameStr = medicationName || "תרופה";
  const messages = tokens.map(token => ({
    to: token,
    title: "תרופה ננטלה ✓",
    body: `התרופה ${nameStr} ננטלה`,
    data: { type: "medication_taken", medicationName: nameStr },
    priority: "normal",
    sound: "default",
  }));

  console.log(`[MedConfirm] Sending medication_taken push for "${nameStr}" to ${tokens.length} token(s)`);
  const response = await postJson("https://exp.host/--/api/v2/push/send", messages);
  console.log("[MedConfirm] Expo push response:", JSON.stringify(response));
}

async function collectMobileTokens(elderId) {
  const tokens = new Set();

  const elderResult = await ddb.send(new GetCommand({
    TableName: USERS_TABLE,
    Key: { id: elderId },
    ProjectionExpression: "mobile_fcm_token",
  }));
  if (elderResult.Item?.mobile_fcm_token) {
    tokens.add(elderResult.Item.mobile_fcm_token);
  }

  try {
    const familyResult = await ddb.send(new QueryCommand({
      TableName: FAMILY_MEMBERS_TABLE,
      IndexName: "elderly_user_id-index",
      KeyConditionExpression: "elderly_user_id = :eid",
      ExpressionAttributeValues: { ":eid": elderId },
      ProjectionExpression: "member_user_id",
    }));

    const memberIds = (familyResult.Items || [])
      .map(r => r.member_user_id)
      .filter(Boolean);

    if (memberIds.length > 0) {
      const batchResult = await ddb.send(new BatchGetCommand({
        RequestItems: {
          [USERS_TABLE]: {
            Keys: memberIds.map(id => ({ id })),
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
