# البناء والتثبيت — لا حزم مرفقة

على مضيف Android مصرح به: JDK 17 وAndroid SDK 35 وBuild Tools، مع الوصول إلى Gradle 8.10.2 وGoogle Maven. من `DriveLink/android`:

```sh
./gradlew testStandardDebugUnitTest testFullTouchDebugUnitTest lintStandardDebug lintFullTouchDebug \
  assembleStandardDebug assembleFullTouchDebug assembleStandardRelease assembleFullTouchRelease
```

من `DriveLink/receiver-dilink`:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
```

تُجهَّز إعدادات Release عبر `DRIVELINK_KEYSTORE_PATH` و`DRIVELINK_KEYSTORE_PASSWORD` و`DRIVELINK_KEY_ALIAS` و`DRIVELINK_KEY_PASSWORD` من مخزن أسرار مصرح به؛ لا تضعها في المستودع. استخدم `apksigner verify --verbose --print-certs` و`aapt dump badging` وتحقق من هوية الشهادة الموثوقة وإصدار `1.4.1` ومعرّفات الحزم قبل `adb install`. وثّق تثبيت DriveLink إلى جانب تطبيق BYD، دون حذفه أو استبداله.

على Mac: `swift test` ثم `xcodegen generate --spec Project.yml` وبناء Scheme `BydWirelessMirror`، وكرر مع `Project-free.yml`. يلزم تسجيل المعرفات الجديدة وApp Group وتوفير profiles ملائمة قبل تصدير IPA موقّع.
