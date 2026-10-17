# OCI 云端守护者 / OCI Cloud Commander

Kotlin + Jetpack Compose 原生 Android 离线教育游戏。玩法是城市资源图、OCI 仿真 Console、白名单 Terminal 和故障诊断。**非 Oracle 官方产品，不连接真实 OCI，不需要账号或密钥，无真实费用。**

当前交付为 **Phase 0–2 增量版（v0.2.1，L01–L30，第 1–5 章）**，不是完整 60 关成品。L31–L60 没有伪造关卡文件或占位通关页面。完整产品规格保存在 [docs/OCI_Cloud_Commander_Android_Development_Spec.md](docs/OCI_Cloud_Commander_Android_Development_Spec.md)。


**通关攻略**：[第 1–30 关完整通关秘籍（Console 逐关操作、三星路径和故障排查）](docs/COMPLETE_WALKTHROUGH.md)。

## 当前实现

- 横屏 Android 10+；Compose Material 3 / Navigation / ViewModel / StateFlow。
- 30 关初始快照、简体中文剧情、操作步骤、分级提示、实时目标、安全约束、失败条件、可选证据目标、三星评分与章节依赖解锁。
- Compartment / Group / Policy / Dynamic Group / Instance Principal 教学子集，默认拒绝及正反授权探测。
- VCN / Subnet、IPv4 CIDR、冲突检测、最长前缀匹配、IGW / NAT / Service Gateway、Route Table、NSG、监听端口和回程规则。
- 原创 Canvas 资源图：点击编辑、长按拖动、双指缩放/平移；布局不参与网络计算。Console 使用实际字段创建和编辑资源，并确认危险删除。
- Phase 2：Compute 电源状态机与 Shape/Image、SSH 分层诊断、Bastion Session、Block Volume 挂载与容量、卷备份/隔离恢复、Object Storage 私有默认与生命周期、训练模式数据库（仅 Vault 引用凭证、备份、恢复点、RPO）、Load Balancer 健康检查/容量/超时雪崩、DNS A/CNAME、TLS 有效期与主机名、端到端 HTTPS 门户探测、教学 Credits 成本模型。Console 显示规则实时推导的运行状态，Compute 有 START/STOP。
- Terminal 支持 OCI 查询、`inspect <principal> <id>` 与 `curl --from web http://app:8080`、`ssh --bastion bastion operator@app`、`df -h app`、`oci lb backend-health lb`、`dig`、`curl https://www.starport.example/`、`advance 5` 等教学命令；无真实 shell。
- 所有入口提交 GameAction；失败事务不产生半写入；派生状态禁止外部写入。撤销/重做、Room 快照与动作历史、加载时重放一致性校验、DataStore 设置。
- 暗色/浅色、高对比强调色、系统字号、可选设备离线中文 TTS、仅关卡前台可选常亮。

初次游戏：主页 → 继续游戏 → Console 选择东京 Region → Mission 阅读概念卡；Console 选择资源并 Inspect 收集可选证据。之后在章节星图解锁下一关。创建资源时查看字段下的格式说明。Nova 提示需要点击领取，不提供一键修复。

## 工程结构

```text
app/                  Android UI、导航与生命周期适配
core/model/           可序列化 WorldState、Resource、GameAction、Level DTO
core/simulator/       纯事务 reducer、IAM、网络、PredicateRegistry、评分
core/command/         终端白名单 parser 与当前世界查询
core/content/         DTO 加载、语义校验、依赖 DAG 校验
core/persistence/     Room 存档/进度与 DataStore 设置
core/testing/         Headless 关卡验证、领域规则和安全反例
  fixtures/           测试专用 action sequences（不进入 APK）
content/chapters/      ch01–ch05 的 30 个实际关卡 JSON
content/schemas/       draft 2020-12 Schema
content/locales/       zh-CN；en-US/ja-JP 仅预留状态标记
scripts/              构建、Schema 验证、generate_phase2.py（L13–L30 内容生成源）
.github/workflows/    Android CI 与模拟器测试定义
```

首版把功能 UI 集中于 `app`，未建立空的 feature 模块冒充功能。扩展边界见 [architecture.md](docs/architecture.md)。

## 构建

已验证工具链：JDK 17、Gradle Wrapper 8.9、AGP 8.7.3、Kotlin/Compose compiler 2.0.20、Compose BOM 2024.09.00、compile/target SDK 35、min SDK 29。API 35 是本机已安装且与 AGP 8.7 兼容的基线，**不声称是 2026 年最新 SDK**；升级 SDK/AGP 并重新验证列入后续工作。

安装 Android SDK 并设置 `ANDROID_HOME` 或本地 `local.properties`（该文件不入库），安装 `platforms;android-35` 与 `build-tools;35.0.0`。首次构建下载依赖需要网络；游戏运行不需要网络。

```powershell
python -m pip install -r scripts/requirements.txt
.\gradlew.bat lintDebug testDebugUnitTest assembleDebug validateContent solveLevels
# 或 .\scripts\build-debug.ps1
```

```sh
python -m pip install -r scripts/requirements.txt
chmod +x gradlew
./gradlew lintDebug testDebugUnitTest assembleDebug validateContent solveLevels
# 或 sh scripts/build-debug.sh
```

若 Python 不在 PATH，用 `-PpythonExecutable=/absolute/path/to/python`。Linux/macOS 使用 JDK 17。

APK：`app/build/outputs/apk/debug/app-debug.apk`（Gradle Debug 签名，可以安装，禁止作为正式发布签名）。

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.example.ocicloudcommander/.MainActivity
```

## 验证状态

2026-10-17 本地已执行并通过（详见 [test-results.md](docs/test-results.md)）：

- `lintDebug`：0 errors，12 warnings（旧版 Navigation Lint 兼容性与 SDK 基线警告，未使用 baseline）。
- `:core:testing:test`（由 `validateContent` / `solveLevels` 执行）：246 tests，0 failures。含 30 关逐关 golden 三星、negative 零星、初始不通关、逐个遗漏必要修复、Boss 独立修复、防派生值篡改、序列化重放；Phase 2 规则单测；Schema 与 Predicate 注册表一致性；所有 Predicate 正反例。
- `validateContent`：30 份 JSON Schema + 运行时语义校验通过；五章各 6 关。
- `assembleDebug`、`assembleDebugAndroidTest`：成功。
- `connectedDebugAndroidTest`（本次独立 API 35 x86_64 模拟器）：2 tests 通过——L01 真实控件 + 旋转/冷启动恢复；L25 通过实际 Console 表单和 Terminal 控件获得三星。只有解锁前置关卡与选关使用测试准备。

`./gradlew verifyFullGame` **目前必须失败**：要求恰好 60 关，当前 30 关。仓库包含 CI 配置；远程运行结果以 GitHub Actions 为准，本地通过不代表远程 CI 已 green。

## 后续阶段与已知差异

[实施与验收清单](docs/implementation-status.md)、[规则目录](docs/rules-catalog.md)、[规则精度](docs/oci-fidelity.md)、[测试计划](docs/test-plan.md)。

- Phase 3 / L31–L42：Metric/Alarm/Logging、Notifications、SMTP Email Delivery 尚未实现；Incident 当前为服务摘要 + 动作/探测事件流。
- Phase 4 / L43–L54：OKE/K8s RBAC、kubectl、Terraform、Vault/Audit 尚未实现，命令 parser 会明确拒绝。
- Phase 5 / L55–L60：灾备、多约束综合事故、自由实验室、独立故障挑战、结局尚未实现。
- 完整语音/声音/震动设置、多语言翻译、资源端点连线手势、完整无障碍与平板验证待补充。

不得输入真实密码、真实私钥或真实 OCI 凭证。这里只接受教学短 ID 和模拟别名。

本次 Phase 2 复核修正了网络/端口/重定向、配置校验、备份数据恢复点、生命周期误通关和 Locale 确定性问题；详情见 [phase2-review.md](docs/phase2-review.md)。L25 设备测试现使用真实 Console 表单及 Terminal 输入，不再直接派发修复动作。
