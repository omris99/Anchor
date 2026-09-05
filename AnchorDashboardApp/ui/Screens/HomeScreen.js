import React, { useContext, useState, useCallback } from 'react';
import { Image, ImageBackground, StyleSheet, Text, View } from 'react-native';
import { useFocusEffect } from '@react-navigation/native';
import ClassicButton from '../components/ClassicButton';
import WellnessStatusCard from '../components/WellnessStatusCard';
import { UserContext } from '../../logic/contexts/UserContext';
import { apiRequest } from '../../logic/services/api/ApiClient';
import { getViewedUserId } from '../../logic/utils/viewedUser';
import { SafeAreaView } from 'react-native-safe-area-context';

export default function HomeScreen({ navigation }) {
    const { user, setUser, pushToken } = useContext(UserContext);
    const greet = user ? `שלום, ${user.firstName}` : 'שלום';
    const [connectivity, setConnectivity] = useState(null);
    const isFamilyMember = user?.userType === 'family_member';

    useFocusEffect(useCallback(() => {
        if (!user?.userId) return;
        apiRequest(`/users/${user.userId}/profile`)
            .then(data => setConnectivity(data))
            .catch(() => setConnectivity(null));
    }, [user?.userId]));

    useFocusEffect(useCallback(() => {
        if (!isFamilyMember || !user?.userId) return;
        apiRequest(`/users/${user.userId}/family/linked-elders`)
            .then(data => {
                const elder = data.elders?.[0];
                setUser(prev => ({
                    ...prev,
                    linkedElderId: elder?.elderly_user_id || null,
                    linkedElderName: elder?.elder_name || null,
                }));
            })
            .catch(() => {});
    }, [isFamilyMember, user?.userId]));

    const allConnected = !!(
        pushToken &&
        connectivity?.watch_paired_at &&
        connectivity?.watch_fcm_registered &&
        connectivity?.mobile_push_registered
    );

    return (
        <ImageBackground
            source={require('../assets/wave-background.png')}
            style={styles.background}
            resizeMode="cover"
        >
        <SafeAreaView style={styles.container} edges={["top"]}>
            <Image
                source={require('../assets/anchor-logo-wide.png')}
                style={styles.logo}
                resizeMode="contain"
            />

            <Text style={styles.greeting}>{greet}</Text>
            {isFamilyMember && (
                <Text style={styles.linkedElderText}>
                    {user?.linkedElderName ? `מחובר למבוגר: ${user.linkedElderName}` : 'עדיין לא מקושר למבוגר'}
                </Text>
            )}

            <WellnessStatusCard userId={getViewedUserId(user)} />

            <View style={styles.buttons}>
                <ClassicButton
                    buttonStyle={styles.mainButton}
                    onPress={() => navigation.navigate('medical-data')}
                >
                    ניטור נתונים רפואיים
                </ClassicButton>

                <ClassicButton
                    buttonStyle={styles.mainButton}
                    onPress={() => navigation.navigate('daily-reports')}
                >
                    היסטוריית דיווחים יומיים
                </ClassicButton>

                <ClassicButton
                    buttonStyle={styles.mainButton}
                    onPress={() => navigation.navigate('reminders-hub')}
                >
                    הגדרת תזכורות
                </ClassicButton>

                <ClassicButton
                    buttonStyle={styles.mainButton}
                    onPress={() => navigation.navigate('connections')}
                >
                    ניהול קישורים
                </ClassicButton>

                <ClassicButton
                    buttonStyle={styles.mainButton}
                    onPress={() => navigation.navigate('emergency-history')}
                >
                    היסטוריית אירועי חירום
                </ClassicButton>

                <ClassicButton
                    buttonStyle={styles.settingsButton}
                    textStyle={styles.settingsText}
                    onPress={() => navigation.navigate('preferences')}
                >
                    העדפות
                </ClassicButton>
                <View style={[styles.connectivityDot, { backgroundColor: allConnected ? '#27ae60' : '#bbb' }]} />
            </View>
        </SafeAreaView>
        </ImageBackground>
    );
}

const styles = StyleSheet.create({
    background: {
        flex: 1,
    },
    container: {
        flex: 1,
        backgroundColor: 'transparent',
        paddingHorizontal: 20,
    },
    logo: {
        width: '80%',
        height: 90,
        alignSelf: 'center',
        marginTop: 20,
        marginBottom: 12,
    },
    greeting: {
        fontSize: 18,
        fontWeight: '600',
        color: '#444',
        textAlign: 'right',
        marginBottom: 10,
    },
    linkedElderText: {
        fontSize: 14,
        color: '#666',
        textAlign: 'right',
        marginBottom: 14,
    },
    buttons: {
        flex: 1,
        alignItems: 'center',
    },
    mainButton: {
        width: '85%',
        height: 56,
        borderRadius: 14,
        marginTop: 14,
    },
    settingsButton: {
        width: '85%',
        height: 56,
        borderRadius: 14,
        marginTop: 50,
        backgroundColor: '#487dbe',
        borderColor: '#4875be',
    },
    settingsText: {
        color: '#fff',
    },
    connectivityDot: {
        marginTop: 'auto',
        alignSelf: 'flex-start',
        width: 12,
        height: 10,
        borderRadius: 4,
        marginBottom: 6,
    },
});
