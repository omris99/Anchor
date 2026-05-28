import React, {useContext, useState} from "react";
import {ActivityIndicator, Alert, StyleSheet, Text, TextInput, View} from "react-native";
import ClassicButton from "../components/ClassicButton";
import {confirmRegistration, resendCode} from "../../logic/services/authentication/ConfirmSignUpService";
import {loginUser} from "../../logic/services/authentication/LoginService";
import {UserContext} from "../../App";

export default function ConfirmSignUpScreen({ route, navigation }) {
    const [code, setCode] = useState("");
    const [loading, setLoading] = useState(false);
    const { setUser } = useContext(UserContext);

    const { email, password } = route.params;

    const handleConfirm = async () => {
        try {
            setLoading(true);

            await confirmRegistration(email, code);

            // D5: seamless progression — log the just-confirmed user straight into the app.
            try {
                const userData = await loginUser(email, password, false);
                setUser(userData);
                navigation.reset({ index: 0, routes: [{ name: "main-tabs" }] });
                return;
            } catch (loginError) {
                console.error(loginError);
                Alert.alert("הצלחה", "האימייל אומת בהצלחה. אנא התחבר.");
                navigation.navigate("welcome");
                return;
            }
        } catch (error) {
            console.error(error);
            Alert.alert("שגיאה", error.message || "אימות הקוד נכשל");
        } finally {
            setLoading(false);
        }
    };

    const handleResend = async () => {
        try {
            await resendCode(email);
            Alert.alert("נשלח", "קוד חדש נשלח לאימייל שלך");
        } catch (error) {
            console.error(error);
            Alert.alert("שגיאה", error.message || "שליחה מחדש נכשלה");
        }
    };

    return (
        <View style={styles.container}>
            <Text style={styles.title}>אימות חשבון</Text>
            <Text style={styles.subtitle}>
                הזן את קוד האימות שנשלח לאימייל{'\n'}{email}
            </Text>

            <TextInput
                style={styles.input}
                placeholder="קוד אימות"
                value={code}
                onChangeText={setCode}
                keyboardType="number-pad"
                autoCapitalize="none"
            />

            <ClassicButton
                buttonStyle={styles.button}
                onPress={() => !loading && handleConfirm()}
                disabled={loading}
            >
                {loading ? <ActivityIndicator color="#fff" /> : "אמת חשבון"}
            </ClassicButton>

            {/* Resend disabled: /auth/resend-code endpoint not yet implemented (coming soon). */}
            <ClassicButton
                buttonStyle={[styles.button, styles.secondaryButton]}
                onPress={handleResend}
                disabled={true}
            >
                שלח קוד מחדש (בקרוב)
            </ClassicButton>
        </View>
    );
}

const styles = StyleSheet.create({
    container: {
        flex: 1,
        justifyContent: "top",
        padding: 24,
        backgroundColor: "#fff",
    },
    title: {
        fontSize: 28,
        fontWeight: "600",
        textAlign: "center",
        marginBottom: 12,
    },
    subtitle: {
        fontSize: 16,
        textAlign: "center",
        color: "#666",
        marginBottom: 24,
    },
    input: {
        borderWidth: 1,
        borderColor: "#ccc",
        borderRadius: 10,
        padding: 14,
        marginBottom: 16,
    },
    button: {
        backgroundColor: "#836bca",
        borderColor: "#471b9e",
        marginBottom: 12,
    },
    secondaryButton: {
        backgroundColor: "#aaa",
        borderColor: "#888",
    },
});