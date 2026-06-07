import { Linking } from 'react-native';

export function openMapLocation(location) {
    const url = `https://www.google.com/maps?q=${location.lat},${location.lng}`;
    Linking.openURL(url);
}
