# 原作、角色、画师和社团的显示名称

这些映射仅用于简体中文界面的显示与搜索。原始名称、标签 ID、数据库、筛选 URL 和书签条件不改写；英文界面继续显示原值。所有数据离线随 APK 提供，不在使用应用时联网翻译。

四字段不套用内容标签的 1,127 项批准名单。保留原文的值仍能查看、搜索和选择，已有本地偏好及旧书签继续兼容；内容标签的翻译资格和显示规则保持原有政策。

## 采用规则

| 字段 | 目标 | 无法确认时 |
| --- | --- | --- |
| 原作、角色 | 可靠的简体中文名称，主要覆盖日本作品；已有可靠中文原名的中国作品和可靠韩作译名也可采用 | 保留原始值 |
| 画师、社团 | 定位实际使用的日文原名或笔名，保持日文字形 | 保留原始值 |

- 按 **命名空间和原始名称** 精确匹配，不使用内容标签的 fullDict 通用词义套用人物或社团。同名的画师、社团、角色分别处理。
- 原作、角色优先使用 EhTagTranslation 对应命名空间的可靠中文名；再使用 Bangumi 的显式简中名、明确英文／罗马字别名及作品关系补充。只有名称、别名或共同平台 ID 可以精确定位身份时才采用，不用近似拼写、任意姓名倒序或罗马字猜汉字。
- 中文名称统一使用 OpenCC `t2s` 转为简体；含日文假名的值不当作中文译名。画师、社团**完全不做**简繁转换，避免把“石恵”“宮坂みゆ”等原名改成中文字形。
- 画师、社团的社区日文名和独立原名核对分别标注：假名名称经过字形风险检查；纯汉字名需要明确原名证据，间隔号或长音符不算假名证明。Bangumi 原名字段、明确日文名、官方账号交集或一致的原名可用于补证。不能把中文昵称、作者本名、现用名任意替换成其另一笔名，也不能把同名公司当作社团。
- Danbooru 的 artist 记录仅供画师身份及别名交叉核对，不直接当成 group 记录。不能仅靠短用户名同名认定同一人；账号比较保留 Pixiv／X 身份及 URL 查询中的作者 ID。
- **明确欧美原作及角色保留原文**。日本配音或日本改编不改变欧美原始 IP 的来源；好莱坞改编也不把日本原始 IP 变成欧美作品。一个原值同时覆盖日本和欧美的不同角色时保留原文，并记录冲突。
- 国籍不等同于笔名来源：作者实际使用的日文笔名可以还原，不能把欧美西文姓名自行音译成日文。未确认、来源冲突、只有中文昵称或找不到原名的项均保留原文。

## 查看全部记录

每个当前原始名称都有一行库存记录，包括**已采用、原值不变、未采用及原因**，不会把词库精确命中数当作完成翻译数。自动规则核对覆盖全部库存；人工重点复核及来源身份核验不表示逐个网页人工检索了全部名称。

| 字段 | 不同原值 | 已采用显示名 | 保留原文 | 全部记录 |
| --- | ---: | ---: | ---: | --- |
| 原作 | 4,397 | [1,845](parody-mapped.md) | 2,552 | [CSV](parody-inventory.csv) |
| 角色 | 17,985 | [6,724](character-mapped.md) | 11,261 | [CSV](character-inventory.csv) |
| 画师 | 33,371 | [10,396](artist-mapped.md) | 22,975 | [CSV](artist-inventory.csv) |
| 社团 | 25,409 | [6,489](group-mapped.md) | 18,920 | [CSV](group-inventory.csv) |
| 合计 | 81,162 | 25,454 | 55,708 | 四份 CSV 完整覆盖 |

准确数量、各来源分配、保留原因及输入／输出校验值见 [coverage.json](coverage.json)。

- [mapping-decisions.json](mapping-decisions.json)：冻结的来源选择和证据；原始库存中没有可靠来源匹配的值由生成器统一记为保留原文。
- [reviewed-overrides.json](reviewed-overrides.json)：带来源的显式名称修订，生成时优先采用；日常修订无需恢复研究用整库。
- [sources.json](sources.json)：源版本、下载地址、归属规则、许可证及 SHA-256。
- [`assets/entity_translations_zh_CN.json`](../../app/src/main/assets/entity_translations_zh_CN.json)：只包含实际改变显示值的四字段映射，不携带网页、图片或简介。

`reason` 明确区分社区中文名、精确别名、平台 ID 交叉、社区日文名、原名身份交叉核对，以及欧美／混源、不明身份、字形风险、缺少来源等保留原因。

常见状态与保留原因：

| 记录代码 | 含义 |
| --- | --- |
| `mapped` / `preserved` | 已采用显示映射／继续使用原值 |
| `no_reliable_source_match` | 没有可靠的对应来源 |
| `unverified_origin_or_identity` / `unverified_cross_source_origin` | 原作来源或跨站同名身份尚不能确认 |
| `western_or_mixed_origin_preserved` | 欧美原始 IP 或同一原值混有欧美身份 |
| `no_reliable_simplified_chinese_name` | 身份已确认，但没有可靠简中名，可能只使用外文品牌名 |
| `han_name_without_explicit_original_orthography` | 只有汉字社区名，不能确认日文原名字形 |
| `script_variant_or_chinese_glyph_risk` | 可能混用简体字或中日异体字 |
| `kana_punctuation_is_not_native_evidence` | 只有日文间隔号等符号，没有可确认原名的假名或独立证据 |
| `multiple_aliases_or_ambiguous_name` | 多个别名或身份冲突，未任选其一 |

研究阶段的原名候选又经合并复核排除了 39 个“只含日文标点”的名称；原始研究数量与实际应用数量分开，最终采用数以 `coverage.json` 为准。

## 复现与维护

普通构建不需要下载研究用整库或安装额外 Python 包：

```sh
python3 scripts/build_entity_translations.py
python3 scripts/build_entity_translations.py --check
python3 scripts/check_entity_translations.py
```

`--check` 校验全部原值分区、来源证据、欧美保留约束及生成文件一致性，已接入 Android CI 和预览版发布检查。更新来源需先审核身份、来源范围及原名字形，再更新冻结的决定；重跑生成器不会自动接受来源中的新名。

研究用脚本及来源规则记录在 [research](research/)；它们使用独立缓存目录，不参与 Android 构建。大型外部数据的固定版本和校验值记在 `sources.json`，不把下载的作品简介、图片或整库放进应用。

## 来源与归属

- EhTagTranslation/Database：固定提交 `e0f66dc4424a53872473b05b7fb006ceebfcbe5d`，CC BY-NC-SA 3.0 中国大陆；沿用对应命名空间的名称，并记录清理标记和简体转换。
- Bangumi-data：固定提交的 `titleTranslate.zh-Hans`，CC BY 4.0。
- anime-offline-database：固定版本，仅用于显式别名及 MAL／AniList／AniDB ID 交叉，中文显示名来自 Bangumi-data；该交叉部分保留 ODbL 1.0／Database Contents License 1.0 归属。
- Bangumi Archive：固定日期快照，仅提取名称、别名及身份／作品关系。官方[版权声明](https://bgm.tv/about/copyright)指向 CC BY-SA 3.0。
- Wikidata：公开的确切条目关系及作品来源国家声明，CC0；来源冲突需复核，不能把国家字段当作唯一真相。
- Danbooru：公开作者元数据中的姓名事实、记录 ID 和身份账号，用于核对原名；没有找到明确的作者元数据开放许可，不声称其为 CC0，不复制媒体或 Wiki 正文。每条采用项保留对应来源与身份核对证据。
- OpenCC：仅在离线生成时用于原作、角色的简体字形规范化，不转换日文原名。

各来源子集分别保留原有归属，不给混合数据或应用代码统一更换许可证。随 APK 的许可证及说明位于 [`assets/licenses`](../../app/src/main/assets/licenses/)。

## 验证范围

- 全部 25,454 个映射及 81,225 条原始记录完成显示值、英文回退和原始查询值的实际方法核对；重复 ID 记录对应的 81,162 个不同名称均有库存记录。
- 358,543 项实际运行时断言、384 个边界 SQL 查询通过；330 个真实显示名关键词查询核对 1,622 条结果，覆盖字段隔离、在线／本地状态、最低计数及内容标签名单。
- 回归包括日文字形、欧美／混源保留、日系 IP 的欧美改编，以及未来标签更新不自动批准译名。周更流程会同步重建库存记录，新增原值继续保留原文。
- Android 9+、Android 8 两个 Debug APK 编译通过；已核对最终 APK 内的名称表、原有内容标签资源、许可证和签名。
- JVM 检查使用轻量 Android 控件／资源／JSON 桩，数据库查询另在 SQLite 执行；未做真机渲染验证。此处记录发布前的本地验证，APK 发布状态见 [Releases](https://github.com/Jesse0810/NClientV3/releases)。

本轮校验值及结果见 [verification.json](verification.json)。
