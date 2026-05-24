import React, { useState } from 'react';
import {
    Alert,
    Image,
    ImageBackground,
    StyleSheet,
    Text,
    TouchableOpacity,
    View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { CameraView, useCameraPermissions } from 'expo-camera';
import ClassicButton from '../components/ClassicButton';

export default function WatchPairingScreen({ navigation }) {
    const [permission, requestPermission] = useCameraPermissions();
    const [scanned, setScanned] = useState(false);
    const [pairedWatchId, setPairedWatchId] = useState(null);

    const handleBarcodeScanned = ({ data }) => {
        if (scanned) return;
        setScanned(true);

        // TODO: POST /watch/pair — שליחת watchId לשרת לקישור עם המשתמש הנוכחי
        // data מכיל את ה-watchId שהשעון הציג
        setPairedWatchId(data);
    };

    const handlePairAgain = () => {
        setScanned(false);
        setPairedWatchId(null);
    };

    if (!permission) {
        return <View style={styles.centered}><Text>טוען...</Text></View>;
    }

    if (!permission.granted) {
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
                    <View style={styles.permissionContainer}>
                        <Text style={styles.permissionText}>נדרשת גישה למצלמה לסריקת קוד ה-QR</Text>
                        <ClassicButton buttonStyle={styles.permissionButton} onPress={requestPermission}>
                            אפשר גישה למצלמה
                        </ClassicButton>
                    </View>
                </SafeAreaView>
            </ImageBackground>
        );
    }

    if (scanned && pairedWatchId) {
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
                    <View style={styles.successContainer}>
                        <View style={styles.successIcon}>
                            <Text style={styles.successCheckmark}>✓</Text>
                        </View>
                        <Text style={styles.successTitle}>הקישור הושלם!</Text>
                        <Text style={styles.successSubtitle}>השעון קושר בהצלחה לחשבונך</Text>
                        <ClassicButton
                            buttonStyle={styles.doneButton}
                            onPress={() => navigation.navigate('main-tabs')}
                        >
                            חזור לדף הבית
                        </ClassicButton>
                        <TouchableOpacity onPress={handlePairAgain} style={styles.pairAgainLink}>
                            <Text style={styles.pairAgainText}>קשר שעון אחר</Text>
                        </TouchableOpacity>
                    </View>
                </SafeAreaView>
            </ImageBackground>
        );
    }

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

                <Text style={styles.title}>קישור שעון</Text>
                <Text style={styles.instruction}>סרוק את קוד ה-QR המוצג על גבי השעון</Text>

                <View style={styles.scannerWrapper}>
                    <CameraView
                        style={styles.camera}
                        facing="back"
                        barcodeScannerSettings={{ barcodeTypes: ['qr'] }}
                        onBarcodeScanned={handleBarcodeScanned}
                    />
                    <View style={styles.overlay}>
                        <View style={styles.viewfinder}>
                            <View style={[styles.corner, styles.cornerTopLeft]} />
                            <View style={[styles.corner, styles.cornerTopRight]} />
                            <View style={[styles.corner, styles.cornerBottomLeft]} />
                            <View style={[styles.corner, styles.cornerBottomRight]} />
                        </View>
                    </View>
                </View>

                <Text style={styles.hint}>כוון את המצלמה לקוד המוצג על השעון</Text>
            </SafeAreaView>
        </ImageBackground>
    );
}

const VIEWFINDER_SIZE = 240;
const CORNER_SIZE = 28;
const CORNER_THICKNESS = 4;
const CORNER_COLOR = '#48AEBE';

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
    centered: {
        flex: 1,
        alignItems: 'center',
        justifyContent: 'center',
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
        marginBottom: 6,
    },
    instruction: {
        fontSize: 16,
        color: '#444',
        textAlign: 'right',
        marginBottom: 24,
    },
    scannerWrapper: {
        alignSelf: 'center',
        width: VIEWFINDER_SIZE + 40,
        height: VIEWFINDER_SIZE + 40,
        borderRadius: 20,
        overflow: 'hidden',
        backgroundColor: '#000',
    },
    camera: {
        flex: 1,
    },
    overlay: {
        ...StyleSheet.absoluteFillObject,
        alignItems: 'center',
        justifyContent: 'center',
    },
    viewfinder: {
        width: VIEWFINDER_SIZE,
        height: VIEWFINDER_SIZE,
    },
    corner: {
        position: 'absolute',
        width: CORNER_SIZE,
        height: CORNER_SIZE,
        borderColor: CORNER_COLOR,
    },
    cornerTopLeft: {
        top: 0,
        left: 0,
        borderTopWidth: CORNER_THICKNESS,
        borderLeftWidth: CORNER_THICKNESS,
        borderTopLeftRadius: 4,
    },
    cornerTopRight: {
        top: 0,
        right: 0,
        borderTopWidth: CORNER_THICKNESS,
        borderRightWidth: CORNER_THICKNESS,
        borderTopRightRadius: 4,
    },
    cornerBottomLeft: {
        bottom: 0,
        left: 0,
        borderBottomWidth: CORNER_THICKNESS,
        borderLeftWidth: CORNER_THICKNESS,
        borderBottomLeftRadius: 4,
    },
    cornerBottomRight: {
        bottom: 0,
        right: 0,
        borderBottomWidth: CORNER_THICKNESS,
        borderRightWidth: CORNER_THICKNESS,
        borderBottomRightRadius: 4,
    },
    hint: {
        fontSize: 14,
        color: '#666',
        textAlign: 'center',
        marginTop: 20,
    },
    permissionContainer: {
        flex: 1,
        alignItems: 'center',
        justifyContent: 'center',
        paddingHorizontal: 20,
    },
    permissionText: {
        fontSize: 18,
        color: '#444',
        textAlign: 'center',
        marginBottom: 24,
        lineHeight: 26,
    },
    permissionButton: {
        width: '80%',
    },
    successContainer: {
        flex: 1,
        alignItems: 'center',
        justifyContent: 'center',
    },
    successIcon: {
        width: 90,
        height: 90,
        borderRadius: 45,
        backgroundColor: '#4CAF50',
        alignItems: 'center',
        justifyContent: 'center',
        marginBottom: 24,
        shadowColor: '#4CAF50',
        shadowOffset: { width: 0, height: 6 },
        shadowOpacity: 0.35,
        shadowRadius: 10,
    },
    successCheckmark: {
        fontSize: 44,
        color: '#fff',
        fontWeight: '700',
    },
    successTitle: {
        fontSize: 28,
        fontWeight: '800',
        color: '#333',
        marginBottom: 8,
    },
    successSubtitle: {
        fontSize: 16,
        color: '#666',
        marginBottom: 40,
    },
    doneButton: {
        width: 240,
    },
    pairAgainLink: {
        marginTop: 20,
    },
    pairAgainText: {
        fontSize: 15,
        color: '#48AEBE',
        fontWeight: '600',
        textDecorationLine: 'underline',
    },
});
