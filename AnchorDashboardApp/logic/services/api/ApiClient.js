import {fetchAuthSession} from "aws-amplify/auth";

const API_BASE_URL = "https://u7cxnohim6.execute-api.us-east-1.amazonaws.com";

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