"""Reproducible origin classification from pinned Eh + cached public Wikidata + pinned Bangumi.
No labels generated or translated. Only exact normalized Wikipedia sitelinks / canonical references.
"""
import collections,hashlib,json,re,unicodedata,urllib.parse
from pathlib import Path
ROOT=Path('/tmp/nclientv3-entity-origin')
REPO=Path(__file__).resolve().parents[3]
EH=Path('/tmp/ehtag-source/database')
EH_PIN='e0f66dc4424a53872473b05b7fb006ceebfcbe5d'
BG=Path('/tmp/nclientv3-entity-sources/bangumi-safe-parody-candidates.json')
def norm(s):return unicodedata.normalize('NFC',s.replace('_',' ').strip()).casefold()
def plain(s):
 s=re.sub(r'!\[[^\]]*\]\([^)]*\)','',s)
 s=re.sub(r'\[([^\]]*)\]\([^)]*\)',r'\1',s)
 s=re.sub(r'[\U0001f000-\U0001faff\ufe0e\ufe0f\u200d]','',s)
 return s.strip()
def entries(ns):
 out={};section=None
 for num,line in enumerate((EH/(ns+'.md')).read_text().splitlines(),1):
  if not line.startswith('|'):continue
  cols=re.split(r'(?<!\\)\|',line)
  if len(cols)<6:continue
  raw,label,desc,links=(c.strip().replace(r'\|','|') for c in cols[1:5])
  if not raw:
   if label.startswith(('▼','↓')):section=plain(label[1:])
   elif label.startswith(('▲','↑')):section=None
   continue
  if raw=='原始标签' or raw.startswith('--'):continue
  out[raw]={'name':raw,'label':plain(label),'description':desc,'links':links,'section':section,'line':num,'url':f'https://github.com/EhTagTranslation/Database/blob/{EH_PIN}/database/{ns}.md#L{num}'}
 return out
parodies=entries('parody');characters=entries('character')
entities=json.loads((ROOT/'wikidata-origins-compact.json').read_text())
lookup={}
for q,e in entities.items():
 for site,link in e.get('sitelinks',{}).items():
  if site in ('zhwiki','enwiki'):lookup[(site,norm(link['title']))]=e
# Americas + Europe + English-speaking Oceania. Unknown countries never inferred western.
WEST={'Q30','Q145','Q16','Q142','Q183','Q38','Q29','Q34','Q20','Q35','Q33','Q55','Q31','Q39','Q40','Q27','Q36','Q213','Q214','Q218','Q28','Q45','Q41','Q219','Q403','Q224','Q215','Q184','Q212','Q159','Q408','Q664','Q155','Q414','Q96','Q298','Q739','Q736','Q419','Q717','Q750','Q77','Q241','Q790','Q800','Q804','Q792','Q778','Q783','Q786','Q811','Q734','Q774','Q242','Q244','Q754','Q810','Q784','Q757','Q769'}
EAST={'Q17':'japan','Q148':'china','Q865':'china','Q8646':'china','Q884':'korea','Q423':'korea'}
ALLOW={'japan','china','korea','east_asia_mixed','generic'}
# Explicit reviewed well-known franchise families. Exact matches only; no substring lineage guessing.
MANUAL={
 'western':{
 'league of legends':'https://en.wikipedia.org/wiki/League_of_Legends',
 'overwatch':'https://en.wikipedia.org/wiki/Overwatch',
 'world of warcraft':'https://en.wikipedia.org/wiki/World_of_Warcraft',
 'warcraft':'https://en.wikipedia.org/wiki/Warcraft',
 'starcraft':'https://en.wikipedia.org/wiki/StarCraft',
 'diablo':'https://en.wikipedia.org/wiki/Diablo_(series)',
 'rwby':'https://en.wikipedia.org/wiki/RWBY',
 'my little pony friendship is magic':'https://en.wikipedia.org/wiki/My_Little_Pony:_Friendship_Is_Magic',
 'equestria girls':'https://en.wikipedia.org/wiki/My_Little_Pony:_Equestria_Girls',
 'frozen':'https://en.wikipedia.org/wiki/Frozen_(2013_film)',
 'the powerpuff girls':'https://en.wikipedia.org/wiki/The_Powerpuff_Girls',
 'spongebob squarepants':'https://en.wikipedia.org/wiki/SpongeBob_SquarePants',
 'avatar the last airbender':'https://en.wikipedia.org/wiki/Avatar:_The_Last_Airbender',
 'avatar the legend of korra':'https://en.wikipedia.org/wiki/The_Legend_of_Korra',
 'the legend of korra':'https://en.wikipedia.org/wiki/The_Legend_of_Korra',
 'adventure time':'https://en.wikipedia.org/wiki/Adventure_Time',
 'gravity falls':'https://en.wikipedia.org/wiki/Gravity_Falls',
 'steven universe':'https://en.wikipedia.org/wiki/Steven_Universe',
 'the owl house':'https://en.wikipedia.org/wiki/The_Owl_House',
 'amphibia':'https://en.wikipedia.org/wiki/Amphibia_(TV_series)',
 'kim possible':'https://en.wikipedia.org/wiki/Kim_Possible',
 'phineas and ferb':'https://en.wikipedia.org/wiki/Phineas_and_Ferb',
 'rick and morty':'https://en.wikipedia.org/wiki/Rick_and_Morty',
 'helluva boss':'https://en.wikipedia.org/wiki/Helluva_Boss',
 'hazbin hotel':'https://en.wikipedia.org/wiki/Hazbin_Hotel',
 'zootopia':'https://en.wikipedia.org/wiki/Zootopia',
 'the incredibles':'https://en.wikipedia.org/wiki/The_Incredibles',
 'the mighty thor':'https://en.wikipedia.org/wiki/Thor_(Marvel_Comics)',
 'marvel':'https://en.wikipedia.org/wiki/Marvel_Comics',
 'dc comics':'https://en.wikipedia.org/wiki/DC_Comics',
 'batman':'https://en.wikipedia.org/wiki/Batman',
 'superman':'https://en.wikipedia.org/wiki/Superman',
 'spider-man':'https://en.wikipedia.org/wiki/Spider-Man',
 'teen titans':'https://en.wikipedia.org/wiki/Teen_Titans',
 'teen titans go':'https://en.wikipedia.org/wiki/Teen_Titans_Go!',
 'x-men':'https://en.wikipedia.org/wiki/X-Men',
 'ben 10':'https://en.wikipedia.org/wiki/Ben_10',
 'miraculous ladybug':'https://en.wikipedia.org/wiki/Miraculous:_Tales_of_Ladybug_%26_Cat_Noir',
 'miraculous tales of ladybug and cat noir':'https://en.wikipedia.org/wiki/Miraculous:_Tales_of_Ladybug_%26_Cat_Noir',
 'danny phantom':'https://en.wikipedia.org/wiki/Danny_Phantom',
 'totally spies':'https://en.wikipedia.org/wiki/Totally_Spies!',
 'the simpsons':'https://en.wikipedia.org/wiki/The_Simpsons',
 'family guy':'https://en.wikipedia.org/wiki/Family_Guy',
 'futurama':'https://en.wikipedia.org/wiki/Futurama',
 'disenchantment':'https://en.wikipedia.org/wiki/Disenchantment_(TV_series)',
 'winx club':'https://en.wikipedia.org/wiki/Winx_Club',
 'witch':'https://en.wikipedia.org/wiki/W.I.T.C.H.',
 'scooby-doo':'https://en.wikipedia.org/wiki/Scooby-Doo',
 'scooby doo':'https://en.wikipedia.org/wiki/Scooby-Doo',
 'sonic the hedgehog':'https://en.wikipedia.org/wiki/Sonic_the_Hedgehog',
 },
 'japan':{
 'touhou project':'https://en.wikipedia.org/wiki/Touhou_Project',
 'kantai collection':'https://en.wikipedia.org/wiki/Kantai_Collection',
 'the idolmaster':'https://en.wikipedia.org/wiki/The_Idolmaster',
 'fate grand order':'https://en.wikipedia.org/wiki/Fate/Grand_Order',
 'fate stay night':'https://en.wikipedia.org/wiki/Fate/stay_night',
 'type-moon':'https://en.wikipedia.org/wiki/Type-Moon',
 'granblue fantasy':'https://en.wikipedia.org/wiki/Granblue_Fantasy',
 'pokemon':'https://en.wikipedia.org/wiki/Pok%C3%A9mon',
 'hololive':'https://en.wikipedia.org/wiki/Hololive_Production',
 'nijisanji':'https://en.wikipedia.org/wiki/Nijisanji',
 'love live':'https://en.wikipedia.org/wiki/Love_Live!',
 'gundam':'https://en.wikipedia.org/wiki/Gundam',
 'dragon ball':'https://en.wikipedia.org/wiki/Dragon_Ball',
 'naruto':'https://en.wikipedia.org/wiki/Naruto',
 'yu-gi-oh':'https://en.wikipedia.org/wiki/Yu-Gi-Oh!',
 'pretty cure':'https://en.wikipedia.org/wiki/Pretty_Cure',
 'powerpuff girls z':'https://en.wikipedia.org/wiki/Powerpuff_Girls_Z',
 'disney twisted-wonderland':'https://en.wikipedia.org/wiki/Disney_Twisted-Wonderland',
 'sword art online':'https://en.wikipedia.org/wiki/Sword_Art_Online',
 'fate zero':'https://en.wikipedia.org/wiki/Fate/Zero',
 'fire emblem':'https://en.wikipedia.org/wiki/Fire_Emblem',
 'street fighter':'https://en.wikipedia.org/wiki/Street_Fighter',
 'silent hill':'https://en.wikipedia.org/wiki/Silent_Hill',
 'sailor moon':'https://en.wikipedia.org/wiki/Sailor_Moon',
 'doraemon':'https://en.wikipedia.org/wiki/Doraemon',
 'bang dream':'https://en.wikipedia.org/wiki/BanG_Dream!',
 'toaru project':'https://en.wikipedia.org/wiki/A_Certain_Magical_Index',
 'dragon quest':'https://en.wikipedia.org/wiki/Dragon_Quest',
 'guilty gear':'https://en.wikipedia.org/wiki/Guilty_Gear',
 'final fantasy':'https://en.wikipedia.org/wiki/Final_Fantasy',
 'sonic the hedgehog':'https://en.wikipedia.org/wiki/Sonic_the_Hedgehog',
 },
 'china':{
 'genshin impact':'https://en.wikipedia.org/wiki/Genshin_Impact',
 'honkai star rail':'https://en.wikipedia.org/wiki/Honkai:_Star_Rail',
 'honkai gakuen':'https://en.wikipedia.org/wiki/Houkai_Gakuen_2',
 'honkai impact 3rd':'https://en.wikipedia.org/wiki/Honkai_Impact_3rd',
 'zenless zone zero':'https://en.wikipedia.org/wiki/Zenless_Zone_Zero',
 'azur lane':'https://en.wikipedia.org/wiki/Azur_Lane',
 'arknights':'https://en.wikipedia.org/wiki/Arknights',
 'girls frontline':'https://en.wikipedia.org/wiki/Girls%27_Frontline',
 'wuthering waves':'https://en.wikipedia.org/wiki/Wuthering_Waves',
 'identity v':'https://en.wikipedia.org/wiki/Identity_V',
 },
 'korea':{
 'blue archive':'https://en.wikipedia.org/wiki/Blue_Archive',
 'goddess of victory nikke':'https://en.wikipedia.org/wiki/Goddess_of_Victory:_Nikke',
 'ragnarok online':'https://en.wikipedia.org/wiki/Ragnarok_Online',
 'last origin':'https://en.wikipedia.org/wiki/Last_Origin',
 'destiny child':'https://en.wikipedia.org/wiki/Destiny_Child_(video_game)',
 }
}
# Sonic was accidentally considered western in some broad-pop-culture lists; Japanese game origin wins explicitly.
MANUAL['western'].pop('sonic the hedgehog')
# Original Japanese IPs retain origin despite later Hollywood adaptations or same-title films.
MANUAL['japan'].update({
 'one piece':'https://en.wikipedia.org/wiki/One_Piece',
 'all you need is kill':'https://en.wikipedia.org/wiki/All_You_Need_Is_Kill',
 'astro boy':'https://en.wikipedia.org/wiki/Astro_Boy',
 'city hunter':'https://en.wikipedia.org/wiki/City_Hunter',
 'cowboy bebop':'https://en.wikipedia.org/wiki/Cowboy_Bebop',
 'ghost in the shell':'https://en.wikipedia.org/wiki/Ghost_in_the_Shell',
 'godzilla':'https://en.wikipedia.org/wiki/Godzilla_(franchise)',
 'gravity rush':'https://en.wikipedia.org/wiki/Gravity_Rush',
 'made in abyss':'https://en.wikipedia.org/wiki/Made_in_Abyss',
 'speed racer':'https://en.wikipedia.org/wiki/Speed_Racer',
})

# Section headings are controlled by exact reviewed family identity, never by guessing a name's writing system.
SECTIONS={
 '东方project 人物':'japan','love live 人物':'japan','偶像大师-the idolm@ster 人物':'japan',
 '各种舰娘人物':'east_asia_mixed','rwby':'western','my little pony 角色':'western','飞天小女警':'western',
 'type-moon':'japan','光之美少女 系列':'japan','刀剑神域':'japan','龙珠系列':'japan',
 '火影忍者':'japan','魔卡少女樱':'japan','火焰之纹章':'japan','街头霸王':'japan','美少女战士':'japan',
 'bang dream':'japan','hololive':'japan','哆啦a梦':'japan','萌单 人物':'japan',
}
# Narrow description wording only: publication/development nationality, not story setting/author inspiration/distribution.
PATTERNS={
 'japan':r'(?:由日本[^。！!]{0,70}(?:开发|製作|制作|创作|創作|推出)|是日本[^。！!]{0,55}(?:游戏|游戏公司|漫画|动画|轻小说|制作|作品)|是一(?:款|部|种)日本(?:的)?(?:漫画|动画|游戏|轻小说)|日本漫画家[^。！!]{0,30}(?:创作|創作))',
 'china':r'(?:由中国[^。！!]{0,70}(?:开发|製作|制作|创作|創作|推出)|是中国[^。！!]{0,40}(?:游戏|漫画|动画)|中国大陆[^。！!]{0,20}(?:开发|制作))',
 'korea':r'(?:由韩国[^。！!]{0,70}(?:开发|製作|制作|创作|創作|推出)|是韩国[^。！!]{0,40}(?:游戏|漫画|动画)|是一(?:款|部)韩国(?:的)?(?:漫画|动画|游戏))',
 'western':r'(?:由(?:美国|英国|法国|加拿大|德国|芬兰|波兰|瑞典)[^。！!]{0,55}(?:公司|工作室|漫画家|作家|动画师|开发|制作|创作)|是一(?:款|部)(?:由)?(?:美国|英国|法国|加拿大|德国|芬兰|波兰|瑞典)[^。！!]{0,35}(?:动画|漫画|游戏|电影)|是(?:美国|英国|法国|加拿大|德国|芬兰|波兰|瑞典)[^。！!]{0,30}(?:漫画|动画|游戏))',
}
bangumi=json.loads(BG.read_text()) if BG.exists() else {}
crosswalk_path=Path('/tmp/nclientv3-entity-sources/crosswalk-safe-parody-candidates.json')
crosswalk=json.loads(crosswalk_path.read_text()) if crosswalk_path.exists() else {}
for name,record in crosswalk.items():bangumi.setdefault(name,record)
all_names=set(parodies)|set(bangumi)|{r[1] for r in json.loads((REPO/'data/tags.json').read_text()) if r[3]==1}
origins={}
for name in sorted(all_names):
 item=parodies.get(name);signals=[]
 for origin,reviewed in MANUAL.items():
  if name in reviewed:signals.append({'origin':origin,'method':'reviewed_franchise_origin','url':reviewed[name]})
 if item:
  for m in re.finditer(r'https?://(zh|en)\.wikipedia\.org/wiki/([^\s)<>]+)',item['links']):
   title=urllib.parse.unquote(m[2]).replace('_',' ').split('#')[0]
   entity=lookup.get((m[1]+'wiki',norm(title)))
   if not entity:continue
   countries=[c.get('mainsnak',{}).get('datavalue',{}).get('value',{}).get('id') for c in entity.get('claims',{}).get('P495',[])]
   countryorigins={EAST[q] if q in EAST else 'western' if q in WEST else 'unknown' for q in countries}
   for origin in countryorigins-{'unknown'}:signals.append({'origin':origin,'method':'wikidata_P495','entity':entity['id'],'countries':countries,'url':'https://www.wikidata.org/wiki/'+entity['id']})
  for origin,pattern in PATTERNS.items():
   match=re.search(pattern,item['description'])
   if match:signals.append({'origin':origin,'method':'source_origin_wording','url':item['url'],'match':match.group(0)[:120]})
 if name in bangumi:
  b=bangumi[name]
  signals.append({'origin':'japan','method':'bangumi_ja_title','url':'https://github.com/bangumi-data/bangumi-data/tree/'+b.get('source_commit','d5f56180c0c71fbeb24bd1914eb04c75f2f358fa'),'title':b['evidence'][0].get('title',b['evidence'][0].get('manami_title','')),'provider_joins':b['evidence'][0].get('provider_joins',[])})
 distinct={x['origin'] for x in signals}
 # Western anywhere always preserved, including mixed/cross-cultural collision.
 if 'western' in distinct:origin='western' if len(distinct)==1 else 'mixed_western'
 elif len(distinct)>1:origin='east_asia_mixed'
 elif distinct:origin=next(iter(distinct))
 else:origin='unknown'
 if name in ('original','various'):
  origin='generic';signals=[{'origin':'generic','method':'non_entity_control_value','url':item['url'] if item else ''}]
 origins[name]={'origin':origin,'translate_allowed':origin in ALLOW,'evidence':signals}

# Verified popular-series supplements are positive only; exclusions always take priority.
supplement_path=ROOT/'supplemental-parody-evidence.json'
if supplement_path.exists():
 for name,record in json.loads(supplement_path.read_text()).items():
  existing=origins.get(name,{'origin':'unknown','evidence':[]})
  if existing['origin'] in {'western','mixed_western','source_conflict'}:continue
  origin=record['origin']
  origins[name]={'origin':origin,'translate_allowed':origin in ALLOW,'evidence':existing['evidence']+[{'method':'verified_popular_franchise_supplement',**record}]}
# Whole-work Western review, including mixed production / source conflicts, overrides language hints.
western_path=Path('/tmp/nclientv3-western-audit/extra-western-parodies.json')
if western_path.exists():
 for name,record in json.loads(western_path.read_text()).items():
  existing=origins.get(name,{'evidence':[]})
  origins[name]={**record,'evidence':existing['evidence']+record['evidence']}
def strong_east_asian_origin(record):
 if record.get('origin') not in ALLOW:return False
 for evidence in record.get('evidence',[]):
  if evidence.get('origin') in {'japan','china','korea','east_asia_mixed'} and evidence.get('method')!='bangumi_ja_title':return True
  if evidence.get('method') in {'verified_popular_franchise_supplement','reviewed_wikipedia_franchise_origin','reviewed_wikipedia_series_or_developer_origin'}:return True
 return False

archive_west=Path('/tmp/nclientv3-entity-sources/archive-western-parody-aliases.json')
if archive_west.exists():
 for name,records in json.loads(archive_west.read_text()).items():
  explicit=[r for r in records if r.get('western')]
  if not explicit:continue
  existing=origins.get(name,{'origin':'unknown','evidence':[]})
  if existing['origin']=='source_conflict':continue
  # A film/TV adaptation or unrelated namesake never changes a proven original East-Asian franchise.
  if strong_east_asian_origin(existing):
   existing.setdefault('ignored_adaptation_or_namesake_witnesses',[]).extend([{'subject':r['id'],'origin_meta_tags':r.get('origin_meta_tags',[]),'matched_alias':r.get('alias')} for r in explicit])
   continue
  origin='western' if existing['origin'] in {'unknown','western'} else 'mixed_western'
  evidence=[{'method':'bangumi_archive_origin_meta_tags','subject':r['id'],'origin_meta_tags':r.get('origin_meta_tags',[]),'matched_alias':r.get('alias'),'url':'https://bgm.tv/subject/'+str(r['id'])} for r in explicit]
  origins[name]={'origin':origin,'translate_allowed':False,'evidence':existing['evidence']+evidence}

label_origins=collections.defaultdict(list)
for name,item in parodies.items():
 label_origins[norm(item['label'])].append(name)
character_results={}
for name,item in characters.items():
 refs=[]
 for ref in re.findall(r'`([^`]+)`',item['description']):
  ref=re.sub(r'^(?:p|parody):\s*','',ref.strip(),flags=re.I)
  if ref in origins and ref not in refs:refs.append(ref)
 evidence=[];signals=set()
 for ref in refs:
  record=origins[ref];signals.add(record['origin'])
  evidence.append({'method':'source_explicit_parody_reference','parody':ref,'origin':record['origin'],'url':item['url']})
 section=item['section']
 # Only use source section if there are no explicit reference relationships.
 if not refs and section:
  key=norm(section)
  section_origin=SECTIONS.get(key)
  if not section_origin:
   targets=label_origins.get(key,[])
   candidates={origins[t]['origin'] for t in targets if t in origins}
   if len(candidates)==1:section_origin=next(iter(candidates))
  if section_origin:
   signals.add(section_origin);evidence.append({'method':'source_reviewed_section','section':section,'origin':section_origin,'url':item['url']})
 # Direct western fandom links provide an exclusion even where series references were omitted.
 for domain in re.findall(r'https?://([^/\s)]+)',item['links']):
  if domain in {'disney.fandom.com','marvel.fandom.com','dc.fandom.com','overwatch.fandom.com','overwatch2.fandom.com','rwby.fandom.com','mlp.fandom.com','harrypotter.fandom.com'}:
   signals.add('western');evidence.append({'method':'western_fandom_namespace','domain':domain,'url':item['url']})
 if signals & {'western','mixed_western'}:origin='mixed_western' if len(signals)>1 else 'western'
 elif 'unknown' in signals:origin='unknown'
 elif len(signals)>1:origin='east_asia_mixed'
 elif signals:origin=next(iter(signals))
 else:origin='unknown'
 character_results[name]={'origin':origin,'translate_allowed':origin in ALLOW,'parody_references':refs,'evidence':evidence}

supplement_path=ROOT/'supplemental-character-evidence.json'
if supplement_path.exists():
 for name,record in json.loads(supplement_path.read_text()).items():
  existing=character_results.get(name,{'origin':'unknown','parody_references':[],'evidence':[]})
  if existing['origin'] in {'western','mixed_western','source_conflict'}:continue
  origin=record['origin']
  character_results[name]={'origin':origin,'translate_allowed':origin in ALLOW,'parody_references':existing['parody_references'] or ([record['parody']] if record.get('parody') else []),'evidence':existing['evidence']+[{'method':'verified_popular_character_supplement',**record}]}
# Explicit Western/mixed-character proof catches non-backtick prose and ambiguous canonical names.
western_chars=Path('/tmp/nclientv3-western-audit/extra-western-characters.json')
if western_chars.exists():
 for name,record in json.loads(western_chars.read_text()).items():
  existing=character_results.get(name,{'parody_references':[],'evidence':[]})
  character_results[name]={**record,'translate_allowed':False,'parody_references':existing['parody_references'],'evidence':existing['evidence']+record.get('evidence',[])}
archive_chars=Path('/tmp/nclientv3-entity-sources/archive-safe-character-candidates.json')
archive_exclusions={}
if archive_chars.exists():
 for name,candidate in json.loads(archive_chars.read_text()).items():
  witnesses=[(r,s) for r in candidate.get('evidence',[]) for s in r.get('western_subjects',[])]
  if not witnesses:continue
  existing=character_results.get(name,{'origin':'unknown','parody_references':[],'evidence':[]})
  has_strong_native=existing['origin'] in ALLOW and (any(e.get('method') in {'verified_popular_character_supplement','source_reviewed_section'} for e in existing['evidence']) or any(strong_east_asian_origin(origins.get(ref,{})) for ref in existing['parody_references']))
  if has_strong_native:
   existing.setdefault('ignored_adaptation_or_namesake_witnesses',[]).extend([{'character':r['id'],'subject':s['id'],'origin_meta_tags':s.get('origin_meta_tags',[])} for r,s in witnesses])
   continue
  # An adaptation relation establishes neither the character's original franchise nor its country.
  origin=existing['origin'] if existing['origin'] in {'western','mixed_western','source_conflict'} else 'unknown'
  evidence=[{'method':'bangumi_archive_related_western_subject','character':r['id'],'subject':s['id'],'origin_meta_tags':s.get('origin_meta_tags',[]),'url':'https://bgm.tv/character/'+str(r['id']),'subject_url':'https://bgm.tv/subject/'+str(s['id'])} for r,s in witnesses]
  record={'origin':origin,'translate_allowed':False,'parody_references':existing['parody_references'],'evidence':existing['evidence']+evidence,'reason':'preserve_original_until_original_ip_is_verified; adaptation_country_alone_is_not_origin'}
  character_results[name]=record;archive_exclusions[name]=record

# All 69 former adaptation-country-only cases are reviewed by original IP identity, never by film country.
audit69=ROOT/'character-archive-adaptation-audit.json'
if audit69.exists():
 reviewed=json.loads(audit69.read_text())
 frozen_inputs=json.loads((ROOT/'archive-only-character-review-input.json').read_text())
 if set(reviewed)!=set(frozen_inputs) or len(reviewed)!=69:raise ValueError('The bounded original-IP audit must cover exactly 69 frozen cases')
 for name,record in reviewed.items():
  existing=character_results.get(name,{'parody_references':[],'evidence':[]})
  origin=record['origin']
  original_proof={'method':'reviewed_original_ip_identity_69','origin':origin,'parody':record.get('parody'),'url':record.get('source_url'),'review_method':record['method'],'reason':record['reason']}
  character_results[name]={'origin':origin,'translate_allowed':origin=='japan','parody_references':([record['parody']] if record.get('parody') else existing['parody_references']),'evidence':[original_proof]+record.get('evidence',[]),'ignored_adaptation_or_namesake_witnesses':record['archive_adaptation_witnesses'],'review_scope':'frozen_69_archive_only_adaptation_country_cases'}
# Export exclusions after final original-IP adjudication; corrected Japanese originals are not exclusions.
archive_exclusions={name:character_results[name] for name in archive_exclusions if character_results[name]['origin'] not in ALLOW}

(ROOT/'archive-western-character-evidence.json').write_text(json.dumps(archive_exclusions,ensure_ascii=False,indent=2,sort_keys=True)+'\n')

for row in json.loads((REPO/'data/tags.json').read_text()):
 if row[3]==2 and row[1] not in character_results:
  character_results[row[1]]={'origin':'unknown','translate_allowed':False,'parody_references':[],'evidence':[],'reason':'no_exact_source_character_entry'}
result={'policy_version':1,'sources':{'EhTagTranslation':EH_PIN,'wikidata':'public country/sitelink snapshots collected 2026-11-12; source SHA-256 recorded in wikidata-source.json','bangumi':'d5f56180c0c71fbeb24bd1914eb04c75f2f358fa'},'parody':origins,'character':character_results}
(ROOT/'origin-evidence.json').write_text(json.dumps(result,ensure_ascii=False,indent=2,sort_keys=True)+'\n')
summary={ns:dict(collections.Counter(v['origin'] for v in result[ns].values())) for ns in ('parody','character')}
(ROOT/'summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n')
print(json.dumps(summary,ensure_ascii=False,indent=2))
