# DriveLink Android 1.4.1 — التثبيت بعد التحقق

من GitHub Actions، نزّل artifact باسم `drivelink-android-release-<commit>` من التشغيل الموافق للـcommit. لا تستخدم حزمة Debug كتحديث إنتاجي. تحقّق من `sha256sum -c SHA256SUMS.txt`، ثم طابق بصمة شهادة كل ملف مع بصمة إصدار DriveLink المعتمدة (`apksigner verify --verbose --print-certs FILE.apk`).

| APK | Package ID |
|---|---|
| `DriveLink-Standard.apk` | `app.drivelink.cast` |
| `DriveLink-FullTouch.apk` | `app.drivelink.cast.touch` |
| `DriveLink-Receiver.apk` | `app.drivelink.receiver` |

على جهاز Android 8.0/API 26 أو أحدث مصرح به، سجّل ما إذا كان تطبيق BYD مثبّتاً، ثم نفّذ `adb -s SERIAL install FILE.apk` لكل ملف. تحقق من وجود المعرفات المنفصلة عبر `adb -s SERIAL shell pm list packages`، وافتح التطبيقات من المشغّل. لا تُزل BYD أو تطبيقاً ذا توقيع غير مطابق. على هاتف ومستقبل مصرح بهما، اختبر بدء التشغيل والاتصال وقطع الشبكة وإعادة الاتصال، وأرفق سجل التشغيل المنقّح. لا تُحتسب هذه التعليمات كاختبار تثبيت ناجح.
