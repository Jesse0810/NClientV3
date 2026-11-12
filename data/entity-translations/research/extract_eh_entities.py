"""Extract all four namespace tables without crossing entity identity domains."""
import hashlib
import json
import re
import sys
from pathlib import Path

ROOT = Path('/tmp/nclientv3-entity-sources')
REPO = Path(__file__).resolve().parents[3]
SOURCE = Path('/tmp/ehtag-source')
sys.path.insert(0, str(REPO / 'scripts'))
from build_tag_translations import plain_label

rows = json.loads((REPO / 'data/tags.json').read_text())
report = {}
for namespace, tag_type in [('parody', 1), ('character', 2), ('artist', 4), ('group', 5)]:
    entries = {}
    path = SOURCE / 'database' / (namespace + '.md')
    for line in path.read_text().splitlines():
        if not line.startswith('|'):
            continue
        columns = re.split(r'(?<!\\)\|', line)
        if len(columns) < 6:
            continue
        name = columns[1].strip().replace(r'\|', '|')
        if not name or name == '原始标签' or set(name) <= set('- '):
            continue
        if name in entries:
            raise ValueError('Duplicate namespace key: ' + namespace + ':' + name)
        entries[name] = {'raw': name, 'label': plain_label(columns[2].strip()),
                         'description': columns[3].strip(), 'links': columns[4].strip()}
    inventory = {name for _, name, _, kind in rows if kind == tag_type}
    matches = {name: entries[name] for name in sorted(inventory & entries.keys())}
    for suffix, value in [('entries', entries), ('matched', matches)]:
        (ROOT / (namespace + '-' + suffix + '.json')).write_text(
            json.dumps(value, ensure_ascii=False, indent=2) + '\n')
    report[namespace] = {'inventory_names': len(inventory), 'source_names': len(entries),
                          'exact_matches': len(matches), 'source_sha256': hashlib.sha256(path.read_bytes()).hexdigest()}
report['source'] = {'url': 'https://github.com/EhTagTranslation/Database',
                    'commit': 'e0f66dc4424a53872473b05b7fb006ceebfcbe5d',
                    'license': 'CC-BY-NC-SA-3.0-CN',
                    'archive_sha256': hashlib.sha256(Path('/tmp/ehtag-database.tar.gz').read_bytes()).hexdigest()}
(ROOT / 'eh-extraction-summary.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
print(json.dumps(report, ensure_ascii=False, indent=2))
