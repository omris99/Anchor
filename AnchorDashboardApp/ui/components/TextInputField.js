import {StyleSheet, TextInput} from "react-native";
import React from "react";

export default function TextInputField({style, placeholder, keyboardType, value, onChangeText, secureTextEntry, ref, onSubmitEditing, returnKeyType, blurOnSubmit, multiline= false, maxLength= 50, editable = true})
{
    return (
        <TextInput
            style={[styles.input, style]}
            placeholder={placeholder}
            textAlign="right"
            keyboardType={keyboardType}
            value={value}
            onChangeText={onChangeText}
            autoCapitalize="none"
            secureTextEntry={secureTextEntry}
            ref={ref}
            onSubmitEditing={onSubmitEditing}
            returnKeyType={returnKeyType}
            blurOnSubmit={blurOnSubmit}
            maxLength={maxLength}
            multiline={multiline}
            editable={editable}
        />
    )
}

const styles = StyleSheet.create({
    input: {
        borderWidth: 1,
        borderColor: '#aaa',
        borderRadius: 8,
        padding: 10,
        margin: 10,
        fontSize: 16,
        backgroundColor: '#ffffff',
    }
})