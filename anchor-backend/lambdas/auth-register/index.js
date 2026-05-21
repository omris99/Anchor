const { CognitoIdentityProviderClient, SignUpCommand } = require("@aws-sdk/client-cognito-identity-provider");

const client = new CognitoIdentityProviderClient({ region: process.env.AWS_REGION || "us-east-1" });

exports.handler = async (event) => {
  let body;
  try {
    body = JSON.parse(event.body);
  } catch {
    return { statusCode: 400, body: JSON.stringify({ error: "Invalid JSON body" }) };
  }

  const { phone_number, password, name, email, user_type } = body;

  if (!email || !password || !name || !user_type) {
    return { statusCode: 400, body: JSON.stringify({ error: "Missing required fields: email, password, name, user_type" }) };
  }

  if (!["elderly", "family_member"].includes(user_type)) {
    return { statusCode: 400, body: JSON.stringify({ error: "user_type must be 'elderly' or 'family_member'" }) };
  }

  const userAttributes = [
    { Name: "name", Value: name },
    { Name: "email", Value: email },
    { Name: "custom:user_type", Value: user_type },
  ];
  if (phone_number) userAttributes.push({ Name: "phone_number", Value: phone_number });

  try {
    const result = await client.send(new SignUpCommand({
      ClientId: process.env.COGNITO_CLIENT_ID,
      Username: email,
      Password: password,
      UserAttributes: userAttributes,
    }));

    return {
      statusCode: 201,
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        message: "User registered. Please verify your email address with the code sent to your inbox.",
        user_sub: result.UserSub,
        confirmed: result.UserConfirmed,
      }),
    };
  } catch (err) {
    const statusCode = err.name === "UsernameExistsException" ? 409 : 400;
    return {
      statusCode,
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ error: err.message }),
    };
  }
};
