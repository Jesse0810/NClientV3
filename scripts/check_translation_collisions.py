#!/usr/bin/env python3
"""Audit effective zh-CN labels with explicitly reviewed content alias concepts.

Run without arguments to regenerate data/translation-collisions; --check verifies
the checked-in audit against the current inventories and application assets.
Uses only the Python standard library. No online translation sources are fetched.
"""

import argparse
import csv
import hashlib
import io
import json
from collections import defaultdict
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "data/translation-collisions"
COLLISION_ASSET = ROOT / "app/src/main/assets/tag_label_collisions_zh_CN.json"
FIELDS = (("tag", 3, "内容标签"), ("parody", 1, "原作"),
          ("character", 2, "角色"), ("artist", 4, "画师"), ("group", 5, "社团"))
ASSETS = "app/src/main/assets/"
CONTENT_REVIEW = "data/tag-translations/content-label-review/reviewed-decisions.json"
INPUTS = ["data/tags.json", ASSETS + "tag_translations_zh_CN.json",
          ASSETS + "entity_translations_zh_CN.json", ASSETS + "content_tag_allowlist.json",
          "data/tag-translations/provenance.json", CONTENT_REVIEW] + [
              f"data/entity-translations/{name}-inventory.csv" for name, _, _ in FIELDS if name != "tag"]


def read_json(relative):
    return json.loads((ROOT / relative).read_text(encoding="utf-8"))


def concept_name(record):
    return record.get("canonical_name", record["name"]) if record["namespace"] == "tag" else record["name"]


def collision_groups(records, fold_case=True, logical=True):
    """Keep raw records; ignore shared labels only within an approved TAG concept."""
    buckets = defaultdict(dict)
    for record in records:
        label = record["display_name"]
        key = (record["namespace"], label.casefold() if fold_case else label)
        buckets[key][record["name"]] = record
    return {key: list(names.values()) for key, names in sorted(buckets.items())
            if len({concept_name(record) if logical else record["name"] for record in names.values()}) > 1}


def content_aliases(labels, allowed):
    review = read_json(CONTENT_REVIEW)
    aliases = {}
    for group in review["merge_groups"]:
        canonical, members = group["canonical_name"], group["members"]
        assert len(members) > 1 and members[0] == canonical and len(members) == len(set(members)), canonical
        assert group["aliases"] == members[1:] and group["evidence"] and group["rationale"], canonical
        for name in members:
            assert name in allowed and name not in aliases, ("Invalid/overlapping approved alias", name)
            assert labels.get(name) == group["display_name"], ("Alias label differs", name)
            decision = review["raw_decisions"][name]
            assert decision["canonical_name"] == canonical, ("Unreviewed alias identity", name)
            aliases[name] = canonical
    return aliases


def check_formatted_labels(records, collision_index):
    """Fail if the UI suffix itself conflicts with another effective label."""
    indexed = {namespace: set(names) for namespace, names in collision_index.items()}
    formatted = []
    for record in records:
        label = record["display_name"]
        if record["name"] in indexed.get(record["namespace"], set()):
            label += "（" + record["name"] + "）"
        formatted.append(dict(record, display_name=label))
    remaining = collision_groups(formatted)
    if remaining:
        examples = [{"namespace": namespace, "label": label,
                     "names": [record["name"] for record in group]}
                    for (namespace, label), group in list(remaining.items())[:5]]
        raise AssertionError("Post-format display collisions remain: "
                             + json.dumps(examples, ensure_ascii=False))
    return formatted


def check_grouping_boundaries():
    def row(namespace, name, label):
        return {"namespace": namespace, "name": name, "display_name": label}
    # Namespace boundaries and duplicate source IDs must never create collisions.
    first = row("tag", "group", "群交")
    assert not collision_groups([first, first, row("group", "orgy", "群交")])
    assert len(collision_groups([first, row("tag", "orgy", "群交")])) == 1
    # Include untranslated labels; do not inspect only dictionary values.
    mixed = [row("artist", "original", "Native"), row("artist", "Native", "Native")]
    assert len(collision_groups(mixed)) == 1
    case_only = [row("artist", "one", "Native"), row("artist", "two", "native")]
    assert len(collision_groups(case_only)) == 1
    assert not collision_groups(case_only, fold_case=False)
    # Only an explicitly approved content alias family is one logical concept.
    alias_pair = [dict(row("tag", name, "变身"), canonical_name="transformation")
                  for name in ("transformation", "tranformation")]
    assert len(collision_groups(alias_pair, logical=False)) == 1
    assert not collision_groups(alias_pair)
    assert not collision_groups(check_formatted_labels(alias_pair, {"tag": []}))
    distinct_meaning = alias_pair + [row("tag", "unreviewed meaning", "变身")]
    assert len(collision_groups(distinct_meaning)) == 1
    assert len(collision_groups([dict(record, namespace="artist") for record in alias_pair])) == 1
    pair = [first, row("tag", "orgy", "群交")]
    index = {"tag": ["group", "orgy"]}
    assert not collision_groups(check_formatted_labels(pair, index))
    # A future preserved raw name can equal an otherwise valid formatted label.
    conflict = pair + [row("tag", "群交（group）", "群交（group）")]
    try:
        check_formatted_labels(conflict, index)
    except AssertionError as error:
        assert "Post-format display collisions remain" in str(error)
    else:
        raise AssertionError("Suffix-to-preserved-name collision was not detected")


def inventory():
    raw = read_json("data/tags.json")
    allowlist = read_json(ASSETS + "content_tag_allowlist.json")
    allowed = set(allowlist)
    assert len(allowed) == len(allowlist), "Duplicate canonical name in content allowlist"
    labels = read_json(ASSETS + "entity_translations_zh_CN.json")
    labels["tag"] = read_json(ASSETS + "tag_translations_zh_CN.json")
    assert set(labels["tag"]) <= allowed, "Inactive content labels leaked into active asset"
    aliases = content_aliases(labels["tag"], allowed)
    provenance = {row["name"]: row for row in read_json("data/tag-translations/provenance.json")}
    assert set(provenance) == allowed, "Content provenance is out of date"
    result, field_counts = [], []
    for namespace, type_id, field in FIELDS:
        rows = [row for row in raw if row[3] == type_id]
        ids = defaultdict(list)
        for identifier, name, _, _ in rows:
            ids[name].append(identifier)
        assert set(labels[namespace]) <= set(ids), f"Unknown mapped names: {namespace}"
        if namespace == "tag":
            metadata = provenance
            metadata_path = "data/tag-translations/provenance.json"
            assert allowed <= set(ids), "Allowlist contains names absent from the inventory"
        else:
            metadata_path = f"data/entity-translations/{namespace}-inventory.csv"
            with (ROOT / metadata_path).open(encoding="utf-8", newline="") as stream:
                metadata = {row["name"]: row for row in csv.DictReader(stream)}
            assert set(metadata) == set(ids), f"Entity inventory is out of date: {namespace}"
        current = []
        for name, tag_ids in sorted(ids.items()):
            selectable = namespace != "tag" or name in allowed
            display = labels[namespace].get(name, name) if selectable else name
            assert isinstance(display, str) and display, (namespace, name)
            source = metadata.get(name)
            if source is not None:
                assert source["display_name"] == display, ("Stale display inventory", namespace, name)
                source_ids = source["tag_ids"]
                if isinstance(source_ids, str):
                    source_ids = [int(value) for value in source_ids.split(";")]
                assert sorted(source_ids) == sorted(tag_ids), ("Stale ID inventory", namespace, name)
            record = {
                "namespace": namespace, "field": field, "name": name,
                "canonical_name": aliases.get(name, name) if namespace == "tag" else name,
                "display_name": display, "tag_ids": sorted(tag_ids),
                "status": "mapped" if name in labels[namespace] and selectable else "preserved",
                "translation_source": source["source"] if source and name in labels[namespace] else "none",
                "visible_in_picker": selectable,
                "metadata_reference": metadata_path if source else "data/tags.json",
            }
            current.append(record)
        result.extend(current)
        groups = collision_groups(current)
        raw_groups = collision_groups(current, logical=False)
        picker = [row for row in current if row["visible_in_picker"]]
        picker_groups = collision_groups(picker)
        colliding = [record for group in groups.values() for record in group]
        field_counts.append({
            "namespace": namespace, "field": field,
            "source_rows": len(rows), "unique_identities": len(current),
            "picker_identities": len(picker), "mapped_identities": sum(row["status"] == "mapped" for row in current),
            "logical_concepts": len({concept_name(row) for row in current}),
            "picker_logical_concepts": len({concept_name(row) for row in picker}),
            "raw_duplicate_label_groups": len(raw_groups),
            "raw_duplicate_label_identities": sum(len(group) for group in raw_groups.values()),
            "intentional_alias_label_groups": sum(len({concept_name(row) for row in group}) == 1
                                                   for group in raw_groups.values()),
            "approved_alias_names": sum(row["name"] != concept_name(row) for row in current),
            "exact_collision_groups": len(collision_groups(current, fold_case=False)),
            "casefold_collision_groups": len(groups), "colliding_identities": len(colliding),
            "colliding_logical_concepts": len({concept_name(row) for row in colliding}),
            "colliding_source_rows": sum(len(row["tag_ids"]) for row in colliding),
            "mapped_colliding_identities": sum(row["status"] == "mapped" for row in colliding),
            "preserved_colliding_identities": sum(row["status"] == "preserved" for row in colliding),
            "display_variants_in_collision_groups": sum(
                len({row["display_name"] for row in group}) for group in groups.values()),
            "groups_with_multiple_display_variants": sum(
                len({row["display_name"] for row in group}) > 1 for group in groups.values()),
            "picker_collision_groups": len(picker_groups),
            "picker_colliding_identities": sum(len(group) for group in picker_groups.values()),
            "mapped_vs_preserved_collision_groups": sum(
                len({row["status"] for row in group}) > 1 for group in groups.values()),
        })
    return result, field_counts


def csv_text(records):
    stream = io.StringIO(newline="")
    writer = csv.DictWriter(stream, fieldnames=[
        "collision_group", "namespace", "field", "display_name", "name", "canonical_name", "tag_ids", "status",
        "translation_source", "visible_in_picker", "group_identity_count", "group_logical_concept_count", "group_display_variant_count",
        "metadata_reference"])
    writer.writeheader()
    for record in records:
        item = dict(record)
        item["tag_ids"] = ";".join(map(str, item["tag_ids"]))
        writer.writerow(item)
    return stream.getvalue()


def render():
    check_grouping_boundaries()
    records, counts = inventory()
    groups = collision_groups(records)
    collisions = []
    per_field = defaultdict(list)
    group_number = defaultdict(int)
    for (namespace, _), group in groups.items():
        group_number[namespace] += 1
        for original in sorted(group, key=lambda item: item["name"]):
            row = dict(original, collision_group=f"{namespace}-{group_number[namespace]:03d}",
                       group_identity_count=len(group),
                       group_logical_concept_count=len({concept_name(item) for item in group}),
                       group_display_variant_count=len({item["display_name"] for item in group}))
            collisions.append(row)
            per_field[namespace].append(row)
    collision_index = {namespace: sorted({row["name"] for row in per_field[namespace]})
                       for namespace, _, _ in FIELDS}
    formatted = check_formatted_labels(records, collision_index)
    for row in counts:
        namespace_records = [record for record in formatted if record["namespace"] == row["namespace"]]
        row["post_format_collision_groups"] = len(collision_groups(namespace_records))
        row["post_format_picker_collision_groups"] = len(collision_groups(
            [record for record in namespace_records if record["visible_in_picker"]]))
    summary = {
        "schema_version": 2,
        "locale": "zh-CN",
        "identity": "Raw identity is namespace + exact source name; all IDs retained. Only reviewed TAG aliases share a logical concept.",
        "collision_comparison": "Exact labels and Unicode casefold checked separately; no cross-namespace merging",
        "translation_policy": "Current application assets only; content allowlist gates display mappings",
        "source_rows": sum(row["source_rows"] for row in counts),
        "unique_identities": len(records),
        "logical_concepts": sum(row["logical_concepts"] for row in counts),
        "raw_duplicate_label_groups": sum(row["raw_duplicate_label_groups"] for row in counts),
        "raw_duplicate_label_identities": sum(row["raw_duplicate_label_identities"] for row in counts),
        "intentional_alias_label_groups": sum(row["intentional_alias_label_groups"] for row in counts),
        "approved_content_alias_names": counts[0]["approved_alias_names"],
        "collision_groups": len(groups),
        "colliding_identities": len(collisions),
        "post_format_collision_groups": len(collision_groups(formatted)),
        "mapped_vs_preserved_collision_groups": sum(row["mapped_vs_preserved_collision_groups"] for row in counts),
        "fields": counts,
        "inputs_sha256": {path: hashlib.sha256((ROOT / path).read_bytes()).hexdigest() for path in INPUTS},
    }
    output = {"summary.json": json.dumps(summary, ensure_ascii=False, indent=2) + "\n",
              "collisions.csv": csv_text(collisions)}
    raw_duplicates, intentional_aliases = [], []
    raw_group_number = defaultdict(int)
    for (namespace, _), group in collision_groups(records, logical=False).items():
        raw_group_number[namespace] += 1
        for original in sorted(group, key=lambda item: item["name"]):
            row = dict(original, collision_group=f"raw-{namespace}-{raw_group_number[namespace]:03d}",
                       group_identity_count=len(group), group_logical_concept_count=len({concept_name(item) for item in group}),
                       group_display_variant_count=len({item["display_name"] for item in group}))
            raw_duplicates.append(row)
            if row["group_logical_concept_count"] == 1:
                intentional_aliases.append(row)
    output["raw-duplicate-labels.csv"] = csv_text(raw_duplicates)
    output["tag-confirmed-aliases.csv"] = csv_text(intentional_aliases)
    for namespace, _, _ in FIELDS:
        output[f"{namespace}-collisions.csv"] = csv_text(per_field[namespace])
    readme = ["# 五字段显示名称重名排查", "",
              "本清单核对当前应用实际使用的名称，不使用已经撤销或未启用的历史译文。",
              "同一字段内，不同原始值显示为同一个名称，会影响用户区分选项；这不等同于每条译文都是错误的。别名、同音人名、同名角色和同一译名对应不同概念均可能发生。",
              "原始值与全部 ID 保持不变。内容标签仅按已确认的别名决策合并逻辑概念；同一概念的正确拼写及旧拼写不再视为需要附原文的语义冲突。未经批准的同名值和其他四字段仍分别判定。", "",
              "| 字段 | 原始记录（含多 ID） | 唯一原值 | 可选原值／概念 | 原值同名组 | 真正冲突组 | 涉及原值 | 附原文后剩余组 | 完整清单 |",
              "|---|---:|---:|---:|---:|---:|---:|---:|---|"]
    for row in counts:
        readme.append(f'| {row["field"]} | {row["source_rows"]} | {row["unique_identities"]} | '
                      f'{row["picker_identities"]}／{row["picker_logical_concepts"]} | '
                      f'{row["raw_duplicate_label_groups"]} | {row["casefold_collision_groups"]} | '
                      f'{row["colliding_identities"]} | {row["post_format_collision_groups"]} | '
                      f'[{row["namespace"]}]({row["namespace"]}-collisions.csv) |')
    tag_counts = counts[0]
    exact_groups = sum(row["exact_collision_groups"] for row in counts)
    readme.extend(["", f'共核对 **{summary["source_rows"]:,} 条记录 / {len(records):,} 个不同原值**，'
                   f'对应 **{summary["logical_concepts"]:,} 个逻辑概念**。原值显示同名 '
                   f'**{summary["raw_duplicate_label_groups"]} 组 / {summary["raw_duplicate_label_identities"]} 个原值**，'
                   f'扣除已批准别名概念后，存在 **{len(groups)} 组 / {len(collisions)} 个原值**真正显示冲突。', "",
                   "内容标签同时扫描全部详情标签和管理／搜索可选清单。只有可选清单内的值会应用内容标签译文，其余详情值保留原文。"
                   f'可选范围为 {tag_counts["picker_identities"]} 个原值／{tag_counts["picker_logical_concepts"]} 个概念，'
                   f'存在 {tag_counts["picker_collision_groups"]} 组真正冲突；'
                   f'全部详情范围为 {tag_counts["unique_identities"]} 个原值／{tag_counts["logical_concepts"]} 个概念，'
                   f'存在 {tag_counts["casefold_collision_groups"]} 组真正冲突。',
                   "四个人物／作品字段不套用内容标签清单，全部原值均参与排查。"
                   f'五字段合计精确相等 {exact_groups} 组，Unicode casefold 比较 {len(groups)} 组；'
                   f'其中 {summary["mapped_vs_preserved_collision_groups"]} 组涉及译名与保留原文之间的重名。', "",
                   f'给索引内原值追加“（原文）”后，全量名称中跨逻辑概念的剩余冲突 **{summary["post_format_collision_groups"]} 组**。'
                   "脚本每次生成和检查都验证格式化后的逻辑概念唯一性；若未来的保留原文与带括号名称再次碰撞，命令会明确失败，并列出字段、显示值及相关原值。", "",
                   "## 内容标签语义复核与已确认别名", "",
                   f'当前批准 **{tag_counts["intentional_alias_label_groups"]} 组／{tag_counts["approved_alias_names"]} 个旧拼写或语言别名**。'
                   "[逐项语义复核](../tag-translations/content-label-review/README.md) 记录定义区分、规范原名、旧拼写和证据；"
                   "[确认别名 CSV](tag-confirmed-aliases.csv) 保留每个成员及全部 ID。正常搜索使用规范原名，旧拼写由单独入口查询；本审计不把不同旧拼写的查询结果视为已联合搜索。", "",
                   "| 当前显示名 | 规范概念 | 保留的原始值 |", "|---|---|---|"])
    for (namespace, _), group in collision_groups(records, logical=False).items():
        if namespace == "tag" and len({concept_name(item) for item in group}) == 1:
            readme.append("| " + group[0]["display_name"].replace("|", "\\|") + " | `" + concept_name(group[0])
                          + "` | " + "、".join("`" + row["name"] + "`" for row in group) + " |")
    readme.extend(["", "## 内容标签剩余真正冲突", "", "| 当前显示名 | 独立原始值 |", "|---|---|"])
    for (namespace, _), group in groups.items():
        if namespace == "tag":
            readme.append("| " + group[0]["display_name"].replace("|", "\\|") + " | "
                          + "、".join("`" + row["name"] + "`" for row in group) + " |")
    if not per_field["tag"]:
        readme.append("| 无 | 无 |")
    if {row["name"] for row in per_field["tag"] if row["display_name"] == "群交"} == {"group", "orgy"}:
        readme.extend(["", "`group` 与 `orgy` 当前均显示“群交”，仍是两个独立的内容标签，不能仅凭译名决定选择哪个原值。"])
    readme.extend(["",
                   "## 复现与来源", "",
                   "```sh", "python3 scripts/check_translation_collisions.py",
                   "python3 scripts/check_translation_collisions.py --check", "```", "",
                   "默认命令重新生成清单及运行时索引 `app/src/main/assets/tag_label_collisions_zh_CN.json`，`--check` 检查这些文件与当前数据是否完全一致。索引仅保存跨逻辑概念仍需附原文的原始名称，不给已确认别名重复加括号。脚本内同时检查字段隔离、多 ID 不重复计数、译名与原文碰撞、仅大小写不同、已批准别名与不同语义值的区分，以及附原文后再次碰撞的情况。", "",
                   "[真正冲突 CSV](collisions.csv) 按字段、显示名称和原始值记录全部剩余冲突；[原值同名 CSV](raw-duplicate-labels.csv) 另保留包含已合并概念的原值同名统计。`tag_ids` 保留所有原始 ID，`canonical_name` 仅对获批内容别名映射到规范概念，`translation_source` 记录当前译文来源，`metadata_reference` 指向已有完整审核记录。", "",
                   "[summary.json](summary.json) 记录原值与逻辑概念两种统计、精确／大小写折叠结果、可选范围统计及输入文件 SHA-256。源数据是 `data/tags.json`，有效名称来自应用中的 `tag_translations_zh_CN.json`、`entity_translations_zh_CN.json` 和 `content_tag_allowlist.json`；内容别名仅来自 `data/tag-translations/content-label-review/reviewed-decisions.json`。", "",
                   "本清单是静态全量数据排查；不代表 Android 下拉框、键盘和异步筛选时序已经通过真机测试。", ""])
    output["README.md"] = "\n".join(readme)
    return output, summary, collision_index


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="fail if generated audit files differ")
    args = parser.parse_args()
    files, summary, collision_index = render()
    if not args.check:
        OUTPUT.mkdir(parents=True, exist_ok=True)
    targets = {OUTPUT / name: content for name, content in files.items()}
    targets[COLLISION_ASSET] = json.dumps(collision_index, ensure_ascii=False, indent=2) + "\n"
    for target, content in targets.items():
        expected = content.encode("utf-8")
        if args.check:
            assert target.is_file() and target.read_bytes() == expected, f"Stale audit: {target}"
        else:
            target.write_bytes(expected)
    print(f'PASS: audited {summary["source_rows"]} rows / {summary["unique_identities"]} identities; '
          f'{summary["collision_groups"]} groups / {summary["colliding_identities"]} colliding identities')
    for row in summary["fields"]:
        print(f'{row["namespace"]}: {row["casefold_collision_groups"]} groups, '
              f'{row["colliding_identities"]} conflicting raw identities, '
              f'{row["picker_identities"]} selectable raw names / {row["picker_logical_concepts"]} logical concepts')


if __name__ == "__main__":
    main()
