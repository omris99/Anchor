// GET /watch/credentials?watch_id=<watchId>
//
// No auth required — the watch has no key yet at this point.
// The watch calls this every few seconds after displaying its QR code,
// waiting for the dashboard user to complete pairing via POST /users/{id}/watch/pair.
// Returns 404 until pairing is done, then returns the permanent watch_api_key.
//
// Lookup uses the watch_id-index GSI (Query), NOT a table Scan. The previous Scan
// passed `Limit: 1`, which caps the number of rows DynamoDB *evaluates* before the
// FilterExpression runs — so it inspected one arbitrary row and returned 404 once
// Anchor_Users held more than one item, even after a successful pair. That left the
// watch stuck polling forever. A Query on the GSI resolves the exact row directly.
// (Create the index first with scripts/add-watch-id-gsi.sh, then deploy this handler.)

const { DynamoDBClient } = require("@aws-sdk/client-dynamodb");
const { DynamoDBDocumentClient, QueryCommand } = require("@aws-sdk/lib-dynamodb");

const ddb = DynamoDBDocumentClient.from(
  new DynamoDBClient({ region: process.env.AWS_REGION || "us-east-1" })
);

const USERS_TABLE = process.env.USERS_TABLE || "Anchor_Users";
const WATCH_ID_INDEX = process.env.WATCH_ID_INDEX || "watch_id-index";

exports.handler = async (event) => {
  const watchId = event?.queryStringParameters?.watch_id;
  if (!watchId) {
    return reply(400, { error: "Missing required query param: watch_id" });
  }

  try {
    // Query the GSI for the user row stamped with this watch_id. The transient
    // pairing row shares the same watch_id but has no watch_api_key, so the filter
    // keeps only the paired user row. watch_id is unique per pairing session, so
    // the query returns at most one match — no Limit needed.
    const result = await ddb.send(new QueryCommand({
      TableName: USERS_TABLE,
      IndexName: WATCH_ID_INDEX,
      KeyConditionExpression: "watch_id = :w",
      FilterExpression: "attribute_exists(watch_api_key)",
      ExpressionAttributeValues: { ":w": watchId },
      ProjectionExpression: "id, watch_api_key",
    }));

    if (!result.Items?.length) {
      // Pairing not complete yet — watch should keep polling.
      return reply(404, { error: "Not paired yet" });
    }

    const { id: user_id, watch_api_key } = result.Items[0];
    return reply(200, { watch_api_key, user_id });
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
