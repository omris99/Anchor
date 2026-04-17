import { signIn, signOut, getCurrentUser } from "aws-amplify/auth";
import AsyncStorage from "@react-native-async-storage/async-storage";

export async function loginUser(phone, password, rememberMe) {
    try {
        await getCurrentUser();
        await signOut();
    } catch {
        // no active session
    }

    const normalizedPhone = phone.startsWith('+')
        ? phone
        : '+972' + phone.replace(/^0/, '');

    const result = await signIn({
        username: normalizedPhone,
        password,
        options: { authFlowType: "USER_PASSWORD_AUTH" },
    });

    if (result.nextStep?.signInStep !== "DONE") {
        throw new Error("תהליך ההתחברות לא הושלם");
    }

    if (rememberMe) {
        await AsyncStorage.setItem("username", phone);
        await AsyncStorage.setItem("password", password);
    } else {
        await AsyncStorage.removeItem("username");
        await AsyncStorage.removeItem("password");
    }

    return result;
}

export async function loadSavedCredentials() {
    const savedUsername = await AsyncStorage.getItem("username");
    const savedPassword = await AsyncStorage.getItem("password");
    if (savedUsername && savedPassword) {
        return { phone: savedUsername, password: savedPassword };
    }
    return null;
}

export async function logoutUser() {
    await signOut();
    await AsyncStorage.removeItem("username");
    await AsyncStorage.removeItem("password");
}
