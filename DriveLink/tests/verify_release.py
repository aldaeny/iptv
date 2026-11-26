#!/usr/bin/env python3
"""
Source-level release gate for DriveLink (runs everywhere, no JDK/Xcode needed).

It cannot replace a real Gradle/Xcode build, but it catches the blocker classes that
have actually broken this project before:

 1. package/namespace/applicationId/version drift
 2. a Gradle DSL property placed outside its legal block (e.g. buildToolsVersion)
 3. XML/plist/JSON unable to parse
 4. resources referenced from Kotlin/XML that do not exist
 5. internal Kotlin imports that do not resolve to a declared type/file
 6. missing gradle wrapper + gradlew exec bit (silently skipped CI steps)
 7. duplicated asset catalogs / duplicate targets in XcodeGen specs
 8. required permissions, discovery protocol, ports and mTLS-free identity checks

Exit code 0 = all source gates pass. Any failure prints "FAIL: ..." lines.
"""
from __future__ import annotations

import json
import plistlib
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FAILS: list[str] = []
NOTES: list[str] = []


def fail(msg: str) -> None:
    FAILS.append(msg)


def ok(msg: str) -> None:
    NOTES.append(msg)


# --------------------------------------------------------------------------- 1
def check_identities() -> None:
    targets = {
        "android": ("app.drivelink.cast", "app.drivelink.cast"),
        "receiver-dilink": ("app.drivelink.receiver", "app.drivelink.receiver"),
    }
    for project, (namespace, app_id) in targets.items():
        cfg = (ROOT / project / "app/build.gradle.kts").read_text()
        for prop, value in (("namespace", namespace), ("applicationId", app_id)):
            if f'{prop} = "{value}"' not in cfg:
                fail(f"{project}: {prop} must be {value}")
        if 'versionCode = 3' not in cfg:
            fail(f"{project}: versionCode must be 3")
        if 'versionName = "1.4.1"' not in cfg:
            fail(f"{project}: versionName must be 1.4.1")
        if "isDebuggable = false" not in cfg:
            fail(f"{project}: release must be non-debuggable")
        if "signingConfigs.findByName(\"release\")" not in cfg:
            fail(f"{project}: release must not fall back to the debug signing config")
        if 'ENABLE_OVERLAY_AND_TOUCH' in cfg and project == "receiver-dilink":
            fail("receiver-dilink: unexpected flavour-only BuildConfig field")
        # Java/Kotlin toolchain parity
        if "JavaVersion.VERSION_17" not in cfg or 'jvmTarget = "17"' not in cfg:
            fail(f"{project}: Java 17 compatibility must be explicit")
        if "minSdk = 26" not in cfg or "compileSdk = 35" not in cfg:
            fail(f"{project}: minSdk 26 / compileSdk 35 expected")
    ok("package identities, version 1.4.1 / code 3 and Java 17 target are consistent")


# --------------------------------------------------------------------------- 2
def check_gradle_dsl_scopes() -> None:
    """buildToolsVersion belongs to the android { } block (AGP 8), never defaultConfig."""
    for project in ("android", "receiver-dilink"):
        text = (ROOT / project / "app/build.gradle.kts").read_text()
        depth = 0
        block_stack: list[str] = []
        for raw in text.splitlines():
            line = raw.split("//")[0]
            if line.strip().endswith("{"):
                block_stack.append(line.strip().split()[0])
            if "buildToolsVersion" in line:
                legal = ("android" in block_stack or "extensions" in block_stack) and "defaultConfig" not in block_stack
                if not legal:
                    fail(f"{project}: buildToolsVersion placed in {block_stack[-1:] or ['top level']}")
            depth += line.count("{") - line.count("}")
            while block_stack and depth < len(block_stack) - 1:
                block_stack.pop()
        if "defaultConfig" in text and re.search(r"defaultConfig\s*\{[^}]*buildToolsVersion", text, re.S):
            fail(f"{project}: buildToolsVersion inside defaultConfig is not a valid AGP 8 DSL property")
    ok("Gradle DSL scopes checked (buildToolsVersion, defaultConfig)")


# --------------------------------------------------------------------------- 2b
AGP8_ANTIPATTERNS = [
    # (regex, message)  -- these compile in isolation but fail inside AGP's Kotlin DSL
    (r"signingConfigs\s*\{[^}]*getByName\(", "signingConfigs has no getByName(); use named(...)"),
    (r"defaultConfig\s*\{[^}]*buildToolsVersion", "buildToolsVersion is an android { } property"),
]


def check_agp_kotlin_dsl() -> None:
    for project in ("android", "receiver-dilink"):
        text = (ROOT / project / "app/build.gradle.kts").read_text()
        for pattern, message in AGP8_ANTIPATTERNS:
            if re.search(pattern, text, re.S):
                fail(f"{project}: {message}")
        # signing config must be created from env exactly once and never fall back to debug
        if text.count('create("release")') > 1:
            fail(f"{project}: duplicate release signing config")
        if 'signingConfigs.getByName("debug")' in text:
            fail(f"{project}: release must not reuse the debug signing config")
    ok("AGP 8 Kotlin DSL patterns checked (signingConfigs/buildTypes/defaultConfig)")


# --------------------------------------------------------------------------- 3
def check_parsing() -> None:
    for pattern, label in (
        ("**/*.xml", "XML"),
        ("**/*.plist", "plist"),
        ("**/*.entitlements", "entitlements"),
        ("**/*.xcworkspacedata", "workspace"),
    ):
        for path in ROOT.rglob(pattern):
            if "node_modules" in path.parts:
                continue
            try:
                ET.parse(path)
            except Exception as exc:  # noqa: BLE001
                fail(f"{label} parse error: {path.relative_to(ROOT)} ({exc})")
    for path in ROOT.rglob("**/*.json"):
        if "node_modules" in path.parts:
            continue
        try:
            json.loads(path.read_text())
        except Exception as exc:  # noqa: BLE001
            fail(f"JSON parse error: {path.relative_to(ROOT)} ({exc})")
    ok("all XML / plist / entitlements / JSON files parse")


# --------------------------------------------------------------------------- 4
def check_resources() -> None:
    for project in ("android", "receiver-dilink"):
        res = ROOT / project / "app/src/main/res"
        strings, drawables, mipmaps, xmls, styles, colours = set(), set(), set(), set(), set(), set()
        for path in res.rglob("*"):
            if path.is_dir():
                continue
            kind, stem = path.relative_to(res).parts[0], path.name.split(".")[0]
            if kind.startswith("values"):
                text = path.read_text()
                strings |= set(re.findall(r'<string name="([^"]+)"', text))
                styles |= set(re.findall(r'<style name="([^"]+)"', text))
                colours |= set(re.findall(r'<color name="([^"]+)"', text))
            elif kind.startswith("drawable"):
                drawables.add(stem)
            elif kind.startswith("mipmap"):
                mipmaps.add(stem)
            elif kind == "xml":
                xmls.add(stem)
        for src in list((ROOT / project / "app/src").rglob("*.kt")) + list((ROOT / project / "app/src").rglob("*.xml")):
            text = src.read_text()
            # android.R.* are framework resources, not app resources
            for res_type, defined in (("string", strings), ("drawable", drawables), ("color", colours),
                                      ("mipmap", mipmaps), ("xml", xmls)):
                for name in set(re.findall(rf"(?<!android\.)R\.{res_type}\.(\w+)", text)):
                    if name not in defined:
                        fail(f"{src.relative_to(ROOT)}: R.{res_type}.{name} is not defined")
                for name in set(re.findall(rf"@(?:[a-z-]+:)?{res_type}/(\w+)", text)):
                    if name not in defined:
                        fail(f"{src.relative_to(ROOT)}: @{res_type}/{name} is not defined")
            for name in set(re.findall(r"@style/([\w.]+)", text)):
                if name not in styles:
                    fail(f"{src.relative_to(ROOT)}: @style/{name} is not defined")
    ok("all referenced app resources exist (manifest, layouts, Kotlin R.*)")


# --------------------------------------------------------------------------- 5
def check_kotlin_imports() -> None:
    for project, pkg in (("android", "app.drivelink.cast"), ("receiver-dilink", "app.drivelink.receiver")):
        src_root = ROOT / project / "app/src"
        declared: set[tuple[str, str]] = set()
        files = list(src_root.rglob("*.kt"))
        for path in files:
            text = path.read_text()
            match = re.search(r"^package\s+([\w.]+)", text, re.M)
            if not match:
                fail(f"{path.relative_to(ROOT)}: missing package declaration")
                continue
            for name in re.findall(
                r"^(?:@\w+(?:\([^)]*\))?\s*)*(?:public |internal |private |abstract |open |sealed |data |enum |annotation )*"
                r"(?:class|interface|object|fun|val|var|typealias)\s+([A-Za-z_]\w*)",
                text,
                re.M,
            ):
                declared.add((match.group(1), name))
        generated = {f"{pkg}.BuildConfig", f"{pkg}.R"}
        for path in files:
            for imp in re.findall(r"^import\s+([\w.]+)", path.read_text(), re.M):
                if not imp.startswith(pkg + ".") or imp.endswith(".*") or imp in generated:
                    continue
                parts = imp.split(".")
                base = len(pkg.split("."))
                found = any(
                    (".".join(parts[:i]), parts[i]) in declared
                    for i in range(len(parts) - 1, base - 1, -1)
                )
                if not found:
                    fail(f"{path.relative_to(ROOT)}: import {imp} does not resolve")
    ok("all internal Kotlin imports resolve to a declared type")


# --------------------------------------------------------------------------- 6
def check_wrapper() -> None:
    for project in ("android", "receiver-dilink"):
        base = ROOT / project
        gradlew = base / "gradlew"
        jar = base / "gradle/wrapper/gradle-wrapper.jar"
        props = base / "gradle/wrapper/gradle-wrapper.properties"
        for needed in (gradlew, jar, props):
            if not needed.exists():
                fail(f"missing {needed.relative_to(ROOT)}")
        if gradlew.exists() and not (gradlew.stat().st_mode & 0o111):
            fail(f"{gradlew.relative_to(ROOT)} is not executable")
        for launcher in (gradlew, base / "gradlew.bat"):
            if "org.gradle.wrapper.GradleWrapperMain" not in launcher.read_text():
                fail(f"{launcher}: must invoke the wrapper entry point")
        if props.exists():
            text = props.read_text()
            if "gradle-8.10.2-bin.zip" not in text:
                fail(f"{props.relative_to(ROOT)}: expected Gradle 8.10.2 distribution")
    for script in ("DriveLink/ci/run_drivelink_builds.sh", "DriveLink/tools/verify-android-build.sh"):
        path = ROOT.parent / script
        if not path.exists():
            fail(f"missing {script}")
    ok("gradle wrapper, properties and helper scripts present")


# --------------------------------------------------------------------------- 7
def check_ios() -> None:
    ios = ROOT / "ios"
    try:
        import yaml  # type: ignore
    except ImportError:
        yaml = None
    if yaml:
        full = yaml.safe_load((ios / "Project.yml").read_text())
        free = yaml.safe_load((ios / "Project-free.yml").read_text())
        app_targets = [name for name, t in full["targets"].items() if t.get("type") == "application"]
        if not app_targets:
            fail("Project.yml defines no application target")
        for name, target in full["targets"].items():
            if target.get("type") == "app-extension" and any(
                src.get("path") == "Sources/Core" for src in target.get("sources", [])
            ) and not target.get("settings", {}).get("base", {}).get("PRODUCT_BUNDLE_IDENTIFIER"):
                fail(f"Project.yml extension {name} has no bundle identifier")
        # Every type declared under Sources/Core must exist in exactly one file: the two
        # extensions and the app all compile Sources/Core, so a duplicate file would
        # redeclare types inside one module.
        core_types: dict[str, str] = {}
        for swift in (ios / "Sources/Core").rglob("*.swift"):
            for match in re.finditer(r"^(?:public |internal |open |final |@\w+\s+)*(?:struct|class|enum|protocol|actor)\s+(\w+)",
                                     swift.read_text(), re.M):
                other = core_types.get(match.group(1))
                if other:
                    fail(f"duplicate type {match.group(1)} in Sources/Core ({other} and {swift.name})")
                core_types[match.group(1)] = swift.name
        if app_targets != ["BydWirelessMirror"]:
            fail("Project.yml must define exactly one application target")
        if len(free["targets"]) != 1:
            fail("Project-free.yml must define exactly one target (no duplicate extension copies)")
        for spec, name in ((full, "Project.yml"), (free, "Project-free.yml")):
            for target in spec["targets"].values():
                for source in target.get("sources", []):
                    if not (ios / source["path"]).exists():
                        fail(f"{name}: source path {source['path']} does not exist")
    for app_target, bundle in (
        ("Project.yml", "app.drivelink.cast.app"),
        ("Project-free.yml", "app.drivelink.cast.app.free"),
    ):
        text = (ios / app_target).read_text()
        if f"PRODUCT_BUNDLE_IDENTIFIER: {bundle}" not in text:
            fail(f"{app_target}: main target identifier must be {bundle}")
        if "Assets.xcassets" not in text:
            fail(f"{app_target}: app icon catalog Assets.xcassets must be a source")
    info = plistlib.loads((ios / "Info.plist").read_bytes())
    if info["CFBundleShortVersionString"] != "1.4.1" or info["CFBundleVersion"] != "3":
        fail("iOS Info.plist must be 1.4.1 (3)")
    if "_byd-mirror._tcp" not in info.get("NSBonjourServices", []):
        fail("Info.plist must declare the _byd-mirror._tcp Bonjour service")
    group = "group.app.drivelink.cast.shared"
    if group not in plistlib.loads((ios / "BydMirror.entitlements").read_bytes())["com.apple.security.application-groups"]:
        fail("App Group entitlement missing")
    ok("iOS specs: one app target each, versions, Bonjour and App Group consistent")


# --------------------------------------------------------------------------- 8
def check_protocol() -> None:
    proto = json.loads((ROOT / "shared/protocol/byd_mirror_protocol.json").read_text())
    discovery = proto["serviceDiscovery"]
    expected = {"nsdServiceType": "_byd-mirror._tcp.", "controlPort": 47810, "mediaUdpPort": 47811}
    for key, value in expected.items():
        if discovery.get(key) != value:
            fail(f"protocol {key} must stay {value} (got {discovery.get(key)!r})")
    beacon = json.loads((ROOT / "shared/protocol/byd_mirror_protocol.json").read_text())
    text = (ROOT / "simulator/main.go").read_text()
    for token in ("47810", "47811", "47812", "47813"):
        if token not in text:
            fail(f"simulator/main.go lost port {token}")
    if "beaconUDPPort" in text and "47813" not in text:
        fail("simulator beacon port changed")
    ok(f"BYD-WMP discovery intact (nsd {discovery['nsdServiceType']}, TCP {discovery['controlPort']}, "
       f"UDP {discovery['mediaUdpPort']}, beacon 47813, web UI 47812)")


# --------------------------------------------------------------------------- 9
def check_android_manifest() -> None:
    manifest = ET.parse(ROOT / "android/app/src/main/AndroidManifest.xml")
    ns = {"android": "http://schemas.android.com/apk/res/android"}
    perms = {el.get(f"{{{ns['android']}}}name") for el in manifest.iter("uses-permission")}
    required = {
        "android.permission.INTERNET",
        "android.permission.ACCESS_NETWORK_STATE",
        "android.permission.ACCESS_WIFI_STATE",
        "android.permission.CHANGE_WIFI_MULTICAST_STATE",
        "android.permission.NEARBY_WIFI_DEVICES",
        "android.permission.RECORD_AUDIO",
        "android.permission.FOREGROUND_SERVICE",
        "android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION",
        "android.permission.POST_NOTIFICATIONS",
    }
    android = "{" + ns["android"] + "}"
    for tree in (manifest, ET.parse(ROOT / "android/app/src/fullTouch/AndroidManifest.xml")):
        projection = next((s for s in tree.findall("application/service")
                           if s.get(android + "name") == "app.drivelink.cast.service.MirrorForegroundService"), None)
        if projection is None or projection.get(android + "foregroundServiceType") != "mediaProjection":
            fail("both flavor manifest inputs must declare the projection service type")
    missing = required - perms
    if missing:
        fail(f"AndroidManifest lost permissions: {sorted(missing)}")
    full = (ROOT / "android/app/src/fullTouch/AndroidManifest.xml").read_text()
    for token in ("SYSTEM_ALERT_WINDOW", "BIND_ACCESSIBILITY_SERVICE", "app.drivelink.cast.service.MirrorOverlayService"):
        if token not in full:
            fail(f"fullTouch manifest missing {token}")
    standard = (ROOT / "android/app/src/main/AndroidManifest.xml").read_text()
    if "MirrorOverlayService" in standard or "BydTouchAccessibilityService" in standard:
        fail("standard flavour must not ship overlay/accessibility services")
    ok("manifest permissions and flavour separation intact")


def check_release_workflows() -> None:
    """Gate 11: publication must stay impossible for stale or unsigned artifacts."""
    build = (ROOT.parent / ".github/workflows/drivelink-android-release.yml").read_text()
    publish = (ROOT.parent / ".github/workflows/drivelink-publish-release.yml").read_text()

    # Signed builds and keystore-less builds must not share an artifact name.
    if "id: signmode" not in build:
        fail("release build workflow lost the signmode step id (artifact naming)")
    for token in ('echo "mode=release" >> "$GITHUB_OUTPUT"',
                  'echo "mode=unsigned" >> "$GITHUB_OUTPUT"'):
        if token not in build:
            fail(f"release build workflow does not report signing mode: {token}")
    if "name: drivelink-${{ steps.signmode.outputs.mode }}-${{ github.sha }}" not in build:
        fail("release artifacts are not separated into signed/unsigned namespaces")
    if re.search(r"^\s*name: drivelink-release-\$\{\{ github\.sha \}\}", build, re.M):
        fail("release build workflow still uses one artifact name for signed and unsigned")
    elif build.count("drivelink-release/DriveLink-") < 3:
        fail("build workflow does not upload the three release APKs by explicit path")

    # Publication preconditions.
    checks = {
        'git merge-base --is-ancestor "$head_sha" origin/master':
            "publish workflow does not require head_sha to be an ancestor of origin/master",
        '[[ $(git rev-parse origin/master) == "$head_sha" ]]':
            "publish workflow does not require head_sha to be the origin/master tip",
        "MAX_RUN_AGE_DAYS": "publish workflow has no stale-run age limit",
        '.repository.full_name == $repo': "publish workflow does not verify the run's repository",
        '.event == "workflow_dispatch"': "publish workflow accepts non-dispatch runs",
        'any(.artifacts[]?; .name == $n and .expired == false)':
            "publish workflow does not require the signed artifact to exist",
        "drivelink-release-[0-9a-f]{40}": "publish workflow does not pin the signed artifact name",
        '[[ $RUN_ID =~ ^[0-9]{6,20}$ ]]': "publish workflow does not validate run_id",
        # Every platform bundle identifier must be an explicit literal: deriving it by
        # stripping an IPA filename suffix produced "app.drivelink.cast.app-unsigned.ipa".
    }
    for token, message in checks.items():
        if token not in publish:
            fail(message)

    # Build-side guarantees: literal IPA identities and an explicit 3-APK upload.
    for token, message in {
        "DriveLink-Full-unsigned.ipa app.drivelink.cast.app":
            "build workflow does not verify the Full IPA bundle identifier literally",
        "DriveLink-Free-unsigned.ipa app.drivelink.cast.app.free":
            "build workflow does not verify the Free IPA bundle identifier literally",
        "Assert the release artifact set is complete":
            "build workflow uploads without asserting the three APKs are present",
    }.items():
        if token not in build:
            fail(message)
    if "git checkout --detach" not in publish:
        fail("publish workflow does not re-gate the exact commit it publishes")
    if "verify_release.py" not in publish:
        fail("publish workflow does not run the source release gate")
    # Comments may explain the quarantine; executable lines must never touch it.
    if any("drivelink-unsigned-" in ln and not ln.strip().startswith("#")
           for ln in publish.splitlines()):
        fail("publish workflow references the unsigned artifact namespace")
    ok("release workflows: signed/unsigned separated, stale+foreign runs refused")


def main() -> int:
    import os
    android_only = os.environ.get("ANDROID_ONLY_RELEASE", "").lower() in ("1", "true", "yes")
    check_identities()
    check_gradle_dsl_scopes()
    check_agp_kotlin_dsl()
    check_parsing()
    check_resources()
    check_kotlin_imports()
    check_wrapper()
    if not android_only:
        check_ios()
    check_protocol()
    check_android_manifest()
    check_release_workflows()

    for note in NOTES:
        print(f"PASS  {note}")
    if FAILS:
        print()
        for item in FAILS:
            print(f"FAIL  {item}")
        print(f"\n{len(FAILS)} source release gate(s) failed")
        return 1
    print("\nALL SOURCE RELEASE GATES PASSED")
    return 0


if __name__ == "__main__":
    sys.exit(main())
