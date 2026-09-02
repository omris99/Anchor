import React from 'react';
import { Image, ImageBackground, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import ClassicButton from '../components/ClassicButton';

export default function RemindersHubScreen({ navigation }) {
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

                <Text style={styles.title}>הגדרת תזכורות</Text>

                <View style={styles.buttons}>
                    <ClassicButton
                        buttonStyle={styles.mainButton}
                        onPress={() => navigation.navigate('medication-reminders')}
                    >
                        תזכורות לתרופות
                    </ClassicButton>

                    <ClassicButton
                        buttonStyle={styles.mainButton}
                        onPress={() => navigation.navigate('water-reminders')}
                    >
                        תזכורות לשתיית מים
                    </ClassicButton>
                </View>
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
        marginBottom: 24,
    },
    buttons: {
        alignItems: 'center',
    },
    mainButton: {
        width: '85%',
        height: 56,
        borderRadius: 14,
        marginTop: 14,
    },
});
