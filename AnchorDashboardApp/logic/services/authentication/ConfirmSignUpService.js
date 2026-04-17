import { resendSignUpCode } from "aws-amplify/auth";
import { apiRequest } from '../api/ApiClient';

export async function confirmRegistration(phone_number, code) {
    return await apiRequest('/auth/confirm', {
        method: 'POST',
        body: JSON.stringify({
            phone_number,
            confirmation_code: code,
        }),
    });
}

export async function resendCode(phone_number) {
    return await resendSignUpCode({ username: phone_number });
}
