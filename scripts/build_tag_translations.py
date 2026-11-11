"""Build offline Chinese display labels without changing canonical tag data."""

import argparse
import csv
import hashlib
import html
import io
import json
import re
import tarfile
import urllib.request
from collections import Counter, defaultdict
from pathlib import Path

from audit_content_tags import APPROVED_CONTENT_TAGS, SEMANTIC_DECISIONS, approved_content_tag_partition

ROOT = Path(__file__).resolve().parent.parent
DATA = ROOT / "data/tag-translations"
ASSETS = ROOT / "app/src/main/assets"
COMMIT = "e0f66dc4424a53872473b05b7fb006ceebfcbe5d"
ARCHIVE_SHA256 = "0e9bce61c735fde92e4b905eaa2bba14311d6e5814772604221d57766f65381c"
ARCHIVE_URL = f"https://codeload.github.com/EhTagTranslation/Database/tar.gz/{COMMIT}"
CORE_NAMESPACES = ("mixed", "other", "location", "female", "male", "language", "reclass")
HAN = re.compile(r"[\u3400-\u9fff]")
# Match kana letters, not the middle dot also used in Chinese transliterated names.
KANA = re.compile(r"[\u3041-\u3096\u309d-\u309f\u30a1-\u30fa\u30fd-\u30ff\u31f0-\u31ff\uff66-\uff9d]")


def read_json(path):
    def unique_keys(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError(f"Duplicate JSON key in {path}: {key}")
            result[key] = value
        return result

    return json.loads(path.read_text(encoding="utf-8-sig"), object_pairs_hook=unique_keys)


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def plain_label(value):
    # Import labels only, never definitions, sample images, or external links.
    value = re.sub(r"!\[[^\]]*\]\([^)]*\)", "", value)
    value = re.sub(r"\[([^\]]*)\]\([^)]*\)", r"\1", value)
    value = re.sub(r"<[^>]*>", " ", value)
    value = html.unescape(value).replace(r"\|", "|")
    value = value.replace("**", "").replace("`", "")
    # Remove decorative pictographs from upstream's icon-bearing names.
    value = re.sub(r"[\U0001f000-\U0001faff\ufe0e\ufe0f\u200d]", "", value)
    return " ".join(value.split()).strip()


def read_database(directory):
    entries = defaultdict(list)
    for path in sorted((directory / "database").glob("*.md")):
        if path.stem == "rows":
            continue
        for line in path.read_text(encoding="utf-8").splitlines():
            if not line.startswith("|"):
                continue
            columns = re.split(r"(?<!\\)\|", line)
            if len(columns) < 4:
                continue
            name = columns[1].strip().replace(r"\|", "|")
            label = plain_label(columns[2].strip())
            if name and label and HAN.search(label):
                entries[name].append((path.stem, label))
    return entries


def choose_label(candidates):
    for namespace in CORE_NAMESPACES:
        for candidate_namespace, label in candidates:
            if namespace == candidate_namespace:
                return label
    # A name shared by different people/groups must not get an arbitrary identity.
    labels = {label for _, label in candidates}
    return next(iter(labels)) if len(labels) == 1 else None


def markdown_cell(value):
    return html.escape(str(value)).replace("|", "&#124;").replace("\n", "<br>")


SOURCES = ("fullDict", "EhTagTranslation", "AI")
SOURCE_FILES = {
    "fullDict": "source-fulldict.md",
    "EhTagTranslation": "source-ehtagtranslation.md",
    "AI": "source-ai.md",
}


def read_label_map(path, *, require_chinese=False):
    labels = read_json(path)
    if not isinstance(labels, dict):
        raise ValueError(f"Expected a name-to-display-value object: {path}")
    for name, label in labels.items():
        if not isinstance(name, str) or not name or not isinstance(label, str) or not label.strip():
            raise ValueError(f"Expected nonempty string names and display values: {path}")
        if require_chinese and (not HAN.search(label) or KANA.search(label)):
            raise ValueError(f"Expected an explicit reviewed Chinese label without kana for {name}")
    return labels


def resolve_provenance(rows, entries, full_dict, ai_reviewed):
    """Assign exactly one source to each clean canonical name, without writing files."""
    ids = defaultdict(list)
    for tag_id, name, _, tag_type in rows:
        if tag_type != 3:
            raise ValueError("Translation candidates must all be content tags")
        ids[name].append(tag_id)
    records, native_script = [], {}
    for name in sorted(ids):
        reference_native_script = None
        if name in full_dict:
            # The user explicitly requires literal values, including Latin labels such as 3D.
            display = full_dict[name]
            source = "fullDict"
            note = "用户提供 fullDict 直接命中，逐字使用其显示值；优先于社区词库与 AI。"
        else:
            community = choose_label(entries.get(name, []))
            if community and KANA.search(community):
                reference_native_script = community
            if community and HAN.search(community) and not KANA.search(community) and community != name:
                display = community
                source = "EhTagTranslation"
                note = "固定社区词库中的可靠汉字显示名；已清除标记／图标，未采用日文假名原字。"
            else:
                source = "AI"
                if name in ai_reviewed:
                    display = ai_reviewed[name]
                    note = "AI 显式审核表提供译名；仅在 fullDict 与可靠社区译名均未命中时采用。"
                else:
                    display = name
                    note = "AI 核对／保留原文：显式审核表未提供可靠译名；不推测姓名或专名，不计为汉化完成。"
                    if reference_native_script:
                        note += "社区候选含日文假名，仅保留参考记录。"
        status = "mapped" if display != name else "preserved"
        if status == "preserved" and name in full_dict:
            note += "显示值与原名相同，不新增应用映射，也不计为汉化完成。"
        elif status == "preserved" and name in ai_reviewed:
            note += "显示值与原名相同，仍按保留原文统计。"
        if status == "preserved" and reference_native_script:
            native_script[name] = reference_native_script
        records.append({
            "name": name,
            "display_name": display,
            "source": source,
            "status": status,
            "tag_ids": sorted(ids[name]),
            "note": note,
        })
    return records, native_script


def write_markdown(path, title, records, introduction):
    lines = [f"# {title}", "", f"共 **{len(records):,}** 个不同原始名称。", "", introduction, "",
             "| 序号 | 原始标签名 | 应用显示值 | 来源 | 状态 | 原始标签 ID | 说明 |",
             "| ---: | --- | --- | --- | --- | --- | --- | --- |"]
    for index, record in enumerate(records, 1):
        status = "有显示映射（mapped）" if record["status"] == "mapped" else "保留原文（preserved）"
        values = [record["name"], record["display_name"], record["source"], status,
                  "; ".join(str(tag_id) for tag_id in record["tag_ids"]), record["note"]]
        lines.append(f"| {index} | " + " | ".join(markdown_cell(value) for value in values) + " |")
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def write_inventory(records, native_script, report):
    mapped = [record for record in records if record["status"] == "mapped"]
    preserved = [record for record in records if record["status"] == "preserved"]
    with (DATA / "inventory.csv").open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.writer(stream, lineterminator="\n")
        writer.writerow(["原始标签名", "应用显示值", "来源", "状态", "原始标签ID", "说明"])
        for record in records:
            writer.writerow([record["name"], record["display_name"], record["source"], record["status"],
                             "; ".join(str(tag_id) for tag_id in record["tag_ids"]), record["note"]])
    links = "[清单总览](inventory.md) · [全部记录 CSV](inventory.csv) · [机器可读来源](provenance.json)"
    write_markdown(DATA / "translated.md", "已有显示映射的内容标签完整清单", mapped,
                   links + "\n\n“显示映射”不等于全部为中文：fullDict 指定的 3D、Cosplay 等拉丁写法也逐字保留。")
    write_markdown(DATA / "untranslated.md", "保留原文的内容标签完整清单", preserved,
                   links + "\n\n这些项目没有新增应用显示映射，不计为已汉化；未知专名不会通过猜测姓名补齐。")
    for source in SOURCES:
        selected = [record for record in records if record["source"] == source]
        mapped_count = sum(record["status"] == "mapped" for record in selected)
        write_markdown(DATA / SOURCE_FILES[source], f"{source} 来源的完整内容标签清单", selected,
                       links + f"\n\n本来源：有显示映射 {mapped_count:,} 项，保留原文 {len(selected) - mapped_count:,} 项。")
    digest = hashlib.sha256((ASSETS / "tag_translations_zh_CN.json").read_bytes()).hexdigest()
    lines = ["# 内容标签显示值与逐条来源", "",
             "候选同时经过两阶段审查及固定批准名单交集；名单外（包括未来新名）不翻译。译名优先级为 **fullDict > EhTagTranslation > AI**。", "",
             "| 来源 | 名称数 | 有显示映射 mapped | 保留原文 preserved | 完整记录 |",
             "| --- | ---: | ---: | ---: | --- |"]
    for source in SOURCES:
        lines.append(f"| {source} | {report['source_counts'][source]:,} | {report['mapped_by_source'][source]:,} | "
                     f"{report['preserved_by_source'][source]:,} | [完整清单]({SOURCE_FILES[source]}) |")
    lines += [f"| 合计 | {len(records):,} | {len(mapped):,} | {len(preserved):,} | [CSV](inventory.csv) |", "",
              f"来源三分类互斥且完整覆盖 **{report['unique_names']:,} 个名称／{report['tag_records']:,} 条原始记录**。",
              f"第一阶段跨字段精确重名排除 **{report['exact_cross_field_excluded']:,} 个名称**；原始第一阶段候选为 **{report['exact_candidate_names']:,} 个名称／{report['exact_candidate_records']:,} 条记录**。",
              f"历史第二阶段确认实体排除 **{report['semantic_excluded']:,} 项**，待核对隔离 **{report['semantic_quarantined']:,} 项**；隔离不计作确认实体。",
              f"固定批准名单 **{report['approved_allowlist_names']:,} 个名称**；当前管理页不展示 **{report['management_hidden_names']:,} 个名称**。",
              f"详情页全部 **{report['detail_visible_names']:,} 个内容标签名称**均可显示，名单外一律原文，不隐藏。",
              f"当前共有 **{report['excluded_names']:,} 个名称**不进入译名候选；新出现或未审查 **{report['semantic_unreviewed_names']:,} 项**不会自动扩大批准范围。", "",
              "`mapped` 表示显示值与规范原名不同；用户词典指定的拉丁显示值也计入映射，不笼统计为纯中文汉化。",
              "`preserved` 表示继续显示规范原名，不计作已译完。AI 来源中，有映射和保留原文分别单独统计。", "",
              "全部名单逐条包含来源、状态、显示值、全部原始 ID 与说明：", "",
              "- [有显示映射的完整名单](translated.md)",
              "- [保留原文的完整名单](untranslated.md)",
              "- [全量机器可读记录](provenance.json)",
              "- [输入校验值、固定社区版本与数量统计](coverage.json)",
              "- [假名原字参考记录，不计入中文汉化](native-script-not-translated.json)", "",
              "- [完整语义决策、确认排除与隔离的区分](semantic-review/README.md)", "",
              "- [固定批准范围及来源校验值](approved-content-tags.json)", "",
              f"应用词库 SHA-256：`{digest}`。", "",
              f"固定社区来源版本：`{COMMIT}`。", "",
              "应用仅加载实际映射（显示值不等于原名）；规范 API 名称、ID、数据库值与查询条件保持原样。",
              "重新生成方式、来源许可与历史资料边界见 [README](README.md)。", ""]
    (DATA / "inventory.md").write_text("\n".join(lines), encoding="utf-8")


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def build(directory, full_dict_path=None, ai_reviewed_path=None, *, check_only=False, semantic_decisions_path=None, approved_path=None):
    full_dict_path = full_dict_path or DATA / "fullDict.json"
    ai_reviewed_path = ai_reviewed_path or DATA / "ai-reviewed.zh-CN.json"
    tag_path = ROOT / "data/tags.json"
    all_rows = read_json(tag_path)
    semantic = approved_content_tag_partition(all_rows, semantic_decisions_path, approved_path)
    rows = semantic["final_records"]
    names = {row[1] for row in rows}
    all_content_names = {row[1] for row in all_rows if row[3] == 3}
    exact_names = {row[1] for row in semantic["exact_records"]}
    exact_cross_field_excluded = all_content_names - exact_names
    excluded_names = all_content_names - names
    full_dict = read_label_map(full_dict_path)
    ai_reviewed = read_label_map(ai_reviewed_path, require_chinese=True)
    entries = read_database(directory)
    records, native_script = resolve_provenance(rows, entries, full_dict, ai_reviewed)
    translations = {record["name"]: record["display_name"] for record in records if record["status"] == "mapped"}
    untranslated = [record["name"] for record in records if record["status"] == "preserved"]
    source_counts = Counter(record["source"] for record in records)
    mapped_by_source = Counter(record["source"] for record in records if record["status"] == "mapped")
    preserved_by_source = Counter(record["source"] for record in records if record["status"] == "preserved")
    assert len(records) == len(names)
    assert {record["name"] for record in records} == names
    assert set(translations).isdisjoint(excluded_names)
    assert len(translations) + len(untranslated) == len(names)
    assert sum(source_counts.values()) == len(names)
    assert sum(len(record["tag_ids"]) for record in records) == len(rows)
    unapproved_after_semantics = {row[1] for row in semantic["semantic_final_records"]} - semantic["approved_names"]
    assert excluded_names == exact_cross_field_excluded | semantic["confirmed_names"] | semantic["quarantined_names"] | unapproved_after_semantics
    community_paths = sorted((directory / "database").glob("*.md"))
    license_path = directory / "LICENSE.md"
    license_bytes = license_path.read_bytes()  # Validate required attribution before writing any output.
    report = {
        "priority": list(SOURCES),
        "tag_records": len(rows),
        "unique_names": len(names),
        "original_tag_records": sum(row[3] == 3 for row in all_rows),
        "original_unique_names": len(all_content_names),
        "excluded_names": len(excluded_names),
        "translation_eligibility_policy": "Only semantic candidates in the fixed approved-content-tags.json; never approve future names automatically",
        "whitelist_enforced": True,
        "approved_allowlist_names": len(semantic["approved_names"]),
        "management_visible_names": len(all_content_names & semantic["approved_names"]),
        "management_hidden_names": len(all_content_names - semantic["approved_names"]),
        "detail_visible_names": len(all_content_names),
        "detail_hidden_names": 0,
        "unapproved_after_semantics_names": len(unapproved_after_semantics),
        "exact_cross_field_excluded": len(exact_cross_field_excluded),
        "exact_candidate_names": len(exact_names),
        "exact_candidate_records": len(semantic["exact_records"]),
        "semantic_review_loaded": semantic["decisions_sha256"] is not None,
        "semantic_excluded": len(semantic["confirmed_names"]),
        "semantic_quarantined": len(semantic["quarantined_names"]),
        "semantic_kept_names": len(semantic["kept_names"]),
        "semantic_unreviewed_names": len(semantic["unreviewed_names"]),
        "semantic_stale_decisions": len(semantic["stale_decision_names"]),
        "mapped_names": len(translations),
        "preserved_names": len(untranslated),
        "source_counts": {source: source_counts[source] for source in SOURCES},
        "mapped_by_source": {source: mapped_by_source[source] for source in SOURCES},
        "preserved_by_source": {source: preserved_by_source[source] for source in SOURCES},
        "ai_mapped_names": mapped_by_source["AI"],
        "ai_preserved_names": preserved_by_source["AI"],
        "preserved_native_script_names": len(native_script),
        "full_dict_input_entries": len(full_dict),
        "ai_reviewed_input_entries": len(ai_reviewed),
        "input_sha256": {"data/tags.json": sha256(tag_path), "fullDict": sha256(full_dict_path),
                         "ai-reviewed": sha256(ai_reviewed_path),
                         "semantic-decisions": semantic["decisions_sha256"],
                         "semantic-review-snapshot": semantic["snapshot_sha256"],
                         "exact-candidates-canonical": semantic["exact_candidate_canonical_sha256"],
                         "approved-content-tags": semantic["approval_sha256"]},
        "source_repository": "https://github.com/EhTagTranslation/Database",
        "source_commit": COMMIT,
        "source_archive_sha256": ARCHIVE_SHA256,
        "community_input_sha256": {str(path.relative_to(directory)): sha256(path)
                                   for path in community_paths + [license_path]},
        "community_license": "CC-BY-NC-SA-3.0-CN",
        "full_dict_source": "User-provided dictionary; no licensing information supplied or inferred",
    }
    if check_only:
        print(json.dumps(report, ensure_ascii=False, indent=2))
        return report
    write_json(ASSETS / "tag_translations_zh_CN.json", translations)
    write_json(ASSETS / "content_tag_allowlist.json", sorted(semantic["approved_names"]))
    write_json(DATA / "provenance.json", records)
    write_json(DATA / "untranslated.json", untranslated)
    write_json(DATA / "native-script-not-translated.json", native_script)
    write_json(DATA / "coverage.json", report)
    write_inventory(records, native_script, report)
    licenses = ASSETS / "licenses"
    licenses.mkdir(parents=True, exist_ok=True)
    (licenses / "EhTagTranslation-LICENSE.md").write_bytes(license_bytes)
    (licenses / "tag-translations-NOTICE.txt").write_text(
        "Offline content-tag display labels, resolved in priority order:\n"
        "1. fullDict: user-provided dictionary snapshot. No licensing information was supplied or inferred.\n"
        "2. EhTagTranslation/Database community labels.\n"
        "Copyright: EhTagTranslation Database contributors.\n"
        f"Source: https://github.com/EhTagTranslation/Database/tree/{COMMIT}\n"
        "Community license: CC BY-NC-SA 3.0 China Mainland.\n"
        "https://creativecommons.org/licenses/by-nc-sa/3.0/cn/\n"
        "3. AI: explicitly reviewed labels; uncertain proper names remain unchanged and are not counted as translated.\n"
        "Changes to community data: selected reliable labels, removed markup/icons, merged namespaces, "
        "and applied higher-priority user values. Japanese kana was not accepted as Chinese translation.\n"
        "Community-derived data retains its community license; this notice does not assign that license "
        "to user-provided dictionary entries. Application code retains its own license.\n"
        "Per-entry provenance and input hashes: data/tag-translations/provenance.json and coverage.json in the repository.\n",
        encoding="utf-8",
    )
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--database", type=Path, help="Existing trusted EhTagTranslation Database source directory")
    parser.add_argument("--full-dict", type=Path, default=DATA / "fullDict.json",
                        help="User-provided fullDict JSON object; exact display values take first priority")
    parser.add_argument("--ai-reviewed", type=Path, default=DATA / "ai-reviewed.zh-CN.json",
                        help="Explicit AI-reviewed Chinese labels; missing names stay unchanged")
    parser.add_argument("--check-only", action="store_true", help="Validate inputs and print counts/hashes without writing outputs")
    parser.add_argument("--semantic-decisions", type=Path, default=SEMANTIC_DECISIONS,
                        help="Semantic decisions after exact exclusions; missing/partial decisions for current frozen-review names are errors")
    parser.add_argument("--approved-content-tags", type=Path, default=APPROVED_CONTENT_TAGS,
                        help="Required fixed approval; final candidates intersect this list and never auto-admit new names")
    args = parser.parse_args()
    if args.database:
        build(args.database, args.full_dict, args.ai_reviewed, check_only=args.check_only,
              semantic_decisions_path=args.semantic_decisions, approved_path=args.approved_content_tags)
        return
    with urllib.request.urlopen(ARCHIVE_URL, timeout=60) as response:
        archive = response.read()
    if hashlib.sha256(archive).hexdigest() != ARCHIVE_SHA256:
        raise ValueError("The pinned community translation archive checksum does not match")
    # Read only named text members; do not extract archive paths to the filesystem.
    import tempfile
    with tempfile.TemporaryDirectory(prefix="nclientv3-tag-source-") as temporary:
        directory = Path(temporary)
        with tarfile.open(fileobj=io.BytesIO(archive), mode="r:gz") as source:
            prefix = f"Database-{COMMIT}/"
            for member in source.getmembers():
                relative = member.name.removeprefix(prefix)
                if relative != "LICENSE.md" and not re.fullmatch(r"database/[a-z]+\.md", relative):
                    continue
                if not member.isfile():
                    continue
                target = directory / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                with source.extractfile(member) as stream:
                    target.write_bytes(stream.read())
        build(directory, args.full_dict, args.ai_reviewed, check_only=args.check_only,
              semantic_decisions_path=args.semantic_decisions, approved_path=args.approved_content_tags)


if __name__ == "__main__":
    main()
