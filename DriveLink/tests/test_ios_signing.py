"""Policy tests only; no certificates, signed apps or provisioning are manufactured."""
import hashlib
import importlib.util
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path

spec = importlib.util.spec_from_file_location('ios_sign', Path(__file__).resolve().parents[1]/'tools/sign-ios.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class ProfilePolicy(unittest.TestCase):
    def setUp(self):
        self.pin = hashlib.sha256(b'certificate-bytes-fixture').hexdigest()
        self.p = {'TeamIdentifier': ['TEAM'], 'ApplicationIdentifierPrefix': ['PREFIX'],
                  'ExpirationDate': datetime.now(timezone.utc) + timedelta(days=1),
                  'DeveloperCertificates': [b'certificate-bytes-fixture'],
                  'Entitlements': {'application-identifier': 'PREFIX.'+module.BASE,
                                   'com.apple.security.application-groups': [module.GROUP]}}

    def check(self):
        module.validate_profile(self.p, module.BASE, 'TEAM', self.pin, 'app-store-connect')

    def test_matches_exact_app_and_approved_signer(self): self.check()

    def test_rejects_debugging(self):
        self.p['Entitlements']['get-task-allow'] = True
        with self.assertRaises(ValueError): self.check()

    def test_rejects_other_distribution_method(self):
        self.p['ProvisionedDevices'] = ['device']
        with self.assertRaises(ValueError): self.check()

    def test_rejects_untrusted_cert(self):
        self.p['DeveloperCertificates'] = []
        with self.assertRaises(ValueError): self.check()

    def test_rejects_wrong_bundle(self):
        self.p['Entitlements']['application-identifier'] = 'PREFIX.*'
        with self.assertRaises(ValueError): self.check()

    def test_rejects_missing_group(self):
        self.p['Entitlements']['com.apple.security.application-groups'] = []
        with self.assertRaises(ValueError): self.check()

    def test_rejects_expired(self):
        self.p['ExpirationDate'] -= timedelta(days=2)
        with self.assertRaises(ValueError): self.check()

if __name__ == '__main__': unittest.main()
