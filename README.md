# 英粤断句朗读

一个使用 Kotlin、Jetpack Compose、MiniMax 云端语音和可配置材料模型的安卓学习应用。它保留英语/粤语文章粘贴、自动断句、编辑和逐句朗读，并提供可离线保存的“智能材料”。

## 使用方法

1. 用 Android Studio 打开项目根目录并等待 Gradle 同步完成。
2. 连接 Android 8.0（API 26）或更高版本的设备，运行 `app`。Redmi K60 可直接侧载 Debug APK。
3. 首次使用前，到“设置”填写 MiniMax API Key；如需“智能材料”，还要填写至少一个材料模型的 Base URL、模型 ID 和 API Key。不要使用曾发到聊天或其他公开位置的旧密钥。
4. 在“智能材料 → AI生成”选择 English/粤语、主题和难度，点击“从固定来源生成 1 篇长文”。应用先从内置白名单 RSS 选文并在本地清洗，再把正文交给模型分级改写和翻译。
5. 在“智能材料 → 粘贴文章”粘贴并自动断句，可编辑、拆分、合并和标题命名；点击“保存到文章列表”后才会永久保存。
6. AI文章和手动文章统一显示在“文章列表”。AI文章按“目标语一句 → 简体普通话一句”播放，列表和详情会保存当前句、已完成句数和听力百分比。

## 材料模型与隐私

- 初次升级会建立 Wawa 配置（`https://wawazz.xyz`、`gpt-5.6-sol`）；可以添加、编辑、启停、删除和拖动排序其他 OpenAI Responses 兼容模型。
- 连接测试调用各提供商的 `GET /v1/models`；生成调用流式 `POST /v1/responses`。请求不再包含 `web_search`，模型只能使用客户端提交的已清洗来源段落。
- 英文来源来自 BBC、NASA 和 UN News；粤语来源来自香港政府新闻网、香港电台和香港政府新闻公报。应用并行读取 RSS/Atom、按主题和质量评分、排除近期 URL，并在最多五个候选间回退。
- HTML 正文由客户端去导航、广告、推荐、订阅提示和重复段落，清洗后的来源快照随草稿保存。模型只负责 IELTS 分级改写、粤语口语化、粤拼和简体翻译；每章必须返回准确的段落覆盖 ID。
- 每个模型每章只调用一次，失败后立即切换下一顺位。只要 SSE 持续有活动就继续等待；连续两分钟无活动才取消。HTTP 521 和无活动超时会跳过相同 Base URL。
- 按当前个人侧载方案，MiniMax 和材料模型 API Key 均以明文保存在应用私有 SharedPreferences；Key 不写入源码、APK、数据库或日志。应用备份和设备迁移明确排除全部应用数据。
- 旧版本加密保存的 Wawa Key 会在首次升级时迁移到新配置，随后删除旧密文。
- Room 在本机永久保存材料、逐句译文、粤拼、来源、模型响应、token 用量和听力进度。应用不后台刷新，也不自动删除历史。

## MiniMax 语音

应用不再使用 Android 系统 TTS，也不要求安装语音包或登录 Google。语音调用 MiniMax 官方接口 `https://api.minimaxi.com/v1/t2a_v2`，固定模型 `speech-2.8-turbo`，会按 MiniMax 账户计费。以下是旧配置自动采用的默认音色，三种语言均可在设置中独立更换：

- 英语：`Serene_Woman`，语言增强 `English`
- 粤语：`Cantonese_GentleLady`，语言增强 `Chinese,Yue`
- 简体普通话：`female-tianmei`，语言增强 `Chinese`

设置页通过 `POST /v1/get_voice` 同步账户音色目录，目录缓存 24 小时，断网时继续显示缓存及内置官方音色。音色通过全屏页面选择，选择后停留在页面内，可继续逐项试听；英语只显示普通英文、美国和英国口音。当前版本仅开放官方系统音色，账户复刻、设计和自定义收藏数据仍保留但不显示。

文章列表按 English / 粤语分别显示，并永久记住上次选择；筛选不会改变智能材料页的生成语言，也不会迁移或复制已有文章。

开始播放时会在后台提前缓存后续 3 句（AI文章同时缓存目标语和普通话）。文章列表长按可进入多选编辑模式，批量提前缓存语音或删除文章；批量生成未缓存语音前会提示可能产生 MiniMax 费用。

生成的 MP3 保存在应用私有目录，最多占用 500 MB，超限时按最久未使用顺序清理。缓存键包含文本、语言、语速、模型和 Voice ID，因此切换音色不会误用旧音频。命中缓存时不会重复请求，可断网播放；未命中缓存时必须联网并配置 MiniMax Key。设置页还可查看占用量和手动清空缓存。

## 构建与测试

项目使用 AGP 9.0.1、Gradle 9.1、`compileSdk 36.1`、`targetSdk 36` 和 `minSdk 26`。在 Android Studio 的内置 JDK 环境下可执行：

```powershell
.\gradlew.bat testUiTestUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
.\gradlew.bat assembleUiTest assembleUiTestAndroidTest
.\gradlew.bat connectedUiTestAndroidTest
```

Debug APK 输出到 `app/build/outputs/apk/debug/app-debug.apk`。设备端测试需要连接真机或启动模拟器。设备测试固定使用独立包名 `com.example.englishcantoneselearning.uitest`；不要把 AndroidTest 目标改回 Debug，否则测试任务卸载目标包时可能同时删除正式应用保存的 Key、文章和语音缓存。

## 安全覆盖更新

- 正式应用包名固定为 `com.example.englishcantoneselearning`，Debug 和 Release 都使用项目内 `signing/english-cantonese-update.jks` 的固定签名。必须安全备份这份签名文件。
- 每次发出新 APK 前必须提高 `app/build.gradle.kts` 中的 `versionCode`；不要卸载旧应用，直接安装新 APK 或使用 `adb install -r app-debug.apk` 覆盖。
- Room 数据库禁止破坏性迁移。修改表结构时必须提高数据库版本并提供完整 `Migration`；缺少迁移时应用会拒绝打开数据库，而不是静默清空材料。
- 把 APK 第一次安装到另一台手机不会自动复制旧手机的数据，因为应用按隐私要求禁用了云备份。该手机首次安装后，今后使用相同签名的更高版本 APK 覆盖更新会保留它自己的 Key、材料和缓存。

固定来源、正文清洗和模型网关测试使用 MockWebServer，不会把测试密钥发到互联网，也不会消耗模型 token。只有用户在应用内主动生成或播放未缓存语音时才会调用已配置的收费接口。
