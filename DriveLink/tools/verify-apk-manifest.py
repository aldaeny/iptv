"""Check the packaged manifest emitted by apkanalyzer, not source overlays."""
import sys
from xml.etree import ElementTree as ET

label = sys.argv[1]
root = ET.parse(sys.stdin).getroot()
key = '{http://schemas.android.com/apk/res/android}'
permissions = {n.attrib[key+'name'] for n in root.findall('uses-permission')}
services = {n.attrib[key+'name']: n for n in root.findall('./application/service')}

def require(condition, message):
    if not condition: sys.exit(label + ': ' + message)

require(root.find('uses-sdk').get(key+'targetSdkVersion') == '34', 'unexpected target SDK')
for perm in ['INTERNET','ACCESS_WIFI_STATE','WAKE_LOCK','CHANGE_WIFI_MULTICAST_STATE','ACCESS_NETWORK_STATE']:
    require('android.permission.'+perm in permissions, 'missing ' + perm)
for perm in ['RECORD_AUDIO','FOREGROUND_SERVICE_MEDIA_PROJECTION','FOREGROUND_SERVICE']:
    require(('android.permission.'+perm in permissions) == (label != 'Receiver'), 'incorrect ' + perm)
require(('android.permission.SYSTEM_ALERT_WINDOW' in permissions) == (label == 'FullTouch'), 'overlay permission mismatch')
projection = services.get('app.drivelink.cast.service.MirrorForegroundService')
require((projection is not None) == (label != 'Receiver'), 'projection service mismatch')
if projection is not None:
    kind = projection.get(key+'foregroundServiceType', '')
    # Binary XML can expose the Android flag value instead of its SDK enum name.
    try:
        media_projection_only = int(kind, 0) == 0x20
    except ValueError:
        media_projection_only = kind == 'mediaProjection'
    exported = projection.get(key+'exported')
    require(media_projection_only and exported == 'false',
            f'unsafe projection service (type={kind!r}, exported={exported!r})')
for name in ['MirrorOverlayService','BydTouchAccessibilityService']:
    require(('app.drivelink.cast.service.'+name in services) == (label=='FullTouch'), 'incorrect '+name)
print(label, 'permissions:', ', '.join(sorted(permissions)), 'services:', ', '.join(sorted(services)))
