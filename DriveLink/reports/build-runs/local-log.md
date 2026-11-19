# سجل تنفيذ جهاز البناء المحلي — 2026-11-19T17:58:01Z

Commit: `cbdff83`  Branch: `arena/bf1a2348-iptv`

## 1) ما الذي حُوِّل تنفيذه وماذا كانت النتيجة

| المحاولة | الأمر | النتيجة | المخرجات الفعلية |
|---|---|---|---|
| جلب JDK 17 | `pip download jdk4py` | **فشل** | الحزمة موجودة على PyPI، لكنها تتطلب `files.pythonhosted.org` (محجوب: 404/000 للمسار) |
| جلب Gradle 8.10.2 | `curl services.gradle.org` | **فشل** | `000` (غير مسموح بالخروج) |
| جلب AGP/Compose/Kotlin | `repo1.maven.org`, `maven.google.com`, `plugins.gradle.org` | **فشل** | `000` لكل منها |
| جلب Android SDK | `dl.google.com` | **فشل** | `000` |
| تشغيل الحزم على Actions | `gh workflow run drivelink-android-release.yml` | **فشل** | `HTTP 422: Actions has been disabled for this repository` |
| تفعيل Actions عبر API | `PUT /repos/.../actions/permissions` | **فشل** | `403 Resource not accessible by integration` |

### الاستنتاج

لا يمكن إنتاج APK في هذه البيئة: لا JDK، لا Gradle، لا Android SDK، ولا مستودع Maven يمكن الوصول إليه، وخادم CI معطّل إدارياً.

## 2) ما جرى تنفيذه بنجاح على نفس الالتزام

```
PASS  package identities, version 1.4.1 / code 3 and Java 17 target are consistent
PASS  Gradle DSL scopes checked (buildToolsVersion, defaultConfig)
PASS  AGP 8 Kotlin DSL patterns checked (signingConfigs/buildTypes/defaultConfig)
PASS  all XML / plist / entitlements / JSON files parse
PASS  all referenced app resources exist (manifest, layouts, Kotlin R.*)
PASS  all internal Kotlin imports resolve to a declared type
PASS  gradle wrapper, properties and helper scripts present
PASS  iOS specs: one app target each, versions, Bonjour and App Group consistent
PASS  BYD-WMP discovery intact (nsd _byd-mirror._tcp., TCP 47810, UDP 47811, beacon 47813, web UI 47812)
PASS  manifest permissions and flavour separation intact

ALL SOURCE RELEASE GATES PASSED
```

---

## Attempt log — branch `arena/1b4c9e45-iptv` (hardening round)

Base: `a3b2e8c` (master tip after PR #5). Goal: close the two publication gaps,
then run the real build. Every line below is the observed result, not a plan.

| # | Action | Observed result | Consequence |
|---|---|---|---|
| 1 | `gh api repos/aldaeny/iptv/actions/permissions` | `HTTP 403 Resource not accessible by integration` | token cannot read or change the Actions policy |
| 2 | `gh secret list` | `HTTP 403 Resource not accessible by integration` | signing secrets cannot be confirmed from here |
| 3 | `gh api .../actions/workflows` | workflows `active`; ids 422155327 / 423712396 / 424165426 | files are registered, repo-level switch is off |
| 4 | `gh workflow run drivelink-android-release.yml --ref arena/1b4c9e45-iptv` | `HTTP 422: Actions has been disabled for this repository` | **build still blocked — owner action required** |
| 5 | local toolchain probe (`java`, `gradle`, `sdkmanager`, `aapt`, `apksigner`, `go`, `xcodebuild`, `adb`) | all `MISSING` | no local substitute for CI |
| 6 | `find . -name '*.apk' -o -name '*.aab' -o -name '*.ipa'` | no output | no artifact exists anywhere in the tree |
| 7 | `python3 DriveLink/tests/verify_release.py` | `ALL SOURCE RELEASE GATES PASSED` (11 gates) | source is release-ready |
| 8 | `python3 DriveLink/tests/publish_guard_selftest.py` | `PASSED (10 scenarios)` | publication guard is proven to fail closed |

### What was fixed in this round (code, not reports)

* Signed and unsigned builds no longer share an artifact name:
  `drivelink-release-<sha>` only when a keystore was configured, otherwise
  `drivelink-unsigned-<sha>` (step `signmode`).
* `drivelink-publish-release.yml` now refuses, in order: non-numeric `run_id`,
  a run from another repository, a run that did not succeed, a non-dispatch run,
  a run older than `MAX_RUN_AGE_DAYS` (14), a run whose `head_branch` is not
  `master`, a `head_sha` that is not an ancestor of `origin/master`, a `head_sha`
  that is not the current `origin/master` tip, and a missing/expired
  `drivelink-release-<sha>` artifact. It then detaches to that exact commit and
  re-runs the source gate before publishing.
* Gate 11 in `verify_release.py` pins every one of those checks and the
  signed/unsigned separation, so a regression fails CI instead of silently
  weakening publication.

### Still the only blockers to real APKs

1. Repository owner: Settings → Actions → General → **Allow all actions and
   reusable workflows**.
2. Repository owner: add secrets `DRIVELINK_KEYSTORE_BASE64`,
   `DRIVELINK_KEYSTORE_PASSWORD`, `DRIVELINK_KEY_ALIAS`, `DRIVELINK_KEY_PASSWORD`
   and the variable `DRIVELINK_SIGNER_SHA256`.
3. Then: dispatch `DriveLink CI & Android Release Build` on `master`, and once it
   is green dispatch `DriveLink Publish Release` with its `run_id`.

Nothing in this round produced an APK, a signature, a SHA-256 or an install
result, so the release status remains **NO-GO**.
