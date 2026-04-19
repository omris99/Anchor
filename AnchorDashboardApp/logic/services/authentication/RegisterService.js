import { apiRequest } from '../api/ApiClient';

export async function registerUser({ email, password, firstName, lastName, phone, userType }) {
    if (!phone || !password || !firstName || !lastName || !userType || !email) {
        throw new Error("יש למלא את כל השדות");
    }

    const normalizedPhone = phone.startsWith('+')
        ? phone
        : '+972' + phone.replace(/^0/, '');

    await apiRequest('/auth/register', {
        method: 'POST',
        body: JSON.stringify({
            phone_number: normalizedPhone,
            password,
            name: `${firstName} ${lastName}`,
            email,
            user_type: userType,
        }),
    });

    return {
        nextStep: { signUpStep: "CONFIRM_SIGN_UP" },
    };
}
