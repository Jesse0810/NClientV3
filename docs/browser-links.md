# 打开浏览器链接修复

排查基线：`4193697`，4.2.11 测试版。

## 原因

主列表的“在浏览器中打开”一直将 `InspectorV3.getUrl()` 直接交给浏览器。旧版该值是网页地址，上游 `7d65d1e` 迁移到 API v2 后变成内部接口地址，但浏览器入口没有同步调整。这会影响书签、普通搜索、标签及在线收藏结果页。

## 修复

内部请求及书签仍使用 API URL；浏览器入口单独通过 `getBrowserUrl()` 生成网页 URL。

| 当前接口 | 网页 |
| --- | --- |
| `/api/v2/search?query=...` | `/search/?q=...` |
| `/api/v2/galleries?page=N` | `/?page=N` |
| `/api/v2/favorites?q=...&page=N` | `/favorites/?q=...&page=N` |
| `/api/v2/galleries/123` | `/g/123/` |
| `/api/v2/galleries/random` | `/random/` |
| `/api/v2/favorites/random` | `/favorites/random` |

- 转换使用当前 API URL 中的完整条件，包含标签、排除项、关键词、页数和上传时间范围，不从单独的关键词字段重新拼接。
- 保留页码、排序、镜像域名及端口，正确处理加号、百分号、引号、中文、Emoji 和范围操作符。查询仍使用原始值，不将界面译名写入 URL。
- 网页参数采用白名单，不转发 API 专用的 `include` 等参数；不修改数据库中的书签 URL。
- 浏览器按钮查询通用 HTTPS 处理程序，再显式指定外部包。存在默认浏览器时直接打开，否则选择浏览器，避免本 App 的网页关联重新接管链接。
- 无法找到浏览器或无法生成网页链接时显示提示，不退回打开 API 接口。

## 验证

- 266 项实际 URL 转换检查通过，已加入 `scripts/check_bookmarks.py`，普通 CI 和 APK 发布流程都会执行。
- 当前完整浏览器启动方法通过 399 项 JVM 断言，覆盖默认/单个/多个浏览器、显式包选择、系统选择器、无浏览器及启动失败；Android Intent、PackageManager、Activity 和 Toast 使用轻量桩。
- 现有 63 项书签查询/范围检查及数据库兼容检查通过。
- Android 9+、Android 8 两个 Debug APK 构建通过。

尚未进行真机浏览器或网站结果的端到端验证。
