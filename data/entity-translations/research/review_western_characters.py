import json,urllib.parse
from pathlib import Path
P=Path('/tmp/nclientv3-western-audit')
rows=json.loads(Path('/workspace/NClientV3/data/tags.json').read_text());inv={r[1] for r in rows if r[3]==2}
old=json.loads(Path('/tmp/nclientv3-entity-origin/origin-evidence.json').read_text())['character'];eh=json.loads(Path('/tmp/nclientv3-entity-sources/character-matched.json').read_text())
result={}
lines=Path('/tmp/ehtag-source/database/character.md').read_text().splitlines()
for line in (P/'character-seeds.tsv').read_text().splitlines():
 raw,parody,title=line.split('\t');assert raw in inv
 mixed=raw in ('ariel','robin','joker')
 origin='mixed_western' if mixed else 'western'
 evidence=[{'method':'reviewed_exact_character_identity','url':'https://en.wikipedia.org/wiki/'+urllib.parse.quote(title.replace(' ','_'),safe="_()'!,:"),'title':title,'parody':parody}]
 for i,source_line in enumerate(lines,1):
  if source_line.startswith('| '+raw+' |'):
   evidence.append({'method':'community_exact_character_entry','url':'https://github.com/EhTagTranslation/Database/blob/e0f66dc4424a53872473b05b7fb006ceebfcbe5d/database/character.md#L'+str(i)});break
 result[raw]={'origin':origin,'translate_allowed':False,'parody_references':sorted(set(old.get(raw,{}).get('parody_references',[]))|{parody}),'reason':'shared_character_name_with_western_source' if mixed else 'reviewed_western_character_identity','evidence':evidence,'previous_origin':old.get(raw,{}).get('origin')}
(P/'extra-western-characters.json').write_text(json.dumps(result,ensure_ascii=False,indent=2,sort_keys=True)+'\n')
print('Explicit character exclusions:',len(result))
print('Previously allowed:',[(r,x['previous_origin']) for r,x in result.items() if x['previous_origin'] in ('japan','east_asia_mixed','china','korea')])
