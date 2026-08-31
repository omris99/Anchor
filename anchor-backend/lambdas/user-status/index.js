// GET /users/{id}/status  (JWT auth — dashboard)
// Returns a wellness status (green / yellow / red). `reason` is always one of
// exactly three fixed captions (STATUS_CAPTIONS) — never varies by which rule
// fired, and never AI-authored text. The specific reason(s) — every rule or
// AI-judged concern that currently applies — live only in `concerns`, a list
// of fixed Hebrew labels for the tap-to-open detail modal.
//
// All concern checks run unconditionally and independently (pending
// emergency, stale check-in, missed medication, no check-in today, sad mood,
// AI-judged concerns) — none of them short-circuit the others. Severity is
// then derived from the resulting set: red if pending_emergency or
// stale_checkin is present, yellow if any other concern is present, else
// green. This matters because an active emergency does not make an earlier
// sad mood irrelevant — the family member reviewing the modal should see
// everything that's currently true, not just the single worst fact.
//
// Layer 2 — OpenAI classification: the model is given the CONCERN_CATALOG
// below and the raw health-metric numbers, and may only return concern ids
// from that fixed list — never free text. This is a closed-vocabulary
// classification, not a text-generation task, specifically to avoid the
// model inventing claims not supported by the data (observed once: it wrote
// "the user is depressed" while the actual mood was "happy"). The caption
// shown on the dot itself is ALWAYS one of our own canned strings — the AI
// never authors user-facing prose. Any OpenAI failure/timeout (6s) just
// yields no extra concerns; the colored dot must never depend on an external
// API being reachable.

const { DynamoDBClient } = require("@aws-sdk/client-dynamodb");
const { DynamoDBDocumentClient, QueryCommand } = require("@aws-sdk/lib-dynamodb");
const { SSMClient, GetParameterCommand } = require("@aws-sdk/client-ssm");
const https = require("https");

const ddb = DynamoDBDocumentClient.from(
  new DynamoDBClient({ region: process.env.AWS_REGION || "us-east-1" })
);
const ssm = new SSMClient({ region: process.env.AWS_REGION || "us-east-1" });

const ALERTS_TABLE   = process.env.ALERTS_TABLE   || "Anchor_Alerts";
const CHECKINS_TABLE = process.env.CHECKINS_TABLE || "Anchor_DailyCheckIns";
const MEDS_TABLE     = process.env.MEDS_TABLE     || "Anchor_MedicationReminders";
const METRICS_TABLE  = process.env.METRICS_TABLE  || "Anchor_BiometricData";

const OPENAI_SSM_PARAM  = process.env.OPENAI_SSM_PARAM || "/anchor/openai-api-key";
const OPENAI_MODEL      = process.env.OPENAI_MODEL     || "gpt-4.1-mini";
const OPENAI_TIMEOUT_MS = 6000;

// Exactly one caption per status — shown on the dot itself. All detail about
// *why* lives only in `concerns`, surfaced in the tap-to-open modal.
const STATUS_CAPTIONS = {
  green:  "הכל תקין",
  yellow: "יש כמה דברים לבדוק",
  red:    "מצב חירום — נדרשת תשומת לב מיידית",
};

// Fixed Hebrew labels — the only text the dashboard ever shows for a concern.
// The AI (see AI_JUDGED_IDS below) may only pick ids from this catalog.
const CONCERN_CATALOG = {
  pending_emergency:    "קריאת חירום פעילה שלא טופלה",
  stale_checkin:        "אין דיווח מזה יותר מ-48 שעות",
  missed_medication:    "תרופה לא נלקחה היום",
  no_checkin_today:     "אין דיווח מהיום",
  sad_mood:             "בדיווח האחרון התקבל רגש עצוב",
  low_activity:         "מעט מאוד פעילות/צעדים היום",
  no_recent_heart_rate: "אין נתוני דופק עדכניים מהשעון",
};

// The subset of the catalog the AI is allowed to judge — the rest are plain
// deterministic facts we already compute ourselves.
const AI_JUDGED_IDS = ["low_activity", "no_recent_heart_rate"];

// Presence of either of these forces the overall status to red, regardless
// of what else is in the concerns list.
const RED_CONCERN_IDS = ["pending_emergency", "stale_checkin"];

let cachedApiKey = null;

exports.handler = async (event) => {
  const userId = event.pathParameters?.id;
  if (!userId) return reply(400, { error: "Missing user id in path" });

  const now = Date.now();
  const h48ago = new Date(now - 48 * 60 * 60 * 1000).toISOString();

  try {
    const concernIds = [];

    // Pending emergency in last 48h — independent of check-in state.
    const alertsResult = await ddb.send(new QueryCommand({
      TableName: ALERTS_TABLE,
      KeyConditionExpression: "user_id = :u AND #ts >= :t48",
      FilterExpression: "is_emergency = :yes AND #s = :pending",
      ExpressionAttributeNames: { "#ts": "timestamp", "#s": "status" },
      ExpressionAttributeValues: {
        ":u": userId,
        ":t48": h48ago,
        ":yes": true,
        ":pending": "pending",
      },
      ScanIndexForward: false,
    }));
    if (alertsResult.Items?.length > 0) concernIds.push("pending_emergency");

    // Missed medication in last 24h — independent of check-in state.
    const medsResult = await ddb.send(new QueryCommand({
      TableName: MEDS_TABLE,
      KeyConditionExpression: "user_id = :u",
      FilterExpression: "#s = :missed AND status_timestamp >= :t24ms",
      ExpressionAttributeNames: { "#s": "status" },
      ExpressionAttributeValues: {
        ":u": userId,
        ":missed": "missed",
        ":t24ms": now - 24 * 60 * 60 * 1000,
      },
    }));
    if ((medsResult.Items || []).length > 0) concernIds.push("missed_medication");

    // Last check-in — recency and mood.
    const checkinsResult = await ddb.send(new QueryCommand({
      TableName: CHECKINS_TABLE,
      KeyConditionExpression: "user_id = :u",
      ExpressionAttributeValues: { ":u": userId },
      ScanIndexForward: false,
      Limit: 1,
    }));

    const lastCheckin = checkinsResult.Items?.[0];
    let hoursSinceLastCheckin = null;
    if (lastCheckin) {
      const lastCheckinMs = new Date(lastCheckin.timestamp).getTime();
      hoursSinceLastCheckin = (now - lastCheckinMs) / (60 * 60 * 1000);
      if (hoursSinceLastCheckin > 48) concernIds.push("stale_checkin");
      else if (hoursSinceLastCheckin > 24) concernIds.push("no_checkin_today");
      if (lastCheckin.status === "sad") concernIds.push("sad_mood");
    }

    // AI classification against the fixed catalog — best-effort only.
    const latestMetric = await getLatestHealthMetric(userId).catch(() => null);
    const aiConcernIds = await getAiConcernIds({
      hoursSinceLastCheckin: hoursSinceLastCheckin === null ? null : Math.round(hoursSinceLastCheckin * 10) / 10,
      latestHeartRate: latestMetric?.heart_rate ?? null,
      latestSteps: latestMetric?.steps ?? null,
    }).catch((err) => {
      console.error("AI concern classification failed:", err.message);
      return [];
    });

    for (const id of aiConcernIds) {
      if (AI_JUDGED_IDS.includes(id) && !concernIds.includes(id)) concernIds.push(id);
    }

    const concerns = concernIds.map((id) => CONCERN_CATALOG[id]);
    const status = concernIds.some((id) => RED_CONCERN_IDS.includes(id))
      ? "red"
      : concerns.length > 0 ? "yellow" : "green";

    return reply(200, { status, reason: STATUS_CAPTIONS[status], concerns });
  } catch (err) {
    return reply(500, { error: err.message });
  }
};

// Returns a subset of AI_JUDGED_IDS — never free text.
async function getAiConcernIds(context) {
  const apiKey = await getOpenAiApiKey();
  if (!apiKey) return [];

  const catalogForPrompt = AI_JUDGED_IDS.map((id) => `${id}: ${CONCERN_CATALOG[id]}`).join("\n");

  const body = {
    model: OPENAI_MODEL,
    messages: [
      {
        role: "system",
        content:
          "אתה מסווג נתוני חיישנים של קשיש בודד מול רשימה סגורה של קטגוריות בעייתיות. " +
          "הקטגוריות האפשריות (אסור להוסיף קטגוריה שאינה ברשימה):\n" +
          catalogForPrompt +
          '\n\nהחזר אך ורק JSON בפורמט {"concern_ids": ["..."]} עם מזהי הקטגוריות (למשל "low_activity") שמתאימות לנתונים, או מערך ריק אם שום דבר לא בולט. ' +
          "אל תחזיר טקסט חופשי, אל תמציא הסברים, ואל תבחר קטגוריה שהנתונים לא תומכים בה בבירור.",
      },
      { role: "user", content: JSON.stringify(context) },
    ],
    response_format: { type: "json_object" },
    temperature: 0,
    max_tokens: 60,
  };

  const raw = await postJsonWithTimeout(
    "https://api.openai.com/v1/chat/completions",
    body,
    { Authorization: `Bearer ${apiKey}` },
    OPENAI_TIMEOUT_MS
  );

  const content = raw?.choices?.[0]?.message?.content;
  if (!content) return [];

  const parsed = JSON.parse(content);
  if (!Array.isArray(parsed.concern_ids)) return [];
  return parsed.concern_ids.filter((id) => AI_JUDGED_IDS.includes(id));
}

async function getLatestHealthMetric(userId) {
  const result = await ddb.send(new QueryCommand({
    TableName: METRICS_TABLE,
    KeyConditionExpression: "user_id = :u",
    ExpressionAttributeValues: { ":u": userId },
    ScanIndexForward: false,
    Limit: 1,
  }));
  return result.Items?.[0] || null;
}

async function getOpenAiApiKey() {
  if (cachedApiKey) return cachedApiKey;
  const param = await ssm.send(new GetParameterCommand({
    Name: OPENAI_SSM_PARAM,
    WithDecryption: true,
  }));
  cachedApiKey = param.Parameter.Value;
  return cachedApiKey;
}

function postJsonWithTimeout(url, body, extraHeaders, timeoutMs) {
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
        try { resolve(JSON.parse(raw)); } catch (e) { reject(e); }
      });
    });
    req.on("error", reject);
    req.setTimeout(timeoutMs, () => req.destroy(new Error("OpenAI request timed out")));
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
