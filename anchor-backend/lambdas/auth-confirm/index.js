const { CognitoIdentityProviderClient, ConfirmSignUpCommand } = require("@aws-sdk/client-cognito-identity-provider");

const client = new CognitoIdentityProviderClient({ region: process.env.AWS_REGION || "us-east-1" });

exports.handler = async (event) => {
  let body;
  try {
    body = JSON.parse(event.body);
  } catch {
    return { statusCode: 400, body: JSON.stringify({ error: "Invalid JSON body" }) };
  }

  const { phone_number, confirmation_code } = body;

  if (!phone_number || !confirmation_code) {
    return { statusCode: 400, body: JSON.stringify({ error: "Missing required fields: phone_number, confirmation_code" }) };
  }

  try {
    await client.send(new ConfirmSignUpCommand({
      ClientId: process.env.COGNITO_CLIENT_ID,
      Username: phone_number,
      ConfirmationCode: confirmation_code,
    }));

    return {
      statusCode: 200,
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ message: "Phone number verified. You can now log in." }),
    };
  } catch (err) {
    const statusCode = err.name === "CodeMismatchException" ? 400 : 400;
    return {
      statusCode,
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ error: err.message }),
    };
  }
};
