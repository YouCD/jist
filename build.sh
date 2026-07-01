APK=$(pwd)/app/build/outputs/apk/debug/app-debug.apk
CRT_PATH=$(pwd)/build/security
SIGNED=/tmp/jist-signed.apk
SIGNER_PATH=/home/ycd/Android/Sdk/build-tools/36.1.0/apksigner
./gradlew assembleDebug && \
adb wait-for-device && adb root && adb remount && \
cp "$APK" "$SIGNED" && \
${SIGNER_PATH} sign --key ${CRT_PATH}/platform.pk8 --cert ${CRT_PATH}/platform.x509.pem "$SIGNED" && \
adb push "$SIGNED" /system/priv-app/Jist/Jist.apk && \
adb shell pm install -r -t /system/priv-app/Jist/Jist.apk && \
adb shell am force-stop dev.rcht.jist && \
adb shell am start -n dev.rcht.jist/.MainActivity
rm "$SIGNED"
