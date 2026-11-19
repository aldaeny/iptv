# DriveLink 1.4.1 — مرشح غير منشور

- عزل هويات تطبيقات Android وiOS عن BYD Mirror، مع تحديث مراجع Gradle وKotlin وManifest وReplayKit وApp Group وKeychain.
- استعادة Gradle wrapper 8.10.2 من مستودع Gradle الرسمي وتطابق Git blob `a4b76b9530d66f5e68d973ea569d8e19de379189`.
- توقيع Release يقرأ إعدادات DriveLink فقط؛ لا يرجع إلى مفتاح debug. بدون إعداد توقيع، لا تعتبر الناتج Release موقعاً أو قابلاً للتوزيع.
- لا تغيير في التشفير أو بروتوكول الشبكة أو مجلد BYD. هذا ليس DriveLink الأصلي المفقود.
- **توحيد الشجرة (متابعة):** دُمج فرع إعادة تصميم الواجهة (UI/UX) في `DriveLink/` وأُزيل مجلد `byd-mirror/` المكرر نهائياً؛ لم يبق سوى شجرة واحدة وهوية واحدة (`1.4.1`) وسير عمل CI واحد (`.github/workflows/drivelink-android-release.yml`).
- **سجل البناء والتحقق النهائي (2026-11-19):** [FINAL_VALIDATION_REPORT.md](FINAL_VALIDATION_REPORT.md) — لا APK/IPA في هذه الدورة، والعوائق والقرار مفصّلة فيه.
- **NO-GO:** لا APK أو IPA مبني في هذه الدورة، ولا إصدار منشور.
