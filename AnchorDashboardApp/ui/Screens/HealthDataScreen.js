import React, { useCallback, useContext, useState } from 'react';
import {
    ActivityIndicator,
    Alert,
    Dimensions,
    Image,
    ImageBackground,
    ScrollView,
    StyleSheet,
    Text,
    TouchableOpacity,
    View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { LineChart } from 'react-native-chart-kit';
import { useFocusEffect } from '@react-navigation/native';
import * as Print from 'expo-print';
import * as Sharing from 'expo-sharing';
import { UserContext } from '../../logic/contexts/UserContext';
import { apiRequest } from '../../logic/services/api/ApiClient';
import { getViewedUserId } from '../../logic/utils/viewedUser';
import { ANCHOR_LOGO_BASE64 } from '../assets/anchorLogoBase64';

const SCREEN_WIDTH = Dimensions.get('window').width;

const METRICS = [
    { key: 'heartRate', label: 'דופק' },
    { key: 'steps', label: 'צעדים' },
];

// Groups raw Anchor_BiometricData readings (many per day) into one row per
// day: average heart rate across the day's readings, and steps as the max
// reading of the day (the watch reports a running daily total, not a delta).
function aggregateHistoryByDay(items) {
    const byDay = {};
    items.forEach(item => {
        const day = item.timestamp.slice(0, 10); // YYYY-MM-DD (UTC)
        if (!byDay[day]) byDay[day] = { heartRateSum: 0, heartRateCount: 0, maxSteps: 0 };
        if (item.heart_rate != null) {
            byDay[day].heartRateSum += item.heart_rate;
            byDay[day].heartRateCount += 1;
        }
        if (item.steps != null) {
            byDay[day].maxSteps = Math.max(byDay[day].maxSteps, item.steps);
        }
    });
    return Object.keys(byDay).sort().map(day => ({
        date: day,
        avgHeartRate: byDay[day].heartRateCount > 0 ? Math.round(byDay[day].heartRateSum / byDay[day].heartRateCount) : null,
        steps: byDay[day].maxSteps,
    }));
}

function formatDayLabel(isoDay) {
    const [year, month, day] = isoDay.split('-');
    return `${day}/${month}/${year.slice(-2)}`;
}

function formatShortDayLabel(isoDay) {
    const [, month, day] = isoDay.split('-');
    return `${parseInt(day, 10)}/${parseInt(month, 10)}`;
}

// Picks up to maxPoints evenly-spaced entries so the on-screen chart stays
// readable even when the real history has up to 30 daily points.
function sampleEvenly(items, maxPoints) {
    if (items.length <= maxPoints) return items;
    const step = (items.length - 1) / (maxPoints - 1);
    return Array.from({ length: maxPoints }, (_, i) => items[Math.round(i * step)]);
}

function buildHealthSummaryHtml({ ts, hrDisplay, stepsDisplay, historyRows }) {
    return `
        <html dir="rtl">
            <head>
                <meta charset="utf-8" />
                <style>
                    body { font-family: Arial, sans-serif; padding: 24px; color: #222; }
                    .header { display: flex; flex-direction: row; justify-content: space-between; align-items: flex-start; margin-bottom: 16px; }
                    .logo { width: 140px; height: auto; }
                    h1 { color: #084a54; font-size: 22px; margin-bottom: 4px; }
                    .subtitle { color: #181818; font-size: 13px; margin-bottom: 0; }
                    h2 { font-size: 16px; color: #444; margin-top: 24px; margin-bottom: 8px; }
                    table { width: 100%; border-collapse: collapse; font-size: 14px; }
                    th, td { border: 1px solid #ddd; padding: 8px; text-align: right; }
                    th { background-color: #eef7f8; color: #2a838f; }
                </style>
            </head>
            <body>
                <div class="header">
                    <div>
                        <h1>סיכום נתונים רפואיים</h1>
                        <div class="subtitle">הופק בתאריך ${new Date().toLocaleString('he-IL')}</div>
                    </div>
                    <img class="logo" src="${ANCHOR_LOGO_BASE64}" />
                </div>

                <h2>ניטור אחרון (${ts})</h2>
                <table>
                    <tr><th>מדד</th><th>ערך</th></tr>
                    <tr><td>דופק</td><td>${hrDisplay}</td></tr>
                    <tr><td>מד צעדים</td><td>${stepsDisplay}</td></tr>
                </table>

                <h2>היסטוריית מדדים — חודש אחרון</h2>
                <table>
                    <tr><th>תאריך</th><th>דופק לב</th><th>צעדים</th></tr>
                    ${historyRows}
                </table>
            </body>
        </html>
    `;
}

export default function HealthDataScreen({ navigation }) {
    const { user } = useContext(UserContext);
    const viewedUserId = getViewedUserId(user);
    const [selectedMetric, setSelectedMetric] = useState('heartRate');
    const [latestMetrics, setLatestMetrics] = useState(null);
    const [loadingMetrics, setLoadingMetrics] = useState(true);
    const [dailyHistory, setDailyHistory] = useState([]);
    const [loadingHistory, setLoadingHistory] = useState(true);
    const [exportingPdf, setExportingPdf] = useState(false);

    const chartPoints = sampleEvenly(dailyHistory, 6);
    const metricData = selectedMetric === 'heartRate'
        ? {
            unit: 'BPM',
            labels: chartPoints.map(day => formatShortDayLabel(day.date)),
            values: chartPoints.map(day => day.avgHeartRate ?? 0),
        }
        : {
            unit: 'צעדים',
            labels: chartPoints.map(day => formatShortDayLabel(day.date)),
            values: chartPoints.map(day => day.steps),
        };

    const STEPS_NORMAL_RANGE = { min: 3000, max: 8000 };
    const isStepsAbnormal = latestMetrics?.steps != null
        && (latestMetrics.steps < STEPS_NORMAL_RANGE.min || latestMetrics.steps > STEPS_NORMAL_RANGE.max);
    const abnormalMetrics = isStepsAbnormal
        ? [{ name: 'צעדים', currentValue: `${latestMetrics.steps.toLocaleString()} צעדים`, normalRange: '3,000–8,000 צעדים' }]
        : [];

    useFocusEffect(
        useCallback(() => {
            if (!viewedUserId) return;

            setLoadingMetrics(true);
            apiRequest(`/users/${viewedUserId}/health-metrics/latest`)
                .then(data => setLatestMetrics(data.latest))
                .catch(() => {})
                .finally(() => setLoadingMetrics(false));

            setLoadingHistory(true);
            apiRequest(`/users/${viewedUserId}/health-metrics/history?days=30`)
                .then(data => setDailyHistory(aggregateHistoryByDay(data?.items || [])))
                .catch(() => {})
                .finally(() => setLoadingHistory(false));
        }, [viewedUserId])
    );

    const handleExportPdf = async () => {
        setExportingPdf(true);
        try {
            const ts = latestMetrics?.timestamp
                ? new Date(latestMetrics.timestamp).toLocaleString('he-IL')
                : 'אין נתונים';
            const hrDisplay = latestMetrics?.heart_rate != null ? `${latestMetrics.heart_rate} BPM` : 'אין נתונים';
            const stepsDisplay = latestMetrics?.steps != null ? `${latestMetrics.steps.toLocaleString()} צעדים` : 'אין נתונים';

            const historyRows = dailyHistory.length > 0
                ? dailyHistory
                    .map(day => `<tr><td>${formatDayLabel(day.date)}</td><td>${day.avgHeartRate != null ? `${day.avgHeartRate} BPM` : '—'}</td><td>${day.steps.toLocaleString()}</td></tr>`)
                    .join('')
                : '<tr><td colspan="3">אין נתונים היסטוריים זמינים</td></tr>';

            const html = buildHealthSummaryHtml({ ts, hrDisplay, stepsDisplay, historyRows });

            const { uri } = await Print.printToFileAsync({ html });
            if (await Sharing.isAvailableAsync()) {
                await Sharing.shareAsync(uri, { mimeType: 'application/pdf', dialogTitle: 'סיכום נתונים רפואיים' });
            }
        } catch (error) {
            Alert.alert('שגיאה', 'לא ניתן היה ליצור את קובץ ה-PDF. נסה שוב.');
        } finally {
            setExportingPdf(false);
        }
    };

    const chartConfig = {
        backgroundGradientFrom: '#fff',
        backgroundGradientTo: '#fff',
        color: (opacity = 1) => `rgba(72, 174, 190, ${opacity})`,
        labelColor: () => '#666',
        strokeWidth: 2,
        propsForDots: { r: '4', strokeWidth: '2', stroke: '#2a838f' },
        decimalPlaces: 0,
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
                <Text style={styles.title}>נתונים רפואיים</Text>

                {!viewedUserId ? (
                    <View style={styles.card}>
                        <Text style={styles.noDataText}>עדיין לא מקושר למבוגר</Text>
                    </View>
                ) : (
                <ScrollView
                    contentContainerStyle={styles.scrollContent}
                    showsVerticalScrollIndicator={false}
                >
                    {/* Metric selector */}
                    <View style={styles.card}>
                        <Text style={styles.sectionLabel}>בחר מדד להצגה:</Text>
                        <View style={styles.metricSelector}>
                            {METRICS.map(metric => (
                                <TouchableOpacity
                                    key={metric.key}
                                    style={[
                                        styles.metricTab,
                                        selectedMetric === metric.key && styles.metricTabActive,
                                    ]}
                                    onPress={() => setSelectedMetric(metric.key)}
                                    activeOpacity={0.7}
                                >
                                    <Text
                                        style={[
                                            styles.metricTabText,
                                            selectedMetric === metric.key && styles.metricTabTextActive,
                                        ]}
                                    >
                                        {metric.label}
                                    </Text>
                                </TouchableOpacity>
                            ))}
                        </View>

                        {/* Graph */}
                        <Text style={styles.graphTitle}>
                            גרף מסכם — חודש אחרון ({metricData.unit})
                        </Text>
                        {loadingHistory ? (
                            <ActivityIndicator color="#48AEBE" style={{ paddingVertical: 16 }} />
                        ) : chartPoints.length > 0 ? (
                            <LineChart
                                data={{ labels: metricData.labels, datasets: [{ data: metricData.values }] }}
                                width={SCREEN_WIDTH - 72}
                                height={180}
                                chartConfig={chartConfig}
                                bezier
                                style={styles.chart}
                                withInnerLines={false}
                                withOuterLines={false}
                            />
                        ) : (
                            <Text style={styles.noDataText}>אין נתונים להצגה</Text>
                        )}
                    </View>

                    {/* Last monitoring */}
                    <View style={styles.card}>
                        {loadingMetrics ? (
                            <ActivityIndicator color="#48AEBE" style={{ paddingVertical: 16 }} />
                        ) : (() => {
                            const ts = latestMetrics?.timestamp
                                ? new Date(latestMetrics.timestamp).toLocaleString('he-IL')
                                : null;
                            const hr = latestMetrics?.heart_rate;
                            const steps = latestMetrics?.steps;
                            return (
                                <>
                                    <Text style={styles.sectionLabel}>ניטור אחרון {ts ? `(${ts})` : ''}</Text>
                                    <View style={styles.metricRow}>
                                        <Text style={styles.metricName}>דופק:</Text>
                                        <Text style={styles.metricValue}>{hr != null ? `${hr} BPM` : 'אין נתונים'}</Text>
                                    </View>
                                    <View style={styles.metricRow}>
                                        <Text style={styles.metricName}>מד צעדים:</Text>
                                        <Text style={styles.metricValue}>{steps != null ? `${steps.toLocaleString()} צעדים` : 'אין נתונים'}</Text>
                                    </View>
                                </>
                            );
                        })()}
                    </View>

                    {/* Abnormal metrics */}
                    {abnormalMetrics.length > 0 && (
                        <View style={[styles.card, styles.abnormalCard]}>
                            <Text style={styles.abnormalTitle}>מדדים שזוהו כחריגים:</Text>
                            {abnormalMetrics.map((item, index) => (
                                <View key={index} style={styles.abnormalRow}>
                                    <Text style={styles.abnormalRange}>
                                        טווח ממוצע נורמלי — {item.normalRange}
                                    </Text>
                                    <Text style={styles.abnormalMetric}>
                                        {item.name} — ערך נוכחי: {item.currentValue}
                                    </Text>
                                </View>
                            ))}
                        </View>
                    )}

                    {/* Export PDF */}
                    <TouchableOpacity
                        style={styles.exportButton}
                        activeOpacity={0.7}
                        onPress={handleExportPdf}
                        disabled={exportingPdf}
                    >
                        {exportingPdf ? (
                            <ActivityIndicator color="#fff" />
                        ) : (
                            <Text style={styles.exportButtonText}>ייצא סיכום למסמך PDF</Text>
                        )}
                    </TouchableOpacity>
                </ScrollView>
                )}
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
    sectionLabel: {
        fontSize: 15,
        fontWeight: '600',
        color: '#444',
        textAlign: 'right',
        marginBottom: 10,
    },
    metricSelector: {
        flexDirection: 'row-reverse',
        gap: 8,
        marginBottom: 14,
    },
    metricTab: {
        flex: 1,
        paddingVertical: 8,
        borderRadius: 10,
        borderWidth: 1.5,
        borderColor: '#48AEBE',
        alignItems: 'center',
    },
    metricTabActive: {
        backgroundColor: '#48AEBE',
    },
    metricTabText: {
        fontSize: 14,
        fontWeight: '600',
        color: '#48AEBE',
    },
    metricTabTextActive: {
        color: '#fff',
    },
    graphTitle: {
        fontSize: 13,
        color: '#666',
        textAlign: 'right',
        marginBottom: 8,
    },
    chart: {
        borderRadius: 10,
        alignSelf: 'center',
    },
    metricRow: {
        flexDirection: 'row-reverse',
        justifyContent: 'space-between',
        alignItems: 'center',
        paddingVertical: 8,
        borderBottomWidth: 1,
        borderBottomColor: '#f0f0f0',
    },
    metricName: {
        fontSize: 15,
        fontWeight: '600',
        color: '#444',
    },
    metricValue: {
        fontSize: 15,
        color: '#333',
        fontWeight: '500',
    },
    noDataText: {
        fontSize: 14,
        color: '#888',
        textAlign: 'center',
        paddingVertical: 24,
    },
    abnormalCard: {
        borderLeftWidth: 4,
        borderLeftColor: '#ff5a5a',
    },
    abnormalTitle: {
        fontSize: 15,
        fontWeight: '700',
        color: '#cc2222',
        textAlign: 'right',
        marginBottom: 10,
    },
    abnormalRow: {
        marginBottom: 10,
        paddingBottom: 10,
        borderBottomWidth: 1,
        borderBottomColor: '#ffe0e0',
    },
    abnormalMetric: {
        fontSize: 14,
        fontWeight: '600',
        color: '#333',
        textAlign: 'right',
    },
    abnormalRange: {
        fontSize: 13,
        color: '#888',
        textAlign: 'right',
        marginTop: 2,
    },
    exportButton: {
        backgroundColor: '#48AEBE',
        borderRadius: 14,
        height: 56,
        borderWidth: 2,
        borderColor: '#2a838f',
        justifyContent: 'center',
        alignItems: 'center',
        shadowColor: '#000',
        shadowOffset: { width: 0, height: 4 },
        shadowOpacity: 0.2,
        shadowRadius: 6,
    },
    exportButtonText: {
        fontSize: 17,
        fontWeight: '700',
        color: '#fff',
    },
});
