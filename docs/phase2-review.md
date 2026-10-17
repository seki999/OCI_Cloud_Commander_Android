# Phase 2 独立复核

本次检查直接针对仓库实际代码与 fixtures，不以先前汇报作为验收结果。原有测试确实通过，新增 L13–L30 也确实包含资源动作与派生规则；但原测试未覆盖以下边界，因此“全绿”不能证明这些配置正确。

## 已复现并修复

| 问题 | 原先行为 | 修正与验证 |
|---|---|---|
| LB NSG 出站被跳过 | 仅有 ingress 时后端仍健康，L28 仍可通过 | LB 必须满足 egress；同步 L25–L30 初始/修复配置与生成源；删掉出站规则后后端、端到端和关卡都失败 |
| URL 端口被忽略 | HTTPS :8443 / :0 / :65536 仍按 443 探测 | 保留显式端口并校验；教学子集只支持 HTTP 80 / HTTPS 443，其他端口明确失败 |
| HTTP 重定向只看 flag | 阻断 80 后仍满足重定向目标 | 同时检查公网至 HTTP Listener 的网络可达性 |
| Compute 必填配置未强制 | 缺 Shape / Image / key 仍进入 RUNNING；清空 Shape 使用隐藏默认容量/成本 | Phase 2 创建/更新强制有效 Shape / Image 与模拟密钥，非法事务保持原状态；Phase 1 教学端点兼容保留 |
| Bucket 默认值仅在表单 | 引擎收到未填 visibility 的创建请求会报错 | 引擎规范化为 private，不依赖 UI |
| 训练 DB 可落在公有子网 | 没有 Public IP 就被当成足够安全 | 训练模式 DB 强制 Private Subnet（教学范围限定） |
| 恢复点来源丢失 | 恢复库再次备份后，旧数据被标为最新时间 | createdTick 与 pointInTime 分离；还原时继承数据实际恢复点；新鲜度也按数据恢复点检查，不能重新打包旧数据来刷“新鲜” |
| L21 有限样本误通关 | archive:20 / delete:90 与样本年龄恰巧匹配，可冒充要求的 30/90 策略 | 新增 storage.lifecycleConfigured，核对阈值与重叠前缀冲突，而不仅看当前对象 tier |
| 事件依赖系统 Locale | 相同 inspect LB 在不同系统语言下产生不同数字格式，可能导致重放不一致 | 模拟事件与关卡 ID 使用 Locale.ROOT；新增 US/FR/Arabic Locale 回归 |
| L25 设备验收表述过强 | 原测试直接 vm.dispatch 创建后端/LB，仅检查页面可显示 | 改为实际 Console Kind/ID/字段/引用/创建控件及 Terminal 输入；只有前置解锁与选关使用准备代码 |

其中首批 5 个测试在旧实现中 5/5 失败；第二批关于重定向、Bucket 默认值、DB 子网的 3 个测试也先复现失败，再修复。L21 错阈值单独复现失败。测试源码保存在 core/testing/src/test/kotlin/commander/Phase2ReviewRegressionTest.kt。

## 复核范围与限制

- 当前仍为 30/60 关，Phase 3–5 未开始。本次没有通过空文件、选择题或写目标标志来补齐。
- 未实现的新服务与原来已记录的教学简化保留，不把它们描述为完整 OCI 行为。
- 本机原 echoloopa_test 的配置引用 API 34，但对应系统镜像当前不可用，因此这次没有重复声称运行该环境。使用新建的独立 commander_review_35（API 35）验证，原 AVD 配置不变。
- 修复内容与派生规则会改变旧快照的重放结果；未实现跨内容版本迁移。发生不一致时明确提示并可重新开始该关，已有最高星级进度保留。
- 详细构建与测试数字见 test-results.md。本记录只覆盖本机验证，不声称远程 CI green。
