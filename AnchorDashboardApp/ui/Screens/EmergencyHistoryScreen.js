import React, { useState, useEffect, useContext } from 'react';
import {
    ActivityIndicator,
    Image,
    ImageBackground,
    ScrollView,
    StyleSheet,
    Text,
    TouchableOpacity,
    View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { UserContext } from '../../logic/contexts/UserContext';
import { apiRequest } from '../../logic/services/api/ApiClient';

const MOCK_EMERGENCY_EVENTS = [
    {
        id: '1',
        timestamp: '4/5/2026, 14:22',
        type: 'זוהתה נפילה!',
        location: null,
        status: 'acknowledged',
        isEmergency: true,
    },
    {
        id: '2',
        timestamp: '1/5/2026, 09:05',
        type: 'לחיצה על כפתור מצוקה',
        location: null,
        status: 'acknowledged',
        isEmergency: true,
        
    },
    {
        id: '3',
        timestamp: '28/4/2026, 20:47',
        type: 'זוהתה נפילה!',
        location: null,
        status: 'acknowledged',
        isEmergency: true,
    },
    {
        id: '4',
        timestamp: '20/4/2026, 11:30',
        type: 'דופק חלש מאוד',
        location: null,
        status: 'acknowledged',
        isEmergency: true,
    },
];

function formatAlertForScreen(alert) {
    const location = (alert.location?.lat != null && alert.location?.lng != null)
        ? { lat: alert.location.lat, lng: alert.location.lng }
        : null;
    return {
        id: alert.id,
        timestamp: new Date(alert.timestamp).toLocaleString('he-IL'),
        type: alert.type === 'SOS' ? 'לחיצת SOS' : 'זוהתה נפילה!',
        location,
        status: alert.status,
        isEmergency: true,
    };
}

export default function EmergencyHistoryScreen({ navigation }) {
    const { user } = useContext(UserContext);
    const [realAlerts, setRealAlerts] = useState([]);
    const [isLoading, setIsLoading] = useState(true);

    useEffect(() => {
        if (!user?.userId) { setIsLoading(false); return; }
        apiRequest(`/users/${user.userId}/emergency-alerts`)
            .then(data => setRealAlerts(data.alerts || []))
            .catch(() => {})
            .finally(() => setIsLoading(false));
    }, [user?.userId]);

    const allEvents = [
        ...realAlerts.map(a => ({ ...formatAlertForScreen(a), _key: `real-${a.id}` })),
        ...MOCK_EMERGENCY_EVENTS.map(e => ({ ...e, _key: `mock-${e.id}` })),
    ];

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
                <Text style={styles.title}>היסטוריית אירועי חירום</Text>

                <ScrollView
                    contentContainerStyle={styles.scrollContent}
                    showsVerticalScrollIndicator={false}
                >
                    {isLoading ? (
                        <ActivityIndicator size="large" color="#48AEBE" style={{ marginTop: 60 }} />
                    ) : (
                        allEvents.map(event => (
                            <TouchableOpacity
                                key={event._key}
                                style={styles.eventCard}
                                activeOpacity={0.85}
                                onPress={() => navigation.navigate('emergency-event', { event })}
                            >
                                <View style={styles.eventHeader}>
                                    <Text style={styles.warningIcon}>⚠️</Text>
                                    <Text style={styles.eventType}>{event.type}</Text>
                                </View>
                                <View style={styles.eventRow}>
                                    <Text style={styles.eventLabel}>תאריך ושעה:</Text>
                                    <Text style={styles.eventValue}>{event.timestamp}</Text>
                                </View>
                                <View style={styles.eventRow}>
                                    <Text style={styles.eventLabel}>סטטוס:</Text>
                                    <Text style={styles.acknowledgedBadge}>טופל</Text>
                                </View>
                            </TouchableOpacity>
                        ))
                    )}
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
        paddingBottom: 30,
    },
    emptyCard: {
        backgroundColor: 'rgba(255,255,255,0.88)',
        borderRadius: 16,
        padding: 24,
        alignItems: 'center',
    },
    emptyText: {
        fontSize: 16,
        color: '#888',
    },
    eventCard: {
        backgroundColor: 'rgba(255,255,255,0.88)',
        borderRadius: 16,
        padding: 16,
        marginBottom: 14,
        borderRightWidth: 4,
        borderRightColor: '#ff5a5a',
        shadowColor: '#000',
        shadowOffset: { width: 0, height: 2 },
        shadowOpacity: 0.08,
        shadowRadius: 6,
    },
    eventHeader: {
        flexDirection: 'row-reverse',
        alignItems: 'center',
        gap: 8,
        marginBottom: 10,
    },
    warningIcon: {
        fontSize: 20,
    },
    eventType: {
        fontSize: 16,
        fontWeight: '700',
        color: '#cc2222',
        textAlign: 'right',
    },
    eventRow: {
        flexDirection: 'row-reverse',
        justifyContent: 'space-between',
        alignItems: 'center',
        paddingVertical: 6,
        borderBottomWidth: 1,
        borderBottomColor: '#f0f0f0',
    },
    eventLabel: {
        fontSize: 14,
        fontWeight: '600',
        color: '#444',
    },
    eventValue: {
        fontSize: 14,
        color: '#333',
    },
    acknowledgedBadge: {
        fontSize: 13,
        fontWeight: '600',
        color: '#2a9d5c',
        backgroundColor: '#e6f7ee',
        paddingHorizontal: 10,
        paddingVertical: 3,
        borderRadius: 8,
    },
});
