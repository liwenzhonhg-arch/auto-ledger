# 验证记录

[返回首页](../README.md) · [架构与取舍](ARCHITECTURE.md) · [使用与构建](GETTING_STARTED.md)

记录日期：**2026-10-09**。本页区分本地检查、此前真机观察与待验证项目；文档美化不增加设备覆盖或采集可靠性证据。

## 本地检查

| 项目 | 结果 | 证据位置 |
| --- | --- | --- |
| 单元测试 | 21 个通过，0 失败 | `app/build/test-results/testDebugUnitTest/TEST-*.xml` |
| Android Lint | 0 errors / 12 warnings | `app/build/reports/lint-results-debug.xml` |
| Debug APK | 构建通过 | `app/build/outputs/apk/debug/app-debug.apk` |
| 网络权限 | 合并 Manifest 无 `INTERNET`、`ACCESS_NETWORK_STATE` | `app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml` |
| 备份与迁移 | `allowBackup=false`，云备份和设备迁移排除全部配置域 | [Manifest](../app/src/main/AndroidManifest.xml) · [提取规则](../app/src/main/res/xml/data_extraction_rules.xml) · [备份规则](../app/src/main/res/xml/backup_rules.xml) |
| 数据结构 | Room v1，唯一指纹索引 | [schema](../app/schemas/com.liwenzhong.autoledger.repository.AutoLedgerDatabase/1.json) |

本次文档整理重新执行了下列任务，构建成功；测试与多数构建任务命中 Gradle 的 `UP-TO-DATE` 缓存，21 项数字来自同一应用源码对应的现有 XML 报告。没有把缓存检查写成重新执行的真机测试。

本地报告与 APK 属于构建输出，不纳入版本控制。Lint 保留依赖版本、资源与 KTX 写法等建议，不标为零告警；构建另有 SDK XML 工具版本提示，未阻断任务。

复现命令：

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon --console=plain
```

## 测试覆盖哪些边界

| 边界 | 对应测试 |
| --- | --- |
| 非微信通知立即忽略 | [NotificationCaptureGateTest](../app/src/test/java/com/liwenzhong/autoledger/notification/NotificationCaptureGateTest.kt) |
| 样本单次采集、重连条件 | [NotificationSampleStoreTest](../app/src/test/java/com/liwenzhong/autoledger/notification/NotificationSampleStoreTest.kt) · [NotificationReconnectTest](../app/src/test/java/com/liwenzhong/autoledger/notification/NotificationReconnectTest.kt) |
| 微信事件、当前窗口与采集开关门禁 | [AccessibilityCaptureGateTest](../app/src/test/java/com/liwenzhong/autoledger/accessibility/AccessibilityCaptureGateTest.kt) |
| 金额解析与通知交易标记 | [WeChatNotificationParserTest](../app/src/test/java/com/liwenzhong/autoledger/parser/WeChatNotificationParserTest.kt) |
| 单笔／多笔页面、金额顺序、负号详情 | [WeChatScreenParserTest](../app/src/test/java/com/liwenzhong/autoledger/parser/WeChatScreenParserTest.kt) |
| OCR 文本整理与深色背景判断 | [OcrTextFormatterTest](../app/src/test/java/com/liwenzhong/autoledger/capture/OcrTextFormatterTest.kt) · [OcrBackgroundTest](../app/src/test/java/com/liwenzhong/autoledger/capture/OcrBackgroundTest.kt) |
| 90 秒边界、乱序与旧指纹 | [ScreenObservationDeduplicationTest](../app/src/test/java/com/liwenzhong/autoledger/repository/ScreenObservationDeduplicationTest.kt) |
| 写入失败后继续处理、取消传播 | [IngestionRecoveryTest](../app/src/test/java/com/liwenzhong/autoledger/repository/IngestionRecoveryTest.kt) |
| 最近保存／失败独立保留 | [CaptureDiagnosticsTest](../app/src/test/java/com/liwenzhong/autoledger/capture/CaptureDiagnosticsTest.kt) |
| 分类展示与支出汇总 | [TransactionDisplayTest](../app/src/test/java/com/liwenzhong/autoledger/ui/TransactionDisplayTest.kt) |
| 保留的内存存储实现 | [InMemoryLedgerStoreTest](../app/src/test/java/com/liwenzhong/autoledger/repository/InMemoryLedgerStoreTest.kt) |

另有 [TransactionDatabaseTest](../app/src/androidTest/java/com/liwenzhong/autoledger/repository/TransactionDatabaseTest.kt) 覆盖 Room 唯一索引、确认更新、固定模拟数据清理和并发采集。测试代码存在、可以编译，不代表全部设备端场景已执行；并发项仍待验证。

## 已记录的真机场景

以下为项目在 **2026-10-09** 留存的人工验证记录，设备为真我 GT8 / Android 16 / realme UI 7.0，本次文档整理未重做这些操作。原始支付页面与交易内容不作为仓库展示素材。

| 场景 | 当时观察 |
| --- | --- |
| 同签名覆盖安装 | 安装成功，原有账目数量保持一致 |
| 首页与采集页 | 暗色界面正常展示 |
| 恢复系统通知授权 | 首页显示通知已连接、界面采集运行中 |
| 微信单笔详情，页面无可读文字 | 单帧本地 OCR 后生成待确认记录 |
| 90 秒内再次打开同一详情 | 显示重复忽略，账目数量不变 |
| 多笔历史账单页 | 被忽略，未按多笔金额批量建账 |

此前还验证过微信交易通知的后台解析以及 Debug 虚构样本的持久化流程。该历史结果不替代当前版本新支付通知的端到端回归，也不代表厂商后台限制下的长期稳定性。

## 尚待验证

- 飞行模式下当前核心链路的完整设备端验收；导出尚未实现，不能提前验收。
- 长时间后台运行与厂商电池管理下的采集稳定性。
- 当前版本新支付通知从系统投递到待确认记录的端到端回归。
- Room 并发仪器测试在无真实数据的专用设备或模拟器上执行。
- 其他 Android 设备、微信页面版本与更多真实脱敏样本。

## 能力边界

开发版没有 OCR 准确率、耗电或延迟的量化基准，不宣称覆盖所有微信交易。页面指纹不是交易编号，90 秒窗口只能合并一段重复采集。对象未知时保留“未确定”，页面时间为采集时间；用户仍需核对。

自动分类、商户记忆、其他支付来源、手动记账保存、CSV 导出、备份与恢复均未完成。首页和统计页包含待确认数据。用户主动的单次屏幕 OCR 当前只展示内存样本，不自动入库。
