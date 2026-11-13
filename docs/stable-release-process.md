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

最终签名始终在私有签名环境完成。如果当前集成可以创建 Release、但无法向 `uploads.github.com` 上传附件，可以通过 `Publish signed stable APKs` 工作流完成上传。它使用 Actions 内置发布凭据，流程如下：

1. 从包含发布工作流的最新主分支创建临时分支 `release-assets/vX.Y.Z`。
2. 该分支只新增 `release-uploads/` 下四个公开发布文件：两个最终已签名 APK、对应 `SHA256SUMS.txt` 及 `manifest.json`。不传输私钥、密码、私有备份或未签名工件。
3. `manifest.json` 仅包含 `tag` 和 `source_commit` 两个字段，例如 `{"tag":"v4.2.14","source_commit":"bf7bfa515fa3d786791605cae819c8164116503c"}`。提交号必须是正式标签所指向的完整提交。
4. 推送临时分支后，工作流检出正式标签，核对清单、提交、文件集合、签名和 APK 内容。它只接受不存在或尚无附件的 Release 草稿，拒绝覆盖已有附件或公开版本。
5. 工作流上传两个 APK 和 SHA-256 清单，重新下载并验证后，公开为非预发布版本并设为 Latest。发布成功后删除临时传输分支。

如果上传中断而草稿已包含部分附件，工作流会停止，保留草稿供检查，不会自动覆盖或公开不完整的版本。

若未来改为 CI 自动签名，应由仓库管理员将私钥和密码配置为受保护的 GitHub Actions Secrets，保留签名证书核验；不可把密钥写入工作流或仓库。仅有代码／发布权限的集成可能无法访问或配置 Secrets，此时继续采用私有签名流程。
