#!/bin/bash
set -e

FLAVOR="${1:-normal}"
CRT_PATH=$(pwd)/build/security
SIGNER_PATH=/home/ycd/Android/Sdk/build-tools/36.0.0/apksigner

if [ "$FLAVOR" = "xposed" ]; then
    APK=$(pwd)/app/build/outputs/apk/xposed/debug/app-xposed-debug.apk
    TARGET=/tmp/jist-xposed-signed.apk

    ./gradlew assembleXposedDebug

    echo "Xposed APK built. Install via:"
    echo "  adb install $APK"
    echo ""
    echo "Then enable the module in LSPosed/EdXposed."
    echo ""

    # Sign with platform key (required only if normal flavor is already system-installed)
    if [ -f "$CRT_PATH/platform.pk8" ] && [ -f "$CRT_PATH/platform.x509.pem" ]; then
        cp "$APK" "$TARGET"
        ${SIGNER_PATH} sign --key ${CRT_PATH}/platform.pk8 --cert ${CRT_PATH}/platform.x509.pem "$TARGET"
        adb root 2>/dev/null
        adb shell pm install -r -t "$TARGET" 2>/dev/null || adb install -r -t "$APK"
        rm "$TARGET"
    else
        adb install -r -t "$APK"
    fi

    adb shell am force-stop dev.rcht.jist
    adb shell am start -n dev.rcht.jist/.MainActivity
else
    APK=$(pwd)/app/build/outputs/apk/debug/app-debug.apk
    SIGNED=/tmp/jist-signed.apk

    ./gradlew assembleDebug && \
    adb wait-for-device && adb root && adb remount && \
    cp "$APK" "$SIGNED" && \
    ${SIGNER_PATH} sign --key ${CRT_PATH}/platform.pk8 --cert ${CRT_PATH}/platform.x509.pem "$SIGNED" && \
    adb push "$SIGNED" /system/priv-app/Jist/Jist.apk && \
    adb shell pm install -r -t /system/priv-app/Jist/Jist.apk && \
    adb shell am force-stop dev.rcht.jist && \
    adb shell am start -n dev.rcht.jist/.MainActivity && \
    rm "$SIGNED"
fi
