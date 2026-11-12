import collections, hashlib, json, re, urllib.parse
from pathlib import Path

ROOT=Path(__file__).resolve().parents[3]
OUT=Path('/tmp/nclientv3-native-entities'); OUT.mkdir(exist_ok=True)
SOURCES=Path('/tmp/nclientv3-entity-sources')
PIN='e0f66dc4424a53872473b05b7fb006ceebfcbe5d'
KANA=re.compile(r'[\u3040-\u30ff]')
HAN=re.compile(r'[\u3400-\u9fff]')
HANGUL=re.compile(r'[\uac00-\ud7af\u1100-\u11ff]')
# Rejection-only: never substitute characters or infer name orthography.
CN_VARIANTS=set('宫广冈岛鸟龙樱泽滨泷苍仓岭亚风枫实雾鸣凤丽华东绿红绯纱绪绫织绵维缪岚铃银铁镜钝钟远边汤桥莲爱梦姬阳阴书图馆乐车马鱼贝师义贤齐斋长译蓝丛圣艺绘斩飞盐须栉晓错说饼喵绅轻贴涩贸产亲热烦结')
# Characters where a Chinese rendering can obscure a different Japanese name form.
JP_VARIANTS=set('惠櫻廣澤濱瀧龍齊齋國繪畫靜豐眞緖會與邊樂實壽')
# Some glyphs occur in actual Japanese names too. They remain usable when an explicit
# original-name field, Japanese article title, or crosschecked artist alias proves them.
RISK=CN_VARIANTS|JP_VARIANTS
WESTERN_NATIVE={'gesundheit':'Explicitly German creator; source katakana is a localization of Western handle, not native name.'}

def read(p):return json.loads(p.read_text())
def clean(value):
    return value.strip().replace('\\|','|')
def risk(value):return sorted(set(value)&RISK)
def name_ok(value,*,explicit=False):
    if not value or len(value)>40 or not (KANA.search(value) or HAN.search(value)):return False
    if HANGUL.search(value) or any(c in value for c in '|/\\<>[]{}@\n\r'):return False
    if re.search(r'https?:|pixiv|Twitter|FANBOX|依頼|仕事|募集|受付|新刊|委託|発売|販売|更新|配信|開催|連絡|通知|通販|コミケ|COMITIA|c10[0-9]|C10[0-9]|\d{4}[-/.]\d',value,re.I):return False
    if re.search(r'社团|画师|作者|原名|中文|同名|旧名|现名|兩位|两位|三位|头像|作品|推特|照片|图像|圖像|姐|个人|個人|avatar|icon|logo',value,re.I):return False
    if not explicit and risk(value):return False
    return True
def source_url(ns):return f'https://github.com/EhTagTranslation/Database/blob/{PIN}/database/{ns}.md'
def url_tokens(url):
    url=urllib.parse.unquote(url).rstrip(').,')
    try:u=urllib.parse.urlparse(url)
    except ValueError:return set()
    host=(u.hostname or '').lower().removeprefix('www.')
    path=u.path.rstrip('/')
    if host in ('x.com','twitter.com','mobile.twitter.com'):
        bits=path.split('/')
        if len(bits)>1 and bits[1] not in ('i','intent','share','search'):
            return {'x:'+bits[1].lower()}
        return set()
    if host in ('pixiv.net','touch.pixiv.net'):
        m=re.search(r'/users/(\d+)',path)
        if not m:m=re.search(r'(?:^|&)id=(\d+)',u.query)
        return {'pixiv:'+m.group(1)} if m else set()
    if host in ('zh.wikipedia.org','ja.wikipedia.org','zh.moegirl.org.cn','en.wikipedia.org'):return set()
    # Query IDs identify creators on shared catalog hosts. Dropping them would
    # collapse all melonbooks/erogamescape profiles into one false identity.
    suffix=('?'+urllib.parse.urlencode(sorted(urllib.parse.parse_qsl(u.query,keep_blank_values=True)))) if u.query else ''
    if not path and host in ('fanbox.cc','fantia.jp','skeb.jp','melonbooks.co.jp','dlsite.com','deviantart.com','bsky.app'):return set()
    return {host+path.lower()+suffix} if host else set()
def link_tokens(links):
    found=set()
    for url in re.findall(r'https?://[^\s<>\]"\)]+',links):found |= url_tokens(url)
    return found
def candidate_record(ns,raw,display,method,evidence,extra=None):
    result={'namespace':ns,'raw':raw,'display':display,'source':'EhTagTranslation','method':method,'evidence':evidence,'source_url':source_url(ns),'source_commit':PIN,'status':'mapped','origin_policy':'Japanese original-name evidence; no Chinese conversion or guessed kanji'}
    if extra:result.update(extra)
    return result

def roman_key(s):
    return re.sub(r'\s+',' ',s.replace('_',' ').strip()).casefold()

def bangumi_sources():
    path=SOURCES/'archive-person.jsonlines'
    indexes={'artist':collections.defaultdict(list),'group':collections.defaultdict(list)}
    url_indexes={'artist':collections.defaultdict(list),'group':collections.defaultdict(list)}
    circles=collections.defaultdict(list)
    if not path.exists():return indexes,url_indexes,circles
    for line in path.open():
        row=json.loads(line)
        text=str(row.get('infobox') or '').replace('\\r\\n','\n').replace('\\n','\n')
        aliases=[(m.group(1).strip(),m.group(2).strip()) for m in re.finditer(r'\[([^\[\]|\r\n]+)\|([^\[\]\r\n]*)\]',text)]
        fields={m.group(1).strip():m.group(2).strip() for m in re.finditer(r'(?m)^\|([^=\r\n]+)=([^\r\n]*)',text)}
        native=[v for k,v in aliases if k=='日文名' and name_ok(v,explicit=True) and not (set(v)&CN_VARIANTS)]
        primary=clean(row.get('name',''))
        if not native and name_ok(primary,explicit=True) and not (set(primary)&CN_VARIANTS):native=[primary]
        native=sorted(set(native))
        if len(native)!=1:continue
        native=native[0]
        career=set(row.get('career') or [])
        ns='artist' if row.get('type')==1 and career&{'mangaka','illustrator'} else 'group' if row.get('type') in (2,3) else None
        if ns:
            roman=[v for k,v in aliases if k in ('罗马字','英文名') and v and not any(c in v for c in '[]{}|')]
            # Do not transform field order, create romaji, or reuse Chinese aliases.
            info={'id':row['id'],'display':native,'primary_name':primary,'type':row.get('type'),'career':sorted(career),'source_url':f"https://bgm.tv/person/{row['id']}",'native_field':'日文名' if any(k=='日文名' and v==native for k,v in aliases) else 'name','romaji_fields':[(k,v) for k,v in aliases if k in ('罗马字','英文名') and v in roman]}
            for value in roman:indexes[ns][roman_key(value)].append(info)
            for token in link_tokens(text):url_indexes[ns][token].append(info)
            # Common structured profile notations omit full URLs.
            for key,value in fields.items():
                if key.casefold() in ('twitter','x') and re.fullmatch(r'@[A-Za-z0-9_]{1,30}',value):url_indexes[ns]['x:'+value[1:].lower()].append(info)
                if key.casefold()=='pixiv':
                    m=re.fullmatch(r'(?:id\s*=\s*)?(\d+)',value)
                    if m:url_indexes[ns]['pixiv:'+m.group(1)].append(info)
        # A person explicitly identifies their circle with a paired English key.
        # This maps only the circle namespace, never the person's own artist name.
        circle_jp=[v for k,v in aliases if k in ('サークル','社团名') and name_ok(v,explicit=True) and not (set(v)&CN_VARIANTS)]
        circle_en=[v for k,v in aliases if k in ('サークルen','社团英文名','社团罗马字') and v and not any(c in v for c in '[]{}|')]
        if len(set(circle_jp))==1:
            for value in circle_en:circles[roman_key(value)].append({'id':row['id'],'display':circle_jp[0],'primary_name':primary,'source_url':f"https://bgm.tv/person/{row['id']}",'native_field':'サークル','romaji_fields':[('サークルen',value)]})
    return indexes,url_indexes,circles

BGM_INDEX,BGM_URLS,BGM_CIRCLES=bangumi_sources()

def bangumi_native(ns,raw,entry,artist):
    candidates=list(BGM_INDEX[ns].get(roman_key(raw),[]))
    methods={c['id']:'explicit_romaji_field' for c in candidates}
    if ns=='group':
        for c in BGM_CIRCLES.get(roman_key(raw),[]):candidates.append(c);methods[c['id']]='paired_circle_original_and_roman_name'
    tokens=link_tokens(entry['links']) if entry else set()
    # Bangumi/Danbooru URL intersection is additional identity evidence. Without
    # an Eh namespace identity, a shared raw generic name alone cannot select it.
    if entry and artist:
        db_tokens=set()
        for item in artist.get('urls',[]):db_tokens |= url_tokens(item.get('url',''))
        if tokens & db_tokens:tokens |= db_tokens
    for token in tokens:
        for c in BGM_URLS[ns].get(token,[]):
            candidates.append(c);methods[c['id']]='official_profile_url_intersection'
    grouped={c['id']:c for c in candidates}
    # A short/generic handle is not enough to join two independent databases.
    # Prefer a full explicit roman name, profile URL, or identical native label.
    grouped={ident:c for ident,c in grouped.items() if methods[ident] in ('official_profile_url_intersection','paired_circle_original_and_roman_name') or ' ' in roman_key(raw) or (entry and c['display']==entry['label'])}
    displays={c['display'] for c in grouped.values()}
    if len(displays)!=1:return None,'ambiguous_bangumi_identity' if displays else None
    display=next(iter(displays))
    evidence=[]
    for c in grouped.values():
        evidence.append({**c,'identity_method':methods[c['id']]})
    return {'namespace':ns,'raw':raw,'display':display,'source':'Bangumi Archive','method':'bangumi_explicit_native_name','evidence':{'records':evidence,'archive_date':'2026-11-10','policy':'Only original name or explicit Japanese-name field; never simplified-Chinese-name field. Exact explicit roman field or corroborated official profile URL; namespace/type isolated.'},'source_url':evidence[0]['source_url'],'source_snapshot':'Bangumi Archive 2026-11-10','status':'mapped','origin_policy':'Source original-name field; no Simplified/Traditional or romaji-to-kanji conversion.'},None
def eh_native(ns,raw,entry):
    label=clean(entry['label']);desc=entry['description']; links=entry['links']
    if raw in WESTERN_NATIVE:return None,'western_native_handle'
    # A Japanese article path is explicit original-name evidence. It may contain
    # established old glyphs; those bytes are retained rather than converted.
    for url in re.findall(r'https?://ja\.wikipedia\.org/wiki/[^\s\)]+',links) if ns=='artist' else []:
        title=urllib.parse.unquote(url.split('/wiki/',1)[1]).replace('_',' ')
        title=re.sub(r'\s*[（(](?:漫画家|イラストレーター|ゲームクリエイター|曖昧さ回避)[)）]$','',title)
        if name_ok(title,explicit=True):
            return candidate_record(ns,raw,title,'explicit_japanese_article_title',{'japanese_article':url,'title':title}),'mapped'
    # An opening original name with its Japanese reading, not a translated birth
    # name or an arbitrary mention of another creator later in the description.
    m=re.match(r'^([\u3400-\u9fff\u3040-\u30ff \u3000]{2,25})[（(]([\u3040-\u30ff \u3000]{1,40})[)）]',desc)
    if m:
        label_name=re.sub(r'[ \u3000]','',m.group(1))
        if name_ok(label_name,explicit=True) and not (set(label_name)&CN_VARIANTS):
            return candidate_record(ns,raw,label_name,'explicit_name_with_japanese_reading',{'description_prefix':m.group(0),'reading':m.group(2)}),'mapped'
    # Avatar alt text in this exact creator row can preserve the actual Japanese
    # glyphs that the Chinese display label lost. Work-title alts are not used.
    for alt in re.findall(r'!\[([^\]]*)\]',desc):
        m=re.fullmatch(r'(.{1,30}?)(?:的)?(?:pixiv|X|Twitter|推特)?头像',alt,re.I)
        if m:
            original=clean(m.group(1))
            if name_ok(original,explicit=True) and not (set(original)&CN_VARIANTS):
                return candidate_record(ns,raw,original,'explicit_creator_avatar_alt',{'avatar_alt':alt}),'mapped'
    for pattern in (r'^(?:日语名|日文名|日语|日文原名)[:：]\s*([^<。；;\n]{1,30})',r'^原名([^<。；;\n]{1,20})$'):
        m=re.search(pattern,desc)
        if m:
            original=m.group(1).strip()
            original=re.sub(r'\s*[（(][\u3040-\u30ff \u3000]+[)）]$','',original)
            # 社 appended to a Chinese explanatory phrase is not part of a brand.
            if name_ok(original,explicit=True) and KANA.search(original) and not (set(original)&CN_VARIANTS):
                return candidate_record(ns,raw,original,'explicit_native_name_description',{'description_value':m.group(0)}),'mapped'
    if KANA.search(label) and name_ok(label):
        return candidate_record(ns,raw,label,'community_japanese_script_name',{'label':label,'qualification':'Community native-script name, not independently claimed official; no known Chinese/Japanese glyph-risk forms.'}),'mapped'
    if HANGUL.search(label):return None,'korean_name_not_required'
    if KANA.search(label) and risk(label):return None,'script_variant_or_chinese_glyph_risk'
    if HAN.search(label):return None,'han_name_without_explicit_original_orthography'
    if any(c in label for c in '|/'):return None,'multiple_aliases_or_ambiguous_name'
    return None,'no_japanese_original_in_community_label'

inventory=collections.defaultdict(dict)
for tid,name,count,type_id in read(ROOT/'data/tags.json'):
    if type_id not in (4,5):continue
    ns='artist' if type_id==4 else 'group'
    value=inventory[ns].setdefault(name,{'raw':name,'ids':[],'count':0})
    value['ids'].append(tid);value['count']+=count

all_maps={};all_records=[];counts={}
db_path=OUT/'danbooru-artists.json'
db=read(db_path) if db_path.exists() else {}
for ns in ('artist','group'):
    eh=read(SOURCES/(ns+'-matched.json'))
    result={};records=[];counter=collections.Counter()
    for raw,inv in sorted(inventory[ns].items()):
        entry=eh.get(raw)
        chosen,reason=eh_native(ns,raw,entry) if entry else (None,'no_exact_namespace_source_match')
        artist=db.get(raw.replace(' ','_'))
        extra_audit={}
        if artist and entry and raw not in WESTERN_NATIVE:
            tokens=link_tokens(entry['links'])
            db_tokens=set()
            for item in artist.get('urls',[]):db_tokens |= url_tokens(item.get('url',''))
            common=sorted(tokens & db_tokens)
            aliases=[]
            for alias in artist.get('other_names',[]):
                alias=alias.replace('_',' ')
                if name_ok(alias,explicit=True) and not (set(alias)&CN_VARIANTS):
                    aliases.append(alias)
            aliases=sorted(set(aliases))
            # Entity identity must be corroborated independently of romanization.
            # Shared name alone (e.g. sakura) is not sufficient across databases.
            agreeing=[a for a in aliases if a==entry['label'] or (chosen and a==chosen['display'])]
            extra_audit={'danbooru_id':artist['id'],'danbooru_identity_url_intersection':common,'danbooru_native_alias_candidates':aliases}
            if ns=='group':
                # Artist records have no artist/group discriminator. Never use an
                # artist record as a circle alias solely because raw names agree.
                extra_audit['danbooru_decision']='not_used_for_group_without_namespace_evidence'
            elif (common or agreeing) and (len(aliases)==1 or len(agreeing)==1):
                native=aliases[0] if len(aliases)==1 else agreeing[0]
                if not chosen or chosen['method']=='community_japanese_script_name' or risk(entry['label']):
                    chosen={'namespace':ns,'raw':raw,'display':native,'source':'Danbooru+EhTagTranslation','method':'crosschecked_native_artist_alias','evidence':{'danbooru_record':f"https://danbooru.donmai.us/artists/{artist['id']}",'danbooru_name':artist['name'],'native_alias':native,'matching_official_profile_urls':common,'exact_community_name_agreement':agreeing},'source_url':source_url(ns),'source_commit':PIN,'status':'mapped','origin_policy':'Existing source alias with corroborated entity identity; no transliteration, no inferred kanji.'}
                    reason='mapped'
            elif aliases:
                extra_audit['danbooru_decision']='ambiguous_aliases' if common else 'unverified_cross_site_identity'
        bgm_choice,bgm_reason=bangumi_native(ns,raw,entry,artist)
        if bgm_choice and raw not in WESTERN_NATIVE:
            # Already-located native pen names may differ from a person's modern
            # primary name. Do not silently replace a source's matching alias.
            if not chosen or chosen['display']==bgm_choice['display']:
                chosen=bgm_choice;reason='mapped'
            else:extra_audit['bangumi_decision']='different_native_alias_preserve_existing_source_name'
        elif bgm_reason:extra_audit['bangumi_decision']=bgm_reason
        if chosen:
            chosen.update({'ids':inv['ids'],'count':inv['count']})
            if entry:chosen['community_label']=entry['label']
            if extra_audit:chosen['alternative_source_review']=extra_audit
            # Keep same-source output byte stable, do not count unchanged strings
            # as translations (but audit the fact that an original was located).
            if chosen['display']==raw:
                chosen['status']='preserved';chosen['reason']='already_original_name'
            else:result[raw]=chosen['display']
            records.append(chosen);counter[chosen['method']]+=1
        else:
            records.append({'namespace':ns,'raw':raw,'display':raw,'status':'preserved','reason':reason,'ids':inv['ids'],'count':inv['count'],'source':'EhTagTranslation' if entry else 'unmapped','community_label':entry['label'] if entry else None,**extra_audit})
            counter[reason]+=1
    all_maps[ns]=result;all_records.extend(records)
    counts[ns]={'inventory':len(inventory[ns]),'exact_eh_matches':len(eh),'mapped':len(result),'methods_and_reasons':dict(counter)}
(OUT/'native-mappings.json').write_text(json.dumps(all_maps,ensure_ascii=False,indent=2,sort_keys=True)+'\n')
(OUT/'native-records.json').write_text(json.dumps(all_records,ensure_ascii=False,indent=2)+'\n')
(OUT/'native-summary.json').write_text(json.dumps(counts,ensure_ascii=False,indent=2)+'\n')
print(json.dumps(counts,ensure_ascii=False,indent=2))
