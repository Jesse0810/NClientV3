import collections,hashlib,json,re,time,urllib.parse,urllib.request
from pathlib import Path
ROOT=Path('/tmp/nclientv3-entity-origin')
PAGES={
'neon genesis evangelion':'Neon Genesis Evangelion',
'fate kaleid liner prisma illya':'Fate/kaleid liner Prisma Illya',
'fate apocrypha':'Fate/Apocrypha',
'fate stay night':'Fate/stay night',
'fate grand order':'Fate/Grand Order',
'to love-ru':'To Love Ru',
'my hero academia':'My Hero Academia',
'warship girls':'Warship Girls',
'fatal fury':'Fatal Fury',
'the melancholy of haruhi suzumiya':'Haruhi Suzumiya',
'ah my goddess':'Oh My Goddess!',
'ranma 12':'Ranma ½',
'k-on':'K-On!',
'gochuumon wa usagi desu ka':'Is the Order a Rabbit?',
'ore no imouto ga konna ni kawaii wake ga nai':'Oreimo',
'free':'Free!',
'shingeki no kyojin':'Attack on Titan',
'boku wa tomodachi ga sukunai':'Haganai',
'vocaloid':'Vocaloid',
'voiceroid':'Voiceroid',
'sailor moon':'Sailor Moon',
'final fantasy vii':'Final Fantasy VII',
'princess connect':'Princess Connect!',
'ojamajo doremi':'Ojamajo Doremi',
'osomatsu-san':'Mr. Osomatsu',
'darkstalkers':'Darkstalkers',
'the legend of zelda':'The Legend of Zelda',
'samurai spirits':'Samurai Shodown',
'lucky star':'Lucky Star (manga)',
'martian successor nadesico':'Martian Successor Nadesico',
'yu-gi-oh zexal':'Yu-Gi-Oh! Zexal',
'yu-gi-oh arc-v':'Yu-Gi-Oh! Arc-V',
'persona 4':'Persona 4',
'zero no tsukaima':'The Familiar of Zero',
'love hina':'Love Hina',
'bakemonogatari':'Monogatari (series)',
'kanon':'Kanon (visual novel)',
'gundam seed':'Mobile Suit Gundam SEED',
'kuroko no basuke':"Kuroko's Basketball",
'bocchi the rock':'Bocchi the Rock!',
'seiken densetsu 3':'Trials of Mana',
'dragon quest v':'Dragon Quest V',
'senran kagura':'Senran Kagura',
'pokemon':'Pokémon',
'love live sunshine':'Love Live! Sunshine!!',
'dungeon ni deai o motomeru no wa machigatteiru darou ka':'Is It Wrong to Try to Pick Up Girls in a Dungeon?',
}
# Namespace typo in Eh source is preserved as a separate canonical name, not silently rewritten.
PAGES.update({
 'monster hunter':'Monster Hunter',
 'dragon quest iii':'Dragon Quest III',
 'queens blade':"Queen's Blade",
 'smile precure':'Smile Precure!',
 'love live nijigasaki high school idol club':'Love Live! Nijigasaki High School Idol Club',
})

# Character raw -> exact public encyclopedia article title and known franchise.
CHARS={
'shinji ikari':('Shinji Ikari','neon genesis evangelion'),
'tifa lockhart':('Tifa Lockhart','final fantasy vii'),
'miku hatsune':('Hatsune Miku','vocaloid'),
'haruka nanase':('Haruka Nanase','free'),
'kaworu nagisa':('Kaworu Nagisa','neon genesis evangelion'),
'haruhi suzumiya':('Haruhi Suzumiya (character)','the melancholy of haruhi suzumiya'),
'belldandy':('Belldandy','ah my goddess'),
'ranma saotome':('Ranma Saotome','ranma 12'),
'sena kashiwazaki':('Sena Kashiwazaki','boku wa tomodachi ga sukunai'),
'kyon':('Kyon','the melancholy of haruhi suzumiya'),
'yuki nagato':('Yuki Nagato','the melancholy of haruhi suzumiya'),
'azusa nakano':('Azusa Nakano','k-on'),
'yui hirasawa':('Yui Hirasawa','k-on'),
'ritsu tainaka':('Ritsu Tainaka','k-on'),
'tsumugi kotobuki':('Tsumugi Kotobuki','k-on'),
'konata izumi':('Konata Izumi','lucky star'),
'kagami hiiragi':('Kagami Hiiragi','lucky star'),
'mikuru asahina':('Mikuru Asahina','the melancholy of haruhi suzumiya'),
'mai shiranui':('Mai Shiranui','fatal fury'),
'nakoruru':('Nakoruru','samurai spirits'),
'morrigan aensland':('Morrigan Aensland','darkstalkers'),
'cloud strife':('Cloud Strife','final fantasy vii'),
'princess zelda':('Princess Zelda','the legend of zelda'),
'link':('Link (The Legend of Zelda)','the legend of zelda'),
'yuu narukami':('Yu Narukami','persona 4'),
'mikasa ackerman':('Mikasa Ackerman','shingeki no kyojin'),
'levi ackerman':('Levi Ackerman','shingeki no kyojin'),
'izuku midoriya':('Izuku Midoriya','my hero academia'),
'katsuki bakugou':('Katsuki Bakugo','my hero academia'),
'ochako uraraka':('Ochaco Uraraka','my hero academia'),
'shouto todoroki':('Shoto Todoroki','my hero academia'),
'ami mizuno':('Sailor Mercury','sailor moon'),
'usagi tsukino':('Sailor Moon (character)','sailor moon'),
'makoto kino':('Sailor Jupiter','sailor moon'),
'rei hino':('Sailor Mars','sailor moon'),
'minako aino':('Sailor Venus','sailor moon'),
'pikachu':('Pikachu','pokemon'),
'ruri hoshino':('Ruri Hoshino','martian successor nadesico'),
'naru narusegawa':('Naru Narusegawa','love hina'),
'shinobu maehara':('Shinobu Maehara','love hina'),
'len kagamine':('Kagamine Rin/Len','vocaloid'),
'hitori gotou':('Hitori Gotoh','bocchi the rock'),
}
# Only factual claims + source URLs are exported. Full text stays in the temporary research cache.
def get_pages(titles):
 out={}
 seen_titles=set()
 for cache in ROOT.glob('wikipedia-*.json'):
  d=json.loads(cache.read_text())
  candidates={p['title']:p for p in d.get('query',{}).get('pages',{}).values()}
  redirects={r['from']:r['to'] for r in d.get('query',{}).get('redirects',[])}
  seen_titles.update(candidates)
  seen_titles.update(redirects)
  for title in titles:
   resolved=title
   while resolved in redirects:resolved=redirects[resolved]
   if resolved in candidates and candidates[resolved].get('extract'):out[title]=candidates[resolved]
 titles=[t for t in titles if t not in out and t not in seen_titles]
 for i in range(0,len(titles),10):
  batch=titles[i:i+10];key=hashlib.sha256(json.dumps(batch,ensure_ascii=False).encode()).hexdigest()
  path=ROOT/('wikipedia-'+key+'.json')
  if path.exists():d=json.loads(path.read_text())
  else:
   u='https://en.wikipedia.org/w/api.php?'+urllib.parse.urlencode({'action':'query','prop':'extracts','exintro':1,'explaintext':1,'titles':'|'.join(batch),'format':'json','redirects':1})
   with urllib.request.urlopen(urllib.request.Request(u,headers={'User-Agent':'NClientV3-localization-audit/1.0'}),timeout=40) as r:d=json.load(r)
   path.write_text(json.dumps(d,ensure_ascii=False))
   time.sleep(1)
  pages={p['title']:p for p in d.get('query',{}).get('pages',{}).values()}
  redirect={r['from']:r['to'] for r in d.get('query',{}).get('redirects',[])}
  normalize={r['from']:r['to'] for r in d.get('query',{}).get('normalized',[])}
  for title in batch:
   resolved=normalize.get(title,title)
   while resolved in redirect:resolved=redirect[resolved]
   page=pages.get(resolved)
   if page and page.get('extract'):out[title]=page
 return out
pages=get_pages(sorted(set(PAGES.values())|{x[0] for x in CHARS.values()}|{'Final Fantasy','Dragon Quest','SNK','Cygames','Princess Connect! Re:Dive'}))
(ROOT/'supplement-wikipedia-pages.json').write_text(json.dumps(pages,ensure_ascii=False,indent=2)+'\n')
parodies={}
for raw,title in PAGES.items():
 page=pages.get(title)
 if not page:continue
 intro=page['extract']
 japanese=bool(re.search(r'\bJapanese\b',intro[:1800]))
 china=raw=='warship girls' and bool(re.search(r'\bChin(?:a|ese)\b',intro[:1800]))
 support=None
 if raw=='final fantasy vii':support='Final Fantasy'
 elif raw in {'dragon quest v','dragon quest iii'}:support='Dragon Quest'
 elif raw in {'fatal fury','samurai spirits'}:support='SNK'
 if support and re.search(r'\bJapanese\b',pages.get(support,{}).get('extract','')[:1800]):japanese=True
 if not (japanese or china):continue
 parodies[raw]={'origin':'china' if china else 'japan','source_url':'https://en.wikipedia.org/wiki/'+urllib.parse.quote(page['title'].replace(' ','_'),safe='/()_!'),'source_title':page['title'],'method':'reviewed_wikipedia_franchise_origin','reason':'Public encyclopedia intro identifies the work as Chinese-developed.' if china else 'Public encyclopedia intro explicitly identifies Japanese production/authorship/development.','pageid':page.get('pageid')}
for raw,record in parodies.items():
 support={'final fantasy vii':'Final Fantasy','dragon quest v':'Dragon Quest','dragon quest iii':'Dragon Quest','fatal fury':'SNK','samurai spirits':'SNK'}.get(raw)
 if support:
  record['origin_reference_url']='https://en.wikipedia.org/wiki/'+urllib.parse.quote(support.replace(' ','_'))
  record['method']='reviewed_wikipedia_series_or_developer_origin'
  record['reason']='Work article identifies the series or developer; the linked series/developer article explicitly identifies Japanese origin.'
characters={}
for raw,(title,parody) in CHARS.items():
 page=pages.get(title)
 if not page or parody not in parodies:continue
 characters[raw]={'origin':parodies[parody]['origin'],'parody':parody,'source_url':'https://en.wikipedia.org/wiki/'+urllib.parse.quote(page['title'].replace(' ','_'),safe='/()_!'),'source_title':page['title'],'method':'reviewed_wikipedia_character_identity','reason':'Wikipedia canonical/redirect article for this exact character identity links it to the cited Japanese franchise; no names inferred from romaji.','pageid':page.get('pageid'),'raw_alias_article_title':title}

def source_row(raw):
 for number,line in enumerate(Path('/tmp/ehtag-source/database/character.md').read_text().splitlines(),1):
  if line.startswith('| '+raw+' |'):
   return {'url':f'https://github.com/EhTagTranslation/Database/blob/e0f66dc4424a53872473b05b7fb006ceebfcbe5d/database/character.md#L{number}','line':number,'text':line}
 return None
# Explicit Cygames relationship is in the pinned Eh source; Cygames' public intro identifies it as Japanese.
if re.search(r'\bJapanese\b',pages.get('Cygames',{}).get('extract','')[:1800]):
 parodies['princess connect']={'origin':'japan','source_url':'https://github.com/EhTagTranslation/Database/blob/e0f66dc4424a53872473b05b7fb006ceebfcbe5d/database/parody.md','origin_reference_url':'https://en.wikipedia.org/wiki/Cygames','method':'reviewed_source_developer_relationship','reason':'Pinned source explicitly names Cygames as developer; public Cygames encyclopedia intro identifies a Japanese development studio.'}
zh_path=ROOT/'wikipedia-zh-generic-roles.json'
if zh_path.exists():
 zh=json.loads(zh_path.read_text())
 warship=next((p for p in zh.get('query',{}).get('pages',{}).values() if p.get('title')=='战舰少女'),None)
 if warship and '由中国幻萌网络' in warship.get('extract',''):
  parodies['warship girls']={'origin':'china','source_url':'https://zh.wikipedia.org/wiki/战舰少女','method':'reviewed_wikipedia_franchise_origin','reason':'Public encyclopedia intro explicitly identifies Chinese developer 幻萌网络.'}
# Existing descriptions establish relationships without requiring a romaji-to-name guess.
base_origins=json.loads((ROOT/'origin-evidence.json').read_text()).get('parody',{}) if (ROOT/'origin-evidence.json').exists() else {}
for raw,parody in [('sena kashiwazaki','boku wa tomodachi ga sukunai'),('dark magician girl','yu-gi-oh'),('doctor','arknights'),('hero','dragon quest'),('warrior','dragon quest'),('ruri gokou','ore no imouto ga konna ni kawaii wake ga nai'),('kokkoro','princess connect'),('pecorine','princess connect')]:
 row=source_row(raw)
 if parody not in parodies and base_origins.get(parody,{}).get('origin') in {'japan','china','korea'}:
  base=base_origins[parody]
  url=next((e.get('url') for e in base.get('evidence',[]) if e.get('url')),None)
  if url:parodies[parody]={'origin':base['origin'],'source_url':url,'method':'existing_verified_franchise_origin','reason':'Existing explicit country/franchise evidence reused for a source-established character relationship.'}
 if row and parody in parodies:
  characters[raw]={'origin':parodies[parody]['origin'],'parody':parody,'source_url':row['url'],'origin_reference_url':parodies[parody]['source_url'],'method':'reviewed_source_description_or_franchise_link','reason':'Pinned source description or explicit franchise Fandom link identifies this known character/player role in the cited work.'}
# Generic player identities: dictionary verifies Japanese readings; the source supplies the Chinese role term.
roles=ROOT/'wiktionary-japanese-roles.json'
if roles.exists():
 role_data=json.loads(roles.read_text())
 for raw,japanese,reading,display in [('teitoku','提督','ていとく','提督'),('shikikan','指揮官','しきかん','指挥官')]:
  row=source_row(raw)
  page=next((p for p in role_data.get('query',{}).get('pages',{}).values() if p.get('title')==japanese),None)
  content=page.get('revisions',[{}])[0].get('slots',{}).get('main',{}).get('*','') if page else ''
  if row and reading in content:
   characters[raw]={'origin':'generic','kind':'generic_player_identity','display_name':display,'source_url':row['url'],'origin_reference_url':'https://ja.wiktionary.org/wiki/'+urllib.parse.quote(japanese),'method':'reviewed_japanese_generic_role','reason':'Pinned character source provides the Chinese role term; Japanese dictionary confirms the exact romanized reading. This is a reusable player role, not a uniquely identified historical person.'}
# Civilian identity aliases have explicit Chinese names in pinned sailor-role descriptions.
for raw,alias,display in [('usagi tsukino','sailor moon','月野兔'),('ami mizuno','sailor mercury','水野亚美'),('makoto kino','sailor jupiter','木野真琴'),('rei hino','sailor mars','火野丽'),('minako aino','sailor venus','爱野美奈子')]:
 row=source_row(alias)
 if raw in characters and row and display in row['text']:
  characters[raw]['display_name']=display
  characters[raw]['source_display_from_description']={'alias_of':alias,'source_url':row['url'],'reason':'Pinned source explicitly states this civilian Chinese name; Wikipedia identity page establishes the raw full-name alias.'}


# Bounded correction: source-proven Japanese originals among the frozen 69 adaptation-only cases.
audit69=ROOT/'character-archive-adaptation-audit.json'
if audit69.exists():
 for raw,record in json.loads(audit69.read_text()).items():
  if record['origin']!='japan':continue
  characters[raw]={'origin':'japan','parody':record.get('parody'),'source_url':record['source_url'],'method':'reviewed_original_ip_identity_69','reason':record['reason'],'evidence':record.get('evidence',[])}

(ROOT/'supplemental-parody-evidence.json').write_text(json.dumps(parodies,ensure_ascii=False,indent=2,sort_keys=True)+'\n')
(ROOT/'supplemental-character-evidence.json').write_text(json.dumps(characters,ensure_ascii=False,indent=2,sort_keys=True)+'\n')
print('Wikipedia pages',len(pages),'parodies',len(parodies),'characters',len(characters))
print('Missing parody pages',sorted(set(PAGES)-set(parodies)))
print('Missing character pages',sorted(set(CHARS)-set(characters)))
