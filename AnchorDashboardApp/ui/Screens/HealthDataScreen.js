import React, { useCallback, useContext, useState } from 'react';
import {
    ActivityIndicator,
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
import { UserContext } from '../../logic/contexts/UserContext';
import { apiRequest } from '../../logic/services/api/ApiClient';

const SCREEN_WIDTH = Dimensions.get('window').width;

const METRICS = [
    { key: 'heartRate', label: 'דופק' },
    { key: 'steps', label: 'צעדים' },
];

// TODO: LOAD — יש לטעון נתוני בריאות אמיתיים מהשרת.
// GET /users/{userId}/health-data?metric=heartRate&range=30d
const MOCK_DATA = {
    heartRate: {
        labels: ['1/4', '8/4', '15/4', '22/4', '29/4', '4/5'],
        values: [72, 75, 68, 80, 74, 71],
        unit: 'BPM',
        normalRange: '60–100',
        latestValue: 71,
        isAbnormal: false,
    },
    steps: {
        labels: ['1/4', '8/4', '15/4', '22/4', '29/4', '4/5'],
        values: [3200, 4100, 2800, 5000, 3700, 2100],
        unit: 'צעדים',
        normalRange: '3000–8000',
        latestValue: 2100,
        isAbnormal: true,
    },
    sleep: {
        labels: ['1/4', '8/4', '15/4', '22/4', '29/4', '4/5'],
        values: [7.5, 6.0, 8.0, 5.5, 7.0, 5.0],
        unit: 'שעות',
        normalRange: '7–9',
        latestValue: 5.0,
        isAbnormal: true,
    },
};

const LAST_MONITORING = {
    timestamp: '4/5/2026, 08:32',
    heartRate: 71,
    sleepHours: 5.0,
    sleepQuality: 'נמוכה',
    steps: 2100,
};

const ABNORMAL_METRICS = [
    { name: 'צעדים', currentValue: '2,100 צעדים', normalRange: '3,000–8,000 צעדים' },
    { name: 'שינה', currentValue: '5.0 שעות', normalRange: '7–9 שעות' },
];

export default function HealthDataScreen({ navigation }) {
    const { user } = useContext(UserContext);
    const [selectedMetric, setSelectedMetric] = useState('heartRate');
    const [latestMetrics, setLatestMetrics] = useState(null);
    const [loadingMetrics, setLoadingMetrics] = useState(true);
    const metricData = MOCK_DATA[selectedMetric];

    useFocusEffect(
        useCallback(() => {
            setLoadingMetrics(true);
            apiRequest(`/users/${user.userId}/health-metrics/latest`)
                .then(data => setLatestMetrics(data.latest))
                .catch(() => {})
                .finally(() => setLoadingMetrics(false));
        }, [user.userId])
    );

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
                    </View>

                    {/* Last monitoring */}
                    <View style={styles.card}>
                        {loadingMetrics ? (
                            <ActivityIndicator color="#48AEBE" style={{ paddingVertical: 16 }} />
                        ) : (() => {
                            const ts = latestMetrics?.timestamp
                                ? new Date(latestMetrics.timestamp).toLocaleString('he-IL')
                                : LAST_MONITORING.timestamp;
                            const hr = latestMetrics?.heart_rate ?? LAST_MONITORING.heartRate;
                            const steps = latestMetrics?.steps ?? LAST_MONITORING.steps;
                            return (
                                <>
                                    <Text style={styles.sectionLabel}>ניטור אחרון ({ts})</Text>
                                    <View style={styles.metricRow}>
                                        <Text style={styles.metricName}>דופק:</Text>
                                        <Text style={styles.metricValue}>{hr} BPM</Text>
                                    </View>
                                    <View style={styles.metricRow}>
                                        <Text style={styles.metricName}>מד צעדים:</Text>
                                        <Text style={styles.metricValue}>{steps.toLocaleString()} צעדים</Text>
                                    </View>
                                </>
                            );
                        })()}
                    </View>

                    {/* Abnormal metrics */}
                    {ABNORMAL_METRICS.length > 0 && (
                        <View style={[styles.card, styles.abnormalCard]}>
                            <Text style={styles.abnormalTitle}>מדדים שזוהו כחריגים:</Text>
                            {ABNORMAL_METRICS.map((item, index) => (
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
                        onPress={() => {
                            // TODO: EXPORT — יצוא PDF של הנתונים הרפואיים
                        }}
                    >
                        <Text style={styles.exportButtonText}>ייצא סיכום למסמך PDF</Text>
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
