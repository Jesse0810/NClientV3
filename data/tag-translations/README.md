# 内容标签汉化与逐条来源

当前将已保留的 **1,127 个内容标签名称锁定为正向批准名单**，译名修订不会自动扩大这份范围。

- **管理标签准入范围**：内容标签仍限于这 1,127 个批准原值，包括其中仍用原文的项；未来新名也不会自动出现。明确拼写／语言别名共 7 组，语义上收拢 9 个别名后对应 1,118 个内容概念。
- **漫画详情**：恢复展示全部内容标签。批准名单内沿用既有显示映射，名单外一律使用原始名称，不再隐藏。
- **翻译范围**：只限这份固定名单；修改历史审查决策或重跑生成器均不会自动批准新名。

批准范围与冻结来源见 [approved-content-tags.json](approved-content-tags.json)；应用读取同名集合的 `assets/content_tag_allowlist.json` 字符串数组。
原始内容标签共 4,780 个不同名称，经过两个相互区分的阶段：

| 阶段／集合 | 不同名称数 | 口径与完整清单 |
| --- | ---: | --- |
| 第一阶段：跨六字段精确同名排除 | 2,531 | [原始同名审计](content-tag-audit/README.md)，原报告继续保持第一阶段口径 |
| 第二阶段：全部语义审查输入 | 2,249 | [冻结的完整输入](semantic-review/input-snapshot.json)，包含原 fullDict、社区及 AI 的全部项，无来源豁免 |
| 第二阶段：确认排除具体实体 | 119 | [确认清单及证据](semantic-review/exclude.md) |
| 第二阶段：高度疑似实体，先隔离 | 1,003 | [隔离清单及待核理由](semantic-review/quarantine.md)，不当作已确认实体 |
| 第二阶段：实际保留的通用内容标签 | 1,127 | [保留清单及理由](semantic-review/keep.md) |
| 当前管理页名单外／不翻译 | 3,653 | 2,531 + 119 + 1,003；详情恢复原名显示，原始数据不删除 |

**2,249 项全覆盖语义审查**与**当前剩余 1,127 项译名清单**是不同范围，不能混为一份名单。
第二阶段逐条区分作品、角色、作者、社团、机构等具体实体与通用行为、身份、特征、场景和内容形式。
不因译名已存在而放过实体，也不因陌生、日语拼写、低计数或存在同名作者就排除普通内容词。
历史“确认排除”和“高度疑似隔离”判定继续保留，两类在证据、统计和清单中仍明确分开。
最新展示政策将限制收敛到管理页与译名资格：漫画详情不再受这些历史隐藏状态影响；其他字段中的同名值也不受内容标签名单限制。

## 保留项的显示值与来源

未专项复核项目的基线优先级为 **fullDict > EhTagTranslation > AI**。用户明确要求对内容标签重名进行语义处理后，[内容名称专项复核](content-label-review/README.md) 中的 31 个原值优先采用逐项审定显示值；这些项目统一计入 **AI 复核**，并在记录中保留先前来源、显示值及证据。每个当前候选仍只归属一个来源：

1. **fullDict**：使用用户提供的最新 [fullDict.json](fullDict.json)。基线命中时逐字采用其值；仅明确列入本次用户授权专项复核的项目可覆盖，原词典文件不修改；`3D`、`Cosplay` 等拉丁显示值也按用户词典保留。
2. **EhTagTranslation**：fullDict 未命中时，采用固定版本社区词库中的可靠汉字显示名。保留原有内容命名空间优先规则；清理标记／图标，不导入解释、图片或链接，不把日文假名原字算作中文汉化。
3. **AI**：前两者未取得有效显示值时，查询显式 [AI 审核译名表](ai-reviewed.zh-CN.json)。只有含汉字且不含假名的审核值会采用；未知姓名、专名或没有可靠对应项的标签保留原文，并明确记为 AI 核对／保留原文，不通过猜测凑齐译名。

当前保留项的实际来源分配：

| 来源 | 当前名称数 | 有显示映射 mapped | 保留原文 preserved |
| --- | ---: | ---: | ---: |
| fullDict | 494 | 494 | 0 |
| EhTagTranslation | 175 | 175 | 0 |
| AI | 458 | 112 | 346 |
| 合计 | 1,127 | 781 | 346 |

因此应用实际加载 **781 个显示映射**，另 **346 个保留项仍显示原文**，不计为已译完。
语义审查前的冻结来源分配 **509 + 200 + 1,540 = 2,249** 仅描述原审查输入，不能继续作为当前来源计数。
当前数量、两阶段分区、mapped/preserved 与输入 SHA-256 以 [coverage.json](coverage.json) 为准；
完整审查口径与旧来源覆盖情况见 [语义审查总览](semantic-review/README.md)。
fullDict 输入有 581 项，原基线命中 509 项，其中 15 项现在采用本次专项复核记录，最终 494 项仍计为 fullDict 来源。另 58 项命中跨字段排除规则、14 项不属于当前内容标签快照，不会因来源优先而重新录入。31 项复核的原来源为 fullDict 15 项、EhTagTranslation 6 项、AI 10 项；复核使 19 个显示值实际改变，其余为明确确认的规范显示值。
用户指定将 `incest` 的显示译名由“近亲相奸”改为“近亲乱伦”。原始上传校验值、当前有效字典校验值与后续修订见 [fullDict 来源记录](fullDict.source.json)；历史审查中的排查前显示值保留原记录。
AI 补译的复核与撤回理由见 [AI 核对记录](ai-review-notes.json)；旧 AI 输入表中的值仍受基线优先级约束；内容名称专项复核是独立、明确列出的用户授权例外。

## 当前清单入口

- [固定 1,127 项批准范围、来源哈希与不可自动扩大的规则](approved-content-tags.json)
- [全部 2,249 项语义审查总览与最新展示规则](semantic-review/README.md)
- [全部逐项语义决策：JSON](semantic-review/decisions.json) · [完整证据记录](semantic-review/reviewed-records.json) · [CSV](semantic-review/review-all.csv)
- [确认排除的 119 项](semantic-review/exclude.md) · [疑似隔离的 1,003 项](semantic-review/quarantine.md) · [保留的 1,127 项](semantic-review/keep.md)
- [语义保留后的纯名称 JSON](semantic-review/remaining-names.json) · [保留的原始标签记录](semantic-review/remaining-tags.json)
- [重名语义修订、拼写别名及修订前后来源](content-label-review/README.md) · [逐项 CSV](content-label-review/reviewed-values.csv)
- [全部来源与数量总览](inventory.md)
- [fullDict 来源完整清单](source-fulldict.md)
- [EhTagTranslation 来源完整清单](source-ehtagtranslation.md)
- [AI 来源完整清单，区分映射与保留原文](source-ai.md)
- [全量 CSV：原名、显示值、来源、状态、全部 ID、说明](inventory.csv)
- [全量机器可读逐条来源](provenance.json)
- [已有显示映射的完整清单](translated.md)
- [保留原文的完整清单](untranslated.md)
- [含假名的参考原字，不计为汉化](native-script-not-translated.json)

以下 `provenance.json`、来源清单、CSV 与映射／原文清单只记录**当前保留的 1,127 项**。
每个候选的 `provenance.json` 记录包含：

```json
{
  "name": "规范原名",
  "display_name": "实际显示值",
  "source": "fullDict / EhTagTranslation / AI 三者之一",
  "status": "mapped / preserved 二者之一",
  "tag_ids": [12345],
  "note": "该来源采用或保留的说明",
  "prior_source": "仅专项复核项：修订前来源",
  "prior_display_name": "仅专项复核项：修订前显示值",
  "review": {"canonical_name": "内容概念的规范原名", "evidence": ["证据引用"]}
}
```

`mapped` 仅表示显示值不同于原名；用户词典指定的拉丁写法也属于显示映射，不笼统计为纯中文翻译。
`preserved` 表示实际仍显示原名，不计为已译完；即使由 AI 回退保留，也必须保留 AI 来源与说明。
当前显示值 CSV、Markdown、JSON 完整覆盖保留的 1,127 个名称，重复名称的各个原始 ID 全部保留。
原 2,249 项的逐项语义去向完整保存在独立的 `semantic-review/` 清单中，没有因为当前词库收窄就丢掉审查记录。
旧文件名 `translated.md` 现在明确表示“已有显示映射”，不是承诺所有内容都已中文化。

## 生成与核对

```sh
python3 scripts/build_tag_translations.py
```

默认读取 `data/tags.json`、`data/tag-translations/fullDict.json` 和
`data/tag-translations/ai-reviewed.zh-CN.json`，并应用
`data/tag-translations/semantic-review/decisions.json` 中的显式语义决策，随后与必填的
`data/tag-translations/approved-content-tags.json` 固定批准名单相交。
`exact_content_tag_candidates` 保留第一阶段原口径；最终 `content_tag_candidates` 同时满足语义审查和正向批准。
未知新名可以在语义层列为未审，但没有批准资格，既不译名也不自动进入管理页；详情始终可按原名显示。
批准文件不从未来候选或 keep 决策自动重建；缺文件、坏 schema、重复名称或数量不符均报错。
不把历史 `overrides.zh-CN.json` 当成独立的高优先来源；普通补译仍显式进入 AI 审核表。本次仅由 `content-label-review/reviewed-decisions.json` 中的用户授权语义复核提供精确覆盖与别名分组；生成器校验名单、前值、成员互斥及统一显示，不从相似拼写自动推断别名。
脚本只读取明确审核值，不调用机器翻译服务，也不会自动创造姓名译法。
语义决策逐条校验名称、决策、字段、置信度、理由与证据；确认排除必须有非空证据，坏文件、重复名称和缺少理由会报错，不能静默跳过。
冻结快照存在时，当前第一阶段候选与快照的交集必须逐项有有效决策；决策文件缺失、空数组或漏掉已审名称均报错，不会让原先排除／隔离项复活。
真正位于快照之外的新名只允许列为语义未审，不自动取得管理／翻译资格。
冻结快照与决策文件都缺失时，语义层才兼容原第一阶段计算；固定批准名单仍必填，最终范围不越过正向名单。

社区词库固定为 `e0f66dc4424a53872473b05b7fb006ceebfcbe5d`，下载时保留正常 TLS 验证并校验固定归档 SHA-256。
脚本只读取指定 Markdown 和许可证成员，不直接解压不受信任的归档路径。
也可使用已有可信源目录，并覆盖两份本地输入路径：

```sh
python3 scripts/build_tag_translations.py \
  --database /path/to/EhTagTranslation-Database \
  --full-dict /path/to/fullDict.json \
  --ai-reviewed /path/to/ai-reviewed.zh-CN.json \
  --semantic-decisions /path/to/semantic-review/decisions.json \
  --approved-content-tags /path/to/approved-content-tags.json
```

先验证来源分配和全部输入哈希、暂不改写应用词库及清单：

```sh
python3 scripts/build_tag_translations.py --database /path/to/EhTagTranslation-Database --check-only
```

`--check-only` 不写任何资产或报告；正常模式从既有批准文件生成正向名单资产，并更新当前范围内的应用词库、逐条来源、统计、CSV 和全部清单。
`coverage.json` 记录原始标签数据、fullDict、AI 审核、内容名称专项复核、语义决策、冻结审查输入、固定批准文件及第一阶段规范化候选记录的 SHA-256，
以及固定社区提交、归档校验值和本次使用的社区文本文件哈希。
生成器只使用 Python 标准库，不访问漫画站点 API，输出可重复生成。别名分组同时生成不可变的 `ContentTagAliasData.java`，规范成员始终排在每组首位。可离线运行 `python3 scripts/check_content_label_review.py` 核对当前显示值、原来源、完整 ID、固定名单与生成的分组类；不需要重新下载社区词库。

完整语义决策写定后，由 `scripts/report_semantic_content_tags.py` 生成全部语义清单，
`scripts/audit_content_tags.py` 从固定批准文件生成正向名单资产；第一阶段 CSV／JSON 继续保留历史原口径。

实际应用文件为 `app/src/main/assets/tag_translations_zh_CN.json`，当前包含 781 个映射，键全部位于固定批准名单内。
`app/src/main/assets/content_tag_allowlist.json` 是固定 1,127 项正向名单，管理页仅允许这些内容标签；未来新名默认不展示、不翻译。
名单外 3,653 个当前名称及未来新名称都可在漫画详情按原名显示。界面语言不会扩大管理名单或翻译资格。
原始 API 名称与 ID 不从库存和来源记录中删除；别名分组不扩大准入范围。查询与旧书签的兼容由运行时代码负责，本清单不将客户端语义合并等同于服务端已经支持别名并集。未知新标签或未映射名称仍按原名展示。
简体中文界面的“管理标签 → 内容标签”按实际显示名进行包含匹配，例如输入“近亲”可找到“近亲乱伦”；已有译名的项不再按英文原名匹配，保留原文的项仍按原文匹配。输入两端空格会被忽略，切换模块或调整数量条件时保留当前搜索词。
其他界面语言继续按原名搜索，其他管理模块及高级搜索联想保留原有匹配方式。搜索结果仍提交原始 Tag 对象与规范名称；自由文本搜索不会被自动机器翻译。

## 来源许可与历史资料

- 用户 fullDict 只标记为“用户提供词典”，不臆断它的作者、许可证或再分发授权。
- 社区来源为 [EhTagTranslation/Database](https://github.com/EhTagTranslation/Database)，社区派生内容保留 **CC BY-NC-SA 3.0 China Mainland** 许可和署名；完整许可证与 NOTICE 继续打包在 `app/src/main/assets/licenses/`。
- NOTICE 分开说明用户词典、社区和 AI 三个来源，不把社区许可证自动套到用户提供词典；应用代码继续遵循自己的许可证。
- [历史插件词典比较](plugin-comparison/README.md) 是先前用户粘贴快照与当时应用词库的比较，**不是本轮最新 fullDict 的统计或实时线上结果**。当前核对以根目录 `fullDict.json`、`provenance.json` 和 `coverage.json` 为准。
- `overrides.zh-CN.json` 与原始审计、历史比较资料继续保留作核对参考；它们不参与当前生成；新的内容名称语义复核仅以专门的审定文件为依据。
