import collections,hashlib,json,re,urllib.parse
from pathlib import Path
P=Path('/tmp/nclientv3-western-audit')
ROOT=Path(__file__).resolve().parents[3]
rows=json.loads((ROOT/'data/tags.json').read_text());inventory={r[1] for r in rows if r[3]==1}
seeds=dict(line.split('\t',1) for line in (P/'seeds.tsv').read_text().splitlines())
# These were recognized as possible Western games, but identity/country evidence is
# insufficient in this bounded pass. Existing unknown status continues to preserve them.
for raw in ['cloud meadow','legend of queen opala']:
 seeds.pop(raw,None)
old=json.loads(Path('/tmp/nclientv3-entity-origin/origin-evidence.json').read_text())
source=json.loads(Path('/tmp/nclientv3-entity-sources/parody-entries.json').read_text())
entities=json.loads((P/'wikidata-entities.json').read_text())
by_title={e.get('sitelinks',{}).get('enwiki',{}).get('title'):e for e in entities.values()}
mixed={'avengers','iron man','lilo and stitch','supernatural','teenage mutant ninja turtles','transformers','voltron','witchblade','river city girls','the adventures of tom sawyer','the three musketeers','peter pan'}
result={}
compact={}
for raw,title in sorted(seeds.items()):
 if raw not in inventory and raw not in source:continue
 entity=by_title.get(title)
 country_ids=[]
 evidence=[{'method':'reviewed_exact_franchise_identity','url':'https://en.wikipedia.org/wiki/'+urllib.parse.quote(title.replace(' ','_'),safe="_()'!,:"),'title':title,'note':'Explicitly reviewed named franchise/work identity; not inferred from English spelling.'}]
 if entity and 'id' in entity and not entity['id'].startswith('-'):
  qid=entity['id']
  country_ids=sorted({s.get('mainsnak',{}).get('datavalue',{}).get('value',{}).get('id') for s in entity.get('claims',{}).get('P495',[]) if s.get('mainsnak',{}).get('datavalue',{}).get('value',{}).get('id')})
  compact[qid]={'title':title,'countries':country_ids,'url':'https://www.wikidata.org/wiki/'+qid}
  if country_ids:evidence.append({'method':'wikidata_P495_checked','url':'https://www.wikidata.org/wiki/'+qid,'entity':qid,'countries':country_ids})
 origin='mixed_western' if raw in mixed else 'western'
 reason='reviewed_western_franchise_identity'
 if raw in mixed:
  reason='western_source_franchise_with_japanese_adaptation_or_cross_origin_work'
  evidence[0]['note']='Raw franchise tag can cover Western source property and Japanese adaptations/cross-origin work; do not localize the shared canonical name globally.'
 if raw in ('the adventures of tom sawyer','the three musketeers','peter pan'):
  original_ip = {
   'the adventures of tom sawyer': ('Mark Twain', 'United States', '1876'),
   'the three musketeers': ('Alexandre Dumas', 'France', '1844'),
   'peter pan': ('J. M. Barrie', 'United Kingdom', '1904 play / 1911 novel'),
  }[raw]
  evidence[0].update({'method':'reviewed_western_original_literary_ip','author':original_ip[0],'original_ip_country':original_ip[1],'original_publication':original_ip[2], 'note':'Generic raw title covers the original Western literary property as well as Japanese adaptations. The original IP takes precedence; a Japanese adaptation or dub does not make the shared canonical tag exclusively Japanese.'})
  reason='generic_shared_title_has_verified_western_original_literary_ip'
 if raw=='katawa shoujo':
  origin='source_conflict';reason='creator_origin_conflicts_with_setting_based_country_metadata'
  evidence[0]['note']='Four Leaf Studios international volunteer creation versus Wikidata Japan country claim; a Japanese fictional setting is not sufficient origin evidence. Preserve pending origin review.'
 result[raw]={'origin':origin,'translate_allowed':False,'reason':reason,'inventory':raw in inventory,'evidence':evidence,'previous_origin':old['parody'].get(raw,{}).get('origin','unknown')}
(P/'extra-western-parodies.json').write_text(json.dumps(result,ensure_ascii=False,indent=2,sort_keys=True)+'\n')
(P/'wikidata-origins-compact.json').write_text(json.dumps(compact,ensure_ascii=False,indent=2,sort_keys=True)+'\n')
# Propagation uses explicit source references already parsed by the main origin pass;
# no character origin is inferred from an English-looking personal name.
char_changes={}
for name,entry in old['character'].items():
 refs=entry.get('parody_references',[])
 hits=sorted(set(refs)&set(result))
 if not hits:continue
 bad=[r for r in hits if result[r]['origin'] in ('western','mixed_western')]
 conflict=[r for r in hits if result[r]['origin']=='source_conflict']
 if bad:
  known_other=[r for r in refs if r not in bad]
  origin='mixed_western' if known_other or any(result[r]['origin']=='mixed_western' for r in bad) else 'western'
 elif conflict:origin='source_conflict'
 else:continue
 char_changes[name]={'origin':origin,'translate_allowed':False,'parody_references':refs,'evidence':[{'method':'reviewed_western_parody_reference','parody':r,'url':result[r]['evidence'][0]['url']} for r in hits],'previous_origin':entry['origin']}
(P/'propagated-character-exclusions.json').write_text(json.dumps(char_changes,ensure_ascii=False,indent=2,sort_keys=True)+'\n')
summary={'all_inventory_parodies_scanned':len(inventory),'reviewed_franchises':len(result),'in_inventory':sum(r['inventory'] for r in result.values()),'relation_only_outside_inventory':sum(not r['inventory'] for r in result.values()),'new_or_changed_exclusions':sum(r['previous_origin'] not in ('western','mixed_western') for r in result.values()),'positive_conflicts':{n:r['previous_origin'] for n,r in result.items() if r['previous_origin']=='japan'},'character_propagation':len(char_changes),'wikidata_entities_downloaded':len(entities),'review_method':'Complete lexical scan of all 4397 canonical parody names, explicit franchise identity review, community source descriptions and bounded Wikidata exact sitelink check. Do not infer ethnicity/country from romanization. No blanket all-English rule.','limitations':['Public Wikidata metadata queries were stopped after HTTP429; not all cited Wikipedia pages were fetched in this pass.','The Western checklist uses explicitly reviewed franchise identities; uncertain fairy-tale homonyms and Japan-specific adaptation titles are not blanket excluded.','Parody records outside canonical inventory only support character-origin relationships and must not become app mapping keys.']}
(P/'summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2,sort_keys=True)+'\n')
print(json.dumps(summary,ensure_ascii=False,indent=2))
