import React, {useState} from "react";
import {ActivityIndicator, Alert, StyleSheet, Text, TextInput, View} from "react-native";
import ClassicButton from "../components/ClassicButton";
import {confirmRegistration, resendCode} from "../../logic/services/authentication/ConfirmSignUpService";

export default function ConfirmSignUpScreen({ route, navigation }) {
    const [code, setCode] = useState("");
    const [loading, setLoading] = useState(false);

    const { email } = route.params;

    const handleConfirm = async () => {
        try {
            setLoading(true);

            await confirmRegistration(email, code);
            Alert.alert(
                "הצלחה",
                "האימייל אומת בהצלחה. חשבונך ממתין לאישור המנהל."
            );

            navigation.navigate("welcome");
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

            <ClassicButton
                buttonStyle={[styles.button, styles.secondaryButton]}
                onPress={handleResend}
            >
                שלח קוד מחדש
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