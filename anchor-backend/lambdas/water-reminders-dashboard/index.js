// /users/{id}/water-reminders  (JWT auth — dashboard only)
//
// GET — fetch current water reminder settings (enabled, frequency_minutes,
//       active_start, active_end) + aggregate watch_scheduled status.
// PUT — save settings, regenerate the concrete Anchor_WaterReminders items for
//       the day from frequency+window, and send an FCM silent push so the
//       watch resyncs immediately (same water_sync pattern as medication_sync).

const { DynamoDBClient } = require("@aws-sdk/client-dynamodb");
const {
  DynamoDBDocumentClient,
  QueryCommand,
  GetCommand,
  UpdateCommand,
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

const WATER_TABLE = process.env.WATER_TABLE || "Anchor_WaterReminders";
const USERS_TABLE = process.env.USERS_TABLE || "Anchor_Users";
const FIREBASE_PROJECT_ID = process.env.FIREBASE_PROJECT_ID || "anchor-5998b";
const SSM_PARAM = process.env.SSM_FIREBASE_PARAM || "/anchor/firebase-service-account";

const VALID_FREQUENCIES_MINUTES = [1, 30, 60, 120, 180];
const TIME_RE = /^([01]\d|2[0-3]):([0-5]\d)$/;
const MAX_GENERATED_ITEMS = 50;

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

  if (method === "PUT") {
    return handlePut(userId, event.body);
  }

  return reply(405, { error: "Method not allowed" });
};

async function handleGet(userId) {
  try {
    const userResult = await ddb.send(new GetCommand({
      TableName: USERS_TABLE,
      Key: { id: userId },
      ProjectionExpression: "water_reminder_enabled, water_reminder_frequency_minutes, water_reminder_active_start, water_reminder_active_end",
    }));
    const u = userResult.Item || {};

    const itemsResult = await ddb.send(new QueryCommand({
      TableName: WATER_TABLE,
      KeyConditionExpression: "user_id = :u",
      ExpressionAttributeValues: { ":u": userId },
    }));
    const items = itemsResult.Items || [];
    const watchScheduled = items.length > 0 && items.every((i) => !!i.watch_scheduled_at);

    return reply(200, {
      enabled: u.water_reminder_enabled ?? false,
      frequency_minutes: u.water_reminder_frequency_minutes ?? 120,
      active_start: u.water_reminder_active_start ?? "08:00",
      active_end: u.water_reminder_active_end ?? "22:00",
      watch_scheduled: watchScheduled,
    });
  } catch (err) {
    return reply(500, { error: err.message });
  }
}

async function handlePut(userId, rawBody) {
  let body;
  try {
    body = JSON.parse(rawBody);
  } catch {
    return reply(400, { error: "Invalid JSON body" });
  }

  const { enabled, frequency_minutes, active_start, active_end } = body;
  if (typeof enabled !== "boolean") {
    return reply(400, { error: "Missing required field: enabled" });
  }
  if (!VALID_FREQUENCIES_MINUTES.includes(frequency_minutes)) {
    return reply(400, { error: "frequency_minutes must be one of " + VALID_FREQUENCIES_MINUTES.join(", ") });
  }
  if (!TIME_RE.test(active_start) || !TIME_RE.test(active_end)) {
    return reply(400, { error: "active_start/active_end must be HH:MM" });
  }
  if (active_start >= active_end) {
    return reply(400, { error: "active_start must be before active_end" });
  }
  if (enabled) {
    const itemCount = generateTimes(active_start, active_end, frequency_minutes).length;
    if (itemCount > MAX_GENERATED_ITEMS) {
      return reply(400, {
        error: `That frequency + window would create ${itemCount} reminders a day (max ${MAX_GENERATED_ITEMS}) — narrow the active window or pick a lower frequency`,
      });
    }
  }

  try {
    await ddb.send(new UpdateCommand({
      TableName: USERS_TABLE,
      Key: { id: userId },
      UpdateExpression: "SET water_reminder_enabled = :e, water_reminder_frequency_minutes = :f, water_reminder_active_start = :s, water_reminder_active_end = :end",
      ExpressionAttributeValues: {
        ":e": enabled,
        ":f": frequency_minutes,
        ":s": active_start,
        ":end": active_end,
      },
    }));

    await regenerateWaterReminderItems(userId, enabled, frequency_minutes, active_start, active_end);
    try { await sendWaterSyncPush(userId); } catch {}

    return reply(200, { enabled, frequency_minutes, active_start, active_end });
  } catch (err) {
    return reply(500, { error: err.message });
  }
}

// ─────────────────────── item regeneration (frequency → concrete times) ─────

async function regenerateWaterReminderItems(userId, enabled, frequencyMinutes, activeStart, activeEnd) {
  const existing = await ddb.send(new QueryCommand({
    TableName: WATER_TABLE,
    KeyConditionExpression: "user_id = :u",
    ExpressionAttributeValues: { ":u": userId },
    ProjectionExpression: "id",
  }));
  for (const item of existing.Items || []) {
    await ddb.send(new DeleteCommand({ TableName: WATER_TABLE, Key: { user_id: userId, id: item.id } }));
  }

  if (!enabled) return;

  const nowIso = new Date().toISOString();
  for (const scheduledTime of generateTimes(activeStart, activeEnd, frequencyMinutes)) {
    await ddb.send(new PutCommand({
      TableName: WATER_TABLE,
      Item: {
        user_id: userId,
        id: crypto.randomUUID(),
        scheduled_time: scheduledTime,
        days_of_week: [0, 1, 2, 3, 4, 5, 6],
        status: "pending",
        created_at: nowIso,
      },
    }));
  }
}

function generateTimes(activeStart, activeEnd, frequencyMinutes) {
  const [startH, startM] = activeStart.split(":").map(Number);
  const [endH, endM] = activeEnd.split(":").map(Number);
  const stepMinutes = frequencyMinutes;

  const times = [];
  for (let cur = startH * 60 + startM; cur <= endH * 60 + endM; cur += stepMinutes) {
    const h = Math.floor(cur / 60);
    const m = cur % 60;
    times.push(`${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}`);
  }
  return times;
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

async function sendWaterSyncPush(userId) {
  await sendFcmPush(userId, { type: "water_sync" });
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
