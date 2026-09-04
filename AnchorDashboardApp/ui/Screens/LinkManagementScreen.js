import React, { useCallback, useContext, useEffect, useState } from 'react';
import {
    ActivityIndicator,
    Alert,
    FlatList,
    Image,
    ImageBackground,
    ScrollView,
    StyleSheet,
    Text,
    TouchableOpacity,
    View,
} from 'react-native';
import { useFocusEffect } from '@react-navigation/native';
import { SafeAreaView } from 'react-native-safe-area-context';
import TextInputField from '../components/TextInputField';
import ClassicButton from '../components/ClassicButton';
import { UserContext } from '../../logic/contexts/UserContext';
import { apiRequest } from '../../logic/services/api/ApiClient';

function ElderlyView({ navigation }) {
    const { user, setUser } = useContext(UserContext);
    const [linkRequests, setLinkRequests] = useState([]);
    const [isLoadingRequests, setIsLoadingRequests] = useState(true);
    const [linkedMembers, setLinkedMembers] = useState([]);
    const [isLoadingMembers, setIsLoadingMembers] = useState(true);

    useFocusEffect(useCallback(() => {
        apiRequest(`/users/${user.userId}/family/requests`)
            .then(data => setLinkRequests(data.requests ?? []))
            .catch(() => {}) // keep whatever was already loaded on error
            .finally(() => setIsLoadingRequests(false));
    }, [user.userId]));

    useFocusEffect(useCallback(() => {
        apiRequest(`/users/${user.userId}/family/linked-members`)
            .then(data => setLinkedMembers(data.members ?? []))
            .catch(() => {})
            .finally(() => setIsLoadingMembers(false));
    }, [user.userId]));

    useEffect(() => {
        apiRequest(`/users/${user.userId}/profile`)
            .then(profile => {
                // Always update watchId + watchName from the backend so that watch_name
                // is shown even when watchId was already set (e.g. set during pairing
                // in this session but before watch_name support was added).
                if (profile.watch_id) {
                    setUser(prev => ({
                        ...prev,
                        watchId: profile.watch_id,
                        watchName: profile.watch_name || null,
                    }));
                }
            })
            .catch(() => {});
    }, []);

    const linkAnotherWatch = () => {
        if (!user?.watchId) {
            navigation.navigate('watch-pairing');
            return;
        }
        Alert.alert(
            'קישור שעון אחר',
            'האם אתה בטוח שברצונך להתנתק מהשעון הנוכחי ולקשר שעון חדש?',
            [
                { text: 'ביטול', style: 'cancel' },
                {
                    text: 'התנתק וקשר שעון',
                    style: 'destructive',
                    onPress: async () => {
                        try {
                            await apiRequest(`/users/${user.userId}/watch/unpair`, { method: 'POST' });
                            setUser(prev => ({ ...prev, watchId: null, watchName: null }));
                            navigation.navigate('watch-pairing');
                        } catch (err) {
                            Alert.alert('שגיאה', err.message || 'לא ניתן להתנתק מהשעון. נסה שוב.');
                        }
                    },
                },
            ]
        );
    };

    const approveRequest = async (requestId) => {
        try {
            await apiRequest(`/users/${user.userId}/family/approve`, {
                method: 'POST',
                body: JSON.stringify({ request_id: requestId }),
            });
            setLinkRequests(prev => prev.filter(request => request.id !== requestId));
            Alert.alert('אושר', 'הקישור אושר בהצלחה!');
        } catch (err) {
            Alert.alert('שגיאה', err.message || 'לא ניתן לאשר את הבקשה. נסה שוב.');
        }
    };

    const rejectRequest = async (requestId) => {
        try {
            await apiRequest(`/users/${user.userId}/family/request/${requestId}`, { method: 'DELETE' });
            setLinkRequests(prev => prev.filter(request => request.id !== requestId));
        } catch (err) {
            Alert.alert('שגיאה', err.message || 'לא ניתן לדחות את הבקשה. נסה שוב.');
        }
    };

    const unlinkMember = (memberId) => {
        Alert.alert(
            'ביטול קישור',
            'האם אתה בטוח שברצונך לבטל את הקישור עם בן המשפחה הזה?',
            [
                { text: 'ביטול', style: 'cancel' },
                {
                    text: 'בטל קישור',
                    style: 'destructive',
                    onPress: async () => {
                        try {
                            await apiRequest(`/users/${user.userId}/family/request/${memberId}`, { method: 'DELETE' });
                            setLinkedMembers(prev => prev.filter(member => member.id !== memberId));
                        } catch (err) {
                            Alert.alert('שגיאה', err.message || 'לא ניתן לבטל את הקישור. נסה שוב.');
                        }
                    },
                },
            ]
        );
    };

    const renderLinkRequest = ({ item }) => (
        <View style={styles.requestCard}>
            <View style={styles.requestInfo}>
                <Text style={styles.requestName}>{item.member_name}</Text>
                <Text style={styles.requestPhone}>{item.member_phone}</Text>
            </View>
            <View style={styles.requestActions}>
                <TouchableOpacity
                    style={styles.rejectButton}
                    onPress={() => rejectRequest(item.id)}
                    activeOpacity={0.8}
                >
                    <Text style={styles.rejectButtonText}>דחה</Text>
                </TouchableOpacity>
                <TouchableOpacity
                    style={styles.approveButton}
                    onPress={() => approveRequest(item.id)}
                    activeOpacity={0.8}
                >
                    <Text style={styles.approveButtonText}>אשר</Text>
                </TouchableOpacity>
            </View>
        </View>
    );

    const renderLinkedMember = ({ item }) => (
        <View style={styles.requestCard}>
            <View style={styles.requestInfo}>
                <Text style={styles.requestName}>{item.member_name}</Text>
                <Text style={styles.requestPhone}>{item.member_phone}</Text>
            </View>
            <TouchableOpacity
                style={styles.rejectButton}
                onPress={() => unlinkMember(item.id)}
                activeOpacity={0.8}
            >
                <Text style={styles.rejectButtonText}>בטל קישור</Text>
            </TouchableOpacity>
        </View>
    );

    return (
        <>
            <View style={styles.section}>
                <Text style={styles.sectionTitle}>קישור שעון</Text>
                {user?.watchId ? (
                    <View style={styles.watchStatusRow}>
                        <Text style={styles.watchStatusDot}>●</Text>
                        <Text style={styles.watchStatusText}>
                            שעון מקושר — {user.watchName || user.watchId.slice(0, 8)}
                        </Text>
                    </View>
                ) : (
                    <Text style={styles.sectionDescription}>סרוק את קוד ה-QR המוצג על השעון לקישור עם חשבונך</Text>
                )}
                <ClassicButton
                    buttonStyle={styles.watchButton}
                    textStyle={styles.watchButtonText}
                    onPress={linkAnotherWatch}
                >
                    {user?.watchId ? 'קשר שעון אחר' : 'קשר שעון'}
                </ClassicButton>
            </View>

            <View style={styles.section}>
                <Text style={styles.sectionTitle}>בקשות קישור</Text>
                {isLoadingRequests ? (
                    <ActivityIndicator color="#48AEBE" />
                ) : linkRequests.length === 0 ? (
                    <Text style={styles.emptyText}>אין בקשות קישור ממתינות</Text>
                ) : (
                    <FlatList
                        data={linkRequests}
                        keyExtractor={item => item.id}
                        renderItem={renderLinkRequest}
                        scrollEnabled={false}
                    />
                )}
            </View>

            <View style={styles.section}>
                <Text style={styles.sectionTitle}>בני משפחה מקושרים</Text>
                {isLoadingMembers ? (
                    <ActivityIndicator color="#48AEBE" />
                ) : linkedMembers.length === 0 ? (
                    <Text style={styles.emptyText}>אין בני משפחה מקושרים</Text>
                ) : (
                    <FlatList
                        data={linkedMembers}
                        keyExtractor={item => item.id}
                        renderItem={renderLinkedMember}
                        scrollEnabled={false}
                    />
                )}
            </View>
        </>
    );
}

function FamilyMemberView() {
    const { user } = useContext(UserContext);
    const [phoneNumber, setPhoneNumber] = useState('');
    const [isSending, setIsSending] = useState(false);

    const sendLinkRequest = async () => {
        if (!phoneNumber.trim()) {
            Alert.alert('שגיאה', 'יש להזין מספר טלפון');
            return;
        }
        const normalizedPhone = phoneNumber.trim().startsWith('+')
            ? phoneNumber.trim()
            : '+972' + phoneNumber.trim().replace(/^0/, '');

        setIsSending(true);
        try {
            await apiRequest(`/users/${user.userId}/family/request`, {
                method: 'POST',
                body: JSON.stringify({
                    elderly_phone: normalizedPhone,
                    member_name: `${user.firstName} ${user.lastName}`.trim(),
                    member_phone: user.phone,
                }),
            });
            Alert.alert('בקשה נשלחה', `בקשת קישור נשלחה למספר ${phoneNumber.trim()}`);
            setPhoneNumber('');
        } catch (err) {
            Alert.alert('שגיאה', err.message || 'לא ניתן לשלוח את הבקשה. נסה שוב.');
        } finally {
            setIsSending(false);
        }
    };

    return (
        <View style={styles.section}>
            <Text style={styles.sectionTitle}>קישור עם מבוגר</Text>
            {user?.linkedElderName && (
                <View style={styles.watchStatusRow}>
                    <Text style={styles.watchStatusDot}>●</Text>
                    <Text style={styles.watchStatusText}>מקושר למבוגר — {user.linkedElderName}</Text>
                </View>
            )}
            <Text style={styles.sectionDescription}>הזן את מספר הטלפון של המבוגר לשליחת בקשת קישור</Text>
            <TextInputField
                placeholder="מספר טלפון"
                value={phoneNumber}
                onChangeText={setPhoneNumber}
                keyboardType="phone-pad"
            />
            <ClassicButton
                buttonStyle={styles.requestButton}
                onPress={sendLinkRequest}
                disabled={isSending}
            >
                {isSending ? <ActivityIndicator color="#fff" /> : 'בקש לקשר'}
            </ClassicButton>
        </View>
    );
}

export default function LinkManagementScreen({ navigation }) {
    const { user } = useContext(UserContext);
    const isElderly = user?.userType === 'elderly';

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

                <Text style={styles.title}>ניהול קישורים</Text>

                <ScrollView contentContainerStyle={styles.scrollContent} keyboardShouldPersistTaps="handled">
                    {isElderly
                        ? <ElderlyView navigation={navigation} />
                        : <FamilyMemberView />
                    }
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
    section: {
        backgroundColor: 'rgba(255,255,255,0.88)',
        borderRadius: 16,
        padding: 16,
        marginBottom: 14,
    },
    sectionTitle: {
        fontSize: 18,
        fontWeight: '700',
        color: '#333',
        textAlign: 'right',
        marginBottom: 6,
    },
    sectionDescription: {
        fontSize: 14,
        color: '#666',
        textAlign: 'right',
        marginBottom: 14,
    },
    emptyText: {
        fontSize: 14,
        color: '#888',
        textAlign: 'right',
    },
    watchStatusRow: {
        flexDirection: 'row-reverse',
        alignItems: 'center',
        marginBottom: 14,
        gap: 8,
    },
    watchStatusDot: {
        fontSize: 12,
        color: '#4CAF50',
    },
    watchStatusText: {
        fontSize: 14,
        color: '#333',
        textAlign: 'right',
    },
    watchButton: {
        width: '100%',
        backgroundColor: '#7B52AB',
        borderColor: '#7B52AB',
    },
    watchButtonText: {
        color: '#fff',
    },
    requestButton: {
        width: '100%',
        marginTop: 10,
    },
    requestCard: {
        backgroundColor: '#f5f5f5',
        borderRadius: 12,
        padding: 14,
        marginBottom: 10,
        flexDirection: 'row-reverse',
        alignItems: 'center',
        justifyContent: 'space-between',
    },
    requestInfo: {
        alignItems: 'flex-end',
        flex: 1,
    },
    requestName: {
        fontSize: 16,
        fontWeight: '700',
        color: '#333',
    },
    requestPhone: {
        fontSize: 13,
        color: '#666',
        marginTop: 2,
    },
    requestActions: {
        flexDirection: 'row-reverse',
        gap: 8,
        marginRight: 12,
    },
    approveButton: {
        backgroundColor: '#4CAF50',
        borderRadius: 8,
        paddingHorizontal: 16,
        paddingVertical: 8,
    },
    approveButtonText: {
        color: '#fff',
        fontWeight: '700',
        fontSize: 14,
    },
    rejectButton: {
        backgroundColor: '#ff5a5a',
        borderRadius: 8,
        paddingHorizontal: 16,
        paddingVertical: 8,
    },
    rejectButtonText: {
        color: '#fff',
        fontWeight: '700',
        fontSize: 14,
    },
});
