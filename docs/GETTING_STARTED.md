# 使用与构建

[返回首页](../README.md) · [架构与取舍](ARCHITECTURE.md) · [验证记录](VERIFICATION.md)

## 安装与第一次采集

最低版本为 **Android 16 / API 36**。已记录的目标设备为真我 GT8、Android 16、realme UI 7.0；其他设备兼容性未作全面验证。

从 [GitHub Releases](https://github.com/liwenzhonhg-arch/auto-ledger/releases) 获取 Debug 测试 APK。仓库当前为私有，下载需要访问权限；应用内部版本号为 `0.1`，日期标签区分构建。更新使用同签名覆盖安装，清除数据或卸载会删除本地账本；当前没有备份、恢复和导出功能。

1. 打开“采集 → 通知访问”，在系统设置授权 Auto Ledger 通知采集。
2. 返回应用，确认通知状态为“已连接”。此后微信通知由系统后台投递时自动进入解析，无需每笔点击采集。
3. 需要无通知场景的页面采集时，开启“Auto Ledger 微信只读采集”系统无障碍权限，并打开应用内“微信自动记账”。
4. 手动进入微信单笔支付结果或详情页，返回 Auto Ledger 查看“已保存 · 待确认”，再到待确认页核对金额、对象和分类。

“收到内容”“正在识别”、系统付款码监控提示均不代表账目已保存。页面时间当前为采集时间；90 秒窗口和重复候选的边界见 [架构文档](ARCHITECTURE.md#1-缺少交易编号时如何处理重复采集)。

## 使用虚构数据演示

无需发起真实支付，也无需授权通知访问。使用 Debug 版现有入口：

1. 在“采集”页点击“模拟微信付款通知”，注入固定虚构样本 **测试商户 / ￥12.34**。
2. 返回首页，查看新增待确认记录和月度汇总。
3. 进入待确认，修改金额、对象或分类，然后确认入账。
4. 重新打开应用，观察记录仍然存在。

同一分钟内重复生成相同模拟文本会被通知指纹合并；跨分钟再点可能产生新的样本。去重演示必须注意这一时间条件。

“清除模拟账目”仅按对象等于固定值“测试商户”删除；如果演示时修改了对象，记录就不会被该入口清除。建议在不含真实账本的测试环境中演示。

模拟入口复用单次内存样本入口和 Repository，不发布系统通知、不伪装微信包名、不持久化原始通知。它能演示应用内流程，不能验证系统通知投递或 OCR。

## 采集与调试

### 通知样本

“采集下一条微信通知”只控制一份原始样本的临时展示，包含 `title`、`text`、`bigText`、`subText`、`textLines`。自动解析独立运行。样本只在进程内存中，可手动清除。

### 页面样本

在“采集微信交易页面”中点击“开始并打开微信”，手动停留在单笔交易页面，再从最近任务切回 Auto Ledger。该入口查看一次内存样本；自动采集仍由总开关控制。

服务的 XML 与代码均限制为 `com.tencent.mm`，只声明窗口内容读取与截图能力，不操作微信界面。自动模式优先读取文字，不能形成候选时使用事件触发的单帧本地 OCR。多笔历史账单页会被忽略。

### 单次屏幕 OCR

手动停留在微信交易页，再切回 Auto Ledger，点击“截取当前微信页面”，同意 Android 单次屏幕共享提示。应用切回微信，只处理一帧并释放投屏会话；OCR 完成后回收位图。

这一入口当前只展示识别样本，**不会自动生成待确认账目**。截图和完整识别原文不写入文件、数据库或日志。

## 后台中断与恢复

| 观察到的状态 | 含义与操作 |
| --- | --- |
| 未授权 | 进入对应系统设置开启授权 |
| 通知已授权、未连接 | 尝试“重新连接通知”；是否成功以系统回调状态为准 |
| 界面采集未运行 | 同时检查系统无障碍授权、应用内开关、服务连接与微信前台状态 |
| 已保存 · 待确认 | 本次已写入；到待确认页核对 |
| 重复忽略 | 当前指纹已存在；结合采集时间判断是否需要人工核对 |
| 识别／保存失败 | 查看独立最近失败时间；之后成功记录不会被普通页面事件覆盖 |

“最近处理”最多保留 12 条来源、结果和时间，进程重启后清空。应用返回前台或通知服务断开时会在授权内请求重绑；重新连接后仅能补读仍保留的微信通知。

realme 后台活动与自启动设置以设备系统入口为准。强制停止后需要重新打开应用，系统无障碍授权被关闭后需要手动恢复。本应用不会自动改权限或定时拉活。

## 从源码构建

### 环境

| 工具 | 项目配置 |
| --- | --- |
| Android SDK | `compileSdk` / `targetSdk` / `minSdk` 均为 36 |
| JDK | 17 或兼容版本；已验证使用 Android Studio 自带 JBR |
| Gradle | Wrapper 9.1.0 |
| Android Gradle Plugin | 9.0.1 |
| Kotlin / Compose BOM | 2.3.20 / 2026.03.01 |
| Room / Tesseract4Android | 2.8.4 / 4.9.0 |

完整依赖版本以 [Version Catalog](../gradle/libs.versions.toml) 为准。

1. 获取仓库后，用 Android Studio 打开项目根目录。
2. 在 SDK Manager 安装 Android SDK Platform 36 及所需 Build Tools。
3. 使用 Android Studio 生成本机的 `local.properties`，或配置 `ANDROID_HOME` 指向 SDK；本机路径文件不提交。
4. 配置 Gradle JDK 并完成同步。首次构建需要联网下载 Gradle、Maven 依赖；OCR 模型已随源码提供，应用运行时不下载模型。

Windows PowerShell：

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon --console=plain
```

macOS / Linux（相同 Gradle 任务；本仓库记录的实际验证环境为 Windows）：

```bash
# 先将 JAVA_HOME 指向 JDK，并配置本机 Android SDK 路径
bash ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon --console=plain
```

APK 位于 `app/build/outputs/apk/debug/app-debug.apk`，测试报告位于 `app/build/reports/tests/testDebugUnitTest/`，Lint 报告位于 `app/build/reports/lint-results-debug.html`。

**设备测试边界**：不要在存有真实账本的手机上执行 `connectedDebugAndroidTest`。Gradle 仪器测试安装流程可能卸载目标 Debug 包并清除账本与系统授权。仪器测试只使用模拟器、专用无真实数据设备或独立测试包。

## 继续开发

解析器输入使用脱敏样本。新增解析分支、金额处理和去重逻辑时补最小可运行测试；不根据未观察到的支付页面猜测格式。金额保持整数分，数据库保持 Room v1，不持久化原始内容。

当前开发顺序与更完整的约束见 [AGENTS.md](../AGENTS.md)；已完成和待验证事项见 [验证记录](VERIFICATION.md)。
