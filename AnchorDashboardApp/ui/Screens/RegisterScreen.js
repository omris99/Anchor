import React, {useRef, useState} from 'react';
import {
    ActivityIndicator,
    Alert,
    Image,
    ImageBackground,
    KeyboardAvoidingView,
    Modal,
    Platform,
    Pressable,
    ScrollView,
    StyleSheet,
    Text,
    TouchableOpacity,
    View
} from 'react-native';
import ClassicButton from "../components/ClassicButton";
import TextInputField from "../components/TextInputField";
import {registerUser} from "../../logic/services/authentication/RegisterService";
import {Picker} from '@react-native-picker/picker';

export default function RegisterScreen({navigation}) {
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [firstName, setFirstName] = useState('');
    const [lastName, setLastName] = useState('');
    const [phone, setPhone] = useState('');
    const [loading, setLoading] = useState(false);
    const [gender, setGender] = useState('');
    const [showGenderPicker, setShowGenderPicker] = useState(false);
    const [userType, setUserType] = useState('');
    const [showUserTypePicker, setShowUserTypePicker] = useState(false);
    const [birthDate, setBirthDate] = useState(null);
    const [showDatePicker, setShowDatePicker] = useState(false);
    const currentYear = new Date().getFullYear();
    const [tempDay, setTempDay] = useState(1);
    const [tempMonth, setTempMonth] = useState(1);
    const [tempYear, setTempYear] = useState(1970);

    const MONTHS = ['ינואר','פברואר','מרץ','אפריל','מאי','יוני','יולי','אוגוסט','ספטמבר','אוקטובר','נובמבר','דצמבר'];

    const passwordRef = useRef(null);
    const firstNameRef = useRef(null);
    const lastNameRef = useRef(null);
    const phoneRef = useRef(null);
    const emailRef = useRef(null);

    const handleRegister = async () => {
        try {
            setLoading(true);

            const result = await registerUser({
                email,
                password,
                firstName,
                lastName,
                phone,
                gender,
                userType,
                birthDate: birthDate ? birthDate.toISOString().split('T')[0] : null,
            });

            if (result.nextStep?.signUpStep === "CONFIRM_SIGN_UP") {
                navigation.navigate("ConfirmSignUp", {
                    email,
                    password,
                    firstName,
                    lastName,
                    phone,
                    gender,
                });
                return;
            }

            // Alert.alert("הצלחה", "ההרשמה הושלמה בהצלחה");
            navigation.navigate("welcome");
        } catch (error) {
            console.error(error);

            if (error.name === "UsernameExistsException") {
                Alert.alert("שגיאה", "האימייל שהוזן כבר בשימוש");
                return;
            }

            if (error.name === "InvalidPasswordException") {
                Alert.alert("שגיאה", "הסיסמה לא עומדת בדרישות");
                return;
            }

            if (error.message) {
                Alert.alert("שגיאה", error.message);
                return;
            }

            // Alert.alert("שגיאה", "אירעה שגיאה במהלך ההרשמה. אנא נסה שוב מאוחר יותר.");
        } finally {
            setLoading(false);
        }
    };

    return (
        <ImageBackground
            source={require('../assets/login-bg.png')}
            style={styles.background}
            imageStyle={{opacity: 1}}
        >
            <KeyboardAvoidingView
                style={{flex: 1}}
                behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
                keyboardVerticalOffset={Platform.OS === 'ios' ? 130 : 0}
            >
                <ScrollView contentContainerStyle={styles.container} showsVerticalScrollIndicator={false}>
                    <Image style={styles.image} source={require('../assets/logo.png')}/>
                    <View style={styles.form}>
                        <Text style={styles.label}>הרשמה :</Text>

                        <TextInputField
                            ref={firstNameRef}
                            placeholder="שם פרטי"
                            value={firstName}
                            onChangeText={setFirstName}
                            onSubmitEditing={() => lastNameRef.current.focus()}
                            blurOnSubmit={false}
                            returnKeyType='next'
                        />

                        <TextInputField
                            ref={lastNameRef}
                            placeholder="שם משפחה"
                            value={lastName}
                            onChangeText={setLastName}
                            onSubmitEditing={() => emailRef.current.focus()}
                            blurOnSubmit={false}
                            returnKeyType='next'
                        />
                        <TextInputField
                            ref={phoneRef}
                            placeholder="מספר פלאפון"
                            value={phone}
                            onChangeText={setPhone}
                            keyboardType="phone-pad"
                            returnKeyType='done'
                        />
                        <TextInputField
                            ref={passwordRef}
                            placeholder="סיסמה"
                            secureTextEntry
                            value={password}
                            onChangeText={setPassword}
                            onSubmitEditing={() => phoneRef.current.focus()}
                            blurOnSubmit={false}
                            returnKeyType='next'
                        />
                        <Pressable style={styles.selectField} onPress={() => setShowGenderPicker(true)}>
                            <Text style={[styles.selectText, !gender && styles.selectPlaceholder]}>
                                {gender === 'male' ? 'זכר' : gender === 'female' ? 'נקבה' : 'מגדר...'}
                            </Text>
                        </Pressable>

                        <Modal visible={showGenderPicker} transparent animationType="slide">
                            <View style={styles.modalContainer}>
                                <TouchableOpacity style={styles.modalOverlay} activeOpacity={1} onPress={() => setShowGenderPicker(false)}/>
                                <View style={styles.pickerSheet}>
                                    <TouchableOpacity style={styles.doneButton} onPress={() => setShowGenderPicker(false)}>
                                        <Text style={styles.doneText}>אישור</Text>
                                    </TouchableOpacity>
                                    <Picker selectedValue={gender} onValueChange={(val) => setGender(val)}>
                                        <Picker.Item label="מגדר..." value="" color="#aaa"/>
                                        <Picker.Item label="זכר" value="male" color="#333"/>
                                        <Picker.Item label="נקבה" value="female" color="#333"/>
                                    </Picker>
                                </View>
                            </View>
                        </Modal>

                        <Pressable style={styles.selectField} onPress={() => setShowUserTypePicker(true)}>
                            <Text style={[styles.selectText, !userType && styles.selectPlaceholder]}>
                                {userType === 'family_member' ? 'בן משפחה' : userType === 'elderly' ? 'מבוגר' : 'סוג משתמש...'}
                            </Text>
                        </Pressable>

                        <Modal visible={showUserTypePicker} transparent animationType="slide">
                            <View style={styles.modalContainer}>
                                <TouchableOpacity style={styles.modalOverlay} activeOpacity={1} onPress={() => setShowUserTypePicker(false)}/>
                                <View style={styles.pickerSheet}>
                                    <TouchableOpacity style={styles.doneButton} onPress={() => setShowUserTypePicker(false)}>
                                        <Text style={styles.doneText}>אישור</Text>
                                    </TouchableOpacity>
                                    <Picker selectedValue={userType} onValueChange={(val) => setUserType(val)}>
                                        <Picker.Item label="סוג משתמש..." value="" color="#aaa"/>
                                        <Picker.Item label="בן משפחה" value="family_member" color="#333"/>
                                        <Picker.Item label="מבוגר" value="elderly" color="#333"/>
                                    </Picker>
                                </View>
                            </View>
                        </Modal>

                        <Pressable onPress={() => {
                            if (birthDate) {
                                setTempDay(birthDate.getDate());
                                setTempMonth(birthDate.getMonth() + 1);
                                setTempYear(birthDate.getFullYear());
                            }
                            setShowDatePicker(true);
                        }} style={styles.dateField}>
                            <Text style={[styles.dateText, !birthDate && styles.datePlaceholder]}>
                                {birthDate ? `${birthDate.getDate()} ${MONTHS[birthDate.getMonth()]} ${birthDate.getFullYear()}` : 'תאריך לידה'}
                            </Text>
                        </Pressable>

                        <Modal visible={showDatePicker} transparent animationType="slide">
                            <View style={styles.modalContainer}>
                                <TouchableOpacity style={styles.modalOverlay} activeOpacity={1} onPress={() => setShowDatePicker(false)}/>
                                <View style={styles.pickerSheet}>
                                    <TouchableOpacity style={styles.doneButton} onPress={() => {
                                        setBirthDate(new Date(tempYear, tempMonth - 1, tempDay));
                                        setShowDatePicker(false);
                                    }}>
                                        <Text style={styles.doneText}>אישור</Text>
                                    </TouchableOpacity>
                                    <View style={styles.datePickersRow}>
                                        <Picker selectedValue={tempDay} onValueChange={setTempDay} style={styles.datePicker}>
                                            {Array.from({length: 31}, (_, i) => i + 1).map(d => (
                                                <Picker.Item key={d} label={String(d)} value={d} color="#333"/>
                                            ))}
                                        </Picker>
                                        <Picker selectedValue={tempMonth} onValueChange={setTempMonth} style={styles.datePicker}>
                                            {MONTHS.map((m, i) => (
                                                <Picker.Item key={i} label={m} value={i + 1} color="#333"/>
                                            ))}
                                        </Picker>
                                        <Picker selectedValue={tempYear} onValueChange={setTempYear} style={styles.datePicker}>
                                            {Array.from({length: currentYear - 1919}, (_, i) => currentYear - i).map(y => (
                                                <Picker.Item key={y} label={String(y)} value={y} color="#333"/>
                                            ))}
                                        </Picker>
                                    </View>
                                </View>
                            </View>
                        </Modal>

                        <TextInputField
                            ref={emailRef}
                            placeholder="אי-מייל"
                            value={email}
                            onChangeText={setEmail}
                            keyboardType="email-address"
                            onSubmitEditing={() => passwordRef.current.focus()}
                            blurOnSubmit={false}
                            returnKeyType='next'
                        />

                        <ClassicButton
                            buttonStyle={{backgroundColor: '#4A90D9', borderColor: '#2E6DA8', marginBottom: 30}}
                            onPress={() => {
                                !loading && handleRegister()
                            }}
                            disabled={loading}>
                            {loading ? (<ActivityIndicator color="#fff"/>) : ("הירשם!")}
                        </ClassicButton>
                    </View>
                </ScrollView>
            </KeyboardAvoidingView>
        </ImageBackground>
    );
}

const styles = StyleSheet.create({
    container: {
        flexGrow: 1,
        alignItems: 'center',
        justifyContent: 'flex-start',
    },
    image: {
        width: 250,
        height: 250,
        resizeMode: 'contain',
        marginTop: 20,
        marginBottom: 20,
    },
    form: {
        width: 280,
    },
    label: {
        fontSize: 24,
        fontWeight: "500",
        marginBottom: 25,
        alignSelf: 'center',
        color: '#636262',
    },
    background: {
        flex: 1,
        alignItems: 'center',
        resizeMode: 'cover',
        width: '100%',
    },
    selectField: {
        borderWidth: 1,
        borderColor: '#aaa',
        borderRadius: 8,
        padding: 10,
        margin: 10,
        justifyContent: 'center',
        backgroundColor: '#ffffff',
    },
    selectText: {
        fontSize: 16,
        color: '#333',
        textAlign: 'right',
    },
    selectPlaceholder: {
        color: '#aaa',
    },
    modalContainer: {
        flex: 1,
        justifyContent: 'flex-end',
        backgroundColor: 'rgba(0,0,0,0)',
    },
    modalOverlay: {
        flex: 1,
    },
    pickerSheet: {
        backgroundColor: '#fff',
        paddingBottom: 20,
    },
    doneButton: {
        alignSelf: 'flex-end',
        paddingHorizontal: 20,
        paddingVertical: 12,
    },
    doneText: {
        color: '#4A90D9',
        fontSize: 16,
        fontWeight: '600',
    },
    dateField: {
        borderWidth: 1,
        borderColor: '#aaa',
        borderRadius: 8,
        padding: 10,
        margin: 10,
        justifyContent: 'center',
        backgroundColor: '#ffffff',
    },
    datePickersRow: {
        flexDirection: 'row',
    },
    datePicker: {
        flex: 1,
    },
    dateText: {
        fontSize: 16,
        color: '#333',
        textAlign: 'right',
    },
    datePlaceholder: {
        color: '#aaa',
    },
});

