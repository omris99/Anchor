import { COGNITO } from "./env";

export const awsAuthConfig = {
    Auth: {
        Cognito: {
            region: COGNITO.region,
            userPoolId: COGNITO.userPoolId,
            userPoolClientId: COGNITO.userPoolClientId,
            loginWith: {
                email: true,
            },
        },
    },
};