// GET /watch/credentials?watch_id=<watchId>
//
// No auth required — the watch has no key yet at this point.
// The watch calls this every few seconds after displaying its QR code,
// waiting for the dashboard user to complete pairing via POST /users/{id}/watch/pair.
// Returns 404 until pairing is done, then returns the permanent watch_api_key.

const { DynamoDBClient } = require("@aws-sdk/client-dynamodb");
const { DynamoDBDocumentClient, ScanCommand } = require("@aws-sdk/lib-dynamodb");

const ddb = DynamoDBDocumentClient.from(
  new DynamoDBClient({ region: process.env.AWS_REGION || "us-east-1" })
);

const USERS_TABLE = process.env.USERS_TABLE || "Anchor_Users";

exports.handler = async (event) => {
  const watchId = event?.queryStringParameters?.watch_id;
  if (!watchId) {
    return reply(400, { error: "Missing required query param: watch_id" });
  }

  try {
    // Scan for a user row that has been stamped with this watch_id and already has a key.
    // At MVP scale a GSI on watch_id would replace this Scan.
    const result = await ddb.send(new ScanCommand({
      TableName: USERS_TABLE,
      FilterExpression: "watch_id = :w AND attribute_exists(watch_api_key)",
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
