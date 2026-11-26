#!/usr/bin/env python3
"""Archive/export both iOS products on a disposable macOS signing worker.

Never log key material, passwords, profiles or expanded shell commands. Import
only the explicitly supplied distribution identity. Do not contact Apple's
portal, register devices or change repository/team settings.
"""
import base64
from datetime import datetime, timezone
import hashlib
import os
from pathlib import Path
import plistlib
import re
import secrets
import shlex
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1] / 'ios'
BASE = 'app.drivelink.cast.app'
GROUP = 'group.app.drivelink.cast.shared'
TARGETS = {'FULL': BASE, 'BROADCAST': BASE + '.broadcast', 'WIDGET': BASE + '.liveactivity', 'FREE': BASE + '.free'}


def checked(cmd, *, cwd=None, capture=False):
    # CalledProcessError would include argv (security import takes a password).
    result = subprocess.run(cmd, cwd=cwd, stdout=subprocess.PIPE if capture else subprocess.DEVNULL,
                            stderr=subprocess.DEVNULL)
    if result.returncode:
        raise RuntimeError(f'{Path(cmd[0]).name} {cmd[1]} failed (exit {result.returncode})')
    return result.stdout


def validate_profile(profile, bundle, team, pin, method):
    ent = profile['Entitlements']
    if team not in profile['TeamIdentifier'] or profile['ExpirationDate'].replace(tzinfo=timezone.utc) <= datetime.now(timezone.utc):
        raise ValueError('wrong team or expired profile: ' + bundle)
    allowed_ids = {prefix + '.' + bundle for prefix in profile['ApplicationIdentifierPrefix']}
    if ent.get('application-identifier') not in allowed_ids or ent.get('get-task-allow', False):
        raise ValueError('profile does not authorize exact distribution app ID: ' + bundle)
    if bundle != TARGETS['FREE'] and GROUP not in ent.get('com.apple.security.application-groups', []):
        raise ValueError('missing App Group in profile: ' + bundle)
    if pin not in {hashlib.sha256(c).hexdigest() for c in profile['DeveloperCertificates']}:
        raise ValueError('profile does not authorize approved certificate: ' + bundle)
    if profile.get('ProvisionsAllDevices') or bool(profile.get('ProvisionedDevices')) != (method == 'release-testing'):
        raise ValueError('wrong distribution method for ' + bundle)


def verify_ipa(ipa, work, expected, team, pin, profiles, method):
    dest = work / (ipa.stem + '-verified')
    checked(['ditto', '-x', '-k', ipa, dest])
    app, = (dest / 'Payload').glob('*.app')
    checked(['codesign', '--verify', '--strict', '--deep', app])
    seen = set()
    for bundle in [app, *app.rglob('*.appex')]:
        info = plistlib.loads((bundle / 'Info.plist').read_bytes())
        bid = info['CFBundleIdentifier']
        if bid in seen or bid not in expected or (info['CFBundleShortVersionString'], info['CFBundleVersion']) != ('1.4.1', '3'):
            raise ValueError('unexpected product/version in IPA')
        seen.add(bid)
        profile = plistlib.loads(checked(['security', 'cms', '-D', '-i', bundle/'embedded.mobileprovision'], capture=True))
        validate_profile(profile, bid, team, pin, method)
        if profile['UUID'] != profiles[bid]:
            raise ValueError('unexpected embedded profile: ' + bid)
        ent = plistlib.loads(checked(['codesign', '-d', '--entitlements', ':-', bundle], capture=True))
        if (ent.get('application-identifier') != profile['Entitlements']['application-identifier'] or
                ent.get('com.apple.developer.team-identifier') != team or ent.get('get-task-allow', False)):
            raise ValueError('invalid signed distribution entitlements: ' + bid)
        groups = set(ent.get('com.apple.security.application-groups', []))
        if groups != ({GROUP} if bid != TARGETS['FREE'] else set()):
            raise ValueError('wrong signed App Groups: ' + bid)
        cert_dir = work / ('cert-' + bid)
        cert_dir.mkdir()
        checked(['codesign', '-d', '--extract-certificates', bundle], cwd=cert_dir)
        if hashlib.sha256((cert_dir/'codesign0').read_bytes()).hexdigest() != pin:
            raise ValueError('untrusted signing certificate: ' + bid)
    if seen != expected:
        raise ValueError('incomplete IPA bundle set')


def main():
    required = ['DRIVELINK_APPLE_TEAM_ID', 'DRIVELINK_IOS_SIGNER_SHA256', 'DRIVELINK_IOS_EXPORT_METHOD',
                'DRIVELINK_IOS_P12_BASE64', 'DRIVELINK_IOS_P12_PASSWORD',
                *('DRIVELINK_IOS_PROFILE_' + key for key in TARGETS)]
    missing = [key for key in required if not os.environ.get(key)]
    if missing:
        sys.exit('::error::BLOCKED Apple signing inputs unavailable: ' + ', '.join(missing))
    if sys.platform != 'darwin' or os.environ.get('RUNNER_ENVIRONMENT') != 'github-hosted':
        sys.exit('Refusing to modify a persistent signing keychain; use an ephemeral hosted Mac')
    team = os.environ['DRIVELINK_APPLE_TEAM_ID']
    pin = os.environ['DRIVELINK_IOS_SIGNER_SHA256'].replace(':','').lower()
    method = os.environ['DRIVELINK_IOS_EXPORT_METHOD']
    if not re.fullmatch('[A-Z0-9]{10}', team) or not re.fullmatch('[a-f0-9]{64}', pin) or method not in ('app-store-connect','release-testing'):
        sys.exit('Invalid team, trusted fingerprint or export method')
    os.umask(0o077)
    out = Path(os.environ['RUNNER_TEMP'])/'drivelink-ios-signed'
    out.mkdir()
    with tempfile.TemporaryDirectory(dir=os.environ['RUNNER_TEMP']) as tmp:
        work = Path(tmp)
        kc = str(work/'sign.keychain-db'); password = secrets.token_hex(32)
        prior = shlex.split(checked(['security','list-keychains','-d','user'],capture=True).decode())
        profiles = {}; installed = []
        checked(['security','create-keychain','-p',password,kc])
        try:
            checked(['security','unlock-keychain','-p',password,kc])
            p12 = work/'identity.p12'; p12.write_bytes(base64.b64decode(os.environ.pop('DRIVELINK_IOS_P12_BASE64'), validate=True))
            checked(['security','import',p12,'-k',kc,'-P',os.environ.pop('DRIVELINK_IOS_P12_PASSWORD'),'-T','/usr/bin/codesign'])
            checked(['security','set-key-partition-list','-S','apple-tool:,apple:,codesign:','-s','-k',password,kc])
            checked(['security','list-keychains','-d','user','-s',kc,*prior])
            ids = checked(['security','find-identity','-v','-p','codesigning',kc],capture=True).decode()
            identities = re.findall(r'\b([A-F0-9]{40}) "(?:Apple Distribution|iPhone Distribution):', ids)
            if len(identities) != 1:
                raise ValueError('expected exactly one valid Apple distribution identity')
            directory = Path.home()/'Library/Developer/Xcode/UserData/Provisioning Profiles'
            directory.mkdir(parents=True, exist_ok=True)
            settings = []
            for key, bid in TARGETS.items():
                path = work/(key+'.mobileprovision'); path.write_bytes(base64.b64decode(os.environ.pop('DRIVELINK_IOS_PROFILE_'+key), validate=True))
                profile = plistlib.loads(checked(['security','cms','-D','-i',path],capture=True))
                validate_profile(profile,bid,team,pin,method)
                uuid = profile['UUID']
                if not re.fullmatch('[A-Fa-f0-9-]{36}',uuid): raise ValueError('invalid profile UUID')
                target = directory/(uuid+'.mobileprovision')
                with target.open('xb') as f:
                    installed.append(target); f.write(path.read_bytes())
                profiles[bid] = uuid; settings.append('DRIVELINK_PROFILE_'+key+'='+uuid)
            for label, project, spec, expected in [('Full','BydWirelessMirror','Project.yml',set(TARGETS.values())-{TARGETS['FREE']}),
                                                   ('Free','BydWirelessMirrorFree','Project-free.yml',{TARGETS['FREE']})]:
                checked(['xcodegen','generate','--spec',spec],cwd=ROOT)
                archive = work/(label+'.xcarchive'); export = work/(label+'-export')
                checked(['xcodebuild','archive','-project',project+'.xcodeproj','-scheme','BydWirelessMirror','-configuration','Release',
                         '-destination','generic/platform=iOS','-archivePath',archive,'CODE_SIGN_STYLE=Manual','DEVELOPMENT_TEAM='+team,
                         'CODE_SIGN_IDENTITY='+identities[0],*settings],cwd=ROOT)
                options = work/'export.plist'; options.write_bytes(plistlib.dumps({'method':method,'teamID':team,'signingStyle':'manual',
                    'signingCertificate':identities[0],'provisioningProfiles':{bid:profiles[bid] for bid in expected},'manageAppVersionAndBuildNumber':False}))
                checked(['xcodebuild','-exportArchive','-archivePath',archive,'-exportPath',export,'-exportOptionsPlist',options])
                ipa, = export.glob('*.ipa'); dest = out/('DriveLink-'+label+'.ipa'); ipa.rename(dest)
                verify_ipa(dest,work,expected,team,pin,profiles,method)
            (out/'IOS_SHA256SUMS.txt').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n' for p in sorted(out.glob('*.ipa'))))
            print('Verified both distribution IPAs and embedded profiles; signer SHA-256: '+pin)
        finally:
            for path in installed: path.unlink(missing_ok=True)
            checked(['security','list-keychains','-d','user','-s',*prior]); checked(['security','delete-keychain',kc])

if __name__ == '__main__':
    main()
