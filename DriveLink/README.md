# DriveLink v1.4 — نسخة مستقلة مبنية على BYD Mirror

هذه نسخة جديدة داخل `aldaeny/iptv`، وليست استعادةً للمصدر الأصلي لـDriveLink / DriveLinkCast v1.4.0. نُسخت من ملفات `byd-mirror/` المتتبّعة عند `36effbd7143b744dee812a4377c4a04d86d3c5d6` مع نسخة واجهة المحاكي الموجودة في شجرة العمل (إصلاحات WebSocket وH.264 وتنظيف الجلسة). لم تُنسخ النسخ الاحتياطية أو مخرجات التسليم أو CI أو مفاتيح التوقيع.

| المجلد | المحتوى |
|---|---|
| `android/` | تطبيق الهاتف Kotlin/Compose بنسختي Standard وFullTouch |
| `receiver-dilink/` | تطبيق الاستقبال Android |
| `ios/` | تطبيق Swift وReplayKit ومواصفات XcodeGen لـFull وFree |
| `simulator/` | محاكي Go مع واجهة ويب؛ ليس خادم DriveLink التاريخي |
| `shared/`, `tests/` | تعريف البروتوكول والاختبارات المستقلة |
| `docs/` | وثائق المصدر المرجعية؛ تقاريرها مؤرّخة على BYD وليست أدلة إصدار لهذه النسخة |

النسخة المرشحة `1.4.1` تستخدم معرفات Android مستقلة: `app.drivelink.cast` و`app.drivelink.cast.touch` و`app.drivelink.receiver`. تستخدم iOS معرفات `app.drivelink.cast.app` و`app.drivelink.cast.app.free` بالإضافة إلى معرفات مستقلة للملحقات وApp Group. عزل المعرفات مثبت في المصدر فقط، وليس باختبار تثبيت. البروتوكول والتشفير لم يتغيرا. هذه ليست استعادة لخادم Node أو واجهات `x-device` أو `/api/reset` أو أدوات QR التاريخية.

[نتائج الإصدار المرشح وتعليمات البناء](releases/v1.4.1/BUILD_REPORT.md).

## الاختبار والبناء

من جذر المستودع:

```sh
python3 DriveLink/tests/verify_isolation.py
python3 DriveLink/tests/verify_protocol.py
node DriveLink/tests/crypto_fuzz_suite.mjs
```

نجح الأمران على هذه النسخة. لم تُنتج APK أو IPA أو يُنفذ بناء Android/iOS لهذه النسخة. للبناء الأصلي راجع [دليل البناء الموروث](docs/BUILD_GUIDE.md) مستخدماً المسارات تحت `DriveLink/` بدلاً من `byd-mirror/`. لتشغيل محاكي Go محلياً: `cd DriveLink/simulator && go run .`، ثم افتح `http://127.0.0.1:47812` على نفس الجهاز. أوقف أي محاكي BYD قائم قبل تشغيل النسخة لأن المنافذ لم تتغير.
