import React, { useContext, useEffect, useState } from 'react';
import {
    ActivityIndicator,
    Alert,
    Image,
    ImageBackground,
    Modal,
    StyleSheet,
    Switch,
    Text,
    TouchableOpacity,
    View,
} from 'react-native';
import DateTimePicker from '@react-native-community/datetimepicker';
import { SafeAreaView } from 'react-native-safe-area-context';
import ClassicButton from '../components/ClassicButton';
import { UserContext } from '../../logic/contexts/UserContext';
import { apiRequest } from '../../logic/services/api/ApiClient';
import { getViewedUserId } from '../../logic/utils/viewedUser';

const FREQUENCY_OPTIONS = [
    { label: 'כל דקה (בדיקה)', value: 1 },
    { label: 'כל חצי שעה (בדיקה)', value: 30 },
    { label: 'כל שעה', value: 60 },
    { label: 'כל שעתיים', value: 120 },
    { label: 'כל 3 שעות', value: 180 },
];

// Mirrors the backend's generateTimes() so the screen can warn before hitting the
// server-side MAX_GENERATED_ITEMS cap, instead of the user only finding out on save.
const MAX_DAILY_REMINDERS = 50;

function formatTime(date) {
    const hours = date.getHours().toString().padStart(2, '0');
    const minutes = date.getMinutes().toString().padStart(2, '0');
    return `${hours}:${minutes}`;
}

function countDailyReminders(activeStart, activeEnd, frequencyMinutes) {
    const startTotal = activeStart.getHours() * 60 + activeStart.getMinutes();
    const endTotal = activeEnd.getHours() * 60 + activeEnd.getMinutes();
    if (startTotal >= endTotal) return 0;
    return Math.floor((endTotal - startTotal) / frequencyMinutes) + 1;
}

function timeToDate(value) {
    const [hours, minutes] = value.split(':').map(Number);
    const date = new Date();
    date.setHours(hours, minutes, 0, 0);
    return date;
}

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

export default function WaterReminderScreen({ navigation }) {
    const { user } = useContext(UserContext);
    const viewedUserId = getViewedUserId(user);
    const [enabled, setEnabled] = useState(false);
    const [frequencyMinutes, setFrequencyMinutes] = useState(120);
    const [activeStart, setActiveStart] = useState(() => timeToDate('08:00'));
    const [activeEnd, setActiveEnd] = useState(() => timeToDate('22:00'));
    const [watchScheduled, setWatchScheduled] = useState(false);
    const [isSaving, setIsSaving] = useState(false);
    const [isLoading, setIsLoading] = useState(true);

    const [isFrequencyModalVisible, setIsFrequencyModalVisible] = useState(false);
    const [pickerTarget, setPickerTarget] = useState(null); // 'start' | 'end' | null
    const [pendingTime, setPendingTime] = useState(new Date());

    const selectedFrequencyLabel =
        FREQUENCY_OPTIONS.find(opt => opt.value === frequencyMinutes)?.label ?? '';

    const dailyReminderCount = enabled ? countDailyReminders(activeStart, activeEnd, frequencyMinutes) : 0;
    const exceedsMax = dailyReminderCount > MAX_DAILY_REMINDERS;

    useEffect(() => {
        if (!viewedUserId) { setIsLoading(false); return; }
        setIsLoading(true);
        (async () => {
            try {
                const data = await apiRequest(`/users/${viewedUserId}/water-reminders`);
                setEnabled(!!data.enabled);
                setFrequencyMinutes(data.frequency_minutes ?? 120);
                setActiveStart(timeToDate(data.active_start ?? '08:00'));
                setActiveEnd(timeToDate(data.active_end ?? '22:00'));
                setWatchScheduled(!!data.watch_scheduled);
            } catch {}
            finally { setIsLoading(false); }
        })();
    }, [viewedUserId]);

    const openTimePicker = (target) => {
        setPendingTime(target === 'start' ? activeStart : activeEnd);
        setPickerTarget(target);
    };

    const confirmTimePicker = () => {
        if (pickerTarget === 'start') setActiveStart(pendingTime);
        if (pickerTarget === 'end') setActiveEnd(pendingTime);
        setPickerTarget(null);
    };

    const cancelTimePicker = () => setPickerTarget(null);

    const save = async () => {
        if (enabled && activeStart >= activeEnd) {
            Alert.alert('שגיאה', 'שעת ההתחלה חייבת להיות לפני שעת הסיום');
            return;
        }
        setIsSaving(true);
        try {
            await apiRequest(`/users/${viewedUserId}/water-reminders`, {
                method: 'PUT',
                body: JSON.stringify({
                    enabled,
                    frequency_minutes: frequencyMinutes,
                    active_start: formatTime(activeStart),
                    active_end: formatTime(activeEnd),
                }),
            });
            setWatchScheduled(false); // watch hasn't picked up the new settings yet
            Alert.alert('נשמר', 'הגדרות תזכורות השתייה נשמרו');
        } catch (err) {
            Alert.alert('שגיאה', err.message || 'לא ניתן לשמור את ההגדרות. נסה שוב.');
        } finally {
            setIsSaving(false);
        }
    };

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

                <Text style={styles.title}>תזכורות לשתיית מים</Text>

                {!viewedUserId ? (
                    <View style={styles.form}>
                        <Text style={styles.toggleLabel}>עדיין לא מקושר למבוגר</Text>
                    </View>
                ) : isLoading ? (
                    <ActivityIndicator size="large" color="#48AEBE" style={{ marginTop: 60 }} />
                ) : (
                <View style={styles.form}>
                    <ToggleRow
                        label="הפעל תזכורות לשתיית מים"
                        value={enabled}
                        onValueChange={setEnabled}
                    />

                    {enabled && (
                        <View style={styles.subSection}>
                            <Text style={styles.subLabel}>תדירות:</Text>
                            <TouchableOpacity
                                style={styles.picker}
                                onPress={() => setIsFrequencyModalVisible(true)}
                                activeOpacity={0.8}
                            >
                                <Text style={styles.pickerText}>{selectedFrequencyLabel}</Text>
                                <Text style={styles.pickerChevron}>▾</Text>
                            </TouchableOpacity>

                            <Text style={styles.subLabel}>משעה:</Text>
                            <TouchableOpacity style={styles.timePicker} onPress={() => openTimePicker('start')}>
                                <Text style={styles.timeText}>{formatTime(activeStart)}</Text>
                            </TouchableOpacity>

                            <Text style={styles.subLabel}>עד שעה:</Text>
                            <TouchableOpacity style={styles.timePicker} onPress={() => openTimePicker('end')}>
                                <Text style={styles.timeText}>{formatTime(activeEnd)}</Text>
                            </TouchableOpacity>

                            <Text style={[styles.dailyCountText, exceedsMax && styles.dailyCountTextError]}>
                                {exceedsMax
                                    ? `${dailyReminderCount} תזכורות ביום — יותר מהמקסימום (${MAX_DAILY_REMINDERS}). צמצם את החלון או הגדל את התדירות.`
                                    : `ייווצרו ${dailyReminderCount} תזכורות ביום`}
                            </Text>

                            <View style={styles.watchStatusRow}>
                                <View style={[styles.watchStatusDot, watchScheduled ? styles.dotGreen : styles.dotOrange]} />
                                <Text style={[styles.watchStatusText, watchScheduled ? styles.textGreen : styles.textOrange]}>
                                    {watchScheduled ? 'מוגדר בשעון' : 'ממתין לשעון'}
                                </Text>
                            </View>
                        </View>
                    )}

                    <ClassicButton
                        buttonStyle={styles.saveButton}
                        onPress={save}
                        disabled={isSaving || exceedsMax || !viewedUserId}
                    >
                        {isSaving ? 'שומר...' : 'שמור'}
                    </ClassicButton>
                </View>
                )}
            </SafeAreaView>

            <Modal
                visible={isFrequencyModalVisible}
                transparent
                animationType="fade"
                onRequestClose={() => setIsFrequencyModalVisible(false)}
            >
                <TouchableOpacity
                    style={styles.modalOverlay}
                    activeOpacity={1}
                    onPress={() => setIsFrequencyModalVisible(false)}
                >
                    <TouchableOpacity activeOpacity={1} style={styles.modalCard}>
                        <Text style={styles.modalTitle}>תדירות תזכורות מים</Text>
                        {FREQUENCY_OPTIONS.map(option => (
                            <TouchableOpacity
                                key={option.value}
                                style={[
                                    styles.frequencyOption,
                                    frequencyMinutes === option.value && styles.frequencyOptionSelected,
                                ]}
                                onPress={() => {
                                    setFrequencyMinutes(option.value);
                                    setIsFrequencyModalVisible(false);
                                }}
                            >
                                <Text style={[
                                    styles.frequencyOptionText,
                                    frequencyMinutes === option.value && styles.frequencyOptionTextSelected,
                                ]}>
                                    {option.label}
                                </Text>
                            </TouchableOpacity>
                        ))}
                    </TouchableOpacity>
                </TouchableOpacity>
            </Modal>

            <Modal
                visible={pickerTarget !== null}
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
                            style={styles.spinner}
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
    form: {
        backgroundColor: 'rgba(255,255,255,0.88)',
        borderRadius: 16,
        padding: 16,
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
    subSection: {
        marginTop: 12,
        borderTopWidth: 1,
        borderTopColor: '#e0e0e0',
        paddingTop: 12,
    },
    subLabel: {
        fontSize: 15,
        fontWeight: '600',
        color: '#444',
        textAlign: 'right',
        marginBottom: 6,
        marginTop: 8,
    },
    picker: {
        flexDirection: 'row-reverse',
        alignItems: 'center',
        justifyContent: 'space-between',
        borderWidth: 1,
        borderColor: '#aaa',
        borderRadius: 8,
        paddingVertical: 12,
        paddingHorizontal: 14,
        backgroundColor: '#fff',
    },
    pickerText: {
        fontSize: 16,
        color: '#48AEBE',
        fontWeight: '600',
    },
    pickerChevron: {
        fontSize: 16,
        color: '#48AEBE',
    },
    timePicker: {
        borderWidth: 1,
        borderColor: '#aaa',
        borderRadius: 8,
        paddingVertical: 12,
        paddingHorizontal: 14,
        backgroundColor: '#fff',
        alignItems: 'flex-end',
    },
    timeText: {
        fontSize: 20,
        fontWeight: '700',
        color: '#48AEBE',
        letterSpacing: 1,
    },
    dailyCountText: {
        fontSize: 13,
        color: '#666',
        textAlign: 'right',
        marginTop: 10,
    },
    dailyCountTextError: {
        color: '#e53935',
        fontWeight: '600',
    },
    watchStatusRow: {
        flexDirection: 'row-reverse',
        alignItems: 'center',
        marginTop: 14,
        gap: 6,
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
        fontSize: 13,
        fontWeight: '500',
    },
    textGreen: {
        color: '#27ae60',
    },
    textOrange: {
        color: '#e67e22',
    },
    saveButton: {
        marginTop: 20,
        width: '100%',
        alignSelf: 'center',
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
        alignItems: 'stretch',
        shadowColor: '#000',
        shadowOffset: { width: 0, height: 6 },
        shadowOpacity: 0.2,
        shadowRadius: 12,
    },
    modalTitle: {
        fontSize: 18,
        fontWeight: '700',
        color: '#333',
        textAlign: 'center',
        marginBottom: 16,
    },
    frequencyOption: {
        paddingVertical: 14,
        paddingHorizontal: 12,
        borderRadius: 10,
        marginBottom: 6,
        backgroundColor: '#f5f5f5',
        alignItems: 'flex-end',
    },
    frequencyOptionSelected: {
        backgroundColor: '#48AEBE',
    },
    frequencyOptionText: {
        fontSize: 16,
        color: '#333',
        fontWeight: '500',
    },
    frequencyOptionTextSelected: {
        color: '#fff',
        fontWeight: '700',
    },
    spinner: {
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
