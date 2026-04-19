import { apiRequest } from '../api/ApiClient';

export async function confirmRegistration(email, code) {
    return await apiRequest('/auth/confirm', {
        method: 'POST',
        body: JSON.stringify({
            email,
            confirmation_code: code,
        }),
    });
}

export async function resendCode(email) {
    return await apiRequest('/auth/resend-code', {
        method: 'POST',
        body: JSON.stringify({ email }),
    });
}
