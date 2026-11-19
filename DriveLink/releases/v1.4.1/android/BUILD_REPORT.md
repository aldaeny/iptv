# Android 1.4.1 — حالة التنفيذ الحالية

**NO-GO — لم تُبنَ APKs.** هذا التقرير المحلي يستبدله تقرير CI آلياً داخل artifact عند اكتمال التحقق.

- استُخرجت الوحدة `:app` من `settings.gradle.kts` في كل من `DriveLink/android` و`DriveLink/receiver-dilink`. الهاتف `standard` و`fullTouch`؛ المستقبل بلا flavors. AGP 8.7.2، Gradle Wrapper 8.10.2، JDK 17، compileSdk 35، Build Tools 35.0.0، minSdk 26 وtargetSdk 34.
- فحص المصدر للبروتوكول والعزل نجح. اجتاز YAML فحص البنية، واجتازت أداة التحقق حالات اختبار بأدوات SDK وهمية: قبول المدخل المتوقع ورفض شهادة Debug وبصمة غير مطابقة. لا تعادل هذه الاختبارات التحقق من APK.
- محاولتا تشغيل Gradle محلياً توقفتا عند `java: not found`، ولا أدوات SDK أو جهاز Android ظاهر في البيئة (`PREFLIGHT.log`). لم يُشغّل سير العمل على GitHub؛ يوجد محلياً ولم يُدفع. لا روابط APK، لا SHA للـAPK، لا تثبيت، ولا اختبار BYD أو iOS.

## تشغيل مخوّل على GitHub

بعد إتاحة `.github/workflows/drivelink-android-release.yml` على GitHub وتمكين `workflow_dispatch` (GitHub يتطلب وجود تعريف سير العمل على الفرع الافتراضي)، اختر مراجعة تحتوي على تغييرات DriveLink. عيّن الأسرار عبر إعدادات المستودع: `DRIVELINK_KEYSTORE_BASE64` و`DRIVELINK_KEYSTORE_PASSWORD` و`DRIVELINK_KEY_ALIAS` و`DRIVELINK_KEY_PASSWORD`. عيّن متغير Actions `DRIVELINK_SIGNER_SHA256` إلى بصمة شهادة Release من مرجع موثوق مستقل. لا تشارك قيم الأسرار في الدردشة أو Git. ابدأ **DriveLink Android Release Build** من Actions. يرفع Debug وRelease بشكل منفصل، ولا يرفع Release إذا لم ينجح التوقيع والتحقق. لا ينشئ GitHub Release عاماً.
