#!/usr/bin/env python3
"""Verify stable APKs before signing or publishing; never signs or uploads files."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import ssl
import subprocess
import zipfile


ROOT = Path(__file__).resolve().parents[1]
ASSETS = (
    "content_tag_allowlist.json",
    "tag_translations_zh_CN.json",
    "entity_translations_zh_CN.json",
    "tag_label_collisions_zh_CN.json",
)
VARIANTS = (
    ("post28", "", "android9plus", 28, 36),
    ("pre28", "_pre28", "android8", 26, 28),
)


def run(*command):
    return subprocess.check_output([str(value) for value in command], text=True, stderr=subprocess.PIPE)


def require(condition, message):
    if not condition:
        raise SystemExit(message)


def version_code(version):
    major, minor, patch = map(int, version.split("."))
    return int(f"{major}{minor}{patch:02d}00")


def verify_checksums(directory, filenames):
    manifest = directory / "SHA256SUMS.txt"
    if not manifest.exists():
        return
    expected = {}
    for line in manifest.read_text().splitlines():
        digest, filename = line.split(maxsplit=1)
        filename = filename.lstrip("*")
        require(re.fullmatch(r"[a-fA-F0-9]{64}", digest), "Invalid SHA-256 manifest entry")
        require(filename not in expected and filename in filenames, "Unexpected or repeated checksum filename")
        expected[filename] = digest.lower()
    require(set(expected) == set(filenames), "Checksum manifest must contain exactly the two stable APKs")
    for filename, digest in expected.items():
        require(hashlib.sha256((directory / filename).read_bytes()).hexdigest() == digest,
                f"Checksum mismatch: {filename}")


def verify_apk(apk, label, min_sdk, target_sdk, version, tools, signed, pinned_certificate):
    require(apk.is_file(), f"Missing APK: {apk}")
    badging = run(tools / "aapt2", "dump", "badging", apk)
    checks = {
        "package": (r"^package: name='([^']+)'", "com.maxwai.nclientv3"),
        "version": (r"^package: .*versionName='([^']+)'", f"{version}-release"),
        "version code": (r"^package: .*versionCode='([^']+)'", str(version_code(version))),
        "minimum SDK": (r"^(?:sdkVersion|minSdkVersion):'([^']+)'", str(min_sdk)),
        "target SDK": (r"^targetSdkVersion:'([^']+)'", str(target_sdk)),
    }
    for name, (pattern, expected) in checks.items():
        match = re.search(pattern, badging, re.MULTILINE)
        require(match is not None and match.group(1) == expected, f"Unexpected {name}: {apk}")
    require("application-debuggable" not in badging, f"Stable APK is debuggable: {apk}")
    manifest = run(tools / "aapt2", "dump", "xmltree", apk, "--file", "AndroidManifest.xml")
    debuggable = re.search(r"android:debuggable[^\n]*", manifest)
    require(debuggable is None or re.search(r"(?:=false\b|=\(type 0x12\)0x0\b|=0\b)", debuggable.group()),
            f"Unexpected manifest debuggable flag: {apk}")
    with zipfile.ZipFile(apk) as archive:
        for asset in ASSETS:
            require(archive.read("assets/" + asset) == (ROOT / "app/src/main/assets" / asset).read_bytes(),
                    f"Bundled asset differs from tagged sources: {asset} in {apk}")
        require("assets/excluded_content_tags.json" not in archive.namelist(), "Obsolete exclusion asset bundled")
        native = sorted(name for name in archive.namelist() if name.startswith("lib/") and name.endswith(".so"))
    # Release resource optimization shortens paths, for example res/yl.xml.
    # Resolve names through the resource table and inspect every configuration.
    resources = run(tools / "aapt2", "dump", "resources", apk)
    layout_paths = {name: [] for name in ("tag_choice_dialog", "autocomplete_entry")}
    current_layout = None
    for line in resources.splitlines():
        resource = re.match(r"\s*resource\s+0x[0-9a-fA-F]+\s+(?:\w+:)?layout/(\S+)", line)
        if re.match(r"\s*resource\s+", line):
            current_layout = resource.group(1) if resource and resource.group(1) in layout_paths else None
        if current_layout:
            path = re.search(r"\(file\)\s+(\S+)\s+type=XML", line)
            if path and path.group(1) not in layout_paths[current_layout]:
                layout_paths[current_layout].append(path.group(1))
    for layout, paths in layout_paths.items():
        require(paths, f"Missing autocomplete layout resource: {layout} in {apk}")
        for path in paths:
            xml = run(tools / "aapt2", "dump", "xmltree", apk, "--file", path)
            require(re.search(r"completionThreshold[^\n]*(?:=1\b|0x1\b)", xml),
                    f"Autocomplete threshold is not one character: {layout} ({path}) in {apk}")
    certificate = None
    if signed:
        signature = run(tools / "apksigner", "verify", "--verbose", "--print-certs", apk)
        certificates = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]{64})", signature)
        require(len(certificates) == 1, f"Expected exactly one APK signer: {apk}")
        certificate = certificates[0].lower()
        require(certificate == pinned_certificate, f"APK signer differs from the pinned release certificate: {apk}")
    else:
        signature = subprocess.run([str(tools / "apksigner"), "verify", str(apk)],
                                   stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        require(signature.returncode != 0, f"Expected an unsigned build artifact: {apk}")
    return dict(file=apk.name, variant=label, bytes=apk.stat().st_size,
                sha256=hashlib.sha256(apk.read_bytes()).hexdigest(),
                package="com.maxwai.nclientv3", version=f"{version}-release",
                version_code=version_code(version), min_sdk=min_sdk, target_sdk=target_sdk,
                debuggable=False, signed=signed, certificate_sha256=certificate,
                bundled_assets_verified=list(ASSETS), autocomplete_threshold=1,
                autocomplete_layout_paths=layout_paths, native_libraries=native)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", required=True, help="Stable version, for example 4.2.14")
    parser.add_argument("--signed", action="store_true", help="Require the pinned stable signing certificate")
    parser.add_argument("--apk-dir", type=Path, help="Directory of already named unsigned or signed stable APKs")
    parser.add_argument("--stage-dir", type=Path, help="Copy verified APKs and write checksums/verification JSON here")
    parser.add_argument("--report", type=Path, help="Write verification JSON without changing input manifests")
    args = parser.parse_args()
    require(re.fullmatch(r"\d+\.\d+\.\d+", args.version), "Expected version X.Y.Z")
    require(not args.signed or args.apk_dir is not None, "--signed requires --apk-dir")
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    require(sdk, "ANDROID_HOME or ANDROID_SDK_ROOT must be configured")
    tools = Path(sdk) / "build-tools/36.0.0"
    for tool in ("aapt2", "apksigner"):
        require((tools / tool).is_file(), f"Missing SDK tool: {tools / tool}")
    pin = None
    if args.signed:
        pin = (ROOT / "config/release-signing-certificate.sha256").read_text().strip().lower()
        require(re.fullmatch(r"[a-f0-9]{64}", pin), "Invalid pinned release certificate SHA-256")
        pem = (ROOT / "config/release-signing-certificate.pem").read_text()
        require(hashlib.sha256(ssl.PEM_cert_to_DER_cert(pem)).hexdigest() == pin,
                "Pinned certificate and SHA-256 differ")
    tag = "v" + args.version
    suffix = "" if args.signed else "_unsigned"
    names = [f"NClientV3_{tag}_{variant[2]}{suffix}.apk" for variant in VARIANTS]
    if args.apk_dir is not None:
        verify_checksums(args.apk_dir, names)
    results = []
    if args.stage_dir:
        args.stage_dir.mkdir(parents=True, exist_ok=True)
    for (flavor, build_suffix, label, min_sdk, target_sdk), filename in zip(VARIANTS, names):
        apk = (args.apk_dir / filename if args.apk_dir else
               ROOT / f"app/build/outputs/apk/{flavor}/release/NClientV3_{args.version}{build_suffix}.apk")
        result = verify_apk(apk, label, min_sdk, target_sdk, args.version, tools, args.signed, pin)
        result["file"] = filename
        if args.stage_dir:
            destination = args.stage_dir / filename
            if apk.resolve() != destination.resolve():
                require(not destination.exists(), f"Refusing to overwrite staged APK: {destination}")
                shutil.copy2(apk, destination)
        results.append(result)
        print(f"PASS {filename}: {result['package']}, {result['version']}, SDK {min_sdk}/{target_sdk}, "
              f"{'pinned signature' if args.signed else 'unsigned build artifact'}")
    report = json.dumps(results, indent=2, ensure_ascii=False) + "\n"
    if args.stage_dir:
        checksum_text = "".join(f"{result['sha256']}  {result['file']}\n" for result in results)
        manifest = args.stage_dir / "SHA256SUMS.txt"
        require(not manifest.exists() or manifest.read_text() == checksum_text,
                "Refusing to overwrite a differing staged checksum manifest")
        manifest.write_text(checksum_text)
        (args.stage_dir / "verification.json").write_text(report)
    if args.report:
        args.report.write_text(report)


if __name__ == "__main__":
    main()
