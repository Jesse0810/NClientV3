# 内容标签重名语义复核

本次逐组处理原有 13 组重名，并补查对应正确拼写及明确相关拼写错误。没有仅因中文译名相同就合并原始标签。其他四个字段继续使用原文区分。

## 合并展示

合并针对内容概念，原始 ID／名称继续保留。按用户选择，主选项默认搜索规范拼写，“查询旧拼写”入口保留各别名的独立查询；不将不受网站支持的 OR 表达式作为合并查询。

| 显示名称 | 规范原值 | 合并成员 | 理由 |
|---|---|---|---|
| 单一女性 | `sole female` | `sole female`、`sole femla`、`sole fmelae` | 两个低频值是 female 的漏字／字母错序；合并到正确原名。sole female 指只有一位女性参与，不等于没有男性，也不必然是叙事女主。 |
| 自慰 | `masturbation` | `masturbation`、`masturbtion`、`maturbation` | 两个低频值分别漏掉 a 或 s，含义不变。 |
| 变身 | `transformation` | `transformation`、`tranformation` | tranformation 比 transformation 少一个 s，归并正确拼写。 |
| 总集篇 | `soushuuhen` | `soushuuhen`、`soshuuhen` | 总集篇（総集編）的低频长音省略写法，归并词库中已有规范罗马字。 |
| 触手 | `tentacles` | `tentacles`、`shokushu` | shokushu 是日语“触手”的罗马字写法，与当前标签语境中的 tentacles 指同一概念；不是另一个物种或行为等级。 |
| 第三方水印 | `watermarked` | `watermarked`、`watermaked` | watermaked 为 watermarked 漏 r；两项共用明确区分扫描组水印的名称。 |
| 系列作 | `multi-work series` | `multi-work series`、`mult-work series` | mult-work series 为 multi-work series 漏 i；原译名尾注只是拼写提示，不代表独立内容概念。 |

## 区分含义

| 原值及新显示 | 原值及新显示 | 理由 |
|---|---|---|
| `human pet` → 人类宠物 | `petplay` → 宠物扮演 | human pet 表达人作为宠物的身份；petplay 表达宠物式对待／角色扮演活动。两者相关，但没有查得当前网站将它们声明为同义词的证据，因此保留原值并用角色／活动措辞区分。 |
| `full body tattoo` → 全身纹身 | `large tattoo` → 大面积纹身 | full body 描述全身；社区 large tattoo 定义只要求足够突出的大面积纹身，并不要求全身覆盖。 |
| `big penis` → 大根 | `huge penis` → 巨根 | 社区定义明确区分 big 与 huge 两档，并将 big 作为 huge 的前置；不合并。 |
| `underwater` → 水下场景 | `underwater sex` → 水下性行为 | underwater 表达水下场景；带 sex 的原值明确限定性行为发生于水下。没有证据可将场景直接等同于具体活动。 |
| `scanmark` → 扫描组水印 | `watermarked` → 第三方水印 | 社区定义明确要求区分：scanmark 是扫描、翻译、上色相关方添加；watermarked 是与制作流程无关第三方的宣传水印。 |
| `defloration` → 女性破处 | `virginity` → 男性失贞 | 按当前社区标签定义，defloration 用于女性初次失贞过程，virginity 在该标签语境用于男性；不是仅从 virginity 普通字面义推断。 |
| `group` → 群交 | `orgy` → 乱交聚会 | group 的社区定义是多人同时性活动；orgy 的词典含义突出纵欲聚会。保留两个原值，用“聚会”表明侧重，不编造三人／四人或大型人数门槛。没有证据声称当前网站有正式人数分界。 |
| `anal` → 肛门插入 | `anal intercourse` → 肛交 | 社区定义明确区分：anal 是更广的肛门插入类别，anal intercourse 是其中的性交类型，并以 anal 为前置。 |

合计复核 31 个原值，7 组合并收拢 9 个别名；保留 1127 个原始批准名称，对应 1118 个可选内容概念。八组有意义区别的原值继续独立。

`sole female` 使用“单一女性”，避免“仅女性”误导为全女性场景，也避免“单女主”额外引入叙事主角含义。

## 记录与证据

- [逐项 CSV](reviewed-values.csv)：包括原译名、原来源、规范名称、新译名和理由。
- [机器可读决策](reviewed-decisions.json)：`merge_groups` 提供规范名及全部成员，`label_overrides` 提供原值到新显示的映射，`raw_decisions` 保留旧来源和逐项理由。
- [来源证据](evidence.json)：固定 EhTagTranslation 版本的定义行与校验值、用户 fullDict、WordNet 普通词义。

原词库中的“全身纹身”“破处”“肛交”等译名曾省略来源定义中的范围差别。本次优先采用能够表达区分的中文，不更改内容标签的批准范围。

人类宠物／宠物扮演、水下场景／水下性行为使用身份与行为、场景与行为的措辞区分；未声称已得到 nhentai 官方别名声明。群交／乱交聚会不设定没有证据的人数阈值。

网站搜索实测发现 `masturbtion` 和 `soshuuhen` 各有一条仅带旧拼写的结果；这部分结果通过“查询旧拼写”入口访问。搜索页选择同一概念的标准写法或旧拼写时会互相替换，避免误用 AND 同时要求两个写法。旧书签中的明确旧拼写查询继续保留。

本目录提供语义审查依据；运行时合并同时处理搜索选择、偏好、详情和旧书签。管理页修改一个合并概念的偏好，会同步该概念各成员的偏好，原始行和漫画关联不删除。
