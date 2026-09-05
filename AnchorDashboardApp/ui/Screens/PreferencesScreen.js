import React, { useContext, useState } from 'react';
import {
    Alert,
    Image,
    ImageBackground,
    ScrollView,
    StyleSheet,
    Switch,
    Text,
    TouchableOpacity,
    View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { signOut } from 'aws-amplify/auth';
import { UserContext } from '../../logic/contexts/UserContext';
import { logoutUser } from '../../logic/services/authentication/LoginService';
import ClassicButton from '../components/ClassicButton';

function ToggleRow({ label, value, onValueChange }) {
    return (
        <View style={styles.toggleRow}>
            <Switch
                value={value}
                onValueChange={onValueChange}
                trackColor={{ false: '#ccc', true: '#48AEBE' }}
                thumbColor="#fff"
            />
            <Text style={styles.toggleLabel}>{label}</Text>
        </View>
    );
}

export default function PreferencesScreen({ navigation }) {
    const { setUser } = useContext(UserContext);
    const [dailyReportsEnabled, setDailyReportsEnabled] = useState(true);
    const [morningTrackingEnabled, setMorningTrackingEnabled] = useState(true);
    const [healthMonitoringEnabled, setHealthMonitoringEnabled] = useState(true);

    return (
        <ImageBackground
            source={require('../assets/wave-background.png')}
            style={styles.background}
        >
            <SafeAreaView style={styles.container} edges={['top']}>
                <View style={styles.header}>
                    <TouchableOpacity style={styles.backButton} onPress={() => navigation.goBack()} activeOpacity={0.7}>
                        <Text style={styles.backArrow}>←</Text>
                    </TouchableOpacity>
                    <Image
                        source={require('../assets/anchor-logo-wide.png')}
                        style={styles.logo}
                        resizeMode="contain"
                    />
                    <View style={styles.headerSpacer} />
                </View>

                <Text style={styles.title}>העדפות משתמש</Text>

                <ScrollView contentContainerStyle={styles.scrollContent} keyboardShouldPersistTaps="handled">

                    {/* General toggles */}
                    <View style={styles.section}>
                        <ToggleRow
                            label="הפעל דיווחים יומיים אוטומטיים"
                            value={dailyReportsEnabled}
                            onValueChange={setDailyReportsEnabled}
                        />
                        <ToggleRow
                            label="הפעל מעקב יקיצת בוקר"
                            value={morningTrackingEnabled}
                            onValueChange={setMorningTrackingEnabled}
                        />
                        <ToggleRow
                            label="הפעל ניטור ותיעוד מדדים רפואיים"
                            value={healthMonitoringEnabled}
                            onValueChange={setHealthMonitoringEnabled}
                        />
                    </View>

                    {/* Logout */}
                    <ClassicButton
                        buttonStyle={styles.logoutButton}
                        textStyle={styles.logoutText}
                        onPress={() =>
                            Alert.alert('התנתקות', 'האם אתה בטוח שברצונך להתנתק?', [
                                { text: 'ביטול', style: 'cancel' },
                                {
                                    text: 'התנתק',
                                    style: 'destructive',
                                    onPress: async () => {
                                        await logoutUser();
                                        await signOut();
                                        setUser(null);
                                        navigation.reset({ index: 0, routes: [{ name: 'welcome' }] });
                                    },
                                },
                            ])
                        }
                    >
                        התנתקות
                    </ClassicButton>
                </ScrollView>
            </SafeAreaView>
        </ImageBackground>
    );
}

const styles = StyleSheet.create({
    background: {
        flex: 1,
        resizeMode: 'cover',
    },
    container: {
        flex: 1,
        backgroundColor: 'transparent',
        paddingHorizontal: 20,
    },
    header: {
        flexDirection: 'row',
        alignItems: 'center',
        marginTop: 16,
        marginBottom: 12,
    },
    backButton: {
        width: 40,
        height: 40,
        borderRadius: 20,
        backgroundColor: '#48AEBE',
        alignItems: 'center',
        justifyContent: 'center',
    },
    backArrow: {
        fontSize: 20,
        color: '#fff',
        fontWeight: '700',
    },
    headerSpacer: {
        width: 40,
    },
    logo: {
        flex: 1,
        height: 50,
    },
    title: {
        fontSize: 26,
        fontWeight: '700',
        color: '#48AEBE',
        textAlign: 'right',
        marginBottom: 16,
    },
    scrollContent: {
        paddingBottom: 40,
    },
    logoutButton: {
        backgroundColor: '#E53935',
        borderColor: '#b71c1c',
        marginBottom: 20,
    },
    logoutText: {
        color: '#fff',
    },
    section: {
        backgroundColor: 'rgba(255,255,255,0.88)',
        borderRadius: 16,
        padding: 16,
        marginBottom: 14,
    },
    toggleRow: {
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'flex-end',
        paddingVertical: 8,
    },
    toggleLabel: {
        fontSize: 16,
        color: '#333',
        fontWeight: '500',
        textAlign: 'right',
        flex: 1,
        marginRight: 12,
    },
});
