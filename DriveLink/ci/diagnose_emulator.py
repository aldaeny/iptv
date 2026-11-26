"""Analyze the complete archived smoke step, excluding its shell setup and environment."""
import hashlib
import re
import sys
from zipfile import ZipFile

with ZipFile(sys.argv[1]) as archive:
    files = [n for n in archive.namelist() if re.search(r'/\d+_Smoke test .*\.txt$', n)]
    for name in archive.namelist():
        if not re.search(r'/\d+_Verify packages, versions, signing and SHA-256.txt$', name):
            continue
        data = archive.read(name).decode('utf-8-sig', errors='replace')
        # Only diagnostic categories; don't forward arguments/environment to annotations.
        for line in data.split('##[endgroup]')[-1].splitlines():
            if re.search(r'(?:Standard|FullTouch|Receiver): (?:incorrect|unexpected|missing|unsafe|overlay|projection)', line):
                print('::notice::Manifest policy: ' + line[:240])
            elif re.search(r'(?:Exception|Error)(?::| in thread)', line):
                print('::notice::APK analyzer: ' + re.sub(r'https?://\S+', '[url]', line)[:240])
            elif 'not found' in line or 'No such file' in line:
                print('::notice::Analyzer dependency: ' + line[:240])
    if not files:
        print('::notice::No archived smoke step; check preceding step conclusions')
        sys.exit(0)
    if len(files) != 1:
        sys.exit('Expected one archived emulator step')
    raw = archive.read(files[0])
lines = raw.decode('utf-8-sig', errors='replace').splitlines()
print(f'::notice::Archived smoke step: {len(lines)} lines; sha256={hashlib.sha256(raw).hexdigest()}')
# Do not forward shell commands, environment blocks, URLs or arbitrary log contents.
markers = {
    'disk-full': r'no space left on device|not enough (?:disk )?space|insufficient disk space',
    'KVM-unavailable': r'requires hardware acceleration|permission denied.*kvm|/dev/kvm.*permission denied',
    'AVD-not-found': r'unknown AVD|cannot find AVD',
    'bad-system-image': r'invalid.*(?:package|system image)|package path is not valid|failed to find package',
    'platform-tools-missing': r'adb: command not found',
    'emulator-crash': r'segmentation fault|core dumped',
    'missing-library': r'error while loading shared libraries',
    'AVD-running': r'Running multiple emulators with the same AVD',
    'install-failed': r'INSTALL_FAILED_[A-Z_]+',
    'activity-error': r'Error type 3|Activity class .* does not exist',
    'install-succeeded': r'\bSuccess\s*$',
}
output = lines[next(i for i, s in enumerate(lines) if '##[endgroup]' in s) + 1:]
for label, pattern in markers.items():
    matches = sum(bool(re.search(pattern, s, re.I)) for s in output)
    if matches:
        print(f'::notice::{label}: {matches} log line(s)')
# Emulator and SDK diagnostics only, not environment values. Mask URLs and workflow characters.
for line in output:
    if re.search(r'\b(?:ERROR|PANIC|FATAL|WARNING)\s*[:|]|^.*(?:Not enough|No space left)', line, re.I):
        clean = re.sub(r'https?://\S+', '[url]', line).replace('%', '%25').replace('\r', '')
        print('::notice::' + clean[:350])
