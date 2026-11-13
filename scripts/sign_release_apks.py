"""Sign verified CI Release artifacts with the privately retained, pinned release key."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--version', required=True)
    parser.add_argument('--input-dir', type=Path, required=True)
    parser.add_argument('--output-dir', type=Path, required=True)
    parser.add_argument('--keystore', type=Path, required=True)
    parser.add_argument('--credentials-file', type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    subprocess.run([
        sys.executable, str(root / 'scripts/verify_release_apks.py'),
        '--version', args.version, '--apk-dir', str(args.input_dir),
    ], check=True)
    tools = Path(os.environ['ANDROID_HOME']) / 'build-tools' / '36.0.0'
    expected = (root / 'config/release-signing-certificate.sha256').read_text().strip()
    credentials = json.loads(args.credentials_file.read_text())
    signing_env = os.environ.copy()
    signing_env['NCLIENTV3_SIGNING_STORE_PASSWORD'] = credentials['store_password']
    signing_env['NCLIENTV3_SIGNING_KEY_PASSWORD'] = credentials['key_password']
    certificate = subprocess.check_output([
        'keytool', '-exportcert', '-keystore', str(args.keystore),
        '-alias', credentials['alias'], '-storepass:env', 'NCLIENTV3_SIGNING_STORE_PASSWORD',
    ], env=signing_env, stderr=subprocess.PIPE)
    if hashlib.sha256(certificate).hexdigest() != expected:
        raise SystemExit('Signing key differs from the pinned release certificate; refusing to sign')
    args.output_dir.mkdir(parents=True, exist_ok=True)
    outputs = []
    with tempfile.TemporaryDirectory(prefix='nclient-release-sign-') as temporary:
        for variant in ['android9plus', 'android8']:
            source = args.input_dir / f'NClientV3_v{args.version}_{variant}_unsigned.apk'
            destination = args.output_dir / f'NClientV3_v{args.version}_{variant}.apk'
            if destination.exists():
                raise SystemExit(f'Refusing to replace existing APK: {destination}')
            aligned = Path(temporary) / f'{variant}.apk'
            subprocess.run([str(tools / 'zipalign'), '-P', '16', '4', str(source), str(aligned)], check=True)
            subprocess.run([
                str(tools / 'apksigner'), 'sign', '--ks', str(args.keystore),
                '--ks-key-alias', credentials['alias'],
                '--ks-pass', 'env:NCLIENTV3_SIGNING_STORE_PASSWORD',
                '--key-pass', 'env:NCLIENTV3_SIGNING_KEY_PASSWORD',
                '--v1-signing-enabled', 'true', '--v2-signing-enabled', 'true',
                '--v3-signing-enabled', 'true', '--out', str(destination), str(aligned),
            ], env=signing_env, check=True)
            subprocess.run([str(tools / 'apksigner'), 'verify', str(destination)], check=True)
            outputs.append(destination)
            print(f'Signed {destination.name} with pinned release certificate {expected}')
    (args.output_dir / 'SHA256SUMS.txt').write_text(''.join(
        f'{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}\n' for path in outputs
    ))


if __name__ == '__main__':
    main()
