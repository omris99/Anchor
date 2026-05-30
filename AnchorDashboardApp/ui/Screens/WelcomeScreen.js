import React, {useContext, useEffect, useState} from 'react';
import {
    ActivityIndicator,
    Alert,
    Image,
    ImageBackground,
    KeyboardAvoidingView,
    Platform,
    Pressable,
    ScrollView,
    StyleSheet,
    Switch,
    Text,
    View
} from 'react-native';
import ClassicButton from "../components/ClassicButton";
import TextInputField from "../components/TextInputField";
import {UserContext} from "../../logic/contexts/UserContext";
import {loadSavedCredentials, loginUser} from "../../logic/services/authentication/LoginService";

export default function WelcomeScreen({ navigation }) {
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [rememberMe, setRememberMe] = useState(false);
    const [loading, setLoading] = useState(false);
    const { setUser } = useContext(UserContext);

    useEffect(() => {
        (async () => {
            try {
                const credentials = await loadSavedCredentials();
                if (credentials) {
                    setUsername(credentials.email);
                    setPassword(credentials.password);
                    setRememberMe(true);
                    await handleLogin(credentials.email, credentials.password, true);
                }
            } catch (err) {
                console.log("Error loading saved credentials:", err);
            }
        })();
    }, []);

    const handleLogin = async (email, pass, auto = false) => {
        if (!email || !pass) {
            Alert.alert('שגיאה', 'יש למלא את כל השדות');
            return;
        }

        setLoading(true);
        try {
            const userData = await loginUser(email, pass, rememberMe);
            setUser(userData);
            const greet = userData.gender === 'female' ? "ברוכה הבאה" : "ברוך הבא";

            if (!auto) Alert.alert('ההתחברות הצליחה', greet + ', ' + userData.firstName);
            navigation.reset({ index: 0, routes: [{ name: "main-tabs" }] });
        } catch (err) {
            if (!auto){
                if (err.code === 'auth/invalid-email') {
                    Alert.alert('שגיאה', 'האימייל שהוזן אינו תקין');
                } else if (err.code === 'auth/invalid-credential') {
                    Alert.alert('שגיאה', 'האימייל או הסיסמה שגויים');
                } else if (err.code === 'auth/network-request-failed'){
                    Alert.alert('שגיאה', `לא ניתן להתחבר לשרת כרגע. ${'\n'}בדוק את חיבור האינטרנט ונסה שוב.`);
                }else {
                    Alert.alert('שגיאה', err.message);
                }
            }
            console.log(err);
        } finally {
            setLoading(false);
        }
    };

    const handleForgotPasswordPress = () => { Alert.alert("Password forgotten"); };

    return (
        <ImageBackground
            source={require('../assets/login-bg.png')}
            style={styles.background}
            imageStyle={{ opacity: 1 }}
        >
            <KeyboardAvoidingView
                style={{ flex: 1 }}
                behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
            >
                <ScrollView contentContainerStyle={styles.container} showsVerticalScrollIndicator={false}>
                    <Image style={styles.image} source={require('../assets/logo.png')} />
                    <View style={styles.form}>
                        <Text style={styles.label}>התחברות לאיזור האישי :</Text>
                        <TextInputField
                            placeholder="אי-מייל"
                            value={username}
                            onChangeText={setUsername}
                            keyboardType="email-address"
                            autoCapitalize="none"
                        />
                        <TextInputField
                            placeholder="סיסמה"
                            secureTextEntry
                            value={password}
                            onChangeText={setPassword}
                        />

                        <View style={{ flexDirection: 'row', alignItems: 'center', marginVertical: 8 }}>

                            <Switch
                                value={rememberMe}
                                onValueChange={setRememberMe}
                            />
                            <Text style={{ marginLeft: 6 }}>זכור אותי</Text>
                            <Pressable onPress={handleForgotPasswordPress} disabled={loading}>
                                <Text style={styles.forgotPasswordText}>שכחת את הסיסמה שלך?</Text>
                            </Pressable>
                        </View>

                        <ClassicButton onPress={() => handleLogin(username, password)} disabled={loading}>
                            {loading ? (
                                <ActivityIndicator color="white" />
                            ) : (
                                <Text>התחברות</Text>
                            )}
                        </ClassicButton>

                        <ClassicButton
                            buttonStyle={{ backgroundColor: '#4A90D9', borderColor: '#2E6DA8' }}
                            onPress={() => navigation.navigate('register')}
                            disabled={loading}
                        >
                            <Text>הרשמה</Text>
                        </ClassicButton>
                    </View>
                </ScrollView>
            </KeyboardAvoidingView>
        </ImageBackground>
    );
}

const styles = StyleSheet.create({
    container: {
        flexGrow: 1,
        alignItems: 'center',
        justifyContent: 'center',
    },
    image: {
        width: 240,
        height: 280,
        resizeMode: 'contain',
        marginBottom: 20,

    },
    form: {
        width: 280,
    },
    label: {
        fontSize: 24,
        fontWeight: "500",
        marginBottom: 25,
        alignSelf: 'center',
        color: '#636262',
    },
    background: {
        flex: 1,
        alignItems: 'center',
        resizeMode: 'contain',
        width: '100%',
    },
    forgotPasswordText: {
        fontSize: 13,
        fontWeight: '500',
        textAlign: 'right',
        textDecorationLine: 'underline',
        color: '#1b73b5',
        marginLeft:15
    }
});
