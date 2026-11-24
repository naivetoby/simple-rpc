# 发布到 Maven Central

## 发版步骤

1. 更新 `pom.xml` 的版本号（如 `3.4.11`），合并到 `main`。
2. 在 GitHub Releases 为该提交选择或创建同版本 tag（如 `v3.4.11`），填写更新说明，点击 **Publish release**。
3. 在 Actions 的 **Publish to Maven Central** 查看结果。[发布工作流](../.github/workflows/release.yml) 会校验 tag、提交和版本，运行完整测试，通过后签名并发布。只有发布正式 Release 才会触发发包。

## 首次配置

在 GitHub 仓库的 **Settings → Environments → `maven-central`** 中添加以下 Secrets：

| Secret | 内容 |
| --- | --- |
| `CENTRAL_USERNAME` | Central Portal 用户令牌的用户名，需有项目命名空间的发布权限 |
| `CENTRAL_PASSWORD` | 同一令牌的密码 |
| `GPG_PRIVATE_KEY` | ASCII 格式的签名私钥；对应公钥需已上传到公共密钥服务器 |
| `GPG_PASSPHRASE` | 签名私钥的口令 |
