"""Compare a user-provided fullDict/JSON snapshot with the app, without executing JS."""

import argparse
import csv
import hashlib
import html
import json
import re
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def parse_dictionary(text):
    match = re.fullmatch(
        r"\s*(?:(?:const|let|var)\s+fullDict\s*=\s*)?(\{.*\})\s*;?\s*", text, re.S
    )
    if not match:
        raise ValueError("Expected JSON or a single fullDict object assignment")
    pairs = json.loads(match.group(1), object_pairs_hook=lambda values: values)
    if not all(isinstance(key, str) and isinstance(value, str) for key, value in pairs):
        raise ValueError("Expected string keys and values only")
    dictionary, duplicates = {}, []
    for key, value in pairs:
        if key in dictionary:
            duplicates.append({"name": key, "previous": dictionary[key], "last": value})
        dictionary[key] = value  # Match an object literal's last-value semantics.
    return dictionary, duplicates, len(pairs)


def md(value):
    return html.escape(str(value)).replace("|", "&#124;").replace("\n", "<br>")


def write_json(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")


def compare(source, output):
    raw_source = source.read_bytes()
    plugin, duplicates, pair_count = parse_dictionary(raw_source.decode("utf-8-sig"))
    asset = ROOT / "app/src/main/assets/tag_translations_zh_CN.json"
    app = json.loads(asset.read_text(encoding="utf-8"))
    rows = json.loads((ROOT / "data/tags.json").read_text(encoding="utf-8"))
    ids = defaultdict(list)
    for tag_id, name, _, tag_type in rows:
        if tag_type == 3:
            ids[name].append(tag_id)
    content = set(ids)
    plugin_content = plugin.keys() & content
    common = app.keys() & plugin_content
    equal = {key for key in common if app[key] == plugin[key]}
    different = common - equal
    plugin_only = plugin_content - app.keys()
    app_only = app.keys() - plugin_content
    outside = plugin.keys() - content

    # Compare the supplied language/category values separately from content tags.
    resources = ROOT / "app/src/main/res"
    defaults = ET.parse(resources / "values/strings.xml").getroot()
    chinese = {node.attrib.get("name"): node.text for node in ET.parse(resources / "values-zh-rCN/strings.xml").getroot()}
    other_values = {}
    for node in defaults:
        key = node.attrib.get("name", "")
        if key.startswith(("tag_language_", "tag_category_")) and node.text in outside:
            other_values[node.text] = chinese.get(key, node.text)

    output.mkdir(parents=True, exist_ok=True)
    write_json(output / "provided-dictionary.json", plugin)
    records = []
    for name in sorted(app.keys() | plugin.keys()):
        if name in equal:
            status = "内容标签：完全一致"
        elif name in different:
            status = "内容标签：译名不同"
        elif name in plugin_only:
            status = "内容标签：仅插件有显示名"
        elif name in app_only:
            status = "内容标签：仅应用有显示名"
        elif name in other_values:
            status = "其他字段：一致" if other_values[name] == plugin[name] else "其他字段：不同"
        else:
            status = "不在当前内容标签快照"
        current = app.get(name, name) if name in content else other_values.get(name, "")
        records.append([status, name, current, plugin.get(name, ""), "; ".join(map(str, sorted(ids.get(name, []))))])
    with (output / "comparison.csv").open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.writer(stream, lineterminator="\n")
        writer.writerow(["比较分类", "原始标签名", "当前应用显示名", "用户提供的插件显示名", "当前内容标签ID"])
        writer.writerows(records)

    stats = {
        "source_mode": "user-provided snapshot; not fetched from the live website",
        "source_sha256": hashlib.sha256(raw_source).hexdigest(),
        "app_asset_sha256": hashlib.sha256(asset.read_bytes()).hexdigest(),
        "source_pairs": pair_count,
        "source_unique_names": len(plugin),
        "duplicate_keys": duplicates,
        "plugin_content_names": len(plugin_content),
        "app_content_names": len(app),
        "common_content_names": len(common),
        "exact_equal": len(equal),
        "different": len(different),
        "plugin_only_content": len(plugin_only),
        "app_only_content": len(app_only),
        "source_outside_content_snapshot": len(outside),
    }
    write_json(output / "summary.json", stats)
    lines = [
        "# 内容标签译名不同项：完整对照", "",
        f"共 **{len(different)}** 项。仅限双方都有显示名的当前内容标签，按原始名称排序。",
        "逐字不同不等于误译：请结合标签原义判断简写、用词或含义上的差异。", "",
        "[比对总览](README.md) · [全部记录 CSV](comparison.csv)", "",
        "| 序号 | 原始标签名 | 当前应用 | 用户提供的插件词典 |",
        "| ---: | --- | --- | --- |",
    ]
    for index, name in enumerate(sorted(different), 1):
        lines.append(f"| {index} | <code>{md(name)}</code> | {md(app[name])} | {md(plugin[name])} |")
    (output / "differences.md").write_text("\n".join(lines) + "\n", encoding="utf-8")

    overview = f"""# 与用户提供的插件词典逐项比对

比较的是用户粘贴的 `fullDict` 快照，不是对在线脚本当前版本的实时抓取。
仅解析对象数据，没有执行上传的 JavaScript。原始输入 SHA-256：`{stats['source_sha256']}`。
规范化后的完整输入保存在 [provided-dictionary.json](provided-dictionary.json)。

| 项目 | 数量 |
| --- | ---: |
| 上传词典中的键值对 | {pair_count} |
| 上传词典中的不同名称 | {len(plugin)} |
| 重复键记录 | {len(duplicates)} |
| 插件词典对应当前内容标签的名称 | {len(plugin_content)} |
| 当前应用提供显示名的内容标签 | {len(app)} |
| 双方都提供显示名 | {len(common)} |
| 其中：逐字完全一致 | {len(equal)} |
| 其中：文字不同 | {len(different)} |
| 仅插件有显示名、应用保留原名 | {len(plugin_only)} |
| 仅应用有显示名、上传词典没有该词条 | {len(app_only)} |
| 上传词典中不属于当前内容标签快照的词条 | {len(outside)} |

- [完整不同项对照：{len(different)} 项](differences.md)
- [完整 CSV：包含一致、不同、仅一方及其他字段，共 {len(records)} 行](comparison.csv)
- [机器可读统计与文件指纹](summary.json)

比较按原始英文名称匹配、按显示字符串逐字判断。标为“不同”的条目不自动判定哪方正确。
部分是简写或同义表达，部分改变了身份、服饰或其他含义，需要逐项核对。
CSV 的“比较分类”列可以筛选出全部相同项、仅插件项或仅应用项。
此操作生成比对记录，不将插件译名自动导入应用词库。

## 插件独有的当前内容标签

| 原始标签名 | 当前应用显示 | 插件显示 |
| --- | --- | --- |
"""
    for name in sorted(plugin_only):
        overview += f"| <code>{md(name)}</code> | {md(name)} | {md(plugin[name])} |\n"
    overview += "\n## 语言／类别值（单独比较）\n\n| 原始值 | 当前应用 | 插件 | 是否一致 |\n| --- | --- | --- | --- |\n"
    for name in sorted(other_values):
        overview += f"| {md(name)} | {md(other_values[name])} | {md(plugin[name])} | {'是' if other_values[name] == plugin[name] else '否'} |\n"
    category_names = {name for _, name, _, tag_type in rows if tag_type == 7}
    overview += f"\n这份上传词典命中 Categories 的值数：{len(plugin.keys() & category_names)}。\n"
    overview += "\n## 未在当前内容标签快照中找到的其他词条\n\n"
    overview += "这些条目不计入当前内容标签的一致率，也不据此推断插件译名有误。\n\n| 原始名称 | 插件显示 |\n| --- | --- |\n"
    for name in sorted(outside - other_values.keys()):
        overview += f"| <code>{md(name)}</code> | {md(plugin[name])} |\n"
    overview += f"\n当前应用词库 SHA-256：`{stats['app_asset_sha256']}`。\n"
    (output / "README.md").write_text(overview, encoding="utf-8")
    print(json.dumps(stats, ensure_ascii=False, indent=2))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("--output", type=Path, default=ROOT / "data/tag-translations/plugin-comparison")
    args = parser.parse_args()
    compare(args.source, args.output)


if __name__ == "__main__":
    main()
