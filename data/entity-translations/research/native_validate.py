import collections,hashlib,json
from pathlib import Path
OUT=Path('/tmp/nclientv3-native-entities')
records=json.loads((OUT/'native-records.json').read_text())
maps=json.loads((OUT/'native-mappings.json').read_text())
lookup={(r['namespace'],r['raw']):r for r in records}
assert len(records)==58780 and len(lookup)==len(records)
for ns,total in [('artist',33371),('group',25409)]:
    rows=[r for r in records if r['namespace']==ns]
    assert len(rows)==total
    assert sum(r['status']=='mapped' for r in rows)==len(maps[ns])
    for r in rows:
        assert r['raw'] and r['display'] and r['ids']
        if r['status']=='mapped':
            assert maps[ns][r['raw']]==r['display']
            assert r.get('source') and r.get('evidence') and r.get('method')
            assert not any(v in r['display'] for v in ['推特','头像','社团','http','pixiv','|','\\'])
            if r['method']=='crosschecked_native_artist_alias':
                assert r['evidence']['matching_official_profile_urls'] or r['evidence']['exact_community_name_agreement']
                assert ns=='artist'
        else:assert r['display']==r['raw'] and r.get('reason')
for raw,expected in [('ishikei','石恵'),('miyasaka miyu','宮坂みゆ'),('miyasaka naco','宮坂なこ'),('takeda hiromitsu','武田弘光'),('shindol','新堂エル'),('muramasa mikado','村正みかど')]:assert maps['artist'][raw]==expected,(raw,maps['artist'].get(raw))
for raw,expected in [('nisemididoronokai','偽MIDI泥の会'),('august','オーガスト')]:assert maps['group'][raw]==expected
assert maps['group'].get('planet')!='株式会社プラネットエンターテイメント'
assert 'gesundheit' not in maps['artist']
assert all(x not in maps['group'] for x in ('clockup','sprite','tennenseki'))
paths=[OUT/'native-records.json',OUT/'native-mappings.json',OUT/'native-summary.json',OUT/'danbooru-artists.json',OUT/'danbooru-source.json',Path('/tmp/nclientv3-native-build.py'),Path('/tmp/nclientv3-native-fetch.py'),Path('/tmp/nclientv3-native-validate.py'),Path('/tmp/ehtag-source/database/artist.md'),Path('/tmp/ehtag-source/database/group.md'),Path('/tmp/nclientv3-entity-sources/archive-person.jsonlines')]
manifest={'snapshot_date':'2026-11-12','complete_inventory':{'artist':33371,'group':25409},'mapping_counts':{ns:len(items) for ns,items in maps.items()},'sources':[{'name':'EhTagTranslation','commit':'e0f66dc4424a53872473b05b7fb006ceebfcbe5d','license':'CC BY-NC-SA 3.0 CN','license_file':'/tmp/ehtag-source/LICENSE.md'},{'name':'Bangumi Archive','snapshot':'2026-11-10.210405Z','license':'CC BY-SA 3.0','license_evidence':'https://bgm.tv/about/copyright','source_metadata':'/tmp/nclientv3-entity-sources/archive-person.jsonlines.source.json'},{'name':'Danbooru public artist metadata','snapshot':'2026-11-12','license_note':'Only proper-name facts and source record identifiers/URLs are extracted. No explicit artist-metadata license was found; no open license is claimed. No media, wiki prose or descriptions copied.','attribution':'https://danbooru.donmai.us','source_metadata':str(OUT/'danbooru-source.json')}],'sha256':{str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in paths},'validation':'PASS full 58780 inventory partition; exact namespace keys; all mapped rows source/evidence; no markup/prose placeholders; representative original names, preserved pen-name aliases, and Western/native-Latin fallback.'}
(OUT/'native-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({'mapped':manifest['mapping_counts'],'records':len(records),'sha256':{p:sha for p,sha in manifest['sha256'].items() if p.endswith(('native-records.json','native-mappings.json','danbooru-artists.json'))},'validation':manifest['validation']},ensure_ascii=False,indent=2))
