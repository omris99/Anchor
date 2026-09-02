// POST /users/{id}/watch/unpair
// Dashboard calls this before sending the elder back to the QR pairing screen.
// Body: none
// Path:  {id} = elderly user's Cognito sub (= Anchor_Users.id)
// Effect: best-effort FCM "watch_unpair" push to the currently-paired watch
//         (so it clears its local key and jumps to the pairing screen live),
//         then unconditionally removes watch_id/watch_api_key/watch_paired_at/
//         watch_name/watch_fcm_token from the elder's Anchor_Users row —
//         revoking the old watch's X-Watch-Key access even if the push fails
//         (e.g. the old watch is lost/dead, which is a realistic reason to
//         re-pair in the first place).

const { DynamoDBClient } = require("@aws-sdk/client-dynamodb");
const {
  DynamoDBDocumentClient,
  GetCommand,
  UpdateCommand,
} = require("@aws-sdk/lib-dynamodb");
const { SSMClient, GetParameterCommand } = require("@aws-sdk/client-ssm");
const crypto = require("crypto");
const https = require("https");

const ddb = DynamoDBDocumentClient.from(
  new DynamoDBClient({ region: process.env.AWS_REGION || "us-east-1" })
);
const ssm = new SSMClient({ region: process.env.AWS_REGION || "us-east-1" });

const USERS_TABLE = process.env.USERS_TABLE || "Anchor_Users";
const FIREBASE_PROJECT_ID = process.env.FIREBASE_PROJECT_ID || "anchor-5998b";
const SSM_PARAM = process.env.SSM_FIREBASE_PARAM || "/anchor/firebase-service-account";

// Cache the service-account key for the Lambda lifetime (avoid SSM call on every request).
let cachedServiceAccount = null;

exports.handler = async (event) => {
  const userId =
    event?.pathParameters?.id ||
    event?.requestContext?.authorizer?.jwt?.claims?.sub;
  if (!userId) {
    return reply(401, { error: "Missing user id (path or JWT sub required)" });
  }

  try {
    try { await sendWatchUnpairPush(userId); } catch {}

    await ddb.send(new UpdateCommand({
      TableName: USERS_TABLE,
      Key: { id: userId },
      UpdateExpression:
        "REMOVE watch_id, watch_api_key, watch_paired_at, watch_name, watch_fcm_token",
    }));

    return reply(200, { unpaired: true });
  } catch (err) {
    return reply(500, { error: err.message });
  }
};

// ─────────────────────────── FCM silent push ────────────────────────────────

async function sendWatchUnpairPush(userId) {
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
  console.log(`[FCM] Sending watch_unpair push to user ${userId}`);
  await postJson(
    `https://fcm.googleapis.com/v1/projects/${FIREBASE_PROJECT_ID}/messages:send`,
    { message: { token: fcmToken, data: { type: "watch_unpair" }, android: { priority: "high" } } },
    { Authorization: `Bearer ${accessToken}` }
  );
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
