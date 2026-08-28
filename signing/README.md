# 更新签名文件

`english-cantonese-update.jks` 是本应用 Debug 和 Release APK 共用的固定更新签名。

- 必须连同整个项目一起备份；不要删除、替换或重新生成。
- 任何手机第一次安装本项目生成的 APK 后，后续 APK 只有继续使用这份签名并提高 `versionCode`，才能直接覆盖更新并保留应用私有数据。
- 丢失签名后，Android 会拒绝覆盖安装。此时只能卸载旧应用再安装，而卸载会删除 Key、材料数据库和语音缓存。
- 这是个人侧载用签名，不应拿去签署其他应用，也不要提交到公开代码仓库。

## 本地配置

1. 复制 `keystore.properties.example` 为 `keystore.properties`。
2. 保持 `storeFile` 指向原始 JKS，并填写真实密码与别名。
3. `keystore.properties` 和 JKS 都被根目录 `.gitignore` 排除。

缺少本地签名配置时，Debug 构建使用 Android 默认调试签名，方便全新 clone 运行测试；
Release 构建会明确失败，避免误发无法覆盖安装的 APK。
