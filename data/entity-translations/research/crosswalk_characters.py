"""Resolve explicit English/romaji character aliases and Japanese subject identity."""
import collections
import hashlib
import json
import re
from pathlib import Path

ROOT = Path('/tmp/nclientv3-entity-sources')
REPO = Path(__file__).resolve().parents[3]
inventory = {name for _, name, _, kind in json.loads((REPO / 'data/tags.json').read_text()) if kind == 2}
bangumi = json.loads((ROOT / 'bangumi-dist-data.json').read_text())['items']
subjects = {}
for item in bangumi:
    for site in item['sites']:
        if site['site'] == 'bangumi':
            subjects[int(site['id'])] = {'title': item['title'], 'lang': item['lang']}
expanded_subjects = ROOT / 'archive-japanese-subjects.json'
if expanded_subjects.exists():
    subjects.update({int(identifier): value for identifier, value in json.loads(expanded_subjects.read_text()).items()})
western_subject_path = ROOT / 'archive-western-subjects.json'
western_subjects = {int(identifier): value for identifier, value in json.loads(western_subject_path.read_text()).items()} if western_subject_path.exists() else {}


def plain(value):
    return value.strip()


def valid_zh(value):
    return bool(re.search('[㐀-鿿]', value)) and not re.search(r'[ぁ-ゖァ-ヺｦ-ﾟ\[\]{}|<>\\]', value) and len(value) < 120


def explicit_aliases(item):
    box = item.get('infobox', '')
    aliases = []
    # A Latin original name is itself an explicit identity value.
    if item['name'].isascii():
        aliases.append(('name', item['name']))
    for key, value in re.findall(r'\[([^\[\]|\r\n]+)\|([^\[\]\r\n]*)\]', box):
        if re.search('英文|英语|罗马|ローマ', key):
            aliases.append((key.strip(), value.strip()))
    for key, value in re.findall(r'(?m)^\|([^=\r\n]+)=([^\r\n]*)', box):
        if re.search('英文|英语|罗马|ローマ', key):
            aliases.append((key.strip(), value.strip()))
    return [(key, value) for key, value in aliases if value and not re.search(r'[\[\]{}|<>\\]', value)]


matches = collections.defaultdict(dict)
records = {}
source_count = 0
with (ROOT / 'archive-character.jsonlines').open() as stream:
    for line in stream:
        item = json.loads(line)
        source_count += 1
        aliases = [(key, value) for key, value in explicit_aliases(item) if value.casefold() in inventory]
        if not aliases:
            continue
        chinese = re.search(r'(?m)^\|简体中文名\s*=\s*([^\r\n]*)', item.get('infobox', ''))
        label = chinese.group(1).strip() if chinese else ''
        records[item['id']] = {'id': item['id'], 'name': item['name'], 'role': item['role'],
            'display_name': label if valid_zh(label) else None, 'ja_subjects': [], 'other_subjects': [], 'western_subjects': []}
        for key, value in aliases:
            matches[value.casefold()][item['id']] = {'key': key, 'value': value}

with (ROOT / 'archive-subject-characters.jsonlines').open() as stream:
    for line in stream:
        link = json.loads(line)
        record = records.get(link['character_id'])
        subject = subjects.get(link['subject_id'])
        if record is not None and link['subject_id'] in western_subjects:
            record['western_subjects'].append(western_subjects[link['subject_id']])
        if record is None or subject is None:
            continue
        key = 'ja_subjects' if subject['lang'] == 'ja' else 'other_subjects'
        record[key].append(dict(subject, id=link['subject_id']))

candidates = {}
unresolved = {}
for name, byid in matches.items():
    evidence = [dict(records[identifier], alias=alias) for identifier, alias in byid.items()]
    labels = {row['display_name'] for row in evidence}
    # Include every matched identity: a second unresolved identity blocks an ambiguous same-name match.
    if len(labels) == 1 and None not in labels and all(row['ja_subjects'] for row in evidence):
        candidates[name] = {'display_name': next(iter(labels)), 'source': 'Bangumi Archive', 'evidence': evidence}
    else:
        unresolved[name] = evidence

for filename, value in [('archive-safe-character-candidates.json', candidates),
                        ('archive-unresolved-character-matches.json', unresolved)]:
    (ROOT / filename).write_text(json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + '\n')
eh = json.loads((ROOT / 'character-matched.json').read_text())
summary = {'source_characters': source_count, 'inventory_names': len(inventory),
    'explicit_alias_inventory_matches': len(matches), 'safe_names': len(candidates),
    'safe_names_not_in_eh': len(set(candidates) - eh.keys()), 'unresolved_or_ambiguous_names': len(unresolved),
    'normalization': 'casefold only, explicit English/romaji field or Latin original name; no reorder/fuzzy/transliteration',
    'origin_limit': 'At least one curated Japan-meta-tag or Japanese-language Bangumi-data subject. Western-IP override still required.'}
(ROOT / 'archive-character-crosswalk-summary.json').write_text(json.dumps(summary, indent=2) + '\n')
print(json.dumps(summary, ensure_ascii=False, indent=2))
