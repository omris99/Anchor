import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Animated, Modal, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { useFocusEffect } from '@react-navigation/native';
import { apiRequest } from '../../logic/services/api/ApiClient';

const STATUS_COLORS = {
    green:  '#2ecc71',
    yellow: '#f4a324',
    red:    '#e74c3c',
};

export default function WellnessStatusCard({ userId }) {
    const [status, setStatus] = useState({ status: 'green', reason: 'טוען...', concerns: [] });
    const [isModalVisible, setIsModalVisible] = useState(false);
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
    const concerns = status.concerns ?? [];
    const hasConcerns = concerns.length > 0;

    return (
        <>
            <TouchableOpacity
                style={styles.card}
                activeOpacity={hasConcerns ? 0.7 : 1}
                disabled={!hasConcerns}
                onPress={() => setIsModalVisible(true)}
            >
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
            </TouchableOpacity>

            <Modal
                visible={isModalVisible}
                transparent
                animationType="fade"
                onRequestClose={() => setIsModalVisible(false)}
            >
                <TouchableOpacity
                    style={styles.modalOverlay}
                    activeOpacity={1}
                    onPress={() => setIsModalVisible(false)}
                >
                    <TouchableOpacity activeOpacity={1} style={styles.modalCard}>
                        <Text style={styles.modalTitle}>מה כדאי לבדוק</Text>
                        {concerns.map((concern, index) => (
                            <View key={index} style={styles.concernRow}>
                                <Text style={styles.concernBullet}>•</Text>
                                <Text style={styles.concernText}>{concern}</Text>
                            </View>
                        ))}
                        <TouchableOpacity
                            style={styles.modalClose}
                            onPress={() => setIsModalVisible(false)}
                        >
                            <Text style={styles.modalCloseText}>סגירה</Text>
                        </TouchableOpacity>
                    </TouchableOpacity>
                </TouchableOpacity>
            </Modal>
        </>
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
    concernRow: {
        flexDirection: 'row-reverse',
        alignItems: 'flex-start',
        gap: 8,
        marginBottom: 10,
    },
    concernBullet: {
        fontSize: 16,
        color: '#1C2B3A',
    },
    concernText: {
        fontSize: 15,
        color: '#1C2B3A',
        textAlign: 'right',
        flex: 1,
    },
    modalClose: {
        marginTop: 12,
        alignSelf: 'center',
        paddingHorizontal: 24,
        paddingVertical: 10,
        borderRadius: 10,
        backgroundColor: '#4A6FA5',
    },
    modalCloseText: {
        color: '#fff',
        fontWeight: '700',
        fontSize: 15,
    },
});
