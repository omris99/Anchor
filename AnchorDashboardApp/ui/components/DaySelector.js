import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';

const DAYS = [
    { id: 0, label: 'א׳' },
    { id: 1, label: 'ב׳' },
    { id: 2, label: 'ג׳' },
    { id: 3, label: 'ד׳' },
    { id: 4, label: 'ה׳' },
    { id: 5, label: 'ו׳' },
    { id: 6, label: 'ש׳' },
];

export const DAY_NAMES = ['ראשון', 'שני', 'שלישי', 'רביעי', 'חמישי', 'שישי', 'שבת'];

export default function DaySelector({ selectedDays, onChange }) {
    const toggleDay = (dayIndex) => {
        if (selectedDays.includes(dayIndex)) {
            onChange(selectedDays.filter(existingDay => existingDay !== dayIndex));
        } else {
            onChange([...selectedDays, dayIndex].sort((a, b) => a - b));
        }
    };

    return (
        <View style={styles.row}>
            {DAYS.map(day => {
                const selected = selectedDays.includes(day.id);
                return (
                    <TouchableOpacity
                        key={day.id}
                        style={[styles.chip, selected && styles.chipSelected]}
                        onPress={() => toggleDay(day.id)}
                        activeOpacity={0.7}
                    >
                        <Text style={[styles.label, selected && styles.labelSelected]}>
                            {day.label}
                        </Text>
                    </TouchableOpacity>
                );
            })}
        </View>
    );
}

const styles = StyleSheet.create({
    row: {
        flexDirection: 'row-reverse',
        justifyContent: 'space-between',
        marginTop: 6,
    },
    chip: {
        width: 38,
        height: 38,
        borderRadius: 19,
        borderWidth: 1.5,
        borderColor: '#48AEBE',
        justifyContent: 'center',
        alignItems: 'center',
        backgroundColor: '#fff',
    },
    chipSelected: {
        backgroundColor: '#48AEBE',
    },
    label: {
        fontSize: 13,
        fontWeight: '600',
        color: '#48AEBE',
    },
    labelSelected: {
        color: '#fff',
    },
});
