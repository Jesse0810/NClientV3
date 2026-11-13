# 五字段显示名称重名排查

本清单核对当前应用实际使用的名称，不使用已经撤销或未启用的历史译文。
同一字段内，不同原始值显示为同一个名称，会影响用户区分选项；这不等同于每条译文都是错误的。别名、同音人名、同名角色和同一译名对应不同概念均可能发生。
原始值与全部 ID 保持不变。内容标签仅按已确认的别名决策合并逻辑概念；同一概念的正确拼写及旧拼写不再视为需要附原文的语义冲突。未经批准的同名值和其他四字段仍分别判定。

| 字段 | 原始记录（含多 ID） | 唯一原值 | 可选原值／概念 | 原值同名组 | 真正冲突组 | 涉及原值 | 附原文后剩余组 | 完整清单 |
|---|---:|---:|---:|---:|---:|---:|---:|---|
| 内容标签 | 4783 | 4780 | 1127／1118 | 7 | 0 | 0 | 0 | [tag](tag-collisions.csv) |
| 原作 | 4402 | 4397 | 4397／4397 | 17 | 17 | 34 | 0 | [parody](parody-collisions.csv) |
| 角色 | 17989 | 17985 | 17985／17985 | 50 | 50 | 101 | 0 | [character](character-collisions.csv) |
| 画师 | 33406 | 33371 | 33371／33371 | 58 | 58 | 116 | 0 | [artist](artist-collisions.csv) |
| 社团 | 25428 | 25409 | 25409／25409 | 11 | 11 | 22 | 0 | [group](group-collisions.csv) |

共核对 **86,008 条记录 / 85,942 个不同原值**，对应 **85,933 个逻辑概念**。原值显示同名 **143 组 / 289 个原值**，扣除已批准别名概念后，存在 **136 组 / 273 个原值**真正显示冲突。

内容标签同时扫描全部详情标签和管理／搜索可选清单。只有可选清单内的值会应用内容标签译文，其余详情值保留原文。可选范围为 1127 个原值／1118 个概念，存在 0 组真正冲突；全部详情范围为 4780 个原值／4771 个概念，存在 0 组真正冲突。
四个人物／作品字段不套用内容标签清单，全部原值均参与排查。五字段合计精确相等 136 组，Unicode casefold 比较 136 组；其中 0 组涉及译名与保留原文之间的重名。

给索引内原值追加“（原文）”后，全量名称中跨逻辑概念的剩余冲突 **0 组**。脚本每次生成和检查都验证格式化后的逻辑概念唯一性；若未来的保留原文与带括号名称再次碰撞，命令会明确失败，并列出字段、显示值及相关原值。

## 内容标签语义复核与已确认别名

当前批准 **7 组／9 个旧拼写或语言别名**。[逐项语义复核](../tag-translations/content-label-review/README.md) 记录定义区分、规范原名、旧拼写和证据；[确认别名 CSV](tag-confirmed-aliases.csv) 保留每个成员及全部 ID。正常搜索使用规范原名，旧拼写由单独入口查询；本审计不把不同旧拼写的查询结果视为已联合搜索。

| 当前显示名 | 规范概念 | 保留的原始值 |
|---|---|---|
| 单一女性 | `sole female` | `sole female`、`sole femla`、`sole fmelae` |
| 变身 | `transformation` | `tranformation`、`transformation` |
| 总集篇 | `soushuuhen` | `soshuuhen`、`soushuuhen` |
| 第三方水印 | `watermarked` | `watermaked`、`watermarked` |
| 系列作 | `multi-work series` | `mult-work series`、`multi-work series` |
| 自慰 | `masturbation` | `masturbation`、`masturbtion`、`maturbation` |
| 触手 | `tentacles` | `shokushu`、`tentacles` |

## 内容标签剩余真正冲突

| 当前显示名 | 独立原始值 |
|---|---|
| 无 | 无 |

## 复现与来源

```sh
python3 scripts/check_translation_collisions.py
python3 scripts/check_translation_collisions.py --check
```

默认命令重新生成清单及运行时索引 `app/src/main/assets/tag_label_collisions_zh_CN.json`，`--check` 检查这些文件与当前数据是否完全一致。索引仅保存跨逻辑概念仍需附原文的原始名称，不给已确认别名重复加括号。脚本内同时检查字段隔离、多 ID 不重复计数、译名与原文碰撞、仅大小写不同、已批准别名与不同语义值的区分，以及附原文后再次碰撞的情况。

[真正冲突 CSV](collisions.csv) 按字段、显示名称和原始值记录全部剩余冲突；[原值同名 CSV](raw-duplicate-labels.csv) 另保留包含已合并概念的原值同名统计。`tag_ids` 保留所有原始 ID，`canonical_name` 仅对获批内容别名映射到规范概念，`translation_source` 记录当前译文来源，`metadata_reference` 指向已有完整审核记录。

[summary.json](summary.json) 记录原值与逻辑概念两种统计、精确／大小写折叠结果、可选范围统计及输入文件 SHA-256。源数据是 `data/tags.json`，有效名称来自应用中的 `tag_translations_zh_CN.json`、`entity_translations_zh_CN.json` 和 `content_tag_allowlist.json`；内容别名仅来自 `data/tag-translations/content-label-review/reviewed-decisions.json`。

本清单是静态全量数据排查；不代表 Android 下拉框、键盘和异步筛选时序已经通过真机测试。
