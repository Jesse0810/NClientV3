#!/usr/bin/env python3
"""Offline consistency checks for reviewed content labels and generated alias data."""

import hashlib
import json
import sys
from collections import Counter, defaultdict

sys.dont_write_bytecode = True
import build_tag_translations as generator


def main():
    root, data, assets = generator.ROOT, generator.DATA, generator.ASSETS
    read = generator.read_json
    approved = read(data / "approved-content-tags.json")["approved_names"]
    allowed = read(assets / "content_tag_allowlist.json")
    assert len(allowed) == len(set(allowed)) and set(allowed) == set(approved)
    review = generator.read_content_label_review(generator.CONTENT_LABEL_REVIEW, allowed)
    expected_java = generator.render_alias_source(review).encode("utf-8")
    assert generator.ALIAS_SOURCE.read_bytes() == expected_java, "Stale generated content alias class"
    labels = read(assets / "tag_translations_zh_CN.json")
    records = read(data / "provenance.json")
    by_name = {record["name"]: record for record in records}
    assert len(by_name) == len(records) and set(by_name) == set(allowed)
    assert labels == {record["name"]: record["display_name"] for record in records if record["status"] == "mapped"}
    assert set(labels) <= set(allowed), "Reviewed labels expanded the approved range"
    assert all(record["status"] == ("mapped" if record["display_name"] != record["name"] else "preserved")
               for record in records)
    raw_ids = defaultdict(list)
    for identifier, name, _, namespace in read(root / "data/tags.json"):
        if namespace == 3:
            raw_ids[name].append(identifier)
    assert all(record["tag_ids"] == sorted(raw_ids[record["name"]]) for record in records)
    full_dict, ai_reviewed = read(data / "fullDict.json"), read(data / "ai-reviewed.zh-CN.json")
    for name, record in by_name.items():
        decision = review["raw_decisions"].get(name)
        if decision is not None:
            assert record["display_name"] == decision["display_name"] and record["source"] == "AI"
            assert record["prior_source"] == decision["previous_source"]
            assert record["prior_display_name"] == decision["previous_display_name"]
            assert record["review"]["canonical_name"] == decision["canonical_name"]
            assert record["review"]["evidence"] == decision["evidence"]
            assert record["tag_ids"] == sorted(decision["tag_ids"])
            if decision["previous_source"] == "fullDict":
                assert decision["previous_display_name"] == full_dict[name], "Original fullDict changed"
            elif decision["previous_source"] == "AI":
                assert decision["previous_display_name"] == ai_reviewed[name], "Original AI baseline changed"
        else:
            assert "review" not in record and "prior_source" not in record
            if name in full_dict:
                assert record["display_name"] == full_dict[name] and record["source"] == "fullDict"
            elif record["source"] == "AI":
                assert record["display_name"] == ai_reviewed.get(name, name)
    canonical = {name: group["canonical_name"] for group in review["merge_groups"] for name in group["members"]}
    concepts = defaultdict(set)
    for name in allowed:
        concepts[labels.get(name, name).casefold()].add(canonical.get(name, name))
    assert all(len(names) == 1 for names in concepts.values()), "Independent content concepts still share a label"
    report = read(data / "coverage.json")
    assert report["content_label_review_names"] == len(review["label_overrides"])
    assert report["content_alias_groups"] == len(review["merge_groups"])
    assert report["content_alias_names"] == sum(len(group["members"]) - 1 for group in review["merge_groups"])
    assert report["approved_logical_names"] == len({canonical.get(name, name) for name in allowed})
    assert report["content_alias_data_sha256"] == hashlib.sha256(expected_java).hexdigest()
    assert report["input_sha256"]["content-label-review"] == generator.sha256(generator.CONTENT_LABEL_REVIEW)
    for key, path in (("fullDict", data / "fullDict.json"), ("ai-reviewed", data / "ai-reviewed.zh-CN.json")):
        assert report["input_sha256"][key] == generator.sha256(path)
    assert report["source_counts"] == dict(Counter(record["source"] for record in records))
    print(f'PASS: {len(review["label_overrides"])} reviewed labels, {len(review["merge_groups"])} alias groups, '
          f'{report["approved_logical_names"]} unique content concepts; original identities and prior sources retained')


if __name__ == "__main__":
    main()
