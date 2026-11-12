"""Exact bilingual title crosswalk; no fuzzy or romaji reconstruction."""
import collections
import hashlib
import json
import re
from pathlib import Path

ROOT = Path('/tmp/nclientv3-entity-sources')
REPO = Path(__file__).resolve().parents[3]


def read(name):
    return json.loads((ROOT / name).read_text())


def write(name, value):
    (ROOT / name).write_text(json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + '\n')


def valid_zh(label):
    return bool(re.search('[㐀-鿿]', label)) and not re.search('[ぁ-ゖァ-ヺｦ-ﾟ]', label)


def aliases(item):
    # Preserve punctuation and spacing: only case is normalized for exact matching.
    return {text.casefold() for text in [item['title']] + item['synonyms']}


PROVIDERS = {
    'mal': 'https://myanimelist.net/anime/',
    'aniList': 'https://anilist.co/anime/',
    'anidb': 'https://anidb.net/anime/',
}
bangumi = read('bangumi-dist-data.json')['items']
manami = read('manami-2026-27.json')['data']
inventory = {name for _, name, _, kind in json.loads((REPO / 'data/tags.json').read_text()) if kind == 1}
direct_matches = collections.defaultdict(list)
for item in bangumi:
    names = [item['title']]
    for language, titles in item.get('titleTranslate', {}).items():
        if language not in ('zh-Hans', 'zh-Hant'):
            names.extend(titles)
    for name in set(title.casefold() for title in names) & inventory:
        direct_matches[name].append(item)
direct = {}
for name, rows in direct_matches.items():
    labels = {row.get('titleTranslate', {}).get('zh-Hans', [''])[0] for row in rows}
    label = next(iter(labels)) if len(labels) == 1 else None
    if label and valid_zh(label) and all(row['lang'] == 'ja' for row in rows):
        direct[name] = {'display_name': label, 'source': 'bangumi-data exact title', 'evidence': rows}
write('bangumi-safe-parody-candidates.json', direct)
source_index = collections.defaultdict(set)
for number, item in enumerate(bangumi):
    for site in item['sites']:
        provider = PROVIDERS.get(site['site'])
        if provider:
            source_index[provider + str(site['id'])].add(number)

exact_matches = collections.defaultdict(list)
for index, item in enumerate(manami):
    names = aliases(item) & inventory
    if not names:
        continue
    joined = set()
    joins = []
    for source in item['sources']:
        target = source_index.get(source)
        if target:
            joined.update(target)
            joins.append(source)
    target_items = [bangumi[number] for number in sorted(joined)]
    labels = {row.get('titleTranslate', {}).get('zh-Hans', [''])[0] for row in target_items}
    label = next(iter(labels)) if len(labels) == 1 else None
    valid = bool(target_items) and all(row['lang'] == 'ja' for row in target_items) and label and valid_zh(label)
    evidence = {
        'manami_index': index,
        'manami_title': item['title'],
        'provider_joins': joins,
        'bangumi_titles': [row['title'] for row in target_items],
        'bangumi_ids': sorted({str(site['id']) for row in target_items for site in row['sites'] if site['site'] == 'bangumi'}),
        'labels': sorted(labels),
        'languages': sorted({row['lang'] for row in target_items}),
        'display_name': label if valid else None,
        'status': 'candidate' if valid else 'unresolved',
    }
    for name in names:
        exact_matches[name].append(evidence)

candidates = {}
conflicts = {}
for name, rows in exact_matches.items():
    labels = {row['display_name'] for row in rows}
    if len(labels) == 1 and None not in labels:
        candidates[name] = {
            'display_name': next(iter(labels)),
            'source': 'bangumi-data + anime-offline-database exact provider ID crosswalk',
            'evidence': rows,
        }
    else:
        conflicts[name] = rows

write('crosswalk-safe-parody-candidates.json', candidates)
write('crosswalk-unresolved-parody-matches.json', conflicts)
eh = read('parody-matched.json')
summary = {
    'inventory_names': len(inventory),
    'bangumi_items': len(bangumi),
    'manami_items': len(manami),
    'inventory_alias_matches': len(exact_matches),
    'safe_names': len(candidates),
    'safe_names_not_in_eh': len(set(candidates) - eh.keys()),
    'safe_names_not_in_eh_or_direct': len(set(candidates) - eh.keys() - direct.keys()),
    'unresolved_or_conflicting_names': len(conflicts),
    'safe_direct_names': len(direct),
    'safe_direct_names_not_in_eh': len(set(direct) - eh.keys()),
    'normalization': 'casefold only; exact whole title/alias, no punctuation/space edits or fuzzy matches',
}
write('crosswalk-summary.json', summary)
metadata = {
    'bangumi-data': {
        'repository': 'https://github.com/bangumi-data/bangumi-data',
        'commit': 'd5f56180c0c71fbeb24bd1914eb04c75f2f358fa',
        'data_url': 'https://raw.githubusercontent.com/bangumi-data/bangumi-data/d5f56180c0c71fbeb24bd1914eb04c75f2f358fa/dist/data.json',
        'license': 'CC BY 4.0 (README License section)',
        'data_sha256': hashlib.sha256((ROOT / 'bangumi-dist-data.json').read_bytes()).hexdigest(),
        'notice_sha256': hashlib.sha256((ROOT / 'bangumi-README.md').read_bytes()).hexdigest(),
    },
    'anime-offline-database': {
        'repository': 'https://github.com/manami-project/anime-offline-database',
        'release': '2026-27',
        'commit': '96da26358c8e599a9c076818aee247c68cb69401',
        'data_url': 'https://github.com/manami-project/anime-offline-database/releases/download/2026-27/anime-offline-database-minified.json.zst',
        'license': 'ODbL-1.0 + Database Contents License 1.0',
        'compressed_sha256': hashlib.sha256((ROOT / 'manami-2026-27.json.zst').read_bytes()).hexdigest(),
        'data_sha256': hashlib.sha256((ROOT / 'manami-2026-27.json').read_bytes()).hexdigest(),
        'license_sha256': hashlib.sha256((ROOT / 'manami-LICENSE').read_bytes()).hexdigest(),
    },
}
write('title-sources.json', metadata)
print(json.dumps(summary, ensure_ascii=False, indent=2))
