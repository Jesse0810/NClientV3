"""Rebuild offline entity display names and complete audit records from frozen decisions.

Never changes canonical tags, IDs, saved queries, or the content-tag allowlist.
Source research is frozen in data/entity-translations, not repeated over the network
during a normal build. Run this with --check to validate committed output.
"""

import argparse
import csv
import hashlib
import io
import json
import re
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DATA = ROOT / "data/entity-translations"
ASSET = ROOT / "app/src/main/assets/entity_translations_zh_CN.json"
TYPES = {"parody": 1, "character": 2, "artist": 4, "group": 5}
TITLES = {"parody": "原作", "character": "角色", "artist": "画师", "group": "社团"}
HAN = re.compile(r"[\u3400-\u9fff\uf900-\ufaff\U00020000-\U0002ffff\U00030000-\U000323af]")
KANA = re.compile(r"[\u3041-\u3096\u309d-\u309f\u30a1-\u30fa\u30fd-\u30ff\u31f0-\u31ff\uff66-\uff9d]")


def read_json(path):
    def unique(pairs):
        result = {}
        for name, value in pairs:
            if name in result:
                raise ValueError(f"Duplicate key in {path}: {name}")
            result[name] = value
        return result
    return json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=unique)


def json_text(value):
    return json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n"


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def cell(value):
    return str(value).replace("|", "&#124;").replace("\n", "<br>")


def build():
    rows = read_json(ROOT / "data/tags.json")
    decisions = read_json(DATA / "mapping-decisions.json")
    if set(decisions) != set(TYPES):
        raise ValueError("Expected four independent entity namespaces")
    sources = read_json(DATA / "sources.json")
    overrides = read_json(DATA / "reviewed-overrides.json")
    for ns, names in overrides.items():
        if ns not in TYPES:
            raise ValueError(f"Unexpected override namespace: {ns}")
        for name, decision in names.items():
            previous = decisions[ns].get(name, {})
            if previous.get("origin") in ("western", "mixed_western", "conflicting_origin") and decision.get("action") == "map":
                raise ValueError(f"Review original-IP evidence before overriding its preserved status: {ns}:{name}")
            decisions[ns][name] = decision
    inventory = {ns: defaultdict(list) for ns in TYPES}
    counts = {ns: Counter() for ns in TYPES}
    for tag_id, name, count, tag_type in rows:
        for ns, expected_type in TYPES.items():
            if tag_type == expected_type:
                inventory[ns][name].append(tag_id)
                counts[ns][name] += count
    asset = {ns: {} for ns in TYPES}
    records = []
    for ns, names in inventory.items():
        extra = set(decisions[ns]) - set(names)
        if extra:
            raise ValueError(f"Decisions outside current {ns} inventory: {sorted(extra)[:5]}")
        for name, ids in sorted(names.items()):
            decision = decisions[ns].get(name, {})
            action = decision.get("action", "preserve")
            if action not in ("map", "preserve"):
                raise ValueError(f"Unexpected decision for {ns}:{name}")
            label = decision.get("display_name", name) if action == "map" else name
            if action == "map":
                if not isinstance(label, str) or not label.strip() or label != label.strip():
                    raise ValueError(f"Empty or padded display name: {ns}:{name}")
                if any(ord(char) < 32 for char in label) or any(char in label for char in "<>\u200b"):
                    raise ValueError(f"Unexpected markup/control character: {ns}:{name}")
            origin = decision.get("origin", "unknown")
            evidence = decision.get("evidence", [])
            source = decision.get("source", "none")
            if source != "none" and source not in sources["sources"]:
                raise ValueError(f"Unknown source {source}: {ns}:{name}")
            if action == "map":
                if not evidence or source == "none":
                    raise ValueError(f"Mapping requires source evidence: {ns}:{name}")
                if origin in ("western", "mixed_western", "conflicting_origin"):
                    raise ValueError(f"Western entity must remain original: {ns}:{name}")
                if ns in ("parody", "character") and (not HAN.search(label) or KANA.search(label)):
                    raise ValueError(f"Expected a Chinese display name: {ns}:{name}")
                if ns in ("artist", "group") and decision.get("reason") == "community_japanese_script_name" and not KANA.search(label):
                    raise ValueError(f"Kana punctuation alone does not identify a native name: {ns}:{name}")
                if label != name:
                    asset[ns][name] = label
            status = "mapped" if name in asset[ns] else "verified_unchanged" if action == "map" else "preserved"
            records.append({
                "namespace": ns, "name": name, "display_name": label,
                "tag_ids": sorted(ids), "count": counts[ns][name],
                "status": status, "source": source, "origin": origin,
                "reason": decision.get("reason", "no_reliable_source_match"),
                "evidence": evidence,
            })
    summary = {
        "unique_names": len(records),
        "tag_records": sum(len(record["tag_ids"]) for record in records),
        "mapped": sum(len(labels) for labels in asset.values()),
        "inventory_sha256": sha256(ROOT / "data/tags.json"),
        "decisions_sha256": sha256(DATA / "mapping-decisions.json"),
        "overrides_sha256": sha256(DATA / "reviewed-overrides.json"),
        "sources_sha256": sha256(DATA / "sources.json"),
        "asset_sha256": hashlib.sha256(json_text(asset).encode()).hexdigest(),
        "policy": "Names are display-only, exact namespace matching; Western and unverified entries preserve their canonical value.",
        "namespaces": {},
    }
    for ns in TYPES:
        selected = [r for r in records if r["namespace"] == ns]
        summary["namespaces"][ns] = {
            "unique_names": len(selected),
            "tag_records": sum(len(r["tag_ids"]) for r in selected),
            "status_counts": dict(sorted(Counter(r["status"] for r in selected).items())),
            "mapped_sources": dict(sorted(Counter(r["source"] for r in selected if r["status"] == "mapped").items())),
            "preservation_reasons": dict(sorted(Counter(r["reason"] for r in selected if r["status"] == "preserved").items())),
        }
    output = {ASSET: json_text(asset), DATA / "coverage.json": json_text(summary)}
    for ns in TYPES:
        selected = [r for r in records if r["namespace"] == ns]
        out = io.StringIO(newline="")
        columns = ["namespace", "name", "display_name", "status", "source", "origin", "reason", "tag_ids", "evidence"]
        writer = csv.DictWriter(out, fieldnames=columns, lineterminator="\n")
        writer.writeheader()
        for record in selected:
            row = {key: record[key] for key in columns}
            row["tag_ids"] = ";".join(map(str, record["tag_ids"]))
            row["evidence"] = json.dumps(record["evidence"], ensure_ascii=False, separators=(",", ":"))
            writer.writerow(row)
        output[DATA / f"{ns}-inventory.csv"] = out.getvalue()
        mapped = [r for r in selected if r["status"] == "mapped"]
        lines = [f"# {TITLES[ns]}已采用显示名称", "",
                 f"{len(mapped):,} 个显示映射。全部已采用、保留原文和原值不变的记录见 [{ns}-inventory.csv]({ns}-inventory.csv)。", "",
                 "| 原始值 | 显示值 | 来源 | 方法 |", "| --- | --- | --- | --- |"]
        lines.extend("| " + " | ".join(cell(r[key]) for key in ("name", "display_name", "source", "reason")) + " |" for r in mapped)
        output[DATA / f"{ns}-mapped.md"] = "\n".join(lines) + "\n"
    return output, summary


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Fail if frozen sources do not reproduce committed output")
    args = parser.parse_args()
    output, summary = build()
    for path, text in output.items():
        if args.check:
            if not path.exists() or path.read_text(encoding="utf-8") != text:
                raise SystemExit(f"Stale generated output: {path.relative_to(ROOT)}")
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text, encoding="utf-8", newline="")
    print(f"{'Checked' if args.check else 'Generated'} {summary['mapped']:,} display names across {summary['unique_names']:,} canonical entity names")


if __name__ == "__main__":
    main()
