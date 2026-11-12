"""Exact subject title aliases with curated origin metadata, no fuzzy joins."""
import collections
import json
import re
from pathlib import Path

ROOT = Path('/tmp/nclientv3-entity-sources')
REPO = Path(__file__).resolve().parents[3]
inventory = {name for _, name, _, kind in json.loads((REPO / 'data/tags.json').read_text()) if kind == 1}
WESTERN = {'欧美', '美国', '英国', '法国', '德国', '加拿大', '意大利', '西班牙', '俄罗斯',
    '波兰', '瑞典', '芬兰', '丹麦', '挪威', '荷兰', '比利时', '澳大利亚', '新西兰', '捷克', '乌克兰'}
OTHER_COUNTRIES = {'中国', '中国大陆', '大陆', '台湾', '中国台湾', '香港', '中国香港', '韩国'}
bangumi_data = json.loads((ROOT / 'bangumi-dist-data.json').read_text())['items']
known_languages = {}
for item in bangumi_data:
    for site in item['sites']:
        if site['site'] == 'bangumi':
            known_languages[int(site['id'])] = item['lang']


def valid_zh(value):
    return bool(re.search('[㐀-鿿]', value)) and not re.search(r'[ぁ-ゖァ-ヺｦ-ﾟ\[\]{}|<>\\]', value) and len(value) < 180


def latin_aliases(item):
    values = [('name', item['name'])]
    box = item.get('infobox', '')
    alias_block = re.search(r'(?ms)^\|别名\s*=\s*\{(.*?)^\s*}', box)
    if alias_block:
        for contents in re.findall(r'\[([^\[\]\r\n]+)\]', alias_block.group(1)):
            if '|' in contents:
                key, value = contents.split('|', 1)
            else:
                key, value = '别名', contents
            values.append((key.strip(), value.strip()))
    for key, value in re.findall(r'(?m)^\|([^=\r\n]+)=([^\r\n]*)', box):
        if re.search('英文|英语|罗马|ローマ', key) or key.strip() == '别名':
            values.append((key.strip(), value.strip()))
    return [(key, value.strip()) for key, value in values if value.strip()
        and value.strip().isascii() and not re.search(r'[\[\]{}|<>\\]', value)]


matches = collections.defaultdict(dict)
ja_subjects = {}
western_subjects = {}
total = 0
meta_country_count = collections.Counter()
with (ROOT / 'archive-subject.jsonlines').open() as stream:
    for line in stream:
        item = json.loads(line)
        total += 1
        meta = set(item.get('meta_tags', []))
        countries = meta & (WESTERN | OTHER_COUNTRIES | {'日本'})
        meta_country_count.update(countries)
        western = bool(meta & WESTERN)
        admin_japanese = '日本' in meta and not western and not meta & OTHER_COUNTRIES
        language_japanese = known_languages.get(item['id']) == 'ja' and not western and not meta & OTHER_COUNTRIES
        if admin_japanese or language_japanese:
            ja_subjects[item['id']] = {'id': item['id'], 'title': item['name'], 'lang': 'ja',
                'type': item['type'], 'origin_basis': 'curated_meta_tags' if admin_japanese else 'bangumi-data_language',
                'origin_meta_tags': sorted(countries)}
        if western:
            western_subjects[item['id']] = {'id': item['id'], 'name': item['name'], 'name_cn': item['name_cn'],
                'origin_meta_tags': sorted(countries), 'latin_aliases': latin_aliases(item)}
        # Subject types 1/2/4/6 are books, animation, games and live action; exclude music-only title collisions.
        if item['type'] not in (1, 2, 4, 6):
            continue
        aliases = [(key, value) for key, value in latin_aliases(item) if value.casefold() in inventory]
        if not aliases:
            continue
        label = item['name_cn'].strip()
        base = {'id': item['id'], 'name': item['name'], 'type': item['type'],
            'display_name': label if valid_zh(label) else None, 'origin_meta_tags': sorted(countries),
            'japanese': admin_japanese or language_japanese,
            'origin_basis': 'curated_meta_tags' if admin_japanese else 'bangumi-data_language' if language_japanese else None,
            'western': western}
        for key, value in aliases:
            matches[value.casefold()][item['id']] = dict(base, alias={'key': key, 'value': value})

candidates = {}
unresolved = {}
western_aliases = {}
for name, byid in matches.items():
    evidence = list(byid.values())
    labels = {item['display_name'] for item in evidence}
    if len(labels) == 1 and None not in labels and all(item['japanese'] for item in evidence):
        candidates[name] = {'display_name': next(iter(labels)), 'source': 'Bangumi Archive', 'evidence': evidence}
    else:
        unresolved[name] = evidence
    if any(item['western'] for item in evidence):
        western_aliases[name] = evidence

for name, value in [('archive-safe-parody-candidates.json', candidates),
                    ('archive-unresolved-parody-matches.json', unresolved),
                    ('archive-japanese-subjects.json', ja_subjects),
                    ('archive-western-subjects.json', western_subjects),
                    ('archive-western-parody-aliases.json', western_aliases)]:
    (ROOT / name).write_text(json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + '\n')
eh = json.loads((ROOT / 'parody-matched.json').read_text())
crosswalk = json.loads((ROOT / 'crosswalk-safe-parody-candidates.json').read_text())
direct = json.loads((ROOT / 'bangumi-safe-parody-candidates.json').read_text())
summary = {'source_subjects': total, 'curated_origin_counts': dict(meta_country_count),
    'known_japanese_subjects': len(ja_subjects), 'curated_western_subjects': len(western_subjects),
    'exact_alias_inventory_matches': len(matches), 'safe_names': len(candidates),
    'safe_names_not_in_eh': len(set(candidates) - eh.keys()),
    'safe_names_not_in_eh_or_other_title_sources': len(set(candidates) - eh.keys() - crosswalk.keys() - direct.keys()),
    'origin_limit': 'Japanese production/country evidence still requires Western original-IP overrides.'}
(ROOT / 'archive-subject-crosswalk-summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2) + '\n')
print(json.dumps(summary, ensure_ascii=False, indent=2))
