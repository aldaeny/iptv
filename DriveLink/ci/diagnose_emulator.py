"""Analyze the complete archived smoke step, excluding its shell setup and environment."""
import hashlib
import re
import sys
from zipfile import ZipFile

with ZipFile(sys.argv[1]) as archive:
    files = [n for n in archive.namelist() if re.search(r'/\d+_Smoke test .*\.txt$', n)]
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
