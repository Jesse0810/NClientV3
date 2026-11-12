# 来源匹配与核对方法

应用构建只使用上一层冻结的 `mapping-decisions.json`。正常重建和逐字校验使用 `scripts/build_entity_translations.py`，不执行本目录的网络采集脚本。

本目录保留来源研究方法、明确排除与补充证据，以及独立归属的 ID 交叉表。研究脚本使用 `/tmp/ehtag-source`、`/tmp/nclientv3-entity-sources`、`/tmp/nclientv3-native-entities`、`/tmp/nclientv3-entity-origin`、`/tmp/nclientv3-western-audit` 作为独立缓存；仓库路径使用当前脚本位置解析。恢复缓存后才可运行，不能直接把空缓存当作没有来源证据。

| 脚本 | 用途 |
| --- | --- |
| `extract_eh_entities.py` | 固定 Eh 版本，分别提取四个命名空间，禁止跨字段套用 |
| `crosswalk_titles.py` | Bangumi 精确标题及 Manami 平台 ID 交叉 |
| `fetch_archive_members.py` | 对固定日期 ZIP 做 Range 读取，只取所需成员，核对 CRC／SHA |
| `crosswalk_subjects.py` / `crosswalk_characters.py` | 显式别名、简中名及作品身份／管理员来源元数据匹配 |
| `native_fetch.py` / `native_build.py` / `native_validate.py` | 原名定位、身份账号交叉与保守排除，完整规则见 [native-name-policy.md](native-name-policy.md) |
| `classify_origins.py` | 原始 IP 来源判定，区分改编与原作，来源不明保留 |
| `supplement_origins.py` | 高频身份及通用玩家称谓补充来源 |
| `review_western.py` / `review_western_characters.py` | 明确欧美作品、同名角色与混源排除 |
| `assemble_decisions.py` | 合并核对结果、简体中文规范化、原名保留与审核修订 |

`requirements.txt` 只供离线研究阶段的 OpenCC 使用；Android 构建不需要安装它。固定源下载 URL、提交、日期、成员 CRC 与 SHA 见上一层 `sources.json`、[title-sources.json](title-sources.json) 和 [archive-source-metadata.json](archive-source-metadata.json)。

`manami-bangumi-crosswalk.json` 是单独提供的别名／平台 ID 交叉数据：交叉部分遵循原源 ODbL 1.0／DbCL 1.0；其中中文名称文本来自 Bangumi-data，保留 CC BY 4.0 归属，不把这些中文名误称为 Manami 译文。全部采用项另在上一层记录实际来源。

研究时的实时 API 响应不保证未来保持相同内容；对应响应校验值及来源记录保留在 `danbooru-source.json` 等元数据中，不声称重新联网一定恢复旧响应。大型作品简介、媒体与完整外部数据库不入库。普通构建从冻结决定复现应用显示值，来源更新后需重新审核，不能绕过保留原文规则。

研究副本仅调整了仓库和缓存位置；原研究脚本的校验值在原名来源清单中保留，本目录副本的校验值另列于 `files.sha256.json`。
