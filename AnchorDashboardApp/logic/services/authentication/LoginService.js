import AsyncStorage from "@react-native-async-storage/async-storage";
import { apiRequest } from '../api/ApiClient';

function parseJwtPayload(token) {
    try {
        const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
        const json = decodeURIComponent(
            atob(base64).split('').map(c => '%' + c.charCodeAt(0).toString(16).padStart(2, '0')).join('')
        );
        return JSON.parse(json);
    } catch {
        return {};
    }
}

export async function loginUser(email, password, rememberMe) {
    const data = await apiRequest('/auth/login', {
        method: 'POST',
        body: JSON.stringify({ email, password }),
    });

    const payload = parseJwtPayload(data.id_token);
    const nameParts = (payload.name || '').split(' ');

    const userData = {
        email,
        userId: payload.sub || '',
        firstName: nameParts[0] || '',
        lastName: nameParts.slice(1).join(' ') || '',
        userType: payload['custom:user_type'] || '',
        phone: payload.phone_number || '',
        idToken: data.id_token,
        accessToken: data.access_token,
        refreshToken: data.refresh_token,
    };

    if (rememberMe) {
        await AsyncStorage.setItem("email", email);
        await AsyncStorage.setItem("password", password);
    } else {
        await AsyncStorage.removeItem("email");
        await AsyncStorage.removeItem("password");
    }

    return userData;
}

export async function loadSavedCredentials() {
    const email = await AsyncStorage.getItem("email");
    const password = await AsyncStorage.getItem("password");
    if (email && password) {
        return { email, password };
    }
    return null;
}

export async function logoutUser() {
    await AsyncStorage.removeItem("email");
    await AsyncStorage.removeItem("password");
}
