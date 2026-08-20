# 相念 · E-ink Photo App

“相念”是配套电子墨水相框的 Android 应用，用于在手机上管理本地相册、AI 相册、信息看板、设备网络与电源设置。

当前正式版本：**v2.0.8**（versionCode 26）

## 主要功能

- 本地相册：从手机相册批量导入、横竖屏构图适配、六色预览、转换后保存到相框 TF 卡。
- AI 相册：手机直连图像模型生成或进行照片风格转换，再上传最终成品到相框。
- 信息看板：天气日期、待办和轻量综合看板，可显示到电子纸。
- 轮播与休眠：设置相册轮播、自动休眠与手动唤醒后的状态。
- 小智语音：支持相册模式切换、下一张、状态查询、彩蛋等已接入语音操作。
- 网络与设备：管理相框 STA/AP 连接、设备状态、电池和充电信息。

## 使用前提

1. 手机与相框连接到同一局域网时，App 会优先通过局域网连接。
2. 手机连接相框 AP 热点时，App 会切换为 AP 直连。
3. 首次使用请在 App 的网络配置中确认设备地址与连接状态。
4. AI 生图 API Key 仅保存在手机本地，不上传到相框。

## 本地相册方向说明

- 竖屏图片以竖屏预览和竖屏相框展示。
- 横屏图片以横屏预览和横屏相框展示。
- 已入库的旧图片如方向记录不正确，可在“设备媒体详情”中使用“预览方向校正”；该校正仅影响 App 预览，不修改 TF 卡中的图片、名称或电子纸内容。

## 构建

环境要求：JDK 17、Android SDK。

    $env:JAVA_HOME='E:\codex\2026_development\esp-E-ink\app\.build-tools\jdk17'
    $env:Path="$env:JAVA_HOME\bin;$env:Path"
    .\gradlew.bat --no-daemon '-Dkotlin.compiler.execution.strategy=in-process' :app:assembleRelease --console=plain

Release APK 输出位置：

    app/build/outputs/apk/release/app-release.apk

## 发布与更新

- 版本号：`app/build.gradle.kts`
- 稳定更新清单：`release-manifest/stable.json`
- 发布 APK 存档：`release-apks/`

发布时需要同步更新版本号、APK SHA-256、文件大小与更新说明，并上传对应版本的 Release Asset。

## 仓库结构

    app/                Android 应用源码
    release-apks/       已发布 APK 存档
    release-manifest/   App 内更新清单
    tools/              本地调试辅助工具

## 注意事项

- 电子纸刷新具有冷却保护，App 会阻止短时间内重复刷新。
- 相框重启或切换 Wi-Fi 后，IP 地址可能变化；请等待设备重新联网后再刷新 App 连接状态。
- 请勿在仓库、截图、日志或发布文件中提交 API Key、Wi-Fi 密码、账号密码等敏感信息。
