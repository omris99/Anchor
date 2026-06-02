import {fetchAuthSession} from "aws-amplify/auth";
import {API_BASE_URL} from "../../../config/env";

export async function apiRequest(path, options = {}) {
    let token;
    try {
        const session = await fetchAuthSession();
        token = session.tokens?.idToken?.toString();
    } catch {
        token = null;
    }

    const response = await fetch(`${API_BASE_URL}${path}`, {
        ...options,
        headers: {
            "Content-Type": "application/json",
            ...(token ? { Authorization: token } : {}),
            ...(options.headers || {}),
        },
    });

    const data = await response.json().catch(() => null);

    if (!response.ok) {
        throw new Error(data?.error || data?.message || "API request failed");
    }

    return data;
}