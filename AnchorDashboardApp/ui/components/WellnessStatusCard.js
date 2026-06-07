import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Animated, StyleSheet, Text, View } from 'react-native';
import { useFocusEffect } from '@react-navigation/native';
import { apiRequest } from '../../logic/services/api/ApiClient';

const STATUS_COLORS = {
    green:  '#2ecc71',
    yellow: '#f4a324',
    red:    '#e74c3c',
};

export default function WellnessStatusCard({ userId }) {
    const [status, setStatus] = useState({ status: 'green', reason: 'טוען...' });
    const pulseAnim = useRef(new Animated.Value(1)).current;

    useFocusEffect(
        useCallback(() => {
            if (!userId) return;
            apiRequest(`/users/${userId}/status`)
                .then(data => setStatus(data))
                .catch(() => {});
        }, [userId])
    );

    useEffect(() => {
        const animation = Animated.loop(
            Animated.sequence([
                Animated.timing(pulseAnim, { toValue: 1.3, duration: 900, useNativeDriver: true }),
                Animated.timing(pulseAnim, { toValue: 1,   duration: 900, useNativeDriver: true }),
            ])
        );
        animation.start();
        return () => animation.stop();
    }, [pulseAnim]);

    const color = STATUS_COLORS[status.status] ?? STATUS_COLORS.green;

    return (
        <View style={styles.card}>
            <Animated.View
                style={[
                    styles.dot,
                    {
                        backgroundColor: color,
                        shadowColor: color,
                        transform: [{ scale: pulseAnim }],
                    },
                ]}
            />
            <Text style={[styles.reason, { color }]}>{status.reason}</Text>
        </View>
    );
}

const styles = StyleSheet.create({
    card: {
        flexDirection: 'row-reverse',
        alignItems: 'center',
        backgroundColor: 'rgba(255,255,255,0.85)',
        borderRadius: 16,
        paddingHorizontal: 18,
        paddingVertical: 14,
        marginBottom: 20,
        gap: 14,
        shadowColor: '#000',
        shadowOffset: { width: 0, height: 2 },
        shadowOpacity: 0.08,
        shadowRadius: 6,
        elevation: 3,
    },
    dot: {
        width: 22,
        height: 22,
        borderRadius: 11,
        shadowOffset: { width: 0, height: 0 },
        shadowOpacity: 0.7,
        shadowRadius: 8,
        elevation: 6,
    },
    reason: {
        fontSize: 16,
        fontWeight: '700',
        textAlign: 'right',
        flex: 1,
    },
});
