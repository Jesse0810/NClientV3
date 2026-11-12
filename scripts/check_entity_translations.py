"""Regressions for verified names, original-IP policy and future tag inventory updates."""

import json
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

sys.dont_write_bytecode = True
import build_entity_translations as generator

ROOT = Path(__file__).resolve().parent.parent
subprocess.run([sys.executable, str(ROOT / "scripts/build_entity_translations.py"), "--check"], check=True)
asset = json.loads((ROOT / "app/src/main/assets/entity_translations_zh_CN.json").read_text())

expected = {
    "parody": {"naruto": "火影忍者", "fate grand order": "命运-冠位指定", "original": "原创",
               "genshin impact": "原神", "monster hunter": "怪物猎人", "all you need is kill": "杀戮轮回"},
    "character": {"pikachu": "皮卡丘", "naruto uzumaki": "漩涡鸣人", "usagi tsukino": "月野兔",
                  "sonic the hedgehog": "刺猬索尼克", "ada wong": "艾达·王"},
    "artist": {"ishikei": "石恵", "miyasaka miyu": "宮坂みゆ", "miyasaka naco": "宮坂なこ"},
    "group": {"nisemididoronokai": "偽MIDI泥の会", "august": "オーガスト"},
}
for namespace, names in expected.items():
    for name, label in names.items():
        assert asset[namespace][name] == label, (namespace, name, "original name or Chinese label changed")
for name in ("avengers", "overwatch", "frozen", "the simpsons", "peter pan"):
    assert name not in asset["parody"], ("Western original must remain unchanged", name)
for name in ("nami", "ariel", "joker", "robin"):
    assert name not in asset["character"], ("Conflicting character identities must remain unchanged", name)
assert asset["group"].get("clockup") != "钟表社"
assert asset["group"].get("sprite") != "雪碧社"

# The weekly tag refresh must audit new names without auto-approving translations,
# including a name already translated in another namespace and an arbitrary raw value.
with tempfile.TemporaryDirectory(prefix="entity-inventory-check-") as temporary:
    fixture = Path(temporary)
    data = fixture / "data/entity-translations"
    data.mkdir(parents=True)
    for name in ("mapping-decisions.json", "sources.json", "reviewed-overrides.json"):
        shutil.copyfile(ROOT / "data/entity-translations" / name, data / name)
    rows = json.loads((ROOT / "data/tags.json").read_text())
    artist_names = {row[1] for row in rows if row[3] == 4}
    shared = next(name for name in asset["parody"] if name not in artist_names)
    fresh = "future name <unverified>"
    while fresh in artist_names:
        fresh += "x"
    next_id = max(row[0] for row in rows) + 1
    rows.extend([[next_id, shared, 1, 4], [next_id + 1, fresh, 1, 4]])
    (fixture / "data/tags.json").write_text(json.dumps(rows, ensure_ascii=False))
    generator.ROOT, generator.DATA = fixture, data
    generator.ASSET = fixture / "app/src/main/assets/entity_translations_zh_CN.json"
    output, summary = generator.build()
    assert json.loads(output[generator.ASSET]) == asset
    assert summary["unique_names"] == json.loads((ROOT / "data/entity-translations/coverage.json").read_text())["unique_names"] + 2
    assert fresh in output[data / "artist-inventory.csv"]

print("PASS: verified names, Japanese glyphs, Western/mixed originals and future inventory isolation")
