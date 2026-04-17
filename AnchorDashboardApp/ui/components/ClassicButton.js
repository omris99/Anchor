import {StyleSheet, Text, TouchableOpacity} from "react-native";
import React from "react";


export default function ClassicButton({buttonStyle, textStyle, onPress, children, disabled})
{
    return(
        <TouchableOpacity style={[styles.button, buttonStyle]} onPress={onPress} disabled={disabled} activeOpacity={0.4}>
            <Text style={[styles.text, textStyle]}>{children}</Text>
        </TouchableOpacity>
    )
}

const styles = StyleSheet.create({
    button: {
        backgroundColor: '#48AEBE',
        width: '70%',
        height: 40,
        borderRadius: 12,
        borderWidth: 2,
        borderColor: '#2a838f',
        alignSelf: 'center',
        justifyContent: 'center',
        alignItems: 'center',
        marginTop: 20,
        shadowColor: '#000000',
        shadowOffset: {width: 0, height: 4},
        shadowOpacity: 0.3,
        shadowRadius: 6,
    },
    text: {
        fontSize: 18,
        fontWeight: '600',
        textAlign: 'center',
        color: '#fff6f6',
    },
})