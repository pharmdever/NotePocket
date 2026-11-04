"""Decode the single repository secret without logging key material."""
import base64
import json
import hashlib
import os
from pathlib import Path
import re
import subprocess

bundle = json.loads(base64.b64decode(os.environ['ANDROID_SIGNING_BUNDLE'], validate=True))
password = bundle['password']
if not re.fullmatch(r'[0-9a-f]{64}', password) or bundle['alias'] != 'notepocket':
    raise ValueError('Unexpected signing bundle format')
key = base64.b64decode(bundle['keystore'], validate=True)
if len(key) > 65536:
    raise ValueError('Unexpected keystore size')
folder = Path(os.environ['RUNNER_TEMP']) / 'notepocket-signing'
folder.mkdir(mode=0o700, exist_ok=True)
path = folder / 'release.p12'
path.write_bytes(key)
path.chmod(0o600)
print('::add-mask::' + password)
os.environ['NP_STORE_PASSWORD'] = password
subprocess.run(['keytool','-list','-keystore',str(path),'-storepass:env','NP_STORE_PASSWORD','-alias','notepocket'], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
with open(os.environ['GITHUB_ENV'],'a',encoding='utf-8') as output:
    output.write(f'NP_KEYSTORE={path}\nNP_STORE_PASSWORD={password}\nNP_KEY_ALIAS=notepocket\n')
cert = subprocess.run(['keytool','-exportcert','-keystore',str(path),'-storepass:env','NP_STORE_PASSWORD','-alias','notepocket'], check=True, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL).stdout
expected = Path('docs/release-certificate-sha256.txt').read_text().strip()
if hashlib.sha256(cert).hexdigest().upper() != expected:
    raise ValueError('Signing certificate differs from the permanent release identity')
print('Persistent release signing configured and certificate verified.')
