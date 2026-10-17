# 测试执行记录

## 2026-10-17 · v0.2.1 独立复核（本次实际执行）

环境：Windows / JDK 17 / Gradle 8.9 / AGP 8.7.3 / compile & target SDK 35。独立新建 commander_review_35（API 35、x86_64、Pixel 5），没有改动 echoloopa_test 的配置或数据。

```text
./gradlew.bat lintDebug testDebugUnitTest assembleDebug validateContent solveLevels connectedDebugAndroidTest -PpythonExecutable=<本机 Python 路径> --console=plain
BUILD SUCCESSFUL in 2m 4s
164 actionable tasks: 26 executed, 138 up-to-date
```

| 验证 | 结果 |
|---|---|
| JVM 领域 / 内容测试 | 246 tests，0 failures；新增 Phase2ReviewRegressionTest 12 项 |
| Schema + semantic / 关卡 | 30 份 draft 2020-12 校验通过；30 关 golden / negative / Boss / 重放等通过 |
| lintDebug | 0 errors / 12 warnings，未隐藏警告 |
| assembleDebug | 成功；v0.2.1 / versionCode 3 |
| API 35 connectedDebugAndroidTest | 2/2 通过（0 skipped / 0 failed） |

设备测试含 L01 实际控件、旋转重建与 Room 恢复；L25 实际选择 Kind、填写 ID/属性/引用、创建 Compute 和 LB，通过 Terminal 输入命令并获得三星。前置关卡解锁和选关作为测试准备；修复动作不再直接 vm.dispatch。上次 L25 用例只是 ViewModel/Room + 页面集成测试，不能作为“真实表单完成配置”的证据；本次已经替换。

本次先复现后修复了 LB egress、显式 URL 端口、Compute 缺配置/空 Shape、备份恢复点、HTTP 重定向、Bucket 默认值、DB 私网、L21 阈值与旧恢复数据的伪新鲜备份；另加跨 Locale 确定性回归。详情见 phase2-review.md。

APK：app/build/outputs/apk/debug/app-debug.apk

SHA-256：`37F0C85D5A4A1A20FC47F0B972B19456F3319BB581FB0A53A54640D0298B36E8`

报告：core/testing/build/reports/tests/test/index.html、app/build/reports/lint-results-debug.html、app/build/reports/androidTests/connected/debug/index.html。

限制：未测试 API 29 真机、平板、分屏、200% 字号、TalkBack、性能基准或设备重启。当前 30/60，不代表 Phase 3–5 完成；完整产品门禁仍预期失败。本记录只覆盖本机验证，不声称远程 CI 已执行。

---



## 2026-10-17 · v0.2.0（上次交付记录，保留参考）

环境：Windows 11、JDK 17.0.20、Gradle 8.9、AGP 8.7.3、compile/target SDK 35。

| 命令 | 结果 |
|---|---|
| `./gradlew lintDebug testDebugUnitTest assembleDebug validateContent solveLevels assembleDebugAndroidTest` | BUILD SUCCESSFUL；lint 0 errors / 12 warnings |
| `:core:testing:test --rerun` | 234 tests，0 failures |
| `python scripts/validate_content.py` | 30 份关卡文档通过 draft 2020-12 Schema |
| `connectedDebugAndroidTest`（API 34 x86_64 模拟器 `-read-only` 启动，横屏 2204×948 截图） | `OfflineJourneyTest`、`PortalJourneyTest` 2/2 通过 |

首次设备执行发现 `OfflineJourneyTest` 旋转后断言错误（期望回到主页，但导航栈恢复后正确停留在关卡页）；已改为断言关卡页与世界快照一致。截图检查发现横屏地图被标签栏遮挡，已改为单行横向滚动标签栏并重排节点。

未执行：API 29 / API 35+ 真机、平板、分屏、200% 字号、TalkBack、性能基准。`verifyFullGame` 按设计失败（30/60）。

## 2026-10-18 · GitHub Actions 环境修复

失败 run：39002042750（dc40801）。两个 job 均在 setup-android 阶段停止，日志为 `Warning: Failed to find package 'tools'`，随后 sdkmanager exit code 1；Gradle 未执行。没有 APK/报告可上传是该失败的后果，Node.js 和 ubuntu-latest 的提醒不是这次失败的直接原因。

修复：升级到已验证使用 Node 24 的 Actions；setup-android v4 显式安装 platform-tools / API 35 / build-tools 35.0.0，避免旧 tools 包；固定 ubuntu-24.04；APK 独立上传为 OCI-Cloud-Commander-debug-apk，报告上传允许在尚未产生文件时跳过。

本地 actionlint 与 git diff --check 通过。远端是否完整通过，以修复提交对应的 Actions run 为准，不能以本地检查代替远端构建。
