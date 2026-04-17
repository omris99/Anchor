const { CognitoIdentityProviderClient, InitiateAuthCommand } = require("@aws-sdk/client-cognito-identity-provider");

const client = new CognitoIdentityProviderClient({ region: process.env.AWS_REGION || "us-east-1" });

exports.handler = async (event) => {
  let body;
  try {
    body = JSON.parse(event.body);
  } catch {
    return { statusCode: 400, body: JSON.stringify({ error: "Invalid JSON body" }) };
  }

  const { phone_number, password } = body;

  if (!phone_number || !password) {
    return { statusCode: 400, body: JSON.stringify({ error: "Missing required fields: phone_number, password" }) };
  }

  try {
    const result = await client.send(new InitiateAuthCommand({
      AuthFlow: "USER_PASSWORD_AUTH",
      ClientId: process.env.COGNITO_CLIENT_ID,
      AuthParameters: {
        USERNAME: phone_number,
        PASSWORD: password,
      },
    }));

    // MFA challenge — user must verify with SMS code
    if (result.ChallengeName === "SMS_MFA") {
      return {
        statusCode: 200,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          challenge: "SMS_MFA",
          session: result.Session,
          message: "SMS code sent. Use POST /auth/verify-mfa to complete login.",
        }),
      };
    }

    // Tokens returned directly (no MFA)
    return {
      statusCode: 200,
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        access_token: result.AuthenticationResult.AccessToken,
        id_token: result.AuthenticationResult.IdToken,
        refresh_token: result.AuthenticationResult.RefreshToken,
        expires_in: result.AuthenticationResult.ExpiresIn,
      }),
    };
  } catch (err) {
    const statusCode = err.name === "NotAuthorizedException" ? 401 : 400;
    return {
      statusCode,
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ error: err.message }),
    };
  }
};
