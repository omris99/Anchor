import React, { useContext, useState } from 'react';
import {
    Alert,
    FlatList,
    Image,
    ImageBackground,
    Modal,
    ScrollView,
    StyleSheet,
    Switch,
    Text,
    TouchableOpacity,
    View,
} from 'react-native';
import DateTimePicker from '@react-native-community/datetimepicker';
import { SafeAreaView } from 'react-native-safe-area-context';
import { signOut } from 'aws-amplify/auth';
import { UserContext } from '../../App';
import { logoutUser } from '../../logic/services/authentication/LoginService';
import TextInputField from '../components/TextInputField';
import ClassicButton from '../components/ClassicButton';

const WATER_FREQUENCY_OPTIONS = [
    { label: 'אף פעם', value: 'never' },
    { label: 'כל שעה', value: '1h' },
    { label: 'כל שעתיים', value: '2h' },
    { label: 'כל 3 שעות', value: '3h' },
];

function formatTime(date) {
    const hours = date.getHours().toString().padStart(2, '0');
    const minutes = date.getMinutes().toString().padStart(2, '0');
    return `${hours}:${minutes}`;
}

let nextMealId = 1;

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

    const [waterEnabled, setWaterEnabled] = useState(false);
    const [waterFrequency, setWaterFrequency] = useState('never');
    const [isFrequencyModalVisible, setIsFrequencyModalVisible] = useState(false);

    const [mealsEnabled, setMealsEnabled] = useState(false);
    const [mealName, setMealName] = useState('');
    const [mealTime, setMealTime] = useState(() => {
        const now = new Date();
        now.setSeconds(0, 0);
        return now;
    });
    const [pendingMealTime, setPendingMealTime] = useState(mealTime);
    const [isMealPickerVisible, setIsMealPickerVisible] = useState(false);
    const [mealReminders, setMealReminders] = useState([]);

    const selectedFrequencyLabel =
        WATER_FREQUENCY_OPTIONS.find(opt => opt.value === waterFrequency)?.label ?? 'אף פעם';

    const addMealReminder = () => {
        if (!mealName.trim()) {
            Alert.alert('שגיאה', 'יש להזין שם ארוחה');
            return;
        }
        const newMeal = {
            id: String(nextMealId++),
            name: mealName.trim(),
            time: mealTime,
        };
        // TODO: SAVE — שמירת תזכורת ארוחה בשרת (POST /users/{userId}/meal-reminders)
        setMealReminders(prev => [...prev, newMeal]);
        setMealName('');
    };

    const removeMealReminder = (mealId) => {
        // TODO: DELETE — מחיקת תזכורת ארוחה בשרת (DELETE /users/{userId}/meal-reminders/{mealId})
        setMealReminders(prev => prev.filter(meal => meal.id !== mealId));
    };

    const renderMealItem = ({ item }) => (
        <View style={styles.mealCard}>
            <View style={styles.mealInfo}>
                <Text style={styles.mealName}>{item.name}</Text>
                <Text style={styles.mealTime}>{formatTime(item.time)}</Text>
            </View>
            <TouchableOpacity onPress={() => removeMealReminder(item.id)} style={styles.deleteButton}>
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

                <Text style={styles.title}>העדפות משתמש</Text>

                <ScrollView contentContainerStyle={styles.scrollContent} keyboardShouldPersistTaps="handled">

                    {/* General toggles */}
                    <View style={styles.section}>
                        {/* TODO: SAVE — כל שינוי toggle צריך לעדכן את ה-preferences בשרת (PUT /users/{userId}/preferences) */}
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

                    {/* Water reminders */}
                    <View style={styles.section}>
                        <ToggleRow
                            label="הפעל תזכורות לשתיית מים"
                            value={waterEnabled}
                            onValueChange={setWaterEnabled}
                        />
                        {waterEnabled && (
                            <View style={styles.subSection}>
                                <Text style={styles.subLabel}>תדירות:</Text>
                                <TouchableOpacity
                                    style={styles.frequencyPicker}
                                    onPress={() => setIsFrequencyModalVisible(true)}
                                    activeOpacity={0.8}
                                >
                                    <Text style={styles.frequencyText}>{selectedFrequencyLabel}</Text>
                                    <Text style={styles.frequencyChevron}>▾</Text>
                                </TouchableOpacity>
                            </View>
                        )}
                    </View>

                    {/* Meal reminders */}
                    <View style={styles.section}>
                        <ToggleRow
                            label="הפעל תזכורות לארוחות"
                            value={mealsEnabled}
                            onValueChange={setMealsEnabled}
                        />
                        {mealsEnabled && (
                            <View style={styles.subSection}>
                                <Text style={styles.subLabel}>שם הארוחה:</Text>
                                <TextInputField
                                    placeholder="לדוגמה: ארוחת בוקר"
                                    value={mealName}
                                    onChangeText={setMealName}
                                />
                                <Text style={styles.subLabel}>שעה:</Text>
                                <TouchableOpacity
                                    style={styles.timePicker}
                                    onPress={() => {
                                        setPendingMealTime(mealTime);
                                        setIsMealPickerVisible(true);
                                    }}
                                >
                                    <Text style={styles.timeText}>{formatTime(mealTime)}</Text>
                                </TouchableOpacity>
                                <ClassicButton
                                    buttonStyle={styles.addButton}
                                    onPress={addMealReminder}
                                >
                                    הוסף
                                </ClassicButton>

                                {mealReminders.length > 0 && (
                                    <>
                                        <Text style={styles.listTitle}>תזכורות לארוחות:</Text>
                                        <FlatList
                                            data={mealReminders}
                                            keyExtractor={item => item.id}
                                            renderItem={renderMealItem}
                                            scrollEnabled={false}
                                        />
                                    </>
                                )}
                            </View>
                        )}
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

            {/* Water frequency modal */}
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
                        {WATER_FREQUENCY_OPTIONS.map(option => (
                            <TouchableOpacity
                                key={option.value}
                                style={[
                                    styles.frequencyOption,
                                    waterFrequency === option.value && styles.frequencyOptionSelected,
                                ]}
                                onPress={() => {
                                    setWaterFrequency(option.value);
                                    setIsFrequencyModalVisible(false);
                                }}
                            >
                                <Text style={[
                                    styles.frequencyOptionText,
                                    waterFrequency === option.value && styles.frequencyOptionTextSelected,
                                ]}>
                                    {option.label}
                                </Text>
                            </TouchableOpacity>
                        ))}
                    </TouchableOpacity>
                </TouchableOpacity>
            </Modal>

            {/* Meal time picker modal */}
            <Modal
                visible={isMealPickerVisible}
                transparent
                animationType="fade"
                onRequestClose={() => setIsMealPickerVisible(false)}
            >
                <TouchableOpacity
                    style={styles.modalOverlay}
                    activeOpacity={1}
                    onPress={() => setIsMealPickerVisible(false)}
                >
                    <TouchableOpacity activeOpacity={1} style={styles.modalCard}>
                        <Text style={styles.modalTitle}>בחר שעה</Text>
                        <DateTimePicker
                            value={pendingMealTime}
                            mode="time"
                            display="spinner"
                            is24Hour
                            onChange={(_event, pickedTime) => {
                                if (pickedTime) setPendingMealTime(pickedTime);
                            }}
                            locale="he"
                            style={styles.picker}
                            textColor="#333"
                            themeVariant="light"
                        />
                        <View style={styles.modalButtons}>
                            <TouchableOpacity
                                style={styles.modalCancel}
                                onPress={() => setIsMealPickerVisible(false)}
                            >
                                <Text style={styles.modalCancelText}>ביטול</Text>
                            </TouchableOpacity>
                            <TouchableOpacity
                                style={styles.modalConfirm}
                                onPress={() => {
                                    setMealTime(pendingMealTime);
                                    setIsMealPickerVisible(false);
                                }}
                            >
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
    frequencyPicker: {
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
    frequencyText: {
        fontSize: 16,
        color: '#48AEBE',
        fontWeight: '600',
    },
    frequencyChevron: {
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
    addButton: {
        marginTop: 14,
        width: '100%',
        alignSelf: 'center',
    },
    listTitle: {
        fontSize: 15,
        fontWeight: '700',
        color: '#444',
        textAlign: 'right',
        marginTop: 16,
        marginBottom: 8,
    },
    mealCard: {
        backgroundColor: '#f5f5f5',
        borderRadius: 10,
        padding: 12,
        marginBottom: 8,
        flexDirection: 'row-reverse',
        alignItems: 'center',
        justifyContent: 'space-between',
    },
    mealInfo: {
        alignItems: 'flex-end',
        flex: 1,
    },
    mealName: {
        fontSize: 15,
        fontWeight: '700',
        color: '#333',
    },
    mealTime: {
        fontSize: 13,
        color: '#666',
        marginTop: 2,
    },
    deleteButton: {
        backgroundColor: '#ff5a5a',
        borderRadius: 8,
        paddingHorizontal: 12,
        paddingVertical: 6,
        marginRight: 10,
    },
    deleteText: {
        color: '#fff',
        fontWeight: '600',
        fontSize: 14,
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
