# 与用户提供的插件词典逐项比对

**历史快照：下列结果记录的是调整为 fullDict 优先之前的应用词库。当前采用值与来源请查看 [本轮三来源总览](../inventory.md) 和 [fullDict 来源清单](../source-fulldict.md)。**

比较的是用户粘贴的 `fullDict` 快照，不是对在线脚本当前版本的实时抓取。
仅解析对象数据，没有执行上传的 JavaScript。原始输入 SHA-256：`978167d4296ce082cbc8f87e77d14ae8ad47bf069db3a73d6029ae0c6cdce3b0`。
规范化后的完整输入保存在 [provided-dictionary.json](provided-dictionary.json)。

| 项目 | 数量 |
| --- | ---: |
| 上传词典中的键值对 | 581 |
| 上传词典中的不同名称 | 581 |
| 重复键记录 | 0 |
| 插件词典对应当前内容标签的名称 | 567 |
| 当前应用提供显示名的内容标签 | 1392 |
| 双方都提供显示名 | 566 |
| 其中：逐字完全一致 | 241 |
| 其中：文字不同 | 325 |
| 仅插件有显示名、应用保留原名 | 1 |
| 仅应用有显示名、上传词典没有该词条 | 826 |
| 上传词典中不属于当前内容标签快照的词条 | 14 |

- [完整不同项对照：325 项](differences.md)
- [完整 CSV：包含一致、不同、仅一方及其他字段，共 1407 行](comparison.csv)
- [机器可读统计与文件指纹](summary.json)

比较按原始英文名称匹配、按显示字符串逐字判断。标为“不同”的条目不自动判定哪方正确。
部分是简写或同义表达，部分改变了身份、服饰或其他含义，需要逐项核对。
CSV 的“比较分类”列可以筛选出全部相同项、仅插件项或仅应用项。
此操作生成比对记录，不将插件译名自动导入应用词库。

## 插件独有的当前内容标签

| 原始标签名 | 当前应用显示 | 插件显示 |
| --- | --- | --- |
| <code>netorare</code> | netorare | NTR/被睡 |

## 语言／类别值（单独比较）

| 原始值 | 当前应用 | 插件 | 是否一致 |
| --- | --- | --- | --- |
| chinese | 中文 | 中文 | 是 |
| english | 英语 | 英文 | 否 |
| japanese | 日语 | 日文 | 否 |
| translated | 已翻译 | 已翻译 | 是 |

这份上传词典命中 Categories 的值数：0。

## 未在当前内容标签快照中找到的其他词条

这些条目不计入当前内容标签的一致率，也不据此推断插件译名有误。

| 原始名称 | 插件显示 |
| --- | --- |
| <code>anaglyph</code> | 红绿3D |
| <code>camel</code> | 骆驼 |
| <code>game sprite</code> | 游戏立绘 |
| <code>paperchild</code> | 纸片人 |
| <code>rewrite</code> | 改文 |
| <code>rhinoceros</code> | 犀牛 |
| <code>speechless</code> | 无台词 |
| <code>stereoscopic</code> | 立体图 |
| <code>steward</code> | 男乘务员 |
| <code>text cleaned</code> | 去字版 |

当前应用词库 SHA-256：`39b8dfbb9775587f37edc66633aa8275fe3acf7817169891d28b6e8ab1201ed3`。
