import { StyleSheet, Platform } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { NavigationContainer, createNavigationContainerRef } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { useState, useEffect, useRef } from "react";
import { Amplify } from "aws-amplify";
import * as Notifications from 'expo-notifications';
import Constants from 'expo-constants';
import { awsAuthConfig } from "./config/awsAuthConfig";
import { UserContext } from "./logic/contexts/UserContext";
import { apiRequest } from "./logic/services/api/ApiClient";
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

// Show emergency notifications even when the app is in the foreground.
Notifications.setNotificationHandler({
    handleNotification: async () => ({
        shouldShowAlert: true,
        shouldPlaySound: true,
        shouldSetBadge: false,
    }),
});

const Stack = createNativeStackNavigator();
const navigationRef = createNavigationContainerRef();

function parseLocationString(str) {
    if (!str) return null;
    const parts = str.split(',');
    const lat = parseFloat(parts[0]);
    const lng = parseFloat(parts[1]);
    return (isNaN(lat) || isNaN(lng)) ? null : { lat, lng };
}

function buildEmergencyEventFromNotification(notificationData) {
    return {
        id: notificationData.alertId,
        timestamp: new Date(notificationData.timestamp).toLocaleString('he-IL'),
        type: notificationData.alertType === 'SOS' ? 'לחיצת SOS' : 'זוהתה נפילה!',
        location: parseLocationString(notificationData.location),
        status: 'pending',
        isEmergency: true,
    };
}

function routeNotification(notificationData) {
    const type = notificationData?.type;
    console.log('[Notification] routeNotification called, type:', type);
    if (!navigationRef.isReady()) {
        console.log('[Notification] navigationRef not ready yet');
        return;
    }
    if (type === 'emergency') {
        console.log('[Notification] Navigating to emergency-event screen');
        navigationRef.navigate('emergency-event', {
            event: buildEmergencyEventFromNotification(notificationData),
        });
    } else if (type === 'medication_taken') {
        console.log('[Notification] Navigating to medication-reminders screen');
        navigationRef.navigate('medication-reminders');
    } else {
        console.log('[Notification] Ignored — unknown type:', type);
    }
}

export default function App() {
    const [user, setUser] = useState(null);
    const notificationResponseSubscription = useRef(null);
    const notificationReceivedSubscription = useRef(null);

    // Register the device FCM token whenever the logged-in user changes.
    useEffect(() => {
        if (!user?.userId) return;

        async function registerDevicePushToken() {
            if (Platform.OS === 'android') {
                await Notifications.setNotificationChannelAsync('emergency', {
                    name: 'Emergency Alerts',
                    importance: Notifications.AndroidImportance.MAX,
                    sound: 'default',
                    vibrationPattern: [0, 250, 250, 250],
                });
            }

            const { status: permissionStatus } = await Notifications.requestPermissionsAsync();
            if (permissionStatus !== 'granted') return;

            try {
                const expoProjectId = Constants.expoConfig?.extra?.eas?.projectId;
                const tokenResult = await Notifications.getExpoPushTokenAsync({ projectId: expoProjectId });
                console.log('[Push] getExpoPushTokenAsync result:', JSON.stringify(tokenResult));
                const expoPushToken = tokenResult.data;
                await apiRequest(`/users/${user.userId}/mobile-fcm-token`, {
                    method: 'POST',
                    body: JSON.stringify({ fcm_token: expoPushToken }),
                });
                console.log('[Push] Expo push token registered successfully for user:', user.userId);
            } catch (error) {
                console.log('[Push] Push token registration failed:', error?.message);
            }
        }

        registerDevicePushToken();
    }, [user?.userId]);

    // Set up notification listeners once on mount.
    useEffect(() => {
        // Handle the case where the app was launched by tapping a notification
        // while the app was completely closed.
        Notifications.getLastNotificationResponseAsync().then(lastResponse => {
            console.log('[Emergency] getLastNotificationResponseAsync result:', JSON.stringify(lastResponse));
            if (lastResponse) {
                routeNotification(lastResponse.notification.request.content.data);
            }
        });

        // Handle tap on a notification while the app was in the background.
        notificationResponseSubscription.current =
            Notifications.addNotificationResponseReceivedListener(response => {
                console.log('[Emergency] addNotificationResponseReceivedListener fired, full response:', JSON.stringify(response));
                routeNotification(response.notification.request.content.data);
            });

        // Handle notification that arrives while the app is open in the foreground.
        notificationReceivedSubscription.current =
            Notifications.addNotificationReceivedListener(notification => {
                console.log('[Emergency] addNotificationReceivedListener fired, full notification:', JSON.stringify(notification));
                routeNotification(notification.request.content.data);
            });

        return () => {
            notificationResponseSubscription.current?.remove();
            notificationReceivedSubscription.current?.remove();
        };
    }, []);

    return (
        <SafeAreaView style={styles.container} edges={["top"]}>
            <UserContext.Provider value={{ user, setUser }}>
                <NavigationContainer ref={navigationRef}>
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
                            options={{ headerShown: false }}
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
