import React from 'react';
import {
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

// TODO: LOAD — יש לטעון דיווחים יומיים אמיתיים מהשרת.
// GET /users/{userId}/reports
const MOCK_REPORTS = [
    {
        id: 'r1',
        dateLabel: 'היום',
        wakeUpTime: '07:14',
        medicationsTaken: [
            { name: 'אקמול', time: '08:00' },
            { name: 'ויטמין D', time: '08:00' },
        ],
        medicationsPending: [
            { name: 'מטפורמין', time: '13:00' },
        ],
        generalFeelingEmoji: '🙂',
        batteryPercent: 68,
        location: { lat: 32.0853, lng: 34.7818 },
    },
    {
        id: 'r2',
        dateLabel: 'אתמול',
        wakeUpTime: '07:52',
        medicationsTaken: [
            { name: 'אקמול', time: '08:00' },
            { name: 'ויטמין D', time: '08:00' },
            { name: 'מטפורמין', time: '13:00' },
        ],
        medicationsPending: [],
        generalFeelingEmoji: '😐',
        batteryPercent: 82,
        location: { lat: 32.0853, lng: 34.7818 },
    },
    {
        id: 'r3',
        dateLabel: '3/5/2026',
        wakeUpTime: '06:45',
        medicationsTaken: [
            { name: 'אקמול', time: '08:00' },
        ],
        medicationsPending: [
            { name: 'ויטמין D', time: '08:00' },
            { name: 'מטפורמין', time: '13:00' },
        ],
        generalFeelingEmoji: '😔',
        batteryPercent: 45,
        location: { lat: 32.0853, lng: 34.7818 },
    },
    {
        id: 'r4',
        dateLabel: '2/5/2026',
        wakeUpTime: '08:15',
        medicationsTaken: [
            { name: 'אקמול', time: '08:00' },
            { name: 'ויטמין D', time: '08:00' },
            { name: 'מטפורמין', time: '13:00' },
        ],
        medicationsPending: [],
        generalFeelingEmoji: '🙂',
        batteryPercent: 90,
        location: { lat: 32.0853, lng: 34.7818 },
    },
    {
        id: 'r5',
        dateLabel: '1/5/2026',
        wakeUpTime: '07:30',
        medicationsTaken: [
            { name: 'אקמול', time: '08:00' },
        ],
        medicationsPending: [
            { name: 'ויטמין D', time: '08:00' },
            { name: 'מטפורמין', time: '13:00' },
        ],
        generalFeelingEmoji: '😔',
        batteryPercent: 55,
        location: { lat: 32.0853, lng: 34.7818 },
    },
    {
        id: 'r6',
        dateLabel: '30/4/2026',
        wakeUpTime: '07:05',
        medicationsTaken: [
            { name: 'אקמול', time: '08:00' },
            { name: 'ויטמין D', time: '08:00' },
            { name: 'מטפורמין', time: '13:00' },
        ],
        medicationsPending: [],
        generalFeelingEmoji: '🙂',
        batteryPercent: 77,
        location: { lat: 32.0853, lng: 34.7818 },
    },
    {
        id: 'r7',
        dateLabel: '29/4/2026',
        wakeUpTime: '08:40',
        medicationsTaken: [
            { name: 'אקמול', time: '08:00' },
            { name: 'מטפורמין', time: '13:00' },
        ],
        medicationsPending: [
            { name: 'ויטמין D', time: '08:00' },
        ],
        generalFeelingEmoji: '😔',
        batteryPercent: 32,
        location: { lat: 32.0853, lng: 34.7818 },
    },
    {
        id: 'r8',
        dateLabel: '28/4/2026',
        wakeUpTime: '07:22',
        medicationsTaken: [
            { name: 'אקמול', time: '08:00' },
            { name: 'ויטמין D', time: '08:00' },
            { name: 'מטפורמין', time: '13:00' },
        ],
        medicationsPending: [],
        generalFeelingEmoji: '😐',
        batteryPercent: 85,
        location: { lat: 32.0853, lng: 34.7818 },
    },
];

function openMapLocation(location) {
    const url = `https://www.google.com/maps?q=${location.lat},${location.lng}`;
    Linking.openURL(url);
}

function ReportCard({ report, isFirst }) {
    const hasPending = report.medicationsPending.length > 0;

    return (
        <View style={[styles.card, isFirst && styles.cardToday]}>
            <Text style={[styles.cardTitle, isFirst && styles.cardTitleToday]}>
                {isFirst ? `דיווח אחרון (${report.dateLabel})` : report.dateLabel}
            </Text>

            <View style={styles.row}>
                <Text style={styles.rowLabel}>יקיצה:</Text>
                <Text style={styles.rowValue}>{report.wakeUpTime}</Text>
            </View>

            <View style={styles.row}>
                <Text style={styles.rowLabel}>
                    תרופות שננטלו ({report.medicationsTaken.length}):
                </Text>
                <Text style={styles.rowValue}>
                    {report.medicationsTaken.length > 0
                        ? report.medicationsTaken
                            .map(m => `${m.name} (${m.time})`)
                            .join(', ')
                        : 'אף תרופה לא נלקחה'}
                </Text>
            </View>

            <View style={[styles.row, hasPending && styles.rowWarning]}>
                <Text style={[styles.rowLabel, hasPending && styles.rowLabelWarning]}>
                    תרופות שנותר לנטול ({report.medicationsPending.length}):
                </Text>
                <Text style={[styles.rowValue, hasPending && styles.rowValueWarning]}>
                    {hasPending
                        ? report.medicationsPending
                            .map(m => `${m.name} (${m.time})`)
                            .join(', ')
                        : 'הכל נלקח ✓'}
                </Text>
            </View>

            <View style={styles.row}>
                <Text style={styles.rowLabel}>הרגשה כללית:</Text>
                <Text style={styles.rowValueEmoji}>{report.generalFeelingEmoji}</Text>
            </View>

            <View style={styles.row}>
                <Text style={styles.rowLabel}>סוללת השעון:</Text>
                <Text style={styles.rowValue}>{report.batteryPercent}%</Text>
            </View>

            <TouchableOpacity
                style={styles.mapButton}
                activeOpacity={0.7}
                onPress={() => openMapLocation(report.location)}
            >
                <Text style={styles.mapButtonText}>מיקום על המפה</Text>
            </TouchableOpacity>
        </View>
    );
}

export default function DailyReportsScreen({ navigation }) {
    const [todayReport, ...historyReports] = MOCK_REPORTS;

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
                <Text style={styles.title}>דיווחים יומיים</Text>

                <ScrollView
                    contentContainerStyle={styles.scrollContent}
                    showsVerticalScrollIndicator={false}
                >
                    <ReportCard report={todayReport} isFirst />

                    <Text style={styles.historyTitle}>היסטוריית דיווחים</Text>

                    {historyReports.map(report => (
                        <ReportCard key={report.id} report={report} isFirst={false} />
                    ))}
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
    card: {
        backgroundColor: 'rgba(255,255,255,0.88)',
        borderRadius: 16,
        padding: 16,
        marginBottom: 16,
        shadowColor: '#000',
        shadowOffset: { width: 0, height: 2 },
        shadowOpacity: 0.08,
        shadowRadius: 6,
    },
    cardToday: {
        borderLeftWidth: 4,
        borderLeftColor: '#48AEBE',
    },
    cardTitle: {
        fontSize: 16,
        fontWeight: '700',
        color: '#444',
        textAlign: 'right',
        marginBottom: 12,
    },
    cardTitleToday: {
        color: '#48AEBE',
    },
    row: {
        flexDirection: 'row-reverse',
        justifyContent: 'space-between',
        alignItems: 'flex-start',
        paddingVertical: 8,
        borderBottomWidth: 1,
        borderBottomColor: '#f0f0f0',
    },
    rowWarning: {
        backgroundColor: 'rgba(255,90,90,0.06)',
        borderRadius: 8,
        paddingHorizontal: 6,
        borderBottomColor: '#ffe0e0',
    },
    rowLabel: {
        fontSize: 14,
        fontWeight: '600',
        color: '#444',
        textAlign: 'right',
        flexShrink: 0,
        marginLeft: 8,
    },
    rowLabelWarning: {
        color: '#cc2222',
    },
    rowValue: {
        fontSize: 14,
        color: '#333',
        textAlign: 'left',
        flex: 1,
        flexWrap: 'wrap',
    },
    rowValueWarning: {
        color: '#cc2222',
        fontWeight: '500',
    },
    rowValueEmoji: {
        fontSize: 22,
    },
    mapButton: {
        marginTop: 14,
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
    historyTitle: {
        fontSize: 18,
        fontWeight: '700',
        color: '#444',
        textAlign: 'right',
        marginBottom: 12,
    },
});
