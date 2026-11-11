"""Export a complete semantic review while keeping confirmed and suspected entities distinct."""

import argparse
import csv
import hashlib
import html
import json
from collections import Counter, defaultdict
from pathlib import Path

from audit_content_tags import APPROVED_CONTENT_TAGS, exact_content_tag_candidates, load_approved_content_tags, load_semantic_decisions

ROOT = Path(__file__).resolve().parent.parent
REVIEW = ROOT / "data/tag-translations/semantic-review"
TITLES = {"keep": "保留的内容标签", "exclude": "确认排除的具体实体", "quarantine": "高度疑似实体：临时隔离"}


def write_json(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def write_records(path, rows):
    path.write_text("[\n" + ",\n".join("  " + json.dumps(row, ensure_ascii=False, separators=(",", ":")) for row in rows) + "\n]\n", encoding="utf-8")


def cell(value):
    return html.escape(str(value)).replace("|", "&#124;").replace("\n", "<br>")


def report(directory, approved_path=None):
    original = json.loads((ROOT / "data/tags.json").read_text(encoding="utf-8"))
    baseline = exact_content_tag_candidates(original)
    original_names = {row[1] for row in original if row[3] == 3}
    current_exact_names = {row[1] for row in baseline}
    snapshot_path = directory / "input-snapshot.json"
    snapshot = json.loads(snapshot_path.read_text(encoding="utf-8"))
    previous = {record["name"]: record for record in snapshot}
    if len(previous) != len(snapshot) or not previous.keys() <= current_exact_names:
        raise ValueError("The frozen input snapshot must have unique names still present in the first-stage candidates")
    baseline_names = set(previous)
    baseline = [row for row in baseline if row[1] in baseline_names]
    decisions_path = directory / "decisions.json"
    decisions = load_semantic_decisions(decisions_path)
    if decisions.keys() != baseline_names:
        raise ValueError("Every frozen candidate, including all fullDict and EhTagTranslation entries, needs a decision")
    approved, approval_sha256 = load_approved_content_tags(approved_path)
    ids = defaultdict(list)
    for tag_id, name, _, _ in baseline:
        ids[name].append(tag_id)
    records = []
    for name in sorted(baseline_names):
        decision = decisions[name]
        before = previous[name]
        record = {**decision, "name": name, "source_before": before["source"],
                  "display_before": before["display_name"], "tag_ids": sorted(ids[name]),
                  "management_hidden": name not in approved,
                  "detail_visible": True,
                  "translation_eligible": name in approved and decision["decision"] == "keep"}
        records.append(record)
    adjudications_path = directory / "adjudications.json"
    adjudications = json.loads(adjudications_path.read_text(encoding="utf-8")) if adjudications_path.exists() else []
    counts = Counter(record["decision"] for record in records)
    by_source = {}
    for source in ("fullDict", "EhTagTranslation", "AI"):
        selected = [record for record in records if record["source_before"] == source]
        current = Counter(record["decision"] for record in selected)
        by_source[source] = {"reviewed": len(selected), **{key: current[key] for key in TITLES}}
    confirmed = {record["name"] for record in records if record["decision"] == "exclude"}
    quarantined = {record["name"] for record in records if record["decision"] == "quarantine"}
    retained = {record["name"] for record in records if record["decision"] == "keep"}
    exact_excluded = original_names - current_exact_names
    assert confirmed.isdisjoint(quarantined)
    assert retained.isdisjoint(confirmed | quarantined)
    assert confirmed | quarantined | retained == baseline_names
    write_records(directory / "reviewed-records.json", records)
    write_records(directory / "remaining-tags.json", [row for row in baseline if row[1] in retained])
    write_json(directory / "remaining-names.json", sorted(retained))
    write_records(directory / "confirmed-tag-records.json", [row for row in baseline if row[1] in confirmed])
    write_records(directory / "quarantined-tag-records.json", [row for row in baseline if row[1] in quarantined])

    headers = ["原始名称", "排查前显示值", "排查前译名来源", "判定", "归属字段", "置信度", "原始标签ID", "理由", "证据或判断依据", "管理页隐藏", "详情页可见", "可使用显示映射"]
    csv_rows = []
    for record in records:
        evidence = record["evidence"]
        rendered_evidence = "; ".join(value if isinstance(value, str) else json.dumps(value, ensure_ascii=False, sort_keys=True) for value in evidence)
        csv_rows.append([
            record["name"], record["display_before"], record["source_before"], TITLES[record["decision"]],
            record["field"], record["confidence"], "; ".join(map(str, record["tag_ids"])),
            record["reason"], rendered_evidence, "是" if record["management_hidden"] else "否",
            "是", "是" if record["translation_eligible"] else "否",
        ])
    with (directory / "review-all.csv").open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.writer(stream, lineterminator="\n")
        writer.writerow(headers)
        writer.writerows(csv_rows)
    for decision, title in TITLES.items():
        selected = [(record, row) for record, row in zip(records, csv_rows) if record["decision"] == decision]
        lines = [f"# {title}", "", f"共 **{len(selected):,}** 个不同名称。", "", "[全部统计与规则](README.md) · [全量 CSV](review-all.csv)", ""]
        if decision == "quarantine":
            lines += ["这些条目尚未确证属于具体实体；历史隔离判断继续保留，不将疑似身份写成事实。当前它们不进入管理页或翻译范围，但漫画详情仍按原名显示。", ""]
        lines += ["| 序号 | " + " | ".join(headers) + " |", "| ---: | " + " | ".join("---" for _ in headers) + " |"]
        for index, (_, row) in enumerate(selected, 1):
            lines.append(f"| {index} | " + " | ".join(cell(value) for value in row) + " |")
        (directory / f"{decision}.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    summary = {
        "scope_names": len(records), "scope_records": len(baseline),
        "reviewed_by_source": by_source,
        "decisions": {key: counts[key] for key in TITLES},
        "first_stage_exact_exclusions": len(exact_excluded),
        "semantic_confirmed_exclusions": len(confirmed),
        "semantic_quarantined_names": len(quarantined),
        "approved_allowlist_names": len(approved),
        "approved_content_tags_sha256": approval_sha256,
        "management_visible_names": len(original_names & approved),
        "management_hidden_names": len(original_names - approved),
        "detail_visible_names": len(original_names),
        "detail_hidden_names": 0,
        "translation_eligible_names": len(retained & approved),
        "unreviewed_current_names": len(current_exact_names - baseline_names),
        "remaining_names": len(retained),
        "remaining_records": sum(row[1] in retained for row in baseline),
        "confirmed_by_field": dict(Counter(record["field"] for record in records if record["decision"] == "exclude")),
        "quarantined_by_field": dict(Counter(record["field"] for record in records if record["decision"] == "quarantine")),
        "input_snapshot_sha256": hashlib.sha256(snapshot_path.read_bytes()).hexdigest(),
        "decisions_sha256": hashlib.sha256(decisions_path.read_bytes()).hexdigest(),
        "second_review_changes": len(adjudications),
        "policy": "Historical semantic decisions stay frozen; only approved names appear in content-tag management or receive translations. Gallery details show every content tag, raw outside approval.",
        "new_translations": 0,
    }
    write_json(directory / "summary.json", summary)
    lines = ["# 全部内容标签的第二轮语义排查", "",
             f"本轮逐项审查第一阶段剩余的 **{len(records):,} 个名称／{len(baseline):,} 条记录**，涵盖所有 fullDict、EhTagTranslation 与 AI 来源，不因已有译名而免查。", "",
             "| 排查前来源 | 审查数 | 保留 | 确认排除 | 高度疑似隔离 |", "| --- | ---: | ---: | ---: | ---: |"]
    for source, values in by_source.items():
        lines.append(f"| {source} | {values['reviewed']:,} | {values['keep']:,} | {values['exclude']:,} | {values['quarantine']:,} |")
    lines += [f"| 合计 | {len(records):,} | {counts['keep']:,} | {counts['exclude']:,} | {counts['quarantine']:,} |", "",
              "完整名单：", "", "- [确认排除项：含类别与证据](exclude.md)",
              "- [高度疑似项：先隔离，未当作已确认事实](quarantine.md)",
              "- [保留的内容标签](keep.md)", "- [全部逐项审查 CSV](review-all.csv)",
              "- [全部逐项记录 JSON](reviewed-records.json)",
              "- [最终剩余名称](remaining-names.json) · [最终剩余原始记录](remaining-tags.json)", "",
              "## 判断与证据边界", "",
              "1. 具体作品、具体角色、作者／署名、社团／机构、语言或类别值，有明确身份依据时标为确认排除。",
              "2. 高度疑似专名但证据不足时保留隔离判定，不把推断写成已证实身份；当前只限管理页隐藏／无翻译资格，详情显示原名。",
              "3. 普通行为、职业身份、种族／角色类型、外观、服饰、物品、场景、画风及作品格式可作为内容属性保留。",
              "4. 不能只因来源是词典、网上存在同名作者／角色、罗马字形式或低作品计数就决定保留／排除。",
              "5. 命名空间、近似拼写和姓名词序是证据或候选；近似匹配不自动证明同一身份。",
              "6. 证据来源分为固定社区词库短条目与 AI 语义判断。只有语义线索的疑似项明确保留不确定性，未冒称在线查证。", "",
              f"第一阶段同名历史排除 **{len(exact_excluded):,}** 项、第二阶段确认 **{len(confirmed):,}** 项和隔离 **{len(quarantined):,}** 项的审计记录继续保留；语义保留 **{len(retained):,}** 个名称。",
              f"当前展示政策改为固定正向批准名单：共 **{len(approved):,}** 个名称，管理页只展示名单内内容标签，当前名单外 **{summary['management_hidden_names']:,}** 个名称不在管理页显示，也不翻译。",
              f"漫画详情恢复展示全部 **{len(original_names):,}** 个内容标签名称；名单外使用原名，不再隐藏。其他类型同名项不受本名单约束，原始数据与数据库不删除。",
              f"当前第一阶段候选中，快照之外的新名称为 **{summary['unreviewed_current_names']:,}** 项；它们不自动进入批准名单。", "",
              "本轮不新增译名。排查前的译名／来源冻结在 [input-snapshot.json](input-snapshot.json)，当前仍使用 fullDict → EhTagTranslation → AI 的译名优先级。",
              "[固定社区词库命名空间证据](namespace-evidence.json) · [机器可读统计](summary.json) · [审查政策](policy.json)",
              f"[第二次复核的逐项改判记录](adjudications.json) 记录 {len(adjudications)} 项 keep → exclude／quarantine 的前后依据。所有语义判断由 AI 完成，社区词库证据另行注明。", "",
              "## 更新与复现", "",
              "在仓库根目录执行；`--database` 可指定固定版本的本地社区词库目录：", "", "```sh",
              "python3 scripts/audit_content_tags.py",
              "python3 scripts/build_tag_translations.py --database /path/to/EhTagTranslation-Database",
              "python3 scripts/report_semantic_content_tags.py", "```", "",
              "[固定批准名单](../approved-content-tags.json) 与历史语义决策分别保存；仅将历史决策改成 keep 或重新运行脚本，不会自动批准新增名称。调整管理／翻译范围须经用户再次批准并显式更新名单。",
              "冻结快照中的候选必须都有决策，不能通过漏掉已审查记录来绕过完整性校验；漫画详情的全量显示不受历史排除／隔离状态限制。", ""]
    (directory / "README.md").write_text("\n".join(lines), encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False, indent=2))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--directory", type=Path, default=REVIEW)
    parser.add_argument("--approved-content-tags", type=Path, default=APPROVED_CONTENT_TAGS)
    args = parser.parse_args()
    report(args.directory, args.approved_content_tags)


if __name__ == "__main__":
    main()
