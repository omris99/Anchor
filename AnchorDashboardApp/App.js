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
import HomeScreen from "./ui/Screens/HomeScreen";
import MedicationRemindersScreen from "./ui/Screens/MedicationRemindersScreen";
import HealthDataScreen from "./ui/Screens/HealthDataScreen";
import DailyReportsScreen from "./ui/Screens/DailyReportsScreen";
import EmergencyHistoryScreen from "./ui/Screens/EmergencyHistoryScreen";
import EmergencyEventScreen from "./ui/Screens/EmergencyEventScreen";
import PreferencesScreen from "./ui/Screens/PreferencesScreen";
import LinkManagementScreen from "./ui/Screens/LinkManagementScreen";
import WatchPairingScreen from "./ui/Screens/WatchPairingScreen";

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
                        <Stack.Screen
                            name="main-tabs"
                            component={HomeScreen}
                            options={{ headerShown: false }}
                        />
                        <Stack.Screen
                            name="medication-reminders"
                            component={MedicationRemindersScreen}
                            options={{ headerShown: false }}
                        />
                        <Stack.Screen
                            name="medical-data"
                            component={HealthDataScreen}
                            options={{ headerShown: false }}
                        />
                        <Stack.Screen
                            name="daily-reports"
                            component={DailyReportsScreen}
                            options={{ headerShown: false }}
                        />
                        <Stack.Screen
                            name="emergency-history"
                            component={EmergencyHistoryScreen}
                            options={{ headerShown: false }}
                        />
                        <Stack.Screen
                            name="emergency-event"
                            component={EmergencyEventScreen}
                            options={{ headerShown: false }}
                        />
                        <Stack.Screen
                            name="preferences"
                            component={PreferencesScreen}
                            options={{ headerShown: false }}
                        />
                        <Stack.Screen
                            name="connections"
                            component={LinkManagementScreen}
                            options={{ headerShown: false }}
                        />
                        <Stack.Screen
                            name="watch-pairing"
                            component={WatchPairingScreen}
                            options={{ headerShown: false }}
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
