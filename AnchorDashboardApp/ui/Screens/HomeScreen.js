import React, { useContext } from 'react';
import { Image, ImageBackground, StyleSheet, Text, View } from 'react-native';
import ClassicButton from '../components/ClassicButton';
import { UserContext } from '../../logic/contexts/UserContext';
import { SafeAreaView } from 'react-native-safe-area-context';

export default function HomeScreen({ navigation }) {
    const { user } = useContext(UserContext);
    const greet = user ? `שלום, ${user.firstName}` : 'שלום';

    return (
        <ImageBackground
            source={require('../assets/wave-background.png')}
            style={styles.background}
            resizeMode="cover"
        >
        <SafeAreaView style={styles.container} edges={["top"]}>
            <Image
                source={require('../assets/anchor-logo-wide.png')}
                style={styles.logo}
                resizeMode="contain"
            />

            <Text style={styles.greeting}>{greet}</Text>
            <Text style={styles.subtitle}>מסך הבית</Text>

            <View style={styles.buttons}>
                <ClassicButton
                    buttonStyle={styles.mainButton}
                    onPress={() => navigation.navigate('medical-data')}
                >
                    ניטור נתונים רפואיים
                </ClassicButton>

                <ClassicButton
                    buttonStyle={styles.mainButton}
                    onPress={() => navigation.navigate('daily-reports')}
                >
                    היסטוריית דיווחים יומיים
                </ClassicButton>

                <ClassicButton
                    buttonStyle={styles.mainButton}
                    onPress={() => navigation.navigate('medication-reminders')}
                >
                    הגדרת תזכורות לתרופות
                </ClassicButton>

                <ClassicButton
                    buttonStyle={styles.mainButton}
                    onPress={() => navigation.navigate('connections')}
                >
                    ניהול קישורים
                </ClassicButton>

                <ClassicButton
                    buttonStyle={styles.mainButton}
                    onPress={() => navigation.navigate('emergency-history')}
                >
                    היסטוריית אירועי חירום
                </ClassicButton>

                <ClassicButton
                    buttonStyle={styles.settingsButton}
                    textStyle={styles.settingsText}
                    onPress={() => navigation.navigate('preferences')}
                >
                    העדפות
                </ClassicButton>
            </View>
        </SafeAreaView>
        </ImageBackground>
    );
}

const styles = StyleSheet.create({
    background: {
        flex: 1,
    },
    container: {
        flex: 1,
        backgroundColor: 'transparent',
        paddingHorizontal: 20,
    },
    logo: {
        width: '80%',
        height: 90,
        alignSelf: 'center',
        marginTop: 20,
        marginBottom: 12,
    },
    greeting: {
        fontSize: 18,
        fontWeight: '600',
        color: '#444',
        textAlign: 'right',
        marginBottom: 2,
    },
    subtitle: {
        fontSize: 26,
        fontWeight: '700',
        color: '#48AEBE',
        textAlign: 'right',
        marginBottom: 28,
    },
    buttons: {
        flex: 1,
        alignItems: 'center',
    },
    mainButton: {
        width: '85%',
        height: 56,
        borderRadius: 14,
        marginTop: 14,
    },
    settingsButton: {
        width: '85%',
        height: 56,
        borderRadius: 14,
        marginTop: 50,
        backgroundColor: '#487dbe',
        borderColor: '#4875be',
    },
    settingsText: {
        color: '#fff',
    },
});
