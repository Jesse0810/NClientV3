import hashlib,json,time,urllib.request,urllib.parse,urllib.error
from pathlib import Path

ROOT=Path(__file__).resolve().parents[3]
OUT=Path('/tmp/nclientv3-native-entities')
OUT.mkdir(exist_ok=True)
CACHE=OUT/'danbooru-batches'
CACHE.mkdir(exist_ok=True)
rows=json.loads((ROOT/'data/tags.json').read_text())
artists={r[1] for r in rows if r[3]==4}
groups=set(json.loads(Path('/tmp/nclientv3-entity-sources/group-matched.json').read_text()))
names=sorted({name.replace(' ','_') for name in artists|groups})
all_records={}
requests=[]
last=0
for start in range(0,len(names),180):
    batch=names[start:start+180]
    key=hashlib.sha256(('urls-v2\n'+'\n'.join(batch)).encode()).hexdigest()[:16]
    cache=CACHE/(key+'.json')
    query={'search[name_space]':' '.join(batch),'search[is_deleted]':'false','limit':1000,'include':'urls','only':'id,name,other_names,group_name,is_deleted,urls[url,normalized_url]'}
    url='https://danbooru.donmai.us/artists.json?'+urllib.parse.urlencode(query)
    if cache.exists():
        response=json.loads(cache.read_text())
    else:
        time.sleep(max(0,1.05-(time.monotonic()-last)))
        last=time.monotonic()
        try:
            with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'NClientV3-native-name-audit/1.0 (public names only)'}),timeout=30) as r:
                response=json.load(r)
            if not isinstance(response,list):raise ValueError('Expected API list')
            cache.write_text(json.dumps(response,ensure_ascii=False,indent=2)+'\n')
        except urllib.error.HTTPError as e:
            retry=e.headers.get('Retry-After')
            (OUT/'fetch-error.json').write_text(json.dumps({'batch':start,'code':e.code,'retry_after':retry,'url':url},indent=2))
            print('FETCH STOP',start,e.code,'retry_after',retry,flush=True)
            raise
    for r in response:
        if r['name'] in batch and not r.get('is_deleted'):
            all_records[r['name']]=r
    requests.append({'batch_start':start,'requested':len(batch),'received':len(response),'cache_file':str(cache),'sha256':hashlib.sha256(cache.read_bytes()).hexdigest(),'url':url})
    if start%900==0:print('Fetched',start+len(batch),'/',len(names),'names; exact records',len(all_records),flush=True)
    (OUT/'danbooru-artists.json').write_text(json.dumps(all_records,ensure_ascii=False,indent=2)+'\n')
    (OUT/'danbooru-source.json').write_text(json.dumps({'source':'Danbooru public artist metadata','retrieved_date':'2026-11-12','endpoint':'https://danbooru.donmai.us/artists.json','canonical_normalization':'exact nhentai raw name with spaces replaced by underscores; API record.name must exactly match','license_note':'Name facts and record identifiers only. No wiki prose, media, or license claim imported. Attribution retained.','rate_limit_policy':'Official API help recommends approx. 1 request/second for long sessions; requests separated by at least 1.05 seconds.','requested_total':len(names),'exact_records':len(all_records),'requests':requests},ensure_ascii=False,indent=2)+'\n')
print('DONE',len(names),'requested,',len(all_records),'exact live artist records',flush=True)
