const { CognitoIdentityProviderClient, RespondToAuthChallengeCommand } = require("@aws-sdk/client-cognito-identity-provider");

const client = new CognitoIdentityProviderClient({ region: process.env.AWS_REGION || "us-east-1" });

exports.handler = async (event) => {
  let body;
  try {
    body = JSON.parse(event.body);
  } catch {
    return { statusCode: 400, body: JSON.stringify({ error: "Invalid JSON body" }) };
  }

  const { phone_number, session, mfa_code } = body;

  if (!phone_number || !session || !mfa_code) {
    return { statusCode: 400, body: JSON.stringify({ error: "Missing required fields: phone_number, session, mfa_code" }) };
  }

  try {
    const result = await client.send(new RespondToAuthChallengeCommand({
      ClientId: process.env.COGNITO_CLIENT_ID,
      ChallengeName: "SMS_MFA",
      Session: session,
      ChallengeResponses: {
        USERNAME: phone_number,
        SMS_MFA_CODE: mfa_code,
      },
    }));

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
    const statusCode = err.name === "CodeMismatchException" ? 400 : 401;
    return {
      statusCode,
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ error: err.message }),
    };
  }
};
