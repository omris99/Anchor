import React, { useContext, useState } from 'react';
import {
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
import { SafeAreaView } from 'react-native-safe-area-context';
import TextInputField from '../components/TextInputField';
import ClassicButton from '../components/ClassicButton';
import { UserContext } from '../../logic/contexts/UserContext';

const MOCK_LINK_REQUESTS = [
    { id: '1', fullName: 'דניאל הרשקו', phone: '050-1234567' },
];

function ElderlyView({ navigation }) {
    const [linkRequests, setLinkRequests] = useState(MOCK_LINK_REQUESTS);

    const approveRequest = (requestId) => {
        // TODO: POST /users/{userId}/family/approve — אישור בקשת קישור
        setLinkRequests(prev => prev.filter(request => request.id !== requestId));
        Alert.alert('אושר', 'הקישור אושר בהצלחה!');
    };

    const rejectRequest = (requestId) => {
        // TODO: DELETE /users/{userId}/family/request/{requestId} — דחיית בקשת קישור
        setLinkRequests(prev => prev.filter(request => request.id !== requestId));
    };

    const renderLinkRequest = ({ item }) => (
        <View style={styles.requestCard}>
            <View style={styles.requestInfo}>
                <Text style={styles.requestName}>{item.fullName}</Text>
                <Text style={styles.requestPhone}>{item.phone}</Text>
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

    return (
        <>
            <View style={styles.section}>
                <Text style={styles.sectionTitle}>קישור שעון</Text>
                <Text style={styles.sectionDescription}>סרוק את קוד ה-QR המוצג על השעון לקישור עם חשבונך</Text>
                <ClassicButton
                    buttonStyle={styles.watchButton}
                    textStyle={styles.watchButtonText}
                    onPress={() => navigation.navigate('watch-pairing')}
                >
                    קשר שעון
                </ClassicButton>
            </View>

            <View style={styles.section}>
                <Text style={styles.sectionTitle}>בקשות קישור</Text>
                {linkRequests.length === 0 ? (
                    <Text style={styles.emptyText}>אין בקשות קישור ממתינות</Text>
                ) : (
                    // TODO: GET /users/{userId}/family/requests — טעינת בקשות קישור ממתינות
                    <FlatList
                        data={linkRequests}
                        keyExtractor={item => item.id}
                        renderItem={renderLinkRequest}
                        scrollEnabled={false}
                    />
                )}
            </View>
        </>
    );
}

function FamilyMemberView() {
    const [phoneNumber, setPhoneNumber] = useState('');

    const sendLinkRequest = () => {
        if (!phoneNumber.trim()) {
            Alert.alert('שגיאה', 'יש להזין מספר טלפון');
            return;
        }
        // TODO: POST /users/{userId}/family/request — שליחת בקשת קישור לקשיש לפי טלפון
        Alert.alert('בקשה נשלחה', `בקשת קישור נשלחה למספר ${phoneNumber.trim()}`);
        setPhoneNumber('');
    };

    return (
        <View style={styles.section}>
            <Text style={styles.sectionTitle}>קישור עם קשיש</Text>
            <Text style={styles.sectionDescription}>הזן את מספר הטלפון של הקשיש לשליחת בקשת קישור</Text>
            <TextInputField
                placeholder="מספר טלפון"
                value={phoneNumber}
                onChangeText={setPhoneNumber}
                keyboardType="phone-pad"
            />
            <ClassicButton
                buttonStyle={styles.requestButton}
                onPress={sendLinkRequest}
            >
                בקש לקשר
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
