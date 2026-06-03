// POST /users/{id}/checkins/request  (JWT auth — dashboard only)
//
// Sends a silent FCM push to the elder's watch asking them to complete
// a daily check-in. The watch launches CheckInActivity on receipt.

const { DynamoDBClient } = require("@aws-sdk/client-dynamodb");
const { DynamoDBDocumentClient, GetCommand } = require("@aws-sdk/lib-dynamodb");
const { SSMClient, GetParameterCommand } = require("@aws-sdk/client-ssm");
const crypto = require("crypto");
const https = require("https");

const ddb = DynamoDBDocumentClient.from(
  new DynamoDBClient({ region: process.env.AWS_REGION || "us-east-1" })
);
const ssm = new SSMClient({ region: process.env.AWS_REGION || "us-east-1" });

const USERS_TABLE        = process.env.USERS_TABLE        || "Anchor_Users";
const FIREBASE_PROJECT_ID = process.env.FIREBASE_PROJECT_ID || "anchor-5998b";
const SSM_PARAM          = process.env.SSM_FIREBASE_PARAM  || "/anchor/firebase-service-account";

let cachedServiceAccount = null;

exports.handler = async (event) => {
  const userId = event.pathParameters?.id;
  if (!userId) {
    return reply(400, { error: "Missing user id in path" });
  }

  const fcmToken = await getWatchFcmToken(userId);
  if (!fcmToken) {
    return reply(404, { error: "Watch not paired or FCM token not registered" });
  }

  const accessToken = await getFcmAccessToken();
  if (!accessToken) {
    return reply(502, { error: "Failed to obtain FCM access token" });
  }

  const message = {
    message: {
      token: fcmToken,
      data: { type: "request_checkin" },
      android: { priority: "high" },
    },
  };

  try {
    await postJson(
      `https://fcm.googleapis.com/v1/projects/${FIREBASE_PROJECT_ID}/messages:send`,
      message,
      { Authorization: `Bearer ${accessToken}` }
    );
    return reply(200, { sent: true });
  } catch (err) {
    return reply(502, { error: err.message });
  }
};

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
