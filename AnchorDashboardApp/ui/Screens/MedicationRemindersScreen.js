import React, { useContext, useEffect, useState } from 'react';
import {
    ActivityIndicator,
    Alert,
    FlatList,
    Image,
    ImageBackground,
    Keyboard,
    Modal,
    StyleSheet,
    Text,
    TouchableOpacity,
    View,
} from 'react-native';
import DateTimePicker from '@react-native-community/datetimepicker';
import { SafeAreaView } from 'react-native-safe-area-context';
import ClassicButton from '../components/ClassicButton';
import TextInputField from '../components/TextInputField';
import DaySelector, { DAY_NAMES } from '../components/DaySelector';
import { UserContext } from '../../logic/contexts/UserContext';
import { apiRequest } from '../../logic/services/api/ApiClient';
import { getViewedUserId } from '../../logic/utils/viewedUser';

function formatTime(date) {
    const hours = date.getHours().toString().padStart(2, '0');
    const minutes = date.getMinutes().toString().padStart(2, '0');
    return `${hours}:${minutes}`;
}

function formatDays(selectedDays) {
    if (selectedDays.length === 7) return 'כל יום';
    return selectedDays.map(dayIndex => DAY_NAMES[dayIndex]).join(', ');
}

// Converts a server medication record to the shape the UI expects.
function serverMedToLocal(med) {
    const [hours, minutes] = med.scheduled_time.split(':').map(Number);
    const time = new Date();
    time.setHours(hours, minutes, 0, 0);
    return {
        id: med.id,
        name: med.medication_name,
        time,
        days: med.days_of_week ?? [0, 1, 2, 3, 4, 5, 6],
        watchScheduled: !!med.watch_scheduled_at,
    };
}

export default function MedicationRemindersScreen({ navigation }) {
    const { user } = useContext(UserContext);
    const viewedUserId = getViewedUserId(user);
    const [medicationName, setMedicationName] = useState('');
    const [selectedTime, setSelectedTime] = useState(() => {
        const now = new Date();
        now.setSeconds(0, 0);
        return now;
    });
    const [selectedDays, setSelectedDays] = useState([]);
    const [reminders, setReminders] = useState([]);
    const [isLoading, setIsLoading] = useState(true);
    const [isRefreshing, setIsRefreshing] = useState(false);

    const [isPickerVisible, setIsPickerVisible] = useState(false);
    const [pendingTime, setPendingTime] = useState(selectedTime);

    const loadReminders = async () => {
        if (!viewedUserId) return;
        try {
            const data = await apiRequest(`/users/${viewedUserId}/medication-reminders`);
            setReminders((data.medications ?? []).map(serverMedToLocal));
        } catch {}
    };

    // Load existing reminders from the server when the screen opens.
    useEffect(() => {
        loadReminders().finally(() => setIsLoading(false));
    }, [viewedUserId]);

    const handleRefresh = async () => {
        setIsRefreshing(true);
        await loadReminders();
        setIsRefreshing(false);
    };

    const openTimePicker = () => {
        setPendingTime(selectedTime);
        setIsPickerVisible(true);
    };

    const confirmTimePicker = () => {
        setSelectedTime(pendingTime);
        setIsPickerVisible(false);
    };

    const cancelTimePicker = () => {
        setIsPickerVisible(false);
    };

    const addReminder = async () => {
        if (!medicationName.trim()) {
            Alert.alert('שגיאה', 'יש להזין שם תרופה לפני הוספת תזכורת');
            return;
        }
        if (selectedDays.length === 0) {
            Alert.alert('שגיאה', 'יש לבחור לפחות יום אחד לנטילת התרופה');
            return;
        }
        Keyboard.dismiss();

        try {
            // Save to server first; use the server-assigned id in local state.
            const saved = await apiRequest(`/users/${viewedUserId}/medication-reminders`, {
                method: 'POST',
                body: JSON.stringify({
                    medication_name: medicationName.trim(),
                    scheduled_time: formatTime(selectedTime),
                    days_of_week: selectedDays,
                }),
            });
            setReminders(prev => [...prev, serverMedToLocal(saved)]);
            setMedicationName('');
            setSelectedDays([]);
        } catch {
            Alert.alert('שגיאה', 'לא ניתן לשמור את התזכורת. נסה שוב.');
        }
    };

    const removeReminder = async (reminderId) => {
        try {
            await apiRequest(`/users/${viewedUserId}/medication-reminders/${reminderId}`, {
                method: 'DELETE',
            });
            setReminders(prev => prev.filter(r => r.id !== reminderId));
        } catch {
            Alert.alert('שגיאה', 'לא ניתן למחוק את התזכורת. נסה שוב.');
        }
    };

    const renderReminder = ({ item }) => (
        <View style={styles.reminderCard}>
            <View style={styles.reminderInfo}>
                <Text style={styles.reminderName}>{item.name}</Text>
                <Text style={styles.reminderDetail}>{formatTime(item.time)}</Text>
                <Text style={styles.reminderDetail}>{formatDays(item.days)}</Text>
                <View style={styles.watchStatusRow}>
                    <View style={[styles.watchStatusDot, item.watchScheduled ? styles.dotGreen : styles.dotOrange]} />
                    <Text style={[styles.watchStatusText, item.watchScheduled ? styles.textGreen : styles.textOrange]}>
                        {item.watchScheduled ? 'מוגדרת' : 'ממתינה'}
                    </Text>
                </View>
            </View>
            <TouchableOpacity onPress={() => removeReminder(item.id)} style={styles.deleteButton}>
                <Text style={styles.deleteText}>הסר</Text>
            </TouchableOpacity>
        </View>
    );

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
                <Text style={styles.title}>תזכורות לתרופות</Text>

                {!viewedUserId ? (
                    <View style={styles.form}>
                        <Text style={styles.label}>עדיין לא מקושר למבוגר</Text>
                    </View>
                ) : isLoading ? (
                    <ActivityIndicator size="large" color="#48AEBE" style={{ marginTop: 60 }} />
                ) : (
                <FlatList
                    data={reminders}
                    keyExtractor={item => item.id}
                    renderItem={renderReminder}
                    ListHeaderComponent={
                        <View style={styles.form}>
                            <Text style={styles.label}>שם התרופה:</Text>
                            <TextInputField
                                placeholder="הכנס שם תרופה"
                                value={medicationName}
                                onChangeText={setMedicationName}
                            />

                            <Text style={styles.label}>שעת נטילה:</Text>
                            <TouchableOpacity style={styles.timePicker} onPress={openTimePicker}>
                                <Text style={styles.timeText}>{formatTime(selectedTime)}</Text>
                            </TouchableOpacity>

                            <Text style={styles.label}>בחר ימי נטילה:</Text>
                            <DaySelector selectedDays={selectedDays} onChange={setSelectedDays} />

                            <ClassicButton
                                buttonStyle={styles.addButton}
                                onPress={addReminder}
                            >
                                הגדר תזכורת
                            </ClassicButton>

                            {reminders.length > 0 && (
                                <View style={styles.existingTitleRow}>
                                    <TouchableOpacity
                                        style={[styles.refreshButton, isRefreshing && styles.refreshButtonDisabled]}
                                        onPress={handleRefresh}
                                        disabled={isRefreshing}
                                        activeOpacity={0.7}
                                    >
                                        <Text style={styles.refreshIcon}>{isRefreshing ? '…' : '↻'}</Text>
                                    </TouchableOpacity>
                                    <Text style={styles.existingTitle}>תזכורות קיימות:</Text>
                                </View>
                            )}
                        </View>
                    }
                    contentContainerStyle={styles.listContent}
                    keyboardShouldPersistTaps="handled"
                    automaticallyAdjustKeyboardInsets
                />
                )}
            </SafeAreaView>

            <Modal
                visible={isPickerVisible}
                transparent
                animationType="fade"
                onRequestClose={cancelTimePicker}
            >
                <TouchableOpacity
                    style={styles.modalOverlay}
                    activeOpacity={1}
                    onPress={cancelTimePicker}
                >
                    <TouchableOpacity activeOpacity={1} style={styles.modalCard}>
                        <Text style={styles.modalTitle}>בחר שעה</Text>

                        <DateTimePicker
                            value={pendingTime}
                            mode="time"
                            display="spinner"
                            is24Hour
                            onChange={(_event, pickedTime) => {
                                if (pickedTime) setPendingTime(pickedTime);
                            }}
                            locale="he"
                            style={styles.picker}
                            textColor="#333"
                            themeVariant="light"
                        />

                        <View style={styles.modalButtons}>
                            <TouchableOpacity style={styles.modalCancel} onPress={cancelTimePicker}>
                                <Text style={styles.modalCancelText}>ביטול</Text>
                            </TouchableOpacity>
                            <TouchableOpacity style={styles.modalConfirm} onPress={confirmTimePicker}>
                                <Text style={styles.modalConfirmText}>אישור</Text>
                            </TouchableOpacity>
                        </View>
                    </TouchableOpacity>
                </TouchableOpacity>
            </Modal>
        </ImageBackground>
    );
}

const styles = StyleSheet.create({
    background: {
        flex: 1,
        resizeMode: "cover"
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
    form: {
        backgroundColor: 'rgba(255,255,255,0.88)',
        borderRadius: 16,
        padding: 16,
        marginBottom: 16,
    },
    label: {
        fontSize: 16,
        fontWeight: '600',
        color: '#444',
        textAlign: 'right',
        marginTop: 8
    },
    timePicker: {
        borderWidth: 1,
        borderColor: '#aaa',
        borderRadius: 8,
        paddingVertical: 12,
        paddingHorizontal: 14,
        marginTop: 6,
        backgroundColor: '#fff',
        alignItems: 'flex-end',
    },
    timeText: {
        fontSize: 20,
        fontWeight: '700',
        color: '#48AEBE',
        letterSpacing: 1,
    },
    addButton: {
        marginTop: 20,
        width: '100%',
        alignSelf: 'center',
    },
    existingTitleRow: {
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'flex-end',
        marginTop: 24,
        marginBottom: 4,
        gap: 10,
    },
    existingTitle: {
        fontSize: 17,
        fontWeight: '700',
        color: '#444',
        textAlign: 'right',
    },
    refreshButton: {
        width: 32,
        height: 32,
        borderRadius: 16,
        backgroundColor: '#48AEBE',
        alignItems: 'center',
        justifyContent: 'center',
    },
    refreshButtonDisabled: {
        backgroundColor: '#a0d4df',
    },
    refreshIcon: {
        fontSize: 18,
        color: '#fff',
        fontWeight: '700',
        lineHeight: 20,
    },
    listContent: {
        paddingBottom: 30,
    },
    reminderCard: {
        backgroundColor: 'rgba(255,255,255,0.9)',
        borderRadius: 12,
        padding: 14,
        marginBottom: 10,
        flexDirection: 'row-reverse',
        alignItems: 'center',
        justifyContent: 'space-between',
        shadowColor: '#000',
        shadowOffset: { width: 0, height: 2 },
        shadowOpacity: 0.1,
        shadowRadius: 4,
    },
    reminderInfo: {
        alignItems: 'flex-end',
        flex: 1,
    },
    reminderName: {
        fontSize: 16,
        fontWeight: '700',
        color: '#333',
    },
    reminderDetail: {
        fontSize: 14,
        color: '#666',
        marginTop: 2,
    },
    deleteButton: {
        backgroundColor: '#ff5a5a',
        borderRadius: 8,
        paddingHorizontal: 12,
        paddingVertical: 6,
        marginRight: 12,
    },
    deleteText: {
        color: '#fff',
        fontWeight: '600',
        fontSize: 14,
    },
    watchStatusRow: {
        flexDirection: 'row-reverse',
        alignItems: 'center',
        marginTop: 5,
        gap: 5,
    },
    watchStatusDot: {
        width: 8,
        height: 8,
        borderRadius: 4,
    },
    dotGreen: {
        backgroundColor: '#27ae60',
    },
    dotOrange: {
        backgroundColor: '#e67e22',
    },
    watchStatusText: {
        fontSize: 12,
        fontWeight: '500',
    },
    textGreen: {
        color: '#27ae60',
    },
    textOrange: {
        color: '#e67e22',
    },
    modalOverlay: {
        flex: 1,
        backgroundColor: 'rgba(0,0,0,0.45)',
        justifyContent: 'center',
        alignItems: 'center',
    },
    modalCard: {
        backgroundColor: '#fff',
        borderRadius: 20,
        padding: 24,
        width: '82%',
        alignItems: 'center',
        shadowColor: '#000',
        shadowOffset: { width: 0, height: 6 },
        shadowOpacity: 0.2,
        shadowRadius: 12,
    },
    modalTitle: {
        fontSize: 20,
        fontWeight: '700',
        color: '#333',
        marginBottom: 12,
    },
    picker: {
        width: '100%',
    },
    modalButtons: {
        flexDirection: 'row',
        marginTop: 16,
        gap: 12,
    },
    modalCancel: {
        flex: 1,
        paddingVertical: 12,
        borderRadius: 10,
        borderWidth: 1.5,
        borderColor: '#48AEBE',
        alignItems: 'center',
    },
    modalCancelText: {
        color: '#48AEBE',
        fontWeight: '600',
        fontSize: 16,
    },
    modalConfirm: {
        flex: 1,
        paddingVertical: 12,
        borderRadius: 10,
        backgroundColor: '#48AEBE',
        alignItems: 'center',
    },
    modalConfirmText: {
        color: '#fff',
        fontWeight: '600',
        fontSize: 16,
    },
});
