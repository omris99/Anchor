import React, { useContext, useState, useEffect } from 'react';
import * as Location from 'expo-location';
import {
    ActivityIndicator,
    Image,
    ImageBackground,
    Linking,
    ScrollView,
    StyleSheet,
    Text,
    TouchableOpacity,
    View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { openMapLocation } from '../../logic/utils/mapUtils';
import { apiRequest } from '../../logic/services/api/ApiClient';
import { UserContext } from '../../logic/contexts/UserContext';

function useReverseGeocode(location) {
    const [address, setAddress] = useState(null);

    useEffect(() => {
        if (!location?.lat || !location?.lng) return;
        let cancelled = false;
        (async () => {
            try {
                const results = await Location.reverseGeocodeAsync(
                    { latitude: location.lat, longitude: location.lng },
                    { useGoogleMaps: false },
                );
                if (cancelled || !results?.length) return;
                const r = results[0];
                const parts = [r.name ?? r.street, r.city, r.country].filter(Boolean);
                setAddress(parts.join(', ') || null);
            } catch {
                // leave address as null — fallback to coordinates shown below
            }
        })();
        return () => { cancelled = true; };
    }, [location?.lat, location?.lng]);

    return address;
}

export default function EmergencyEventScreen({ route, navigation }) {
    const { user } = useContext(UserContext);
    const event = route?.params?.event;
    const [acknowledged, setAcknowledged] = useState(event?.status === 'acknowledged');
    const [isAcknowledging, setIsAcknowledging] = useState(false);
    const resolvedAddress = useReverseGeocode(event?.location);

    async function handleAcknowledge() {
        setIsAcknowledging(true);
        try {
            await apiRequest(`/emergency/${event.id}/acknowledge`, {
                method: 'POST',
                body: JSON.stringify({ user_id: user?.userId }),
            });
            setAcknowledged(true);
        } catch {
            // alert not found on server — acknowledge locally anyway
            setAcknowledged(true);
        } finally {
            setIsAcknowledging(false);
        }
    }

    if (!event) {
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
                    <View style={styles.card}>
                        <Text style={styles.locationText}>לא נמצאו פרטי אירוע</Text>
                    </View>
                </SafeAreaView>
            </ImageBackground>
        );
    }

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

                <ScrollView
                    contentContainerStyle={styles.scrollContent}
                    showsVerticalScrollIndicator={false}
                >
                    {/* Alert Section - Updated Style */}
                    <View style={styles.alertContainer}>
                        <View style={styles.iconCircle}>
                            <Text style={styles.alertIcon}>⚠️</Text>
                        </View>
                        <Text style={styles.alertTitle}>מצוקה זוהתה!</Text>
                        <Text style={styles.alertSubtitle}>נדרשת התייחסות מיידית</Text>
                    </View>

                    {/* Event details card */}
                    <View style={styles.card}>
                        <View style={styles.detailRow}>
                            <Text style={styles.detailLabel}>תאריך ושעה:</Text>
                            <Text style={styles.detailValue}>{event.timestamp}</Text>
                        </View>
                        <View style={[styles.detailRow, styles.detailRowLast]}>
                            <Text style={styles.detailLabel}>סוג האירוע:</Text>
                            <Text style={styles.eventTypeText}>{event.type}</Text>
                        </View>
                    </View>

                    {/* Location card */}
                    <View style={styles.card}>
                        <Text style={styles.sectionLabel}>מיקום אחרון:</Text>
                        <Text style={styles.locationText}>
                            {resolvedAddress
                                ? `📍 ${resolvedAddress}`
                                : event.location
                                    ? `📍 ${event.location.lat.toFixed(5)}, ${event.location.lng.toFixed(5)}`
                                    : '📍 מיקום לא זמין'}
                        </Text>
                        {event.location != null && (
                            <TouchableOpacity
                                style={styles.mapButton}
                                activeOpacity={0.7}
                                onPress={() => openMapLocation(event.location)}
                            >
                                <Text style={styles.mapButtonText}>מיקום על המפה</Text>
                            </TouchableOpacity>
                        )}
                    </View>

                    {/* Acknowledge button */}
                    <TouchableOpacity
                        style={[
                            styles.acknowledgeButton,
                            acknowledged && styles.acknowledgeButtonDone,
                        ]}
                        activeOpacity={0.8}
                        onPress={handleAcknowledge}
                        disabled={acknowledged || isAcknowledging}
                    >
                        {isAcknowledging
                            ? <ActivityIndicator color="#fff" />
                            : <Text style={styles.acknowledgeButtonText}>
                                {acknowledged ? '✓ הקריאה טופלה' : 'אישור קבלת קריאה'}
                              </Text>
                        }
                    </TouchableOpacity>
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
    scrollContent: {
        paddingBottom: 40,
    },
    // New Clean Alert Style
    alertContainer: {
        backgroundColor: '#FFF5F5',
        borderRadius: 24,
        paddingVertical: 32,
        paddingHorizontal: 20,
        alignItems: 'center',
        marginBottom: 20,
        // Shadow instead of Border
        shadowColor: '#CC2222',
        shadowOffset: { width: 0, height: 6 },
        shadowOpacity: 0.1,
        shadowRadius: 12,
        elevation: 4,
    },
    iconCircle: {
        backgroundColor: '#FFE0E0',
        width: 80,
        height: 80,
        borderRadius: 40,
        justifyContent: 'center',
        alignItems: 'center',
        marginBottom: 16,
    },
    alertIcon: {
        fontSize: 40,
    },
    alertTitle: {
        fontSize: 26,
        fontWeight: '800',
        color: '#CC2222',
        textAlign: 'center',
        marginBottom: 4,
    },
    alertSubtitle: {
        fontSize: 15,
        color: '#EE5555',
        fontWeight: '500',
    },
    card: {
        backgroundColor: 'rgba(255,255,255,0.92)',
        borderRadius: 20,
        padding: 18,
        marginBottom: 16,
        shadowColor: '#000',
        shadowOffset: { width: 0, height: 2 },
        shadowOpacity: 0.05,
        shadowRadius: 8,
    },
    sectionLabel: {
        fontSize: 14,
        fontWeight: '700',
        color: '#666',
        textAlign: 'right',
        marginBottom: 12,
        textTransform: 'uppercase',
    },
    detailRow: {
        flexDirection: 'row-reverse',
        justifyContent: 'space-between',
        alignItems: 'center',
        paddingVertical: 12,
        borderBottomWidth: 1,
        borderBottomColor: '#F0F0F0',
    },
    detailRowLast: {
        borderBottomWidth: 0,
    },
    detailLabel: {
        fontSize: 15,
        fontWeight: '600',
        color: '#444',
    },
    detailValue: {
        fontSize: 15,
        color: '#333',
    },
    eventTypeText: {
        fontSize: 16,
        fontWeight: '800',
        color: '#CC2222',
    },
    locationText: {
        fontSize: 15,
        color: '#555',
        fontWeight: '500',
        textAlign: 'right',
        marginBottom: 14,
    },
    mapButton: {
        backgroundColor: '#48AEBE',
        borderRadius: 12,
        height: 46,
        justifyContent: 'center',
        alignItems: 'center',
    },
    mapButtonText: {
        fontSize: 15,
        fontWeight: '700',
        color: '#fff',
    },
    acknowledgeButton: {
        backgroundColor: '#2A9D5C',
        borderRadius: 18,
        height: 60,
        justifyContent: 'center',
        alignItems: 'center',
        shadowColor: '#2A9D5C',
        shadowOffset: { width: 0, height: 4 },
        shadowOpacity: 0.3,
        shadowRadius: 8,
        marginTop: 10,
    },
    acknowledgeButtonDone: {
        backgroundColor: '#9E9E9E',
        shadowOpacity: 0,
    },
    acknowledgeButtonText: {
        fontSize: 18,
        fontWeight: '700',
        color: '#FFF',
    },
});