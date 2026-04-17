import { StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { createContext, useState } from "react";
import { Amplify } from "aws-amplify";
import { awsAuthConfig } from "./config/awsAuthConfig";
import WelcomeScreen from './ui/Screens/WelcomeScreen';
import RegisterScreen from './ui/Screens/RegisterScreen';
import ConfirmSignUpScreen from "./ui/Screens/ConfirmSignUpScreen";

Amplify.configure(awsAuthConfig);

export const UserContext = createContext(null);
const Stack = createNativeStackNavigator();

export default function App() {
    const [user, setUser] = useState(null);
    return (
        <SafeAreaView style={styles.container} edges={["top"]}>
            <UserContext.Provider value={{ user, setUser }}>
                <NavigationContainer>
                    <Stack.Navigator initialRouteName="welcome">
                        <Stack.Screen
                            name="welcome"
                            component={WelcomeScreen}
                            options={{ headerShown: false, title: 'התחברות' }}
                        />
                        <Stack.Screen
                            name="register"
                            component={RegisterScreen}
                            options={{ title: 'הרשמה' }}
                        />
                        <Stack.Screen
                            name="ConfirmSignUp"
                            component={ConfirmSignUpScreen}
                            options={{ title: 'אימות הרשמה' }}
                        />
                    </Stack.Navigator>
                </NavigationContainer>
            </UserContext.Provider>
        </SafeAreaView>
    );
}

const styles = StyleSheet.create({
    container: {
        flex: 1,
        backgroundColor: '#fff',
    },
});
