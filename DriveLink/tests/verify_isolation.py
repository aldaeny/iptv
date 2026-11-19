#!/usr/bin/env python3
"""Source-level package, extension and release signing contract checks."""
import plistlib
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
for project, identifier in [("android", "app.drivelink.cast"), ("receiver-dilink", "app.drivelink.receiver")]:
    app = ROOT / project / "app"
    config = (app / "build.gradle.kts").read_text()
    for prop in ("namespace", "applicationId"):
        assert f'{prop} = "{identifier}"' in config, (project, prop)
    assert 'versionName = "1.4.1"' in config
    assert 'isDebuggable = false' in config
    assert re.search(r'^\s*signingConfig = signingConfigs\.findByName\("release"\)\s*$', config, re.M)
    if project == "android":
        assert 'applicationIdSuffix = ".touch"' in config
    for path in list(app.rglob('*.kt')) + list(app.rglob('AndroidManifest.xml')):
        assert not re.search(r'com\.byd\.(mirror|receiver)', path.read_text()), path
    assert list((app / 'src' / 'main' / 'java' / Path(*identifier.split('.'))).rglob('*.kt'))

ios = ROOT / 'ios'
expected = ['app.drivelink.cast.app', 'app.drivelink.cast.app.broadcast', 'app.drivelink.cast.app.liveactivity']
assert re.findall(r'PRODUCT_BUNDLE_IDENTIFIER: (\S+)', (ios / 'Project.yml').read_text()) == expected
assert re.findall(r'PRODUCT_BUNDLE_IDENTIFIER: (\S+)', (ios / 'Project-free.yml').read_text()) == ['app.drivelink.cast.app.free']
assert expected[1] in (ios / 'Sources/UI/Views/MirrorDashboardView.swift').read_text()
group = 'group.app.drivelink.cast.shared'
assert group in (ios / 'Sources/Core/Session/SharedSessionStore.swift').read_text()
assert plistlib.loads((ios / 'BydMirror.entitlements').read_bytes())['com.apple.security.application-groups'] == [group]
print('PASS source package isolation, extensions, shared group and release signing configuration')
