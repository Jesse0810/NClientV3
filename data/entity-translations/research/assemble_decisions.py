import json, re, hashlib, shutil
from collections import Counter
from pathlib import Path
from opencc import OpenCC

ROOT=Path(__file__).resolve().parents[3]
OUT=ROOT/'data/entity-translations'
SRC=Path('/tmp/nclientv3-entity-sources')
ORIGIN=Path('/tmp/nclientv3-entity-origin')
NATIVE=Path('/tmp/nclientv3-native-entities')
WEST=Path('/tmp/nclientv3-western-audit')
COMMIT='e0f66dc4424a53872473b05b7fb006ceebfcbe5d'
NS={'parody':1,'character':2,'artist':4,'group':5}
ALLOWED={'japan','china','korea','east_asia_mixed','generic','generic_east_asian_role'}
HAN=re.compile(r'[\u3400-\u9fff\uf900-\ufaff\U00020000-\U0002ffff\U00030000-\U000323af]')
KANA=re.compile(r'[\u3041-\u3096\u309d-\u309f\u30a1-\u30fa\u30fd-\u30ff\u31f0-\u31ff\uff66-\uff9d]')
cc=OpenCC('t2s')

def load(path, default=None):
    return json.loads(path.read_text()) if path.exists() else default

def write(path, value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2,sort_keys=True)+'\n')

def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()

def chinese(label):
    if not label or not HAN.search(label) or KANA.search(label):return None
    label=cc.convert(label).strip()
    if any(c in label for c in '<>\u200b') or any(ord(c)<32 for c in label):return None
    # Multiple different characters sharing one tag are not a single translated identity.
    if '|' in label:return None
    return label

def source_ref(ns,raw):return {'source':'EhTagTranslation','namespace':ns,'raw':raw}

def compact_native(e):
    if isinstance(e,dict):
        return {k:compact_native(v) for k,v in e.items() if k not in {'summary','description','links','community_label'}}
    if isinstance(e,list):return [compact_native(x) for x in e]
    return e

def public_manifest(value):
    if isinstance(value,dict):
        return {(Path(k).name if k.startswith('/tmp/') else k):public_manifest(v) for k,v in value.items()}
    if isinstance(value,list):return [public_manifest(v) for v in value]
    if isinstance(value,str) and value.startswith('/tmp/'):return Path(value).name
    return value

rows=load(ROOT/'data/tags.json')
names={ns:{x[1] for x in rows if x[3]==num} for ns,num in NS.items()}
origin=load(ORIGIN/'origin-evidence.json')
extra_west=load(WEST/'extra-western-parodies.json',{})
extra_char_west=load(WEST/'propagated-character-exclusions.json',{})
extra_char_west.update(load(WEST/'extra-western-characters.json',{}))
archive_west=load(SRC/'archive-western-parody-aliases.json',{})
supp_char=load(ORIGIN/'supplemental-character-evidence.json',{})
decisions={ns:{} for ns in NS}

for ns in ('parody','character'):
    eh=load(SRC/f'{ns}-matched.json')
    archive=load(SRC/f'archive-safe-{ns}-candidates.json',{})
    direct=load(SRC/'bangumi-safe-parody-candidates.json',{}) if ns=='parody' else {}
    cross=load(SRC/'crosswalk-safe-parody-candidates.json',{}) if ns=='parody' else {}
    for raw in sorted(names[ns]):
        entry=eh.get(raw)
        info=origin[ns].get(raw,{})
        orig=info.get('origin','unknown')
        evidence=[]
        if entry:evidence.append(source_ref(ns,raw))
        evidence.extend(info.get('evidence',[]))
        blocked=orig in {'western','mixed_western','conflicting_origin'}
        if ns=='parody' and raw in extra_west:
            blocked=True;orig=extra_west[raw]['origin'];evidence.extend(extra_west[raw].get('evidence',[]))
        # A Hollywood adaptation does not turn a Japanese original franchise Western.
        # Archive-only same-name country collisions remain unresolved instead.
        if ns=='parody' and raw in archive_west and orig not in ALLOWED:
            blocked=True;orig='unknown';evidence.append({'source':'Bangumi Archive','western_subjects':[x['id'] for x in archive_west[raw] if x.get('western')]})
        if ns=='character' and raw in extra_char_west:
            blocked=True;orig=extra_char_west[raw]['origin'];evidence.extend(extra_char_west[raw].get('evidence',[]))
        if blocked:
            decisions[ns][raw]={'action':'preserve','source':'EhTagTranslation' if entry else 'reviewed-origin','origin':orig,'reason':'western_or_mixed_origin_preserved' if orig in {'western','mixed_western','conflicting_origin'} else 'unverified_cross_source_origin','evidence':evidence}
            continue
        # Supplemental exact identity records may prove a civilian alias for an existing source tag.
        supplemental=supp_char.get(raw) if ns=='character' else None
        label=chinese(entry['label']) if entry else None
        source='EhTagTranslation' if entry else 'none'
        method='community_chinese_name_simplified'
        archive_mixed = raw in archive and any(item.get('western_subjects') for item in archive[raw]['evidence'])
        if orig not in ALLOWED and raw in archive and not archive_mixed:
            orig='japan'
            evidence.append({'source':'Bangumi Archive','identity':compact_native(archive[raw]['evidence'])})
        if supplemental and supplemental.get('origin') in ALLOWED:
            orig=supplemental['origin']
            evidence.append({'source':'reviewed-origin','identity':compact_native(supplemental)})
            if not label:
                related_raw=supplemental.get('community_character') or supplemental.get('source_character')
                if related_raw and related_raw in eh:
                    label=chinese(eh[related_raw]['label']);source='EhTagTranslation';method='verified_character_alias'
                    evidence.append(source_ref(ns,related_raw))
                if not label and supplemental.get('display_name'):
                    label=chinese(supplemental['display_name']);source='EhTagTranslation' if supplemental.get('source_display_from_description') else 'reviewed-origin';method='verified_character_name'
        if orig in ALLOWED:
            if not label:
                for candidate_source,candidate,reason in (
                    ('Bangumi Archive',archive.get(raw),'archive_exact_alias_and_japanese_subject'),
                    ('bangumi-data',direct.get(raw),'bangumi_exact_title'),
                    ('bangumi-data+anime-offline-database',cross.get(raw),'crosswalk_shared_provider_ids')):
                    if not candidate:continue
                    proposed=chinese(candidate.get('display_name'))
                    if proposed:
                        label=proposed;source=candidate_source;method=reason
                        evidence.append({'source':candidate_source,'identity':compact_native(candidate.get('evidence',[]))})
                        break
            if label:
                decisions[ns][raw]={'action':'map','display_name':label,'source':source,'origin':orig,'reason':method,'evidence':evidence}
                continue
        if entry or raw in archive or raw in direct or raw in cross or info.get('evidence'):
            reason='unverified_origin_or_identity' if orig not in ALLOWED else 'no_reliable_simplified_chinese_name'
            decisions[ns][raw]={'action':'preserve','source':'EhTagTranslation' if entry else 'reviewed-origin','origin':orig,'reason':reason,'evidence':evidence}

native_records=load(NATIVE/'native-records.json')
for row in native_records:
    ns,raw=row['namespace'],row['raw']
    assert ns in {'artist','group'} and raw in names[ns]
    source=row.get('source','none')
    if source=='unmapped':source='none'
    evidence=[]
    if row.get('source_url'):evidence.append({'source':'EhTagTranslation','namespace':ns,'raw':raw})
    if row.get('evidence'):evidence.append(compact_native(row['evidence']))
    is_mapped=row['status']=='mapped'
    reason=row.get('method') or row.get('reason') or 'unverified_native_name'
    if is_mapped and reason=='community_japanese_script_name' and not KANA.search(row['display']):
        is_mapped=False
        reason='kana_punctuation_is_not_native_evidence'
        evidence.append({'candidate':row['display'],'note':'Japanese punctuation alone does not prove an original native name; no independent original-name evidence was recorded.'})
    record={'action':'map' if is_mapped else 'preserve','source':source,'origin':'native_name' if is_mapped else 'unknown','reason':reason,'evidence':evidence}
    if is_mapped:
        record['display_name']=row['display'] # Byte-for-byte native spelling; NEVER OpenCC.
        if not evidence:record['evidence']=[source_ref(ns,raw)]
    if is_mapped or source!='none' or reason!='no_exact_namespace_source_match':decisions[ns][raw]=record

overrides=load(OUT/'reviewed-overrides.json',{})
for ns,entries in overrides.items():
    assert ns in NS and set(entries)<=names[ns]
    for raw,record in entries.items():
        assert decisions[ns].get(raw,{}).get('origin') not in {'western','mixed_western','conflicting_origin'}
        decisions[ns][raw]=record

OUT.mkdir(parents=True,exist_ok=True)
write(OUT/'mapping-decisions.json',decisions)
source_data={
 'sources':{
  'EhTagTranslation':{'repository':'https://github.com/EhTagTranslation/Database','commit':COMMIT,'archive_sha256':'0e9bce61c735fde92e4b905eaa2bba14311d6e5814772604221d57766f65381c','license':'CC BY-NC-SA 3.0 China Mainland','row_lookup':'database/{namespace}.md exact raw tag column'},
  'reviewed-origin':{'description':'Country/identity evidence from public Wikidata claims, exact franchise identity review and cited public encyclopedia pages; never infers kanji from romanized names.','license':'Wikidata claims CC0; third-party names/facts retain their original terms','wikidata':load(ORIGIN/'wikidata-source.json',{})},
  'bangumi-data':load(SRC/'title-sources.json')['bangumi-data'],
  'bangumi-data+anime-offline-database':{'labels_from':'bangumi-data titleTranslate.zh-Hans','matching_source':load(SRC/'title-sources.json')['anime-offline-database'],'license':'Chinese labels CC BY 4.0; separate alias/provider crosswalk ODbL-1.0 + DbCL-1.0'},
  'Bangumi Archive':{'repository':'https://github.com/bangumi/Archive','copyright_url':'https://bgm.tv/about/copyright','license':'CC BY-SA 3.0','members':{x:load(SRC/f'archive-{x}.jsonlines.source.json') for x in ('character','person','subject','subject-characters')}},
 },
 'normalization':{'parody_character':'OpenCC t2s, opencc-python-reimplemented 0.1.7; no kana accepted as a Chinese label','artist_group':'No script conversion; original native spelling retained'},
 'post_merge_guards':{'community_native_names':'Require actual kana script, not just the middle dot or prolonged-sound punctuation. Unverified pure-Han/Latin names retain their canonical value.','filtered_name_counts':{ns:sum(x['reason']=='kana_punctuation_is_not_native_evidence' for x in decisions[ns].values()) for ns in ('artist','group')}},
 'scope':{'western':'Preserve original, including mixed Western/Japanese source identities','unresolved':'Preserve original and record why','runtime':'Offline, Simplified Chinese only; canonical IDs/values unchanged'},
 'input_hashes':{'native_records':digest(NATIVE/'native-records.json'),'origins':digest(ORIGIN/'origin-evidence.json'),'western_overrides':digest(WEST/'extra-western-parodies.json')},
}
source_names={r['source'] for group in decisions.values() for r in group.values()}-{'none'}
for source in source_names-set(source_data['sources']):
    source_data['sources'][source]={'description':'Cross-checked Japanese original names; see exact per-entry identity evidence and separate original source notices.','sources':source.split('+'),'license':'Source subsets retain original licenses; Danbooru artist metadata has no explicit open license; factual names and record identifiers only.'}
if (NATIVE/'native-manifest.json').exists():source_data['native_source_manifest']=public_manifest(load(NATIVE/'native-manifest.json'))
write(OUT/'sources.json',source_data)
print('Decision counts', {ns:dict(Counter(x['action'] for x in values.values())) for ns,values in decisions.items()})
print('Sources',sorted(source_names))
