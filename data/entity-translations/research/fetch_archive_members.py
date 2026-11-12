"""Fetch and verify selected ZIP members using bounded explicit HTTP byte ranges."""
import concurrent.futures
import hashlib
import json
import struct
import sys
import urllib.request
import zlib
from pathlib import Path

ROOT = Path('/tmp/nclientv3-entity-sources')
URL = 'https://github.com/bangumi/Archive/releases/download/archive/dump-2026-11-10.210405Z.zip'
MEMBERS = json.loads((ROOT / 'bangumi-archive-members.json').read_text())
BLOCK = 8 * 1024 * 1024


def range_get(start, end):
    request = urllib.request.Request(URL, headers={'User-Agent': 'NClientV3-name-source-audit',
        'Range': f'bytes={start}-{end}'})
    response = urllib.request.urlopen(request, timeout=30)
    if response.status != 206 or response.headers.get('Content-Range') != f'bytes {start}-{end}/451238635':
        response.close()
        raise ValueError('Server did not honor bounded explicit range')
    return response


def extract(member):
    filename = member['filename']
    offset = member['header_offset']
    with range_get(offset, offset + 79) as response:
        header = response.read()
    assert header[:4] == b'PK\x03\x04'
    name_length, extra_length = struct.unpack('<4s5H3L2H', header[:30])[-2:]
    payload = offset + 30 + name_length + extra_length
    remaining = member['compressed_size']
    decompressor = zlib.decompressobj(-15)
    compressed_hash = hashlib.sha256()
    raw_hash = hashlib.sha256()
    crc = 0
    output_size = 0
    destination = ROOT / ('archive-' + filename)
    with destination.open('wb') as output:
        while remaining:
            count = min(BLOCK, remaining)
            with range_get(payload, payload + count - 1) as response:
                received = 0
                while chunk := response.read(1024 * 1024):
                    received += len(chunk)
                    compressed_hash.update(chunk)
                    decoded = decompressor.decompress(chunk)
                    output.write(decoded)
                    output_size += len(decoded)
                    raw_hash.update(decoded)
                    crc = zlib.crc32(decoded, crc)
                assert received == count
            payload += count
            remaining -= count
        decoded = decompressor.flush()
        output.write(decoded)
        output_size += len(decoded)
        raw_hash.update(decoded)
        crc = zlib.crc32(decoded, crc)
    assert decompressor.eof and output_size == member['size'] and crc == member['crc32']
    result = dict(member, source_url=URL, raw_sha256=raw_hash.hexdigest(),
                  compressed_sha256=compressed_hash.hexdigest(), filename=str(destination.name))
    (ROOT / (destination.name + '.source.json')).write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result), flush=True)
    return result


requested = set(sys.argv[1:] or ['character.jsonlines', 'subject-characters.jsonlines', 'person.jsonlines'])
targets = [member for member in MEMBERS if member['filename'] in requested]
with concurrent.futures.ThreadPoolExecutor(max_workers=3) as executor:
    results = list(executor.map(extract, targets))
previous = ROOT / 'archive-source-members.json'
merged = {row['filename']: row for row in json.loads(previous.read_text())} if previous.exists() else {}
merged.update({row['filename']: row for row in results})
previous.write_text(json.dumps(list(merged.values()), indent=2) + '\n')
