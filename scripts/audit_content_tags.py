"""Audit content-tag history and package a fixed management/translation allowlist."""

import argparse
import csv
import hashlib
import html
import json
import math
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SEMANTIC_DECISIONS = ROOT / "data/tag-translations/semantic-review/decisions.json"
SEMANTIC_SNAPSHOT = ROOT / "data/tag-translations/semantic-review/input-snapshot.json"
APPROVED_CONTENT_TAGS = ROOT / "data/tag-translations/approved-content-tags.json"
OTHER_FIELDS = {
    4: ("Artists", "画师"),
    5: ("Groups", "社团"),
    1: ("Parodies", "原作"),
    2: ("Characters", "角色"),
    6: ("Languages", "语言"),
    7: ("Categories", "类别"),
}


def write_json(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def write_records(path, rows):
    # One complete record per line makes the exported data readable in diffs.
    path.write_text("[\n" + ",\n".join("  " + json.dumps(row, ensure_ascii=False, separators=(",", ":")) for row in rows) + "\n]\n", encoding="utf-8")


def cell(value):
    return html.escape(str(value)).replace("|", "&#124;").replace("\n", "<br>")


def exact_content_tag_candidates(rows):
    """First pass only: preserve the original six-field exact-name audit scope."""
    other_names = {row[1] for row in rows if row[3] in OTHER_FIELDS}
    return [row for row in rows if row[3] == 3 and row[1] not in other_names]


def _read_semantic_decisions(path):
    """Read schema-validated records; partition validation enforces frozen-name coverage."""
    try:
        raw = path.read_bytes()
    except FileNotFoundError:
        return {}, None

    def unique_keys(pairs):
        record = {}
        for key, value in pairs:
            if key in record:
                raise ValueError(f"Duplicate JSON key in semantic decisions {path}: {key}")
            record[key] = value
        return record

    def invalid_constant(value):
        raise ValueError(f"Invalid JSON constant in semantic decisions {path}: {value}")

    items = json.loads(raw.decode("utf-8-sig"), object_pairs_hook=unique_keys,
                       parse_constant=invalid_constant)
    if not isinstance(items, list):
        raise ValueError(f"Semantic decisions must be an array: {path}")

    def nonempty_evidence(value):
        if isinstance(value, str):
            return bool(value.strip())
        if isinstance(value, dict):
            return bool(value) and any(nonempty_evidence(item) for item in value.values())
        if isinstance(value, list):
            return any(nonempty_evidence(item) for item in value)
        return isinstance(value, (int, float)) and not isinstance(value, bool) and math.isfinite(value)

    decisions = {}
    required = {"name", "decision", "field", "confidence", "reason", "evidence"}
    for index, record in enumerate(items):
        if not isinstance(record, dict) or not required <= record.keys():
            raise ValueError(f"Invalid semantic decision schema at item {index} in {path}")
        name = record["name"]
        if not isinstance(name, str) or not name.strip():
            raise ValueError(f"Semantic decision name must be nonempty at item {index} in {path}")
        if name in decisions:
            raise ValueError(f"Duplicate semantic decision name in {path}: {name}")
        if record["decision"] not in ("keep", "exclude", "quarantine"):
            raise ValueError(f"Invalid semantic decision for {name}")
        if not isinstance(record["field"], str) or not record["field"].strip():
            raise ValueError(f"Semantic decision field must be nonempty for {name}")
        confidence = record["confidence"]
        valid_confidence = ((isinstance(confidence, str) and bool(confidence.strip()))
                            or (isinstance(confidence, (int, float)) and not isinstance(confidence, bool)
                                and math.isfinite(confidence) and 0 <= confidence <= 1))
        if not valid_confidence:
            raise ValueError(f"Invalid semantic decision confidence for {name}")
        if not isinstance(record["reason"], str) or not record["reason"].strip():
            raise ValueError(f"Semantic decision reason must be nonempty for {name}")
        evidence = record["evidence"]
        if not isinstance(evidence, list) or any(
            not isinstance(item, (str, dict)) or not nonempty_evidence(item) for item in evidence
        ):
            raise ValueError(f"Semantic decision evidence must contain nonempty strings/objects for {name}")
        if record["decision"] == "exclude" and not evidence:
            raise ValueError(f"Confirmed semantic exclusion requires evidence for {name}")
        decisions[name] = record
    return decisions, hashlib.sha256(raw).hexdigest()


def load_semantic_decisions(path=None):
    """Load validated review records keyed by exact canonical name; never infer new entities."""
    decisions, _ = _read_semantic_decisions(Path(path) if path is not None else SEMANTIC_DECISIONS)
    return decisions


def _read_semantic_snapshot():
    try:
        raw = SEMANTIC_SNAPSHOT.read_bytes()
    except FileNotFoundError:
        return set(), None

    def unique_keys(pairs):
        record = {}
        for key, value in pairs:
            if key in record:
                raise ValueError(f"Duplicate JSON key in semantic snapshot {SEMANTIC_SNAPSHOT}: {key}")
            record[key] = value
        return record

    def invalid_constant(value):
        raise ValueError(f"Invalid JSON constant in semantic snapshot {SEMANTIC_SNAPSHOT}: {value}")

    records = json.loads(raw.decode("utf-8-sig"), object_pairs_hook=unique_keys,
                         parse_constant=invalid_constant)
    if not isinstance(records, list):
        raise ValueError(f"Semantic input snapshot must be an array: {SEMANTIC_SNAPSHOT}")
    names = set()
    for index, record in enumerate(records):
        if not isinstance(record, dict) or not isinstance(record.get("name"), str) or not record["name"].strip():
            raise ValueError(f"Invalid semantic snapshot name at item {index} in {SEMANTIC_SNAPSHOT}")
        if record["name"] in names:
            raise ValueError(f"Duplicate semantic snapshot name in {SEMANTIC_SNAPSHOT}: {record['name']}")
        names.add(record["name"])
    return names, hashlib.sha256(raw).hexdigest()


def semantic_review_partition(rows, decisions_path=None):
    """Apply explicit semantic decisions after the unchanged exact-name first pass."""
    exact_rows = exact_content_tag_candidates(rows)
    exact_names = {row[1] for row in exact_rows}
    path = Path(decisions_path) if decisions_path is not None else SEMANTIC_DECISIONS
    decisions, decisions_sha256 = _read_semantic_decisions(path)
    snapshot_names, snapshot_sha256 = _read_semantic_snapshot()
    missing_known = (exact_names & snapshot_names) - decisions.keys()
    if missing_known:
        preview = "; ".join(sorted(missing_known)[:10])
        raise ValueError(f"Semantic decisions omit {len(missing_known)} current frozen-review names: {preview}")
    confirmed = {name for name in exact_names if name in decisions and decisions[name]["decision"] == "exclude"}
    quarantined = {name for name in exact_names if name in decisions and decisions[name]["decision"] == "quarantine"}
    kept = {name for name in exact_names if name in decisions and decisions[name]["decision"] == "keep"}
    unreviewed = exact_names - decisions.keys()
    blocked = confirmed | quarantined
    final_rows = [row for row in exact_rows if row[1] not in blocked]
    canonical = json.dumps(exact_rows, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    assert confirmed.isdisjoint(quarantined)
    assert kept.isdisjoint(blocked)
    assert (blocked | kept | unreviewed) == exact_names
    return {
        "exact_records": exact_rows,
        "final_records": final_rows,
        "confirmed_names": confirmed,
        "quarantined_names": quarantined,
        "kept_names": kept,
        "unreviewed_names": unreviewed,
        "stale_decision_names": decisions.keys() - exact_names,
        "decisions_sha256": decisions_sha256,
        "snapshot_sha256": snapshot_sha256,
        "exact_candidate_canonical_sha256": hashlib.sha256(canonical).hexdigest(),
    }


def load_approved_content_tags(path=None):
    """Read a fixed approval, never infer or regenerate approvals from newer tag data."""
    path = Path(path) if path is not None else APPROVED_CONTENT_TAGS
    raw = path.read_bytes()  # A missing approval is an error, not permission to translate everything.

    def unique_keys(pairs):
        record = {}
        for key, value in pairs:
            if key in record:
                raise ValueError(f"Duplicate JSON key in approved content tags {path}: {key}")
            record[key] = value
        return record

    def invalid_constant(value):
        raise ValueError(f"Invalid JSON constant in approved content tags {path}: {value}")

    approval = json.loads(raw.decode("utf-8-sig"), object_pairs_hook=unique_keys,
                          parse_constant=invalid_constant)
    if not isinstance(approval, dict) or approval.get("schema_version") != 1 or isinstance(approval.get("schema_version"), bool):
        raise ValueError(f"Invalid approved content-tag schema: {path}")
    if approval.get("scope") != "TagType.TAG":
        raise ValueError(f"Approval must apply only to canonical content-tag names: {path}")
    names = approval.get("approved_names")
    if not isinstance(names, list) or any(not isinstance(name, str) or not name.strip() for name in names):
        raise ValueError(f"Approved names must be an array of nonempty strings: {path}")
    if len(set(names)) != len(names):
        raise ValueError(f"Duplicate approved content-tag name: {path}")
    count = approval.get("approved_count")
    if not isinstance(count, int) or isinstance(count, bool) or count != len(names):
        raise ValueError(f"Approved content-tag count does not match names: {path}")
    for key in ("approved_from_decisions_sha256", "approved_from_snapshot_sha256"):
        digest = approval.get(key)
        if not isinstance(digest, str) or len(digest) != 64 or any(c not in "0123456789abcdef" for c in digest):
            raise ValueError(f"Approval must record a valid {key}: {path}")
    return frozenset(names), hashlib.sha256(raw).hexdigest()


def approved_content_tag_partition(rows, decisions_path=None, approved_path=None):
    semantic = semantic_review_partition(rows, decisions_path)
    approved, approval_sha256 = load_approved_content_tags(approved_path)
    semantic_final = semantic["final_records"]
    return {
        **semantic,
        "semantic_final_records": semantic_final,
        "final_records": [row for row in semantic_final if row[1] in approved],
        "approved_names": approved,
        "approval_sha256": approval_sha256,
    }


def content_tag_candidates(rows, decisions_path=None, approved_path=None):
    """Translation candidates must satisfy both semantic review and the fixed approval."""
    return approved_content_tag_partition(rows, decisions_path, approved_path)["final_records"]


def audit(source, output, allowlist_asset=None, semantic_decisions_path=None, approved_path=None):
    raw = source.read_bytes()
    rows = json.loads(raw)
    tags = [row for row in rows if row[3] == 3]
    by_name = defaultdict(list)
    other = defaultdict(lambda: defaultdict(list))
    for row in rows:
        if row[3] == 3:
            by_name[row[1]].append(row)
        elif row[3] in OTHER_FIELDS:
            other[row[1]][row[3]].append(row)

    excluded_names = by_name.keys() & other.keys()
    remaining_names = by_name.keys() - excluded_names
    excluded_records = [row for row in tags if row[1] in excluded_names]
    remaining_records = exact_content_tag_candidates(rows)
    semantic = approved_content_tag_partition(rows, semantic_decisions_path, approved_path)
    semantic_confirmed = semantic["confirmed_names"]
    semantic_quarantined = semantic["quarantined_names"]
    management_hidden = by_name.keys() - semantic["approved_names"]
    final_names = {row[1] for row in semantic["final_records"]}
    excluded = []
    for name in sorted(excluded_names):
        excluded.append({
            "name": name,
            "tag_ids": sorted(row[0] for row in by_name[name]),
            "matches": [
                {"field": english, "field_zh": chinese, "type": tag_type,
                 "ids": sorted(row[0] for row in other[name][tag_type])}
                for tag_type, (english, chinese) in OTHER_FIELDS.items() if tag_type in other[name]
            ],
        })
    app_path = ROOT / "app/src/main/assets/tag_translations_zh_CN.json"
    app = json.loads(app_path.read_text(encoding="utf-8")) if app_path.exists() else {}
    per_field = {
        english: {"field_zh": chinese, "overlapping_names": sum(tag_type in other[name] for name in excluded_names)}
        for tag_type, (english, chinese) in OTHER_FIELDS.items()
    }
    additional = {}
    for label, normalize in [("trim", str.strip), ("casefold", str.casefold), ("trim_casefold", lambda name: name.strip().casefold())]:
        other_normalized = {normalize(name) for name in other}
        additional[label] = len({name for name in by_name if normalize(name) in other_normalized} - excluded_names)
    report = {
        "rule": "Exclude a Tags value iff its original name exactly matches a value in Artists, Groups, Parodies, Characters, Languages, or Categories; no semantic exceptions",
        "source_sha256": hashlib.sha256(raw).hexdigest(),
        "tag_records": len(tags), "tag_names": len(by_name),
        "excluded_records": len(excluded_records), "excluded_names": len(excluded_names),
        "remaining_records": len(remaining_records), "remaining_names": len(remaining_names),
        "overlap_by_field": per_field,
        "number_of_matching_fields": dict(sorted(Counter(len(other[name]) for name in excluded_names).items())),
        "additional_matches_under_normalization": additional,
        "existing_app_mappings_excluded": len(app.keys() & excluded_names),
        "existing_app_mappings_remaining": len(app.keys() & remaining_names),
        "new_translations": 0,
        "original_data_modified": False,
        "app_dictionary_modified": False,
        "semantic_review_loaded": semantic["decisions_sha256"] is not None,
        "semantic_confirmed": len(semantic_confirmed),
        "semantic_quarantined": len(semantic_quarantined),
        "semantic_kept": len(semantic["kept_names"]),
        "semantic_unreviewed": len(semantic["unreviewed_names"]),
        "semantic_stale_decisions": len(semantic["stale_decision_names"]),
        "semantic_decisions_sha256": semantic["decisions_sha256"],
        "semantic_snapshot_sha256": semantic["snapshot_sha256"],
        "exact_candidate_canonical_sha256": semantic["exact_candidate_canonical_sha256"],
        "approved_allowlist_names": len(semantic["approved_names"]),
        "approved_content_tags_sha256": semantic["approval_sha256"],
        "management_visible_names": len(by_name.keys() & semantic["approved_names"]),
        "management_hidden_names": len(management_hidden),
        "management_hidden_records": sum(row[1] in management_hidden for row in tags),
        "detail_visible_names": len(by_name),
        "detail_hidden_names": 0,
        "translation_eligible_names": len(final_names),
        "final_candidate_names": len(final_names),
        "final_candidate_records": len(semantic["final_records"]),
    }
    assert excluded_names.isdisjoint(remaining_names)
    assert excluded_names | remaining_names == by_name.keys()
    assert not remaining_names & other.keys()
    assert Counter(tuple(row) for row in excluded_records + remaining_records) == Counter(tuple(row) for row in tags)
    assert excluded_names.isdisjoint(semantic_confirmed | semantic_quarantined)
    assert management_hidden.isdisjoint(final_names)
    assert final_names <= semantic["approved_names"]
    if allowlist_asset is not None:
        allowlist_asset.parent.mkdir(parents=True, exist_ok=True)
        write_json(allowlist_asset, sorted(semantic["approved_names"]))
        report["positive_allowlist_asset_generated"] = True
        report["positive_allowlist_asset_names"] = len(semantic["approved_names"])
    output.mkdir(parents=True, exist_ok=True)
    write_json(output / "summary.json", report)
    write_records(output / "excluded.json", excluded)
    write_records(output / "excluded-tag-records.json", excluded_records)
    write_records(output / "remaining-tags.json", remaining_records)
    write_json(output / "remaining-names.json", sorted(remaining_names))

    for is_excluded, filename, names in [(True, "excluded", sorted(excluded_names)), (False, "remaining", sorted(remaining_names))]:
        records = []
        for name in names:
            tag_ids = "; ".join(str(row[0]) for row in sorted(by_name[name]))
            fields = "; ".join(f"{english}（{chinese}）" for tag_type, (english, chinese) in OTHER_FIELDS.items() if tag_type in other.get(name, {}))
            evidence = "; ".join(f"{english}: " + ", ".join(str(row[0]) for row in sorted(other[name][tag_type])) for tag_type, (english, _) in OTHER_FIELDS.items() if tag_type in other.get(name, {}))
            records.append([name, tag_ids, fields, evidence, app.get(name, "")])
        headings = ["原始标签名", "Tags 原始ID", "重合字段", "对应字段的原始ID", "既有词库显示名（本轮未新增）"]
        with (output / f"{filename}.csv").open("w", encoding="utf-8-sig", newline="") as stream:
            writer = csv.writer(stream, lineterminator="\n")
            writer.writerow(headings)
            writer.writerows(records)
        lines = [f"# {'排除项完整清单' if is_excluded else '排除后剩余候选值完整清单'}", "", f"共 **{len(records):,}** 个不同原始名称。", "", "[审计总览](README.md) · [CSV](" + filename + ".csv)", "", "既有词库显示名仅为历史核对信息，不表示本轮进行了汉化。", "", "| 序号 | " + " | ".join(headings) + " |", "| ---: | --- | --- | --- | --- | --- |"]
        for index, record in enumerate(records, 1):
            lines.append(f"| {index} | <code>{cell(record[0])}</code> | " + " | ".join(cell(value) for value in record[1:]) + " |")
        (output / f"{filename}.md").write_text("\n".join(lines) + "\n", encoding="utf-8")

    runtime_note = (
        "已同时生成正向批准名单资产 `content_tag_allowlist.json`。管理标签中的内容标签只展示固定批准名称；名单外（包括未来新名）不进入管理页，也不获得译名。"
        "漫画详情恢复展示全部内容标签，名单外使用原名；其他字段不受内容标签名单限制。\n"
        if allowlist_asset is not None else ""
    )
    overview = f"""# Tags 跨字段同名排除审计

本轮先做候选数据清理，**没有新增汉化**。
规则按用户指定：Tags 的原始名称只要与 Artists、Groups、Parodies、Characters、Languages、Categories 任一字段中的值完全相同，就从待汉化候选集中排除。
本页及原有 excluded／remaining JSON、CSV、Markdown 始终记录第一阶段精确重名口径，不把后续语义隔离混算为重名命中。

| 集合 | 不同名称 | 原始记录（按 ID） | 完整记录 |
| --- | ---: | ---: | --- |
| 原始 Tags | {len(by_name):,} | {len(tags):,} | 原始 data/tags.json |
| 跨字段同名排除 | {len(excluded_names):,} | {len(excluded_records):,} | [Markdown](excluded.md) · [CSV](excluded.csv) · [匹配证据 JSON](excluded.json) |
| 排除后剩余候选 | {len(remaining_names):,} | {len(remaining_records):,} | [Markdown](remaining.md) · [CSV](remaining.csv) · [纯名称 JSON](remaining-names.json) |

可供下一步使用的清洗数据：[remaining-tags.json](remaining-tags.json)，保持原始 `[id, name, count, type]` 格式和每条记录。
第一阶段精确重名排除的原始记录：[excluded-tag-records.json](excluded-tag-records.json)。
两组互不重叠，合起来完整保留原始 Tags 的全部名称、ID、计数与类型。
本轮不覆盖原始数据，不改数据库，也不生成新的译名。
{runtime_note}
下一步汉化还须应用 [第二阶段语义审查](../semantic-review/README.md)：确认实体排除 {len(semantic_confirmed):,} 项，待核对隔离 {len(semantic_quarantined):,} 项。
“待核对隔离”不等于已确认实体；这些历史语义判定继续保留，但当前展示政策以 [固定批准名单](../approved-content-tags.json) 为准。
管理页不展示的当前名称共 {len(management_hidden):,} 个；详情页全部 {len(by_name):,} 个名称均可显示，名单外不翻译。
固定批准名单共 {len(semantic['approved_names']):,} 个名称；最终译名候选为 {len(final_names):,} 个名称／{len(semantic['final_records']):,} 条记录。
其中语义审查明确保留 {len(semantic['kept_names']):,} 项，新出现或未审查 {len(semantic['unreviewed_names']):,} 项；未审新名不自动判为实体，也不会自动加入管理／翻译批准范围。

## 与六个字段的重合情况

| 字段 | 重合名称数 |
| --- | ---: |
"""
    for english, info in per_field.items():
        overview += f"| {english}（{info['field_zh']}） | {info['overlapping_names']:,} |\n"
    overview += f"""
同一个名称可能与多个字段重合，分项不能直接相加；排除并集为 **{len(excluded_names):,}**。
去除首尾空格、忽略大小写、两者结合三种方式额外命中的数量分别为：{additional['trim']}、{additional['casefold']}、{additional['trim_casefold']}。
例如 `glasses` 同时出现在 Groups 中，按本轮“同名即排除”规则也在排除集；六个类别名也全部排除。
剩余集合只表示通过本轮同名检查，不代表所有剩余值的语义或原有分类都已核实。

## 现有汉化的历史核对信息

当前应用词库已有映射中，**{report['existing_app_mappings_excluded']:,}** 个属于本轮排除集，**{report['existing_app_mappings_remaining']:,}** 个位于剩余候选集。
清单末列列出已有显示名，便于后续清理；本轮没有继续运行汉化生成流程，也未改动应用词库。
名单外内容标签不应用既有译名：管理页不展示，漫画详情则恢复原名显示；其他类型中的同名值保留。

原始数据 SHA-256：`{report['source_sha256']}`。
全部统计与匹配规则见 [summary.json](summary.json)。

重新生成：`python3 scripts/audit_content_tags.py`。默认同时生成正向批准名单资产，可通过 `--allowlist-asset` 指定输出路径。脚本仅读取已冻结的批准文件，不会从后续新候选自动扩大批准范围；不连接网络或网站 API。
"""
    (output / "README.md").write_text(overview, encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, default=ROOT / "data/tags.json")
    parser.add_argument("--output", type=Path, default=ROOT / "data/tag-translations/content-tag-audit")
    parser.add_argument("--allowlist-asset", type=Path,
                        default=ROOT / "app/src/main/assets/content_tag_allowlist.json",
                        help="Output the existing fixed approval names as a positive runtime allowlist")
    parser.add_argument("--approved-content-tags", type=Path, default=APPROVED_CONTENT_TAGS,
                        help="Required fixed content-tag approval; missing or invalid files are errors")
    parser.add_argument("--semantic-decisions", type=Path, default=SEMANTIC_DECISIONS,
                        help="Validated decisions must cover all current frozen-review names; only genuinely new names may be unreviewed")
    args = parser.parse_args()
    audit(args.input, args.output, args.allowlist_asset, args.semantic_decisions, args.approved_content_tags)


if __name__ == "__main__":
    main()
