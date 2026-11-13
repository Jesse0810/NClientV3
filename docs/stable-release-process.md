# 正式版构建、签名与发布

正式版使用 `com.maxwai.nclientv3` 包名、`X.Y.Z-release` 版本名及固定签名证书。Debug 测试版使用独立的 `.debug` 包名；用户可先保留测试版，再通过导出／导入迁移数据。仓库中的 `config/release-signing-certificate.pem` 和 `.sha256` 仅包含公开证书及其指纹。

## 构建

1. 为已核验的代码准备 `docs/releases/vX.Y.Z.md`，提交后创建正式标签 `vX.Y.Z`。
2. `Build stable release APKs` 工作流运行书签、名称表、重名、别名和更新选择检查，使用 JDK 17 构建 Android 9+ 与 Android 8 两个 Release APK。Release 启用 R8 和资源收缩。
3. 工作流以 `-PunsignedRelease=true` 关闭签名，不读取或上传私钥。`stable-unsigned-apks` 工件包含两个带 `_unsigned.apk` 后缀的构建产物、`SHA256SUMS.txt` 和 `verification.json`；这些工件尚不可作为已签名正式安装包发布。

本地重复构建同样可以执行：

```bash
bash gradlew --no-daemon --max-workers=2 -PunsignedRelease=true assemblePost28Release assemblePre28Release
python3 scripts/verify_release_apks.py --version X.Y.Z --stage-dir release-assets
```

构建校验覆盖正式包名、版本号与版本码、系统要求、非调试标志、四个内嵌标签名称／批准名单文件及单字符下拉提示配置。

## 固定签名与核验

签名私钥、密码和备份放在仓库之外的私有存储。私钥必须长期保留，后续正式版继续使用同一个签名；更换签名将影响已有用户覆盖更新。私钥及包含密码的备份不得提交到 Git、上传到 Releases 或普通构建工件。

从成功的对应标签工作流下载 `stable-unsigned-apks`，使用私有凭据文件及固定私钥执行签名脚本。脚本先验证输入 APK 和 SHA-256，再通过 Android SDK 的 `zipalign` 和 `apksigner` 签名：

```bash
python3 scripts/sign_release_apks.py --version X.Y.Z --input-dir /private/unsigned-apks --output-dir /private/signed-apks --keystore /private/release.jks --credentials-file /private/release-credentials.json
```

最终安装包命名为：

- `NClientV3_vX.Y.Z_android9plus.apk`
- `NClientV3_vX.Y.Z_android8.apk`

签名后运行：

```bash
python3 scripts/verify_release_apks.py --version X.Y.Z --signed --apk-dir /private/signed-apks --stage-dir /private/publish-assets
```

该命令进一步执行 `apksigner verify`，要求两个 APK 均由仓库固定公开证书签名，并生成最终 `SHA256SUMS.txt` 和核验记录。输入目录已有校验清单时会先校验，不会覆盖不一致的清单。

## 发布

创建对应标签的 GitHub Release 草稿，上传两个已签名 APK 和最终 `SHA256SUMS.txt`。重新下载全部附件，检查校验清单、版本、系统要求、固定证书与内嵌数据，确认后将其公开为非预发布版本并设为 Latest。

示例下载核验命令：

```bash
python3 scripts/verify_release_apks.py --version X.Y.Z --signed --apk-dir /private/downloaded-assets --report /private/downloaded-assets/verification.json
```

应用内更新只从本仓库正式版本中选择匹配系统的已签名 APK，跳过 Debug、预发布及校验文件。

当前发布流程由私有签名环境完成最终签名与上传。若未来改为 CI 自动签名，应由仓库管理员将私钥和密码配置为受保护的 GitHub Actions Secrets，保留签名证书核验；不可把密钥写入工作流或仓库。仅有代码／发布权限的集成可能无法访问或配置 Secrets，此时继续采用私有签名流程。
