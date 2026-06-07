// /users/{id}/medication-reminders  (JWT auth — dashboard only)
//
// GET    — fetch all reminders for the user
// POST   — create a new reminder; sends FCM silent push so the watch syncs immediately
// DELETE /users/{id}/medication-reminders/{medId} — remove a reminder + FCM push

const { DynamoDBClient } = require("@aws-sdk/client-dynamodb");
const {
  DynamoDBDocumentClient,
  QueryCommand,
  GetCommand,
  PutCommand,
  DeleteCommand,
} = require("@aws-sdk/lib-dynamodb");
const { SSMClient, GetParameterCommand } = require("@aws-sdk/client-ssm");
const crypto = require("crypto");
const https = require("https");

const ddb = DynamoDBDocumentClient.from(
  new DynamoDBClient({ region: process.env.AWS_REGION || "us-east-1" })
);
const ssm = new SSMClient({ region: process.env.AWS_REGION || "us-east-1" });

const MEDS_TABLE  = process.env.MEDS_TABLE  || "Anchor_MedicationReminders";
const USERS_TABLE = process.env.USERS_TABLE || "Anchor_Users";
const FIREBASE_PROJECT_ID = process.env.FIREBASE_PROJECT_ID || "anchor-5998b";
const SSM_PARAM = process.env.SSM_FIREBASE_PARAM || "/anchor/firebase-service-account";

// Cache the service-account key for the Lambda lifetime (avoid SSM call on every request).
let cachedServiceAccount = null;

exports.handler = async (event) => {
  const method = event.requestContext?.http?.method;
  const userId = event.pathParameters?.id;

  if (!userId) {
    return reply(400, { error: "Missing user id in path" });
  }

  if (method === "GET") {
    return handleGet(userId);
  }

  if (method === "POST") {
    return handlePost(userId, event.body);
  }

  if (method === "DELETE") {
    const medId = event.pathParameters?.medId;
    return handleDelete(userId, medId);
  }

  return reply(405, { error: "Method not allowed" });
};

async function handleGet(userId) {
  try {
    const result = await ddb.send(new QueryCommand({
      TableName: MEDS_TABLE,
      KeyConditionExpression: "user_id = :u",
      ExpressionAttributeValues: { ":u": userId },
    }));
    return reply(200, { medications: result.Items || [] });
  } catch (err) {
    return reply(500, { error: err.message });
  }
}

async function handlePost(userId, rawBody) {
  let body;
  try {
    body = JSON.parse(rawBody);
  } catch {
    return reply(400, { error: "Invalid JSON body" });
  }

  const { medication_name, scheduled_time, days_of_week } = body;
  if (!medication_name || !scheduled_time) {
    return reply(400, { error: "Missing required fields: medication_name, scheduled_time" });
  }

  const item = {
    user_id: userId,
    id: crypto.randomUUID(),
    medication_name,
    scheduled_time,
    days_of_week: days_of_week ?? [0, 1, 2, 3, 4, 5, 6],
    status: "pending",
    created_at: new Date().toISOString(),
  };

  try {
    await ddb.send(new PutCommand({ TableName: MEDS_TABLE, Item: item }));
    // Best-effort: await so Lambda doesn't freeze before the push completes.
    try { await sendMedicationSyncPush(userId); } catch {}
    return reply(201, item);
  } catch (err) {
    return reply(500, { error: err.message });
  }
}

async function handleDelete(userId, medId) {
  if (!medId) {
    return reply(400, { error: "Missing medId in path" });
  }
  try {
    await ddb.send(new DeleteCommand({
      TableName: MEDS_TABLE,
      Key: { user_id: userId, id: medId },
    }));
    try { await sendMedicationDeletePush(userId, medId); } catch {}
    return reply(200, { deleted: true });
  } catch (err) {
    return reply(500, { error: err.message });
  }
}

// ─────────────────────────── FCM silent push ────────────────────────────────

async function sendFcmPush(userId, data) {
  const fcmToken = await getWatchFcmToken(userId);
  if (!fcmToken) {
    console.log(`[FCM] No watch_fcm_token for user ${userId} — skipping push`);
    return;
  }
  const accessToken = await getFcmAccessToken();
  if (!accessToken) {
    console.log("[FCM] Failed to obtain FCM access token");
    return;
  }
  console.log(`[FCM] Sending ${data.type} push to user ${userId}`);
  await postJson(
    `https://fcm.googleapis.com/v1/projects/${FIREBASE_PROJECT_ID}/messages:send`,
    { message: { token: fcmToken, data, android: { priority: "high" } } },
    { Authorization: `Bearer ${accessToken}` }
  );
}

async function sendMedicationSyncPush(userId) {
  await sendFcmPush(userId, { type: "medication_sync" });
}

async function sendMedicationDeletePush(userId, medId) {
  await sendFcmPush(userId, { type: "medication_delete", med_id: medId });
}

async function getWatchFcmToken(userId) {
  const result = await ddb.send(new GetCommand({
    TableName: USERS_TABLE,
    Key: { id: userId },
    ProjectionExpression: "watch_fcm_token",
  }));
  return result.Item?.watch_fcm_token || null;
}

async function getFcmAccessToken() {
  if (!cachedServiceAccount) {
    const param = await ssm.send(new GetParameterCommand({
      Name: SSM_PARAM,
      WithDecryption: true,
    }));
    cachedServiceAccount = JSON.parse(param.Parameter.Value);
  }

  const sa = cachedServiceAccount;
  const now = Math.floor(Date.now() / 1000);

  const header  = base64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const payload = base64url(JSON.stringify({
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  }));

  const sign = crypto.createSign("RSA-SHA256");
  sign.update(`${header}.${payload}`);
  const sig = sign.sign(sa.private_key, "base64")
    .replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");

  const jwt = `${header}.${payload}.${sig}`;

  const body = new URLSearchParams({
    grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
    assertion: jwt,
  }).toString();

  const resp = await postForm("https://oauth2.googleapis.com/token", body);
  return resp.access_token || null;
}

function base64url(str) {
  return Buffer.from(str)
    .toString("base64")
    .replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function postJson(url, body, extraHeaders = {}) {
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
        ...extraHeaders,
      },
    }, (res) => {
      let raw = "";
      res.on("data", (c) => (raw += c));
      res.on("end", () => {
        try { resolve(JSON.parse(raw)); } catch { resolve({}); }
      });
    });
    req.on("error", reject);
    req.write(data);
    req.end();
  });
}

function postForm(url, body) {
  return new Promise((resolve, reject) => {
    const u = new URL(url);
    const req = https.request({
      hostname: u.hostname,
      path: u.pathname,
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
        "Content-Length": Buffer.byteLength(body),
      },
    }, (res) => {
      let raw = "";
      res.on("data", (c) => (raw += c));
      res.on("end", () => {
        try { resolve(JSON.parse(raw)); } catch { resolve({}); }
      });
    });
    req.on("error", reject);
    req.write(body);
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
