# Auto Ledger

仅供本人 Android 手机使用的离线自动记账工具。

## 核心目标

监听微信、支付宝、云闪付、农行和招商银行的交易通知，在手机本地生成账目，并通过商户记忆和关键词规则离线分类。通知信息不足时进入待确认，不猜测入账。

## 隐私承诺

- 应用不申请网络权限。
- 不连接服务器，不使用云同步、广告、统计或在线 AI。
- 账本只保存在手机应用内部。
- 不读取短信，不使用无障碍服务。
- Android 云备份和数据迁移将被显式关闭。

## 当前状态

已创建 Android 16 / API 36 的最小 Kotlin + Jetpack Compose 工程，并完成首页界面预览：

- 本月收入、支出和待处理数量
- 最近账目列表
- 账目、图表和手动记账三个入口

当前页面使用演示数据，通知监听、数据库、自动分类和导出尚未实现。

## 目标设备

- 真我 GT8
- Android 16
- realme UI 7.0

## 本机构建

已安装 Android Studio Quail 2、Android SDK 36 和 Platform Tools。

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug --no-daemon --console=plain
```

Debug APK：`app\build\outputs\apk\debug\app-debug.apk`

下一步：在目标手机上采集脱敏后的微信真实交易通知结构，再实现微信通知采集、解析、去重和本地入账闭环。
