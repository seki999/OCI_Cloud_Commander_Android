# 内容开发约定

每个真实可玩的关卡在 content/chapters/chXX/level-NNN.json 注册，并进入 content/index.json；未实现关卡不加入 index。不生成标题空壳占位。

1. 使用 draft 2020-12 Schema，额外属性默认拒绝。
2. initialWorld 使用 WorldState DTO，resources 为 ID→Resource map，与运行态一致。properties 的字符串是输入字段格式，按 Kind 运行时语义校验。
3. products 显式列出任务将创建的资源 ID/Kind。所有目标引用必须为初始资源、products 或已知网络教学端点；引用指向将来产物不等于该目标已经通过。
4. Predicate 参数必须与注册集合完全一致。本地化 title/brief/steps/hints/tutorial/message 全部可解析；IDs 唯一、章节/order 对应、依赖 DAG 无循环。
5. required objectives 决定基础过关；安全红线也放入 required 或 failureTriggers，不能让严重越权获得一星。constraints 对更高星级生效。optional 用于证据/更优配置。每关 reward 和 optimalActionLimit 显式定义。
6. 测试专用 fixtures 位于 core/testing/fixtures，包含真实 GameAction golden/negative sequences，不打包到 APK。脚本不准写 health、status、Ready、delivered 或 objective 标志。
7. 修改 schema/字段/Predicate/Action 时同步模型、语义验证、模拟规则和正反例。升级破坏旧存档时必须增加版本并实现迁移或明确拒绝。

L01–L12 的 JSON 已在生成后经多轮加固脚本修改，`generate_phase1.py` 不再是可直接重跑的来源，请直接编辑这 12 份 JSON。L13–L30 的唯一来源是 `python scripts/generate_phase2.py`（同时生成测试 fixtures、合并 zh-CN、重建 index 与 Schema 注册表）；不要手改其输出。运行 `python scripts/validate_content.py`、`./gradlew validateContent solveLevels` 验证。

`python scripts/validate_content.py --require-60` 和 `./gradlew verifyFullGame` 是完整产品门禁，当前阶段预期失败。增加 Phase 2 时不能降低门禁中的 60 要求；阶段验收集扩展到 30/42/54，最终 60。

新增 Predicate：storage.lifecycleConfigured(bucket, prefix, archiveDays, deleteDays)，用于验证规则意图，避免有限样本恰巧匹配而误通关。DB_BACKUP 的 pointInTime 属派生字段，禁止在 Action 中写入；旧初始备份可省略并回退其 createdTick。
